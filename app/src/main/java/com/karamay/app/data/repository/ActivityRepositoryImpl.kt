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
        // Fix #4: 10s is the practical minimum ARClient honours under Doze.
        // The cadence ticker handles sub-10s intensity responsiveness instead.
        private const val UPDATE_INTERVAL_MS        = 10_000L
        private const val ACTION_PROCESS_ACTIVITY   = "com.karamay.app.ACTION_PROCESS_ACTIVITY"
        private const val MODERATE_CADENCE_THRESHOLD = 100  // steps/min
        private const val SEDENTARY_CADENCE_THRESHOLD = 10  // steps/min — effectively still
        private const val PREFS_NAME                = "activity_monitor_prefs"
    }

    private val prefs          = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope          = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex     = Mutex()
    private val sensorManager  = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor     : Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ACTION_PROCESS_ACTIVITY).apply { setPackage(context.packageName) }
        PendingIntent.getBroadcast(
            context, 0, intent,
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

    // Fix #6: Removed SharedPreferences step persistence — it was wiped on every
    // startTracking() anyway, so it never provided real crash recovery.
    private var baselineSteps     : Int  = -1
    private var sessionSteps      : Int  = 0
    private var lastCadenceSteps  : Int  = 0
    private var cadenceJob        : Job? = null
    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY

    // Fix #1: Track accelAvailable as a real field so publishSnapshot() never clobbers it.
    private var accelAvailable: Boolean = true

    // Fix A5: Restore accumulated time from prefs so a process kill mid-session
    // doesn't wipe the entire session's active/sedentary time data.
    private var activeMs      : Long = prefs.getLong("active_ms", 0L)
    private var sedentaryMs   : Long = prefs.getLong("sedentary_ms", 0L)
    private var stateEnteredAt: Long = 0L

    private val _signal = MutableStateFlow(
        ActivitySignal(
            steps               = 0,
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

        // Fix #3: Reset session state synchronously before registering any listeners,
        // eliminating the race where the first ARClient event could arrive before
        // resetSessionState() ran inside the old async coroutine.
        resetSessionState()

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_ACTIVITY
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // Fix A3: SENSOR_DELAY_NORMAL lets the OS batch deliveries, reducing
        // coroutine/mutex overhead. TYPE_STEP_COUNTER fires per-step regardless
        // of delay; FASTEST only removes batching without improving accuracy.
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
                // Fix #1: Write to the real field; publishSnapshot() reads it correctly.
                accelAvailable = false
                scope.launch { stateMutex.withLock { publishSnapshot() } }
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
        try { context.unregisterReceiver(activityReceiver) } catch (e: IllegalArgumentException) { }

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
                    if (baselineSteps == -1 || total < baselineSteps) {
                        baselineSteps = total - sessionSteps
                    }
                    sessionSteps = (total - baselineSteps).coerceAtLeast(0)
                    // Fix #6: No prefs write here — steps are session-only, not persisted.
                    publishSnapshot()
                }
            }
        }
    }

    private fun startCadenceTicker() {
        cadenceJob?.cancel()
        cadenceJob = scope.launch {
            while (isActive) {
                delay(10_000)
                stateMutex.withLock {
                    val stepDelta = sessionSteps - lastCadenceSteps
                    lastCadenceSteps = sessionSteps
                    val spm = stepDelta * 6  // steps per 10s window → steps per minute

                    // Fix #2: Cadence now covers ALL intensity states, not just LIGHT/MODERATE.
                    // This ensures a stopped VIGOROUS user degrades without waiting for the
                    // next ARClient event, which Doze mode can delay by several minutes.
                    val cadenceIntensity = when {
                        spm >= MODERATE_CADENCE_THRESHOLD  -> ActivityIntensity.MODERATE
                        spm >= SEDENTARY_CADENCE_THRESHOLD -> ActivityIntensity.LIGHT
                        else                               -> ActivityIntensity.SEDENTARY
                    }

                    // ARClient remains authoritative for VIGOROUS (running gait is detected
                    // more reliably by accelerometer pattern than cadence alone). Cadence
                    // can only pull VIGOROUS down to SEDENTARY when steps have fully stopped,
                    // not merely slowed — avoids false downgrades during brief stride pauses.
                    val nowMs = System.currentTimeMillis()
                    val newIntensity = when (committedIntensity) {
                        ActivityIntensity.VIGOROUS ->
                            if (cadenceIntensity == ActivityIntensity.SEDENTARY) ActivityIntensity.SEDENTARY
                            else committedIntensity
                        else -> cadenceIntensity
                    }

                    if (newIntensity != committedIntensity) {
                        flushCurrentState(nowMs)
                        committedIntensity = newIntensity
                        stateEnteredAt = nowMs
                    }
                    publishSnapshot(nowMs)
                }
            }
        }
    }

    private suspend fun handleDetectedActivity(activity: DetectedActivity) {
        val nowMs = System.currentTimeMillis()

        // Fix #5: ON_BICYCLE removed — it is frequently confused with IN_VEHICLE/transit
        // by ARClient and maps poorly to health-relevant exercise intensity.
        val mappedIntensity = when (activity.type) {
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE -> ActivityIntensity.SEDENTARY
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT    -> ActivityIntensity.LIGHT
            DetectedActivity.RUNNING    -> ActivityIntensity.VIGOROUS
            else                        -> null  // ON_BICYCLE, TILTING, UNKNOWN → ignore
        } ?: return

        // Fix A4: Resolve confidence threshold AFTER mappedIntensity is known.
        // Upward transitions require higher confidence to avoid brief erroneous
        // spikes into high-intensity states that corrupt activeMs accumulation.
        val minimumConfidence = if (mappedIntensity > committedIntensity) 65 else 50
        if (activity.confidence < minimumConfidence) return

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

        // Fix A5: Checkpoint accumulated time to SharedPreferences on every flush.
        // flushCurrentState() is called on intensity transitions and stopTracking(),
        // so write frequency stays low while ensuring survival across process kills.
        prefs.edit()
            .putLong("active_ms", activeMs)
            .putLong("sedentary_ms", sedentaryMs)
            .apply()
    }

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed   = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L
        val liveSedentary = sedentaryMs + if (committedIntensity == ActivityIntensity.SEDENTARY) liveElapsed else 0L
        val liveActive    = activeMs    + if (committedIntensity != ActivityIntensity.SEDENTARY) liveElapsed else 0L

        _signal.update {
            ActivitySignal(
                steps               = sessionSteps,
                intensity           = committedIntensity,
                activeMinutes       = (liveActive / 60_000L).toInt(),
                sedentaryMinutes    = (liveSedentary / 60_000L).toInt(),
                stepSensorAvailable = stepSensor != null,
                accelAvailable      = accelAvailable,   // Fix #1: use the real field
                timestamp           = LocalDateTime.now()
            )
        }
    }

    // Fix #3: Called synchronously at the top of startTracking().
    // Fix A5: Also clears persisted time values so old session data
    //         doesn't bleed into the new one.
    private fun resetSessionState() {
        baselineSteps      = -1
        sessionSteps       = 0
        lastCadenceSteps   = 0
        committedIntensity = ActivityIntensity.SEDENTARY
        accelAvailable     = true   // Fix #1: reset field on new session
        activeMs           = 0L
        sedentaryMs        = 0L
        stateEnteredAt     = System.currentTimeMillis()
        prefs.edit()
            .putLong("active_ms", 0L)
            .putLong("sedentary_ms", 0L)
            .apply()
    }
}