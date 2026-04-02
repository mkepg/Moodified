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
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.repository.ActivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ActivityRepository {

    companion object {
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val ACTION_PROCESS_ACTIVITY = "com.karamay.app.ACTION_PROCESS_ACTIVITY"

        // General fitness threshold: 100+ steps/min is considered moderate intensity
        private const val MODERATE_CADENCE_THRESHOLD = 100
    }

    // --- Hardware Step Counter ---
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // --- Google Play Services Activity Recognition ---
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
                result?.mostProbableActivity?.let { handleDetectedActivity(it) }
            }
        }
    }

    private val stateLock = Any()

    private var baselineSteps: Int = -1
    private var sessionSteps:  Int = 0

    // Cadence Tracking Variables
    private var lastCadenceCheckMs: Long = 0L
    private var lastCadenceSteps: Int = 0

    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY
    private var activeMs:    Long = 0L
    private var sedentaryMs: Long = 0L
    private var stateEnteredAt: Long = 0L

    private val _signal = MutableStateFlow(
        ActivitySignal(
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

        synchronized(stateLock) {
            resetSessionState()
        }

        stepSensor?.let {
            sensorManager.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL)
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

        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return

        sensorManager.unregisterListener(stepListener)
        activityRecognitionClient.removeActivityUpdates(pendingIntent)

        try {
            context.unregisterReceiver(activityReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignored
        }

        synchronized(stateLock) {
            flushCurrentState(nowMs = System.currentTimeMillis())
            publishSnapshot()
        }
    }

    private val stepListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            synchronized(stateLock) {
                val total = event.values[0].toInt()
                if (baselineSteps == -1) baselineSteps = total
                sessionSteps = (total - baselineSteps).coerceAtLeast(0)
                publishSnapshot()
            }
        }
    }

    private fun handleDetectedActivity(activity: DetectedActivity) {
        if (activity.confidence < 50) return

        val nowMs = System.currentTimeMillis()
        val mappedIntensity = mapGoogleActivityToIntensity(activity.type)

        if (mappedIntensity == null) return

        synchronized(stateLock) {
            if (mappedIntensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity = mappedIntensity
                stateEnteredAt = nowMs
            }
            publishSnapshot(nowMs)
        }
    }

    private fun mapGoogleActivityToIntensity(type: Int): ActivityIntensity? = when (type) {
        DetectedActivity.STILL,
        DetectedActivity.IN_VEHICLE -> ActivityIntensity.SEDENTARY

        // Route walking through our custom cadence calculator
        DetectedActivity.WALKING,
        DetectedActivity.ON_FOOT    -> calculateWalkingIntensity()

        DetectedActivity.ON_BICYCLE -> ActivityIntensity.MODERATE
        DetectedActivity.RUNNING    -> ActivityIntensity.VIGOROUS

        DetectedActivity.TILTING,
        DetectedActivity.UNKNOWN    -> null
        else -> null
    }

    /**
     * Calculates Steps Per Minute (SPM) using the hardware step counter
     * to differentiate between a casual stroll and a power walk.
     */
    private fun calculateWalkingIntensity(): ActivityIntensity {
        val nowMs = System.currentTimeMillis()
        val currentSteps = sessionSteps

        // Initialize cadence trackers on the first pass
        if (lastCadenceCheckMs == 0L) {
            lastCadenceCheckMs = nowMs
            lastCadenceSteps = currentSteps
            return ActivityIntensity.LIGHT
        }

        val elapsedMs = nowMs - lastCadenceCheckMs
        val stepDelta = currentSteps - lastCadenceSteps

        // Failsafe to prevent division by zero for extremely rapid broadcasts
        if (elapsedMs < 1000) return committedIntensity

        // Calculate cadence (Steps Per Minute)
        val stepsPerSecond = stepDelta.toFloat() / (elapsedMs / 1000f)
        val stepsPerMinute = (stepsPerSecond * 60f).toInt()

        // Reset the window for the next batch update
        lastCadenceCheckMs = nowMs
        lastCadenceSteps = currentSteps

        return if (stepsPerMinute >= MODERATE_CADENCE_THRESHOLD) {
            ActivityIntensity.MODERATE
        } else {
            ActivityIntensity.LIGHT
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
                activeMinutes       = (liveActive    / 60_000L).toInt(),
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

        lastCadenceCheckMs = 0L
        lastCadenceSteps   = 0

        committedIntensity = ActivityIntensity.SEDENTARY
        activeMs           = 0L
        sedentaryMs        = 0L
        stateEnteredAt     = System.currentTimeMillis()
    }
}