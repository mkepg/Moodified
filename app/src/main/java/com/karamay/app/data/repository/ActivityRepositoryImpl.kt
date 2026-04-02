package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.karamay.app.core.service.TrackingService
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
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
    @ApplicationContext private val context: Context
) : ActivityRepository {

    companion object {
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val ACTION_PROCESS_ACTIVITY = "com.karamay.app.ACTION_PROCESS_ACTIVITY"
        private const val MODERATE_CADENCE_THRESHOLD = 100
        private const val PREFS_NAME = "activity_monitor_prefs"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ACTION_PROCESS_ACTIVITY).apply {
            setPackage(context.packageName)
        }
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_PROCESS_ACTIVITY && ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent)
                result?.mostProbableActivity?.let { activity ->
                    scope.launch { handleDetectedActivity(activity) }
                }
            }
        }
    }

    private var baselineSteps: Int = -1
    private var sessionSteps: Int = prefs.getInt("session_steps", 0)

    private var lastCadenceSteps: Int = sessionSteps
    private var cadenceJob: Job? = null

    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY
    private var activeMs: Long = 0L
    private var sedentaryMs: Long = 0L
    private var stateEnteredAt: Long = 0L

    private val _signal = MutableStateFlow(
        ActivitySignal(
            steps               = sessionSteps,
            stepSensorAvailable = stepSensor != null,
            accelAvailable      = true
        )
    )

    private val _isTracking = AtomicBoolean(false)

    override fun observeSignal(): Flow<ActivitySignal> = _signal.asStateFlow()
    override val isTracking: Boolean get() = _isTracking.get()

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (_isTracking.getAndSet(true)) return true

        scope.launch {
            stateMutex.withLock { resetSessionState() }
        }

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_ACTIVITY
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        stepSensor?.let {
            sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        ContextCompat.registerReceiver(
            context,
            activityReceiver,
            IntentFilter(ACTION_PROCESS_ACTIVITY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        activityRecognitionClient.requestActivityUpdates(UPDATE_INTERVAL_MS, pendingIntent)
            .addOnFailureListener {
                _signal.update { it.copy(accelAvailable = false) }
            }

        startCadenceTicker()
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return

        cadenceJob?.cancel()

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP_ACTIVITY
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        sensorManager.unregisterListener(stepListener)
        activityRecognitionClient.removeActivityUpdates(pendingIntent)
        try {
            context.unregisterReceiver(activityReceiver)
        } catch (e: IllegalArgumentException) {
        }

        scope.launch {
            stateMutex.withLock {
                flushCurrentState(nowMs = System.currentTimeMillis())
                publishSnapshot()
            }
        }
    }

    private val stepListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            scope.launch {
                stateMutex.withLock {
                    val total = event.values[0].toInt()

                    // Survive device reboots: if total is less than baseline, a reboot occurred
                    if (baselineSteps == -1 || total < baselineSteps) {
                        baselineSteps = total - sessionSteps
                    }

                    sessionSteps = (total - baselineSteps).coerceAtLeast(0)
                    prefs.edit().putInt("session_steps", sessionSteps).apply()

                    publishSnapshot()
                }
            }
        }
    }

    private fun startCadenceTicker() {
        cadenceJob?.cancel()
        cadenceJob = scope.launch {
            while (isActive) {
                delay(10_000) // Check cadence every 10 seconds
                stateMutex.withLock {
                    val stepDelta = sessionSteps - lastCadenceSteps
                    lastCadenceSteps = sessionSteps

                    // Extrapolate 10 seconds of steps to a minute
                    val spm = stepDelta * 6

                    if (committedIntensity == ActivityIntensity.LIGHT || committedIntensity == ActivityIntensity.MODERATE) {
                        committedIntensity = if (spm >= MODERATE_CADENCE_THRESHOLD) {
                            ActivityIntensity.MODERATE
                        } else {
                            ActivityIntensity.LIGHT
                        }
                        publishSnapshot()
                    }
                }
            }
        }
    }

    private suspend fun handleDetectedActivity(activity: DetectedActivity) {
        if (activity.confidence < 50) return
        val nowMs = System.currentTimeMillis()

        val mappedIntensity = when (activity.type) {
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE -> ActivityIntensity.SEDENTARY
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT    -> ActivityIntensity.LIGHT // Ticker will upgrade to MODERATE if fast enough
            DetectedActivity.ON_BICYCLE -> ActivityIntensity.MODERATE
            DetectedActivity.RUNNING    -> ActivityIntensity.VIGOROUS
            else -> null
        }

        if (mappedIntensity == null) return

        stateMutex.withLock {
            if (mappedIntensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity = mappedIntensity
                stateEnteredAt = nowMs
            }
            publishSnapshot(nowMs)
        }
    }

    private fun flushCurrentState(nowMs: Long) {
        if (stateEnteredAt == 0L) return
        val elapsed = (nowMs - stateEnteredAt).coerceAtLeast(0L)
        if (committedIntensity == ActivityIntensity.SEDENTARY) {
            sedentaryMs += elapsed
        } else {
            activeMs += elapsed
        }
        stateEnteredAt = nowMs
    }

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L
        val liveSedentary = sedentaryMs + if (committedIntensity == ActivityIntensity.SEDENTARY) liveElapsed else 0L
        val liveActive    = activeMs    + if (committedIntensity != ActivityIntensity.SEDENTARY) liveElapsed else 0L

        _signal.update {
            ActivitySignal(
                steps               = sessionSteps,
                intensity           = committedIntensity,
                activeMinutes       = (liveActive / 60_000L).toInt(),
                sedentaryMinutes    = (liveSedentary / 60_000L).toInt(),
                stepSensorAvailable = stepSensor != null,
                accelAvailable      = true,
                timestamp           = LocalDateTime.now()
            )
        }
    }

    private fun resetSessionState() {
        baselineSteps      = -1
        sessionSteps       = 0
        lastCadenceSteps   = 0
        committedIntensity = ActivityIntensity.SEDENTARY
        activeMs           = 0L
        sedentaryMs        = 0L
        stateEnteredAt     = System.currentTimeMillis()
        prefs.edit().putInt("session_steps", 0).apply()
    }
}