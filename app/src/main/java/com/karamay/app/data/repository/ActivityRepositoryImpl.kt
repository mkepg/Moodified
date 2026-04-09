package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.service.TrackingService
import com.karamay.app.core.utils.BatteryUtils
import com.karamay.app.data.local.dao.activity.ActivityDailySummaryDao
import com.karamay.app.data.local.dao.activity.ActivityTelemetryDao
import com.karamay.app.data.local.datasource.ActivityPreferencesDataSource
import com.karamay.app.data.local.datasource.DeviceSensorDataSource
import com.karamay.app.data.local.entity.activity.ActivityDailySummaryEntity
import com.karamay.app.data.local.entity.activity.ActivityTelemetryEntity
import com.karamay.app.data.receiver.activity.ActivityReceiver
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.usecase.activity.CalculateActivityIntensityUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityTelemetryDao: ActivityTelemetryDao,
    private val activityDailySummaryDao: ActivityDailySummaryDao,
    private val preferencesDataSource: ActivityPreferencesDataSource,
    private val deviceSensorDataSource: DeviceSensorDataSource,
    private val calculateIntensity: CalculateActivityIntensityUseCase,
) : ActivityRepository {

    companion object {
        private const val TAG = "ActivityRepo"
        private const val UPDATE_INTERVAL_MS        = 60_000L
        private const val MAX_REPORT_LATENCY_US     = 5 * 60 * 1_000_000L
        private const val CADENCE_WINDOW_MS         = 60_000L
        private const val CADENCE_TICK_MS           = 10_000L
        private const val STALENESS_THRESHOLD_MS    = 35_000L
        private const val RECOGNITION_AUTHORITY_MS  = 70_000L
        private const val MAX_SEGMENT_RESTORE_MS    = 60 * 60_000L
    }

    private val scope         = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex    = Mutex()

    // Delegated to DataSource
    private val stepSensor: Sensor? = deviceSensorDataSource.getStepCounterSensor()
    private val activityRecognitionClient = deviceSensorDataSource.getActivityRecognitionClient()

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ActivityReceiver::class.java).apply {
                action = ActivityReceiver.ACTION_PROCESS_ACTIVITY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private var cadenceJob:         Job?             = null
    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY
    private var accelAvailable:     Boolean           = true
    private var stateEnteredAt:     Long              = 0L
    private var lastRecognitionTimestamp: Long        = 0L

    private val isRecognitionAuthoritative: Boolean
        get() = (System.currentTimeMillis() - lastRecognitionTimestamp) < RECOGNITION_AUTHORITY_MS

    private val cadenceWindow   = ArrayDeque<Pair<Long, Int>>()
    private var instantCadenceSpm: Int = 0

    private val _isTracking = AtomicBoolean(false)
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildInitialSignal())
    override fun observeSignal(): Flow<ActivitySignal> = _signal.asStateFlow()

    private fun buildInitialSignal(): ActivitySignal {
        val restoredIntensity = runCatching {
            ActivityIntensity.valueOf(preferencesDataSource.intensity)
        }.getOrDefault(ActivityIntensity.SEDENTARY)

        val activeMs    = preferencesDataSource.activeMs
        val sedentaryMs = preferencesDataSource.sedentaryMs
        val steps       = preferencesDataSource.sessionSteps

        return ActivitySignal(
            steps               = steps,
            intensity           = restoredIntensity,
            activeMinutes       = (activeMs / 60_000L).toInt(),
            sedentaryMinutes    = (sedentaryMs / 60_000L).toInt(),
            instantCadenceSpm   = 0,
            stepSensorAvailable = stepSensor != null,
            accelAvailable      = true,
            timestamp           = LocalDateTime.now(),
            isTracking          = preferencesDataSource.isTracking,
            hasActiveSession    = preferencesDataSource.isTracking || activeMs > 0L
                    || sedentaryMs > 0L || steps > 0
        )
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (!BatteryUtils.isIgnoringBatteryOptimizations(context)) {
            Log.w(TAG, "Battery optimisation active — tracking may be interrupted in Doze.")
        }
        if (_isTracking.getAndSet(true)) return true

        preferencesDataSource.isTracking = true
        _signal.update { it.copy(isTracking = true, hasActiveSession = true) }

        scope.launch {
            stateMutex.withLock {
                checkAndRolloverDay()
                committedIntensity = runCatching {
                    ActivityIntensity.valueOf(preferencesDataSource.intensity)
                }.getOrDefault(ActivityIntensity.SEDENTARY)

                val savedAnchor = preferencesDataSource.segmentStartMillis
                val nowMs       = System.currentTimeMillis()

                if (savedAnchor != -1L) {
                    val gap = nowMs - savedAnchor
                    val restoredDayKey = preferencesDataSource.dayKey
                    val todayKey       = LocalDate.now().toString()

                    if (restoredDayKey == todayKey && gap in 1L..MAX_SEGMENT_RESTORE_MS) {
                        when (committedIntensity) {
                            ActivityIntensity.SEDENTARY,
                            ActivityIntensity.IN_VEHICLE -> preferencesDataSource.sedentaryMs += gap
                            else                         -> preferencesDataSource.activeMs    += gap
                        }
                        Log.d(TAG, "Restored intra-day segment gap of ${gap}ms for $committedIntensity")
                    } else {
                        Log.d(TAG, "Skipping segment restore: gap=${gap}ms day=$restoredDayKey")
                    }
                    preferencesDataSource.segmentStartMillis = -1L
                    stateEnteredAt = 0L
                } else {
                    stateEnteredAt = nowMs
                    preferencesDataSource.segmentStartMillis = nowMs
                }
                publishSnapshot()
            }
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_ACTIVITY
            }
        )

        stepSensor?.let {
            deviceSensorDataSource.registerStepListener(
                stepListener, it,
                android.hardware.SensorManager.SENSOR_DELAY_NORMAL,
                MAX_REPORT_LATENCY_US.toInt()
            )
        } ?: Log.w(TAG, "No step counter sensor available.")

        activityRecognitionClient.requestActivityUpdates(UPDATE_INTERVAL_MS, pendingIntent)
            .addOnFailureListener { e ->
                Log.e(TAG, "Activity recognition registration failed: ${e.message}")
                accelAvailable = false
                scope.launch { stateMutex.withLock { publishSnapshot() } }
            }

        startCadenceTicker()
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        preferencesDataSource.isTracking = false
        _signal.update { it.copy(isTracking = false) }

        if (!_isTracking.getAndSet(false)) return

        cadenceJob?.cancel()
        cadenceJob = null

        context.startService(
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_ACTIVITY
            }
        )

        deviceSensorDataSource.unregisterStepListener(stepListener)
        activityRecognitionClient.removeActivityUpdates(pendingIntent)

        scope.launch {
            stateMutex.withLock {
                flushCurrentState(nowMs = System.currentTimeMillis())
                preferencesDataSource.segmentStartMillis = -1L
                stateEnteredAt = 0L
                persistDailySummary(isPartialDay = true)
                publishSnapshot()
            }
        }
    }

    override fun resetSession() {
        scope.launch {
            stateMutex.withLock {
                flushCurrentState(nowMs = System.currentTimeMillis())
                persistDailySummary(isPartialDay = true)
                preferencesDataSource.resetSession()

                val currentlyTracking = preferencesDataSource.isTracking
                committedIntensity       = ActivityIntensity.SEDENTARY
                accelAvailable           = true
                stateEnteredAt           = if (currentlyTracking) System.currentTimeMillis() else 0L
                lastRecognitionTimestamp = 0L
                instantCadenceSpm        = 0
                cadenceWindow.clear()

                _signal.value = ActivitySignal(
                    isTracking        = currentlyTracking,
                    hasActiveSession  = currentlyTracking,
                    instantCadenceSpm = 0
                )
            }
        }
    }

    override suspend fun updateActivityIntensity(intensity: ActivityIntensity, confidence: Int) {
        val nowMs = System.currentTimeMillis()
        val minimumConfidence = if (intensity > committedIntensity) 65 else 50
        if (confidence < minimumConfidence) return

        stateMutex.withLock {
            if (intensity == ActivityIntensity.SEDENTARY ||
                intensity == ActivityIntensity.IN_VEHICLE) {
                lastRecognitionTimestamp = nowMs
            }

            if (intensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity = intensity
                stateEnteredAt     = nowMs
                preferencesDataSource.intensity          = committedIntensity.name
                preferencesDataSource.segmentStartMillis = nowMs
                updatePeakIntensity(intensity)
            }
            publishSnapshot(nowMs)
        }
    }

    override suspend fun flushTelemetryToDb() {
        stateMutex.withLock {
            checkAndRolloverDay()
            val snapshot = _signal.value
            activityTelemetryDao.insert(
                ActivityTelemetryEntity(
                    timestampMillis  = System.currentTimeMillis(),
                    steps            = snapshot.steps,
                    activeMinutes    = snapshot.activeMinutes,
                    sedentaryMinutes = snapshot.sedentaryMinutes,
                    intensity        = snapshot.intensity.name
                )
            )
            persistDailySummary(isPartialDay = false)
        }
    }

    override suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long) {
        activityTelemetryDao.deleteOlderThan(cutoffMillis)
        val cutoffDate = java.time.Instant.ofEpochMilli(cutoffMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()
        activityDailySummaryDao.deleteOlderThan(cutoffDate)
    }

    override fun getDailySummary(date: LocalDate): Flow<ActivityDailySummary?> =
        activityDailySummaryDao.getByDate(date.toString())
            .map { entity -> entity?.toDomain() }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<ActivityDailySummary>> {
        val startDate = endDate.minusDays(6).toString()
        val end       = endDate.toString()
        return activityDailySummaryDao.getBetweenDates(startDate, end)
            .map { entities -> entities.map { it.toDomain() } }
    }

    private suspend fun checkAndRolloverDay() {
        val storedKey = preferencesDataSource.dayKey
        val todayKey  = LocalDate.now().toString()

        if (storedKey == todayKey || storedKey.isEmpty()) {
            if (storedKey.isEmpty()) {
                preferencesDataSource.dayKey = todayKey
            }
            return
        }

        Log.d(TAG, "Day rollover detected: $storedKey → $todayKey. Flushing previous day.")
        flushCurrentState(nowMs = System.currentTimeMillis())
        persistDailySummaryForDate(date = storedKey, isPartialDay = false)
        preferencesDataSource.rolloverToNewDay(todayKey)
        committedIntensity = ActivityIntensity.SEDENTARY
        stateEnteredAt     = System.currentTimeMillis()
    }

    private fun flushCurrentState(nowMs: Long) {
        if (stateEnteredAt == 0L) return
        val elapsed = (nowMs - stateEnteredAt).coerceAtLeast(0L)
        when (committedIntensity) {
            ActivityIntensity.SEDENTARY,
            ActivityIntensity.IN_VEHICLE -> preferencesDataSource.sedentaryMs += elapsed
            else                         -> preferencesDataSource.activeMs    += elapsed
        }
        stateEnteredAt = nowMs
        preferencesDataSource.segmentStartMillis = nowMs
    }

    private suspend fun persistDailySummary(isPartialDay: Boolean) {
        persistDailySummaryForDate(
            date        = preferencesDataSource.dayKey.ifEmpty { LocalDate.now().toString() },
            isPartialDay = isPartialDay
        )
    }

    private suspend fun persistDailySummaryForDate(date: String, isPartialDay: Boolean) {
        val peakIntensity = runCatching {
            ActivityIntensity.valueOf(preferencesDataSource.peakIntensity)
        }.getOrDefault(ActivityIntensity.SEDENTARY)

        activityDailySummaryDao.upsert(
            ActivityDailySummaryEntity(
                date             = date,
                totalSteps       = preferencesDataSource.sessionSteps,
                activeMinutes    = (preferencesDataSource.activeMs / 60_000L).toInt(),
                sedentaryMinutes = (preferencesDataSource.sedentaryMs / 60_000L).toInt(),
                peakIntensity    = peakIntensity.name,
                isPartialDay     = isPartialDay,
            )
        )
    }

    private fun updatePeakIntensity(newIntensity: ActivityIntensity) {
        val currentPeak = runCatching {
            ActivityIntensity.valueOf(preferencesDataSource.peakIntensity)
        }.getOrDefault(ActivityIntensity.SEDENTARY)

        if (newIntensity.ordinal > currentPeak.ordinal) {
            preferencesDataSource.peakIntensity = newIntensity.name
        }
    }

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed    = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L
        val isSedentaryLike = committedIntensity == ActivityIntensity.SEDENTARY ||
                committedIntensity == ActivityIntensity.IN_VEHICLE

        val liveSedentary = preferencesDataSource.sedentaryMs + if (isSedentaryLike) liveElapsed else 0L
        val liveActive    = preferencesDataSource.activeMs    + if (!isSedentaryLike) liveElapsed else 0L

        val latestSpm = if (cadenceWindow.size >= 2) {
            val oldest     = cadenceWindow.first()
            val newest     = cadenceWindow.last()
            val elapsedMin = (newest.first - oldest.first) / 60_000.0
            val delta      = newest.second - oldest.second
            if (elapsedMin > 0 && delta >= 0) (delta / elapsedMin).toInt().coerceAtMost(200) else 0
        } else 0

        _signal.update {
            ActivitySignal(
                steps               = preferencesDataSource.sessionSteps,
                intensity           = committedIntensity,
                activeMinutes       = (liveActive    / 60_000L).toInt(),
                sedentaryMinutes    = (liveSedentary / 60_000L).toInt(),
                instantCadenceSpm   = latestSpm,
                stepSensorAvailable = stepSensor != null,
                accelAvailable      = accelAvailable,
                timestamp           = LocalDateTime.now(),
                isTracking          = preferencesDataSource.isTracking,
                hasActiveSession    = stateEnteredAt > 0L || liveActive > 0L
                        || liveSedentary > 0L || preferencesDataSource.sessionSteps > 0
            )
        }
    }

    private val stepListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            scope.launch {
                stateMutex.withLock {
                    checkAndRolloverDay()
                    handleStepEvent(sensorTotal = event.values[0].toInt())
                }
            }
        }
    }

    private fun handleStepEvent(sensorTotal: Int) {
        val currentBootEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val needsBaselineReset =
            preferencesDataSource.isBaselineStale(currentBootEpoch) ||
                    preferencesDataSource.baselineSteps == -1 ||
                    sensorTotal < preferencesDataSource.baselineSteps

        if (needsBaselineReset) {
            val previousSessionSteps = preferencesDataSource.sessionSteps.coerceAtLeast(0)
            val newBaseline = sensorTotal - previousSessionSteps
            preferencesDataSource.baselineSteps   = newBaseline
            preferencesDataSource.bootEpochMillis = currentBootEpoch
            Log.d(TAG, "Step baseline reset. total=$sensorTotal prev=$previousSessionSteps new=$newBaseline")
        }

        preferencesDataSource.sessionSteps =
            (sensorTotal - preferencesDataSource.baselineSteps).coerceAtLeast(0)

        val now = System.currentTimeMillis()
        cadenceWindow.addLast(now to preferencesDataSource.sessionSteps)

        while (cadenceWindow.isNotEmpty() && now - cadenceWindow.first().first > CADENCE_WINDOW_MS) {
            cadenceWindow.removeFirst()
        }

        publishSnapshot()
    }

    private fun startCadenceTicker() {
        cadenceJob?.cancel()
        cadenceJob = scope.launch {
            while (isActive) {
                delay(CADENCE_TICK_MS)
                stateMutex.withLock {
                    val nowMs       = System.currentTimeMillis()
                    val freshWindow = pruneCadenceWindow(nowMs)

                    if (isRecognitionAuthoritative &&
                        (committedIntensity == ActivityIntensity.SEDENTARY ||
                                committedIntensity == ActivityIntensity.IN_VEHICLE)) {
                        publishSnapshot(nowMs)
                        return@withLock
                    }

                    val newIntensity = calculateIntensity(freshWindow, committedIntensity)

                    if (newIntensity != committedIntensity) {
                        flushCurrentState(nowMs)
                        committedIntensity = newIntensity
                        stateEnteredAt     = nowMs
                        preferencesDataSource.intensity          = committedIntensity.name
                        preferencesDataSource.segmentStartMillis = nowMs
                        updatePeakIntensity(newIntensity)
                    }
                    publishSnapshot(nowMs)
                }
            }
        }
    }

    private fun pruneCadenceWindow(nowMs: Long): List<Pair<Long, Int>> {
        while (cadenceWindow.isNotEmpty() &&
            nowMs - cadenceWindow.first().first > CADENCE_WINDOW_MS) {
            cadenceWindow.removeFirst()
        }
        if (cadenceWindow.isNotEmpty() &&
            nowMs - cadenceWindow.last().first > STALENESS_THRESHOLD_MS) {
            cadenceWindow.clear()
        }
        return cadenceWindow.toList()
    }
}