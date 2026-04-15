package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.karamay.app.core.coordination.PollingJob
import com.karamay.app.core.coordination.TrackingCoordinator
import com.karamay.app.core.utils.BatteryUtils
import com.karamay.app.data.local.dao.activity.ActivityDailySummaryDao
import com.karamay.app.data.local.dao.activity.ActivityTelemetryDao
import com.karamay.app.data.local.datasource.ActivityPreferencesDataSource
import com.karamay.app.data.local.datasource.DeviceSensorDataSource
import com.karamay.app.data.local.entity.activity.ActivityDailySummaryEntity
import com.karamay.app.data.local.entity.activity.ActivityTelemetryEntity
import com.karamay.app.data.receiver.activity.ActivityReceiver
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.usecase.activity.CalculateActivityIntensityUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityTelemetryDao:              ActivityTelemetryDao,
    private val activityDailySummaryDao:           ActivityDailySummaryDao,
    private val preferencesDataSource:             ActivityPreferencesDataSource,
    private val deviceSensorDataSource:            DeviceSensorDataSource,
    private val coordinator:                       TrackingCoordinator,
    private val calculateActivityIntensityUseCase: CalculateActivityIntensityUseCase
) : ActivityRepository {

    companion object {
        private const val TAG                    = "ActivityRepo"
        private const val UPDATE_INTERVAL_MS     = 60_000L
        private const val MAX_REPORT_LATENCY_US  = 5 * 60 * 1_000_000L
        private const val CADENCE_WINDOW_MS      = 60_000L
        private const val CADENCE_TICK_MS        = 10_000L
        private const val STALENESS_THRESHOLD_MS = 35_000L
        private const val RECOGNITION_AUTHORITY_MS = 180_000L
        private const val MAX_SEGMENT_RESTORE_MS = 60 * 60_000L

        // Wait-and-See Buffer: 9 ticks (90 seconds) allows Google API time to
        // detect vehicle transit before the step counter defaults to Sedentary.
        private const val SEDENTARY_GRACE_TICKS_LIMIT = 9
        private const val IN_VEHICLE_STALE_RELEASE_MS = RECOGNITION_AUTHORITY_MS + 30_000L
    }

    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

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

    private var committedIntensity:        ActivityIntensity = ActivityIntensity.SEDENTARY
    private var accelAvailable:            Boolean           = true
    private var stateEnteredAt:            Long              = 0L
    private var lastRecognitionTimestamp:  Long              = 0L
    private var sedentaryGraceTicks:       Int               = 0

    private val isRecognitionAuthoritative: Boolean
        get() = (System.currentTimeMillis() - lastRecognitionTimestamp) < RECOGNITION_AUTHORITY_MS

    private val cadenceWindow       = ArrayDeque<Pair<Long, Int>>()
    private var instantCadenceSpm:  Int = 0

    @Volatile private var _isProcessActive: Boolean = false
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildInitialSignal())
    override fun observeSignal(): Flow<ActivitySignal> = _signal.asStateFlow()

    private val cadencePollJob = PollingJob(
        scope      = scope,
        mutex      = stateMutex,
        intervalMs = CADENCE_TICK_MS,
        tag        = "$TAG/cadence",
        isActive   = { _isProcessActive }
    ) { tickCadence() }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        val hasActivityPerm = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasActivityPerm) {
            Log.w(TAG, "startTracking: ACTIVITY_RECOGNITION permission missing. Gracefully pausing.")
            return false
        }

        if (!BatteryUtils.isIgnoringBatteryOptimizations(context)) {
            Log.w(TAG, "Battery optimisation active — tracking may be interrupted in Doze.")
        }

        preferencesDataSource.isTracking = true
        _signal.update { it.copy(isTracking = true, hasActiveSession = true) }

        if (_isProcessActive) {
            Log.d(TAG, "startTracking: already active — circuit breaker.")
            return true
        }

        _isProcessActive = true

        scope.launch {
            stateMutex.withLock {
                checkAndRolloverDay()
                committedIntensity = runCatching {
                    ActivityIntensity.valueOf(preferencesDataSource.intensity)
                }.getOrDefault(ActivityIntensity.SEDENTARY)

                val savedAnchor = preferencesDataSource.segmentStartMillis
                val nowMs       = System.currentTimeMillis()

                if (savedAnchor != -1L) {
                    val gap           = nowMs - savedAnchor
                    val restoredDayKey = preferencesDataSource.dayKey
                    val todayKey       = LocalDate.now().toString()

                    if (restoredDayKey == todayKey) {
                        if (gap in 1L..MAX_SEGMENT_RESTORE_MS) {
                            when (committedIntensity) {
                                ActivityIntensity.SEDENTARY,
                                ActivityIntensity.IN_VEHICLE -> preferencesDataSource.sedentaryMs += gap
                                else                         -> preferencesDataSource.activeMs    += gap
                            }
                            Log.d(TAG, "Restored gap of ${gap}ms for $committedIntensity")
                        } else if (gap > MAX_SEGMENT_RESTORE_MS) {
                            preferencesDataSource.baselineSteps = -1
                            Log.d(TAG, "Gap of ${gap}ms exceeded limit. Discarding time and forcing step baseline reset.")
                        }
                    }
                }
                stateEnteredAt = nowMs
                preferencesDataSource.segmentStartMillis = nowMs
                publishSnapshot()
            }
        }

        coordinator.startActivity()

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

        cadencePollJob.start()
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        preferencesDataSource.isTracking = false
        _signal.update { it.copy(isTracking = false) }

        if (!_isProcessActive) {
            Log.d(TAG, "stopTracking: already stopped — circuit breaker bypassed for prefs.")
            return
        }

        _isProcessActive = false
        cadencePollJob.stop()
        coordinator.stopActivity()
        deviceSensorDataSource.unregisterStepListener(stepListener)
        activityRecognitionClient.removeActivityUpdates(pendingIntent)

        scope.launch {
            stateMutex.withLock {
                flushCurrentState(System.currentTimeMillis())
                preferencesDataSource.segmentStartMillis = -1L
                stateEnteredAt = 0L
                persistDailySummary(isPartialDay = true)
                publishSnapshot()
            }
        }
    }

    override suspend fun updateActivityIntensity(intensity: ActivityIntensity, confidence: Int) {
        val nowMs             = System.currentTimeMillis()
        val minimumConfidence = if (intensity > committedIntensity) 65 else 50

        if (confidence < minimumConfidence) return

        stateMutex.withLock {
            lastRecognitionTimestamp = nowMs
            // Reset buffer because we have a high-confidence signal from Google
            sedentaryGraceTicks = 0

            if (intensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity = intensity
                stateEnteredAt     = nowMs
                preferencesDataSource.intensity          = committedIntensity.name
                preferencesDataSource.segmentStartMillis = nowMs
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

    override suspend fun insertMockSummary(summary: ActivityDailySummary) {
        activityDailySummaryDao.upsert(
            com.karamay.app.data.local.entity.activity.ActivityDailySummaryEntity.fromDomain(summary)
        )
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
        activityDailySummaryDao.getByDate(date.toString()).map { it?.toDomain() }

    override fun getWeeklySummaries(endDate: LocalDate): Flow<List<ActivityDailySummary>> =
        activityDailySummaryDao
            .getBetweenDates(endDate.minusDays(6).toString(), endDate.toString())
            .map { it.map { e -> e.toDomain() } }

    @SuppressLint("MissingPermission")
    override fun pauseTracking() {
        if (!_isProcessActive) return
        Log.d(TAG, "pauseTracking: Suspending processes due to missing permissions. Intent preserved.")
        _isProcessActive = false
        cadencePollJob.stop()
        deviceSensorDataSource.unregisterStepListener(stepListener)
        activityRecognitionClient.removeActivityUpdates(pendingIntent)

        scope.launch {
            stateMutex.withLock {
                flushCurrentState(System.currentTimeMillis())
                preferencesDataSource.segmentStartMillis = -1L
                stateEnteredAt = 0L
                persistDailySummary(isPartialDay = true)
                publishSnapshot()
            }
        }
    }

    private suspend fun checkAndRolloverDay() {
        val storedKey = preferencesDataSource.dayKey
        val today     = LocalDate.now()
        val todayKey  = today.toString()

        if (today.year < 2024) {
            Log.w(TAG, "System clock indicates year ${today.year}. Awaiting NTP sync before rollover checks.")
            return
        }

        if (storedKey == todayKey || storedKey.isEmpty()) {
            if (storedKey.isEmpty()) preferencesDataSource.dayKey = todayKey
            return
        }

        Log.d(TAG, "Day rollover: $storedKey → $todayKey")
        flushCurrentState(System.currentTimeMillis())
        persistDailySummaryForDate(storedKey, isPartialDay = false)
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
            date         = preferencesDataSource.dayKey.ifEmpty { LocalDate.now().toString() },
            isPartialDay = isPartialDay
        )
    }

    private suspend fun persistDailySummaryForDate(date: String, isPartialDay: Boolean) {
        val targetDate   = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        val zone         = ZoneId.systemDefault()
        val startOfDayMs = targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDayMs   = targetDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val dayTelemetry = activityTelemetryDao.getTelemetryListBetween(startOfDayMs, endOfDayMs)
        val dynamicPeak  = dayTelemetry
            .mapNotNull { runCatching { ActivityIntensity.valueOf(it.intensity) }.getOrNull() }
            .maxByOrNull { it.ordinal } ?: committedIntensity

        val distribution = mutableMapOf<ActivityIntensity, Int>()
        var lastTimestamp = startOfDayMs

        for (telemetry in dayTelemetry) {
            val intensity = runCatching { ActivityIntensity.valueOf(telemetry.intensity) }
                .getOrDefault(ActivityIntensity.SEDENTARY)
            val durationMs = telemetry.timestampMillis - lastTimestamp
            if (durationMs > 0) {
                val mins = (durationMs / 60_000L).toInt()
                distribution[intensity] = distribution.getOrDefault(intensity, 0) + mins
            }
            lastTimestamp = telemetry.timestampMillis
        }

        val finalGap = System.currentTimeMillis() - lastTimestamp
        if (finalGap > 0 && date == LocalDate.now().toString()) {
            val finalMins = (finalGap / 60_000L).toInt()
            distribution[committedIntensity] = distribution.getOrDefault(committedIntensity, 0) + finalMins
        }

        activityDailySummaryDao.upsert(
            ActivityDailySummaryEntity.fromDomain(
                ActivityDailySummary(
                    date                    = date,
                    totalSteps              = preferencesDataSource.sessionSteps,
                    activeMinutes           = (preferencesDataSource.activeMs    / 60_000L).toInt(),
                    sedentaryMinutes        = (preferencesDataSource.sedentaryMs / 60_000L).toInt(),
                    peakIntensity           = dynamicPeak,
                    isPartialDay            = isPartialDay,
                    minutesPerIntensityBand = distribution
                )
            )
        )
    }

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed     = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L
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
                hasActiveSession    = preferencesDataSource.isTracking || stateEnteredAt > 0L || liveActive > 0L
                        || liveSedentary > 0L || preferencesDataSource.sessionSteps > 0
            )
        }
    }

    private suspend fun tickCadence() {
        val nowMs       = System.currentTimeMillis()
        val freshWindow = pruneCadenceWindow(nowMs)

        if (committedIntensity == ActivityIntensity.IN_VEHICLE) {
            val inVehicleDuration = nowMs - stateEnteredAt
            if (!isRecognitionAuthoritative && inVehicleDuration > IN_VEHICLE_STALE_RELEASE_MS) {
                Log.d(TAG, "IN_VEHICLE state stale — releasing to SEDENTARY")
                updateState(ActivityIntensity.SEDENTARY, nowMs)
            } else {
                publishSnapshot(nowMs)
            }
            return
        }

        if (isRecognitionAuthoritative && committedIntensity == ActivityIntensity.SEDENTARY) {
            sedentaryGraceTicks = 0
            publishSnapshot(nowMs)
            return
        }

        val newIntensity: ActivityIntensity
        if (freshWindow.size < 2) {
            newIntensity = committedIntensity // Hold state if window is sparse
        } else {
            newIntensity = calculateActivityIntensityUseCase(freshWindow, committedIntensity)
        }

        // Apply Buffer Logic
        if (newIntensity == ActivityIntensity.SEDENTARY && committedIntensity != ActivityIntensity.SEDENTARY) {
            sedentaryGraceTicks++
            if (sedentaryGraceTicks >= SEDENTARY_GRACE_TICKS_LIMIT) {
                updateState(ActivityIntensity.SEDENTARY, nowMs)
            } else {
                // Hold current state (Wait-and-See)
                publishSnapshot(nowMs)
            }
        } else {
            sedentaryGraceTicks = 0
            if (newIntensity != committedIntensity) {
                updateState(newIntensity, nowMs)
            } else {
                publishSnapshot(nowMs)
            }
        }
    }

    private fun updateState(newIntensity: ActivityIntensity, nowMs: Long) {
        flushCurrentState(nowMs)
        committedIntensity = newIntensity
        stateEnteredAt     = nowMs
        preferencesDataSource.intensity          = committedIntensity.name
        preferencesDataSource.segmentStartMillis = nowMs
        publishSnapshot(nowMs)
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
        val needsReset =
            preferencesDataSource.isBaselineStale(currentBootEpoch) ||
                    preferencesDataSource.baselineSteps == -1 ||
                    sensorTotal < preferencesDataSource.baselineSteps

        if (needsReset) {
            val prev        = preferencesDataSource.sessionSteps.coerceAtLeast(0)
            val newBaseline = sensorTotal - prev
            preferencesDataSource.baselineSteps   = newBaseline
            preferencesDataSource.bootEpochMillis = currentBootEpoch
            Log.d(TAG, "Step baseline reset: total=$sensorTotal prev=$prev new=$newBaseline")
        }

        if (committedIntensity == ActivityIntensity.IN_VEHICLE && isRecognitionAuthoritative) {
            val prevSessionSteps = preferencesDataSource.sessionSteps.coerceAtLeast(0)
            val newBaseline      = sensorTotal - prevSessionSteps
            preferencesDataSource.baselineSteps = newBaseline
            return
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
            activeMinutes       = (activeMs    / 60_000L).toInt(),
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
}