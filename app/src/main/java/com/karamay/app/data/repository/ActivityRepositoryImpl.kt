package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.karamay.app.core.service.TrackingService
import com.karamay.app.core.utils.BatteryUtils
import com.karamay.app.data.local.dao.ActivityTelemetryDao
import com.karamay.app.data.local.datasource.ActivityPreferencesDataSource
import com.karamay.app.data.local.entity.ActivityTelemetryEntity
import com.karamay.app.data.receiver.activity.ActivityReceiver
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.usecase.activity.CalculateActivityIntensityUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityTelemetryDao: ActivityTelemetryDao,
    private val preferencesDataSource: ActivityPreferencesDataSource,
    private val calculateIntensity: CalculateActivityIntensityUseCase
) : ActivityRepository {

    companion object {
        private const val UPDATE_INTERVAL_MS          = 60_000L
        private const val MAX_REPORT_LATENCY_US       = 5 * 60 * 1_000_000L
        private const val CADENCE_WINDOW_MS           = 60_000L
        private const val CADENCE_TICK_MS             = 10_000L
    }

    private val scope         = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex    = Mutex()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val activityRecognitionClient = ActivityRecognition.getClient(context)
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityReceiver::class.java).apply {
            action = ActivityReceiver.ACTION_PROCESS_ACTIVITY
        }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private var cadenceJob        : Job? = null
    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY
    private var accelAvailable    : Boolean = true
    private var stateEnteredAt    : Long = 0L
    private val cadenceWindow = ArrayDeque<Pair<Long, Int>>()

    // RUNTIME HARDWARE LOCK: Always starts false on process boot so sensors register
    private val _isTracking = AtomicBoolean(false)

    // UI EXPOSURE: Relies on the database intent
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildInitialSignal())
    override fun observeSignal(): Flow<ActivitySignal> = _signal.asStateFlow()

    private fun buildInitialSignal(): ActivitySignal {
        val restoredIntensity = runCatching {
            ActivityIntensity.valueOf(preferencesDataSource.intensity)
        }.getOrDefault(ActivityIntensity.SEDENTARY)

        val activeMs = preferencesDataSource.activeMs
        val sedentaryMs = preferencesDataSource.sedentaryMs
        val steps = preferencesDataSource.sessionSteps

        return ActivitySignal(
            steps               = steps,
            intensity           = restoredIntensity,
            activeMinutes       = (activeMs / 60_000L).toInt(),
            sedentaryMinutes    = (sedentaryMs / 60_000L).toInt(),
            stepSensorAvailable = stepSensor != null,
            accelAvailable      = true,
            timestamp           = LocalDateTime.now(),
            isTracking          = preferencesDataSource.isTracking,
            hasActiveSession    = preferencesDataSource.isTracking || activeMs > 0L || sedentaryMs > 0L || steps > 0
        )
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (!BatteryUtils.isIgnoringBatteryOptimizations(context)) {
            Log.w("ActivityTracker", "WARNING: Battery optimization is active.")
        }

        // Only block if sensors are ALREADY registered in this specific process instance
        if (_isTracking.getAndSet(true)) return true

        preferencesDataSource.isTracking = true
        _signal.update { it.copy(isTracking = true, hasActiveSession = true) }

        scope.launch {
            stateMutex.withLock {
                stateEnteredAt = System.currentTimeMillis()
                publishSnapshot()
            }
        }

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_ACTIVITY
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        stepSensor?.let {
            sensorManager.registerListener(
                stepListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL,
                MAX_REPORT_LATENCY_US.toInt()
            )
        }

        activityRecognitionClient.requestActivityUpdates(UPDATE_INTERVAL_MS, pendingIntent)
            .addOnFailureListener {
                accelAvailable = false
                scope.launch { stateMutex.withLock { publishSnapshot() } }
            }

        startCadenceTicker()
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        // Update database intent first so UI responds immediately
        preferencesDataSource.isTracking = false
        _signal.update { it.copy(isTracking = false) }

        // If sensors were never registered, safely exit
        if (!_isTracking.getAndSet(false)) return

        cadenceJob?.cancel()

        context.startService(
            Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP_ACTIVITY
            }
        )

        sensorManager.unregisterListener(stepListener)
        activityRecognitionClient.removeActivityUpdates(pendingIntent)

        scope.launch {
            stateMutex.withLock {
                flushCurrentState(nowMs = System.currentTimeMillis())
                publishSnapshot()
            }
        }
    }

    override fun resetSession() {
        scope.launch {
            stateMutex.withLock {
                preferencesDataSource.resetSession()
                val currentlyTracking = preferencesDataSource.isTracking

                committedIntensity = ActivityIntensity.SEDENTARY
                accelAvailable     = true
                stateEnteredAt     = if (currentlyTracking) System.currentTimeMillis() else 0L
                cadenceWindow.clear()

                _signal.value = ActivitySignal(
                    isTracking = currentlyTracking,
                    hasActiveSession = currentlyTracking
                )
            }
        }
    }

    override suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long) {
        activityTelemetryDao.deleteOlderThan(cutoffMillis)
    }

    override suspend fun updateActivityIntensity(intensity: ActivityIntensity, confidence: Int) {
        val nowMs = System.currentTimeMillis()
        val minimumConfidence = if (intensity > committedIntensity) 65 else 50
        if (confidence < minimumConfidence) return

        stateMutex.withLock {
            if (intensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity = intensity
                stateEnteredAt     = nowMs
                preferencesDataSource.intensity = committedIntensity.name
            }
            publishSnapshot(nowMs)
        }
    }

    override suspend fun flushTelemetryToDb() {
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
    }

    private val stepListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            scope.launch {
                stateMutex.withLock {
                    val total = event.values[0].toInt()
                    if (preferencesDataSource.baselineSteps == -1 || total < preferencesDataSource.baselineSteps) {
                        preferencesDataSource.baselineSteps = total - preferencesDataSource.sessionSteps
                    }
                    preferencesDataSource.sessionSteps = (total - preferencesDataSource.baselineSteps).coerceAtLeast(0)

                    val now = System.currentTimeMillis()
                    cadenceWindow.addLast(now to preferencesDataSource.sessionSteps)
                    while (cadenceWindow.isNotEmpty() && now - cadenceWindow.first().first > CADENCE_WINDOW_MS) {
                        cadenceWindow.removeFirst()
                    }

                    publishSnapshot()
                }
            }
        }
    }

    private fun startCadenceTicker() {
        cadenceJob?.cancel()
        cadenceJob = scope.launch {
            while (isActive) {
                delay(CADENCE_TICK_MS)
                stateMutex.withLock {
                    val nowMs = System.currentTimeMillis()
                    val newIntensity = calculateIntensity(cadenceWindow.toList(), committedIntensity)

                    if (newIntensity != committedIntensity) {
                        flushCurrentState(nowMs)
                        committedIntensity = newIntensity
                        stateEnteredAt     = nowMs
                        preferencesDataSource.intensity = committedIntensity.name
                    }
                    publishSnapshot(nowMs)
                }
            }
        }
    }

    private fun flushCurrentState(nowMs: Long) {
        if (stateEnteredAt == 0L) return
        val elapsed = (nowMs - stateEnteredAt).coerceAtLeast(0L)
        when (committedIntensity) {
            ActivityIntensity.SEDENTARY  -> preferencesDataSource.sedentaryMs += elapsed
            ActivityIntensity.IN_VEHICLE -> {  }
            else                         -> preferencesDataSource.activeMs += elapsed
        }
        stateEnteredAt = nowMs
    }

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed   = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L
        val liveSedentary = preferencesDataSource.sedentaryMs +
                if (committedIntensity == ActivityIntensity.SEDENTARY) liveElapsed else 0L
        val liveActive = preferencesDataSource.activeMs +
                if (committedIntensity != ActivityIntensity.SEDENTARY && committedIntensity != ActivityIntensity.IN_VEHICLE) liveElapsed else 0L

        val snapshot = ActivitySignal(
            steps               = preferencesDataSource.sessionSteps,
            intensity           = committedIntensity,
            activeMinutes       = (liveActive / 60_000L).toInt(),
            sedentaryMinutes    = (liveSedentary / 60_000L).toInt(),
            stepSensorAvailable = stepSensor != null,
            accelAvailable      = accelAvailable,
            timestamp           = LocalDateTime.now(),
            isTracking          = preferencesDataSource.isTracking,
            hasActiveSession    = stateEnteredAt > 0L || liveActive > 0L || liveSedentary > 0L || preferencesDataSource.sessionSteps > 0
        )
        _signal.update { snapshot }
    }
}