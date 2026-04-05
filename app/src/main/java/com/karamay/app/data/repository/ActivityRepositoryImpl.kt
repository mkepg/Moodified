package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.DetectedActivity
import com.karamay.app.core.service.TrackingService
import com.karamay.app.data.local.dao.ActivityTelemetryDao
import com.karamay.app.data.local.entity.ActivityTelemetryEntity
import com.karamay.app.data.receiver.activity.ActivityEventBus
import com.karamay.app.data.receiver.activity.ActivityReceiver
import com.karamay.app.data.receiver.activity.ActivitySignalBus
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
    @ApplicationContext private val context: Context,
    private val activityEventBus: ActivityEventBus,
    // Fix A: Injected ActivitySignalBus — symmetric with SleepSignalBus in SleepRepositoryImpl.
    private val activitySignalBus: ActivitySignalBus,
    private val activityTelemetryDao: ActivityTelemetryDao,
) : ActivityRepository {

    companion object {
        private const val UPDATE_INTERVAL_MS          = 60_000L
        private const val MAX_REPORT_LATENCY_US       = 5 * 60 * 1_000_000L
        private const val CADENCE_WINDOW_MS           = 60_000L
        private const val CADENCE_TICK_MS             = 10_000L
        private const val MAX_CADENCE_SPM             = 160
        private const val MODERATE_CADENCE_THRESHOLD  = 100
        private const val SEDENTARY_CADENCE_THRESHOLD = 10
        private const val PREFS_NAME                  = "activity_monitor_prefs"
        private const val TELEMETRY_FLUSH_INTERVAL_MS = 5 * 60_000L
    }

    private val prefs         = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    private var baselineSteps     : Int  = prefs.getInt("baseline_steps", -1)
    private var sessionSteps      : Int  = prefs.getInt("session_steps", 0)
    private var cadenceJob        : Job? = null
    private var telemetryJob      : Job? = null
    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY
    private var accelAvailable    : Boolean = true
    private var activeMs          : Long = prefs.getLong("active_ms", 0L)
    private var sedentaryMs       : Long = prefs.getLong("sedentary_ms", 0L)
    private var stateEnteredAt    : Long = 0L

    private val cadenceWindow = ArrayDeque<Pair<Long, Int>>()

    private val _isTracking = AtomicBoolean(prefs.getBoolean("is_tracking", false))

    override val isTracking: Boolean get() = _isTracking.get()

    // Fix A: observeSignal() now delegates to ActivitySignalBus, exactly mirroring
    // SleepRepositoryImpl.observeLiveSignal() → sleepSignalBus.signals.
    // This means the UI receives the persisted last-known state immediately on first
    // collection after a process kill, instead of zeroed-out defaults.
    override fun observeSignal(): Flow<ActivitySignal> = activitySignalBus.signal

    init {
        // Fix A: init() so the bus restores persisted state before any observer collects.
        activitySignalBus.init(context)

        // Fix B: With ActivityEventBus now @Singleton, this listener reliably reaches
        // the same instance used by ActivityReceiver.
        activityEventBus.setListener { activity ->
            scope.launch { handleDetectedActivity(activity) }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Boolean {
        if (_isTracking.getAndSet(true)) return true

        prefs.edit().putBoolean("is_tracking", true).apply()

        // Fix A: Notify the bus that tracking is active so observers see isTracking = true.
        activitySignalBus.setTrackingState(true)

        resetSessionState()

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
        startTelemetryFlusher()
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stopTracking() {
        if (!_isTracking.getAndSet(false)) return

        prefs.edit().putBoolean("is_tracking", false).apply()

        // Fix A: Notify the bus so observers see isTracking = false immediately.
        activitySignalBus.setTrackingState(false)

        cadenceJob?.cancel()
        telemetryJob?.cancel()

        // Fix F: Clear the event bus listener so stale activity events delivered after
        // stopTracking() do not continue updating committedIntensity or flushing state.
        activityEventBus.clearListener()

        // Fix D: Use plain startService() for the stop action rather than
        // startForegroundService(). startForegroundService() is only for starting a
        // foreground service — using it for a stop action is semantically wrong and
        // would re-start a stopped service just to shut it down again.
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
                resetSessionState()
                // Fix A: Also reset the bus so observers see the cleared state.
                activitySignalBus.resetSession()
            }
        }
    }

    override suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long) {
        activityTelemetryDao.deleteOlderThan(cutoffMillis)
    }

    // ── Sensor listener ───────────────────────────────────────────────────────

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

                    prefs.edit()
                        .putInt("baseline_steps", baselineSteps)
                        .putInt("session_steps", sessionSteps)
                        .apply()

                    val now = System.currentTimeMillis()
                    cadenceWindow.addLast(now to sessionSteps)
                    while (cadenceWindow.isNotEmpty() &&
                        now - cadenceWindow.first().first > CADENCE_WINDOW_MS
                    ) {
                        cadenceWindow.removeFirst()
                    }

                    publishSnapshot()
                }
            }
        }
    }

    // ── Coroutine jobs ────────────────────────────────────────────────────────

    private fun startCadenceTicker() {
        cadenceJob?.cancel()
        cadenceJob = scope.launch {
            while (isActive) {
                delay(CADENCE_TICK_MS)
                stateMutex.withLock {
                    val spm = if (cadenceWindow.size >= 2) {
                        val oldest     = cadenceWindow.first()
                        val newest     = cadenceWindow.last()
                        val elapsedMin = (newest.first - oldest.first) / 60_000.0
                        val delta      = newest.second - oldest.second
                        if (elapsedMin > 0) (delta / elapsedMin).toInt().coerceAtMost(MAX_CADENCE_SPM)
                        else 0
                    } else 0

                    val cadenceIntensity = when {
                        spm >= MODERATE_CADENCE_THRESHOLD  -> ActivityIntensity.MODERATE
                        spm >= SEDENTARY_CADENCE_THRESHOLD -> ActivityIntensity.LIGHT
                        else                               -> ActivityIntensity.SEDENTARY
                    }

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
                        stateEnteredAt     = nowMs
                    }
                    publishSnapshot(nowMs)
                }
            }
        }
    }

    private fun startTelemetryFlusher() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                delay(TELEMETRY_FLUSH_INTERVAL_MS)
                val snapshot = activitySignalBus.signal.value
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
        }
    }

    // ── Internal state management ─────────────────────────────────────────────

    private suspend fun handleDetectedActivity(activity: DetectedActivity) {
        val nowMs = System.currentTimeMillis()
        val mappedIntensity = when (activity.type) {
            DetectedActivity.STILL      -> ActivityIntensity.SEDENTARY
            DetectedActivity.IN_VEHICLE -> ActivityIntensity.IN_VEHICLE
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT    -> ActivityIntensity.LIGHT
            DetectedActivity.RUNNING    -> ActivityIntensity.VIGOROUS
            else                        -> null
        } ?: return

        val minimumConfidence = if (mappedIntensity > committedIntensity) 65 else 50
        if (activity.confidence < minimumConfidence) return

        stateMutex.withLock {
            if (mappedIntensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity = mappedIntensity
                stateEnteredAt     = nowMs
            }
            publishSnapshot(nowMs)
        }
    }

    private fun flushCurrentState(nowMs: Long) {
        if (stateEnteredAt == 0L) return
        val elapsed = (nowMs - stateEnteredAt).coerceAtLeast(0L)
        when (committedIntensity) {
            ActivityIntensity.SEDENTARY  -> sedentaryMs += elapsed
            ActivityIntensity.IN_VEHICLE -> { /* tracked separately */ }
            else                         -> activeMs += elapsed
        }
        stateEnteredAt = nowMs
        prefs.edit()
            .putLong("active_ms", activeMs)
            .putLong("sedentary_ms", sedentaryMs)
            .apply()
    }

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed   = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L
        val liveSedentary = sedentaryMs + if (committedIntensity == ActivityIntensity.SEDENTARY) liveElapsed else 0L
        val liveActive    = activeMs    + if (committedIntensity != ActivityIntensity.SEDENTARY &&
                                               committedIntensity != ActivityIntensity.IN_VEHICLE) liveElapsed else 0L
        val snapshot = ActivitySignal(
            steps               = sessionSteps,
            intensity           = committedIntensity,
            activeMinutes       = (liveActive / 60_000L).toInt(),
            sedentaryMinutes    = (liveSedentary / 60_000L).toInt(),
            stepSensorAvailable = stepSensor != null,
            accelAvailable      = accelAvailable,
            timestamp           = LocalDateTime.now(),
            // Fix A: isTracking carried through from the bus so it survives the snapshot.
            isTracking          = _isTracking.get()
        )
        // Fix A: Publish through the bus so the flow is persisted and restored on restart.
        activitySignalBus.emit(snapshot)
    }

    private fun resetSessionState() {
        baselineSteps      = -1
        sessionSteps       = 0
        committedIntensity = ActivityIntensity.SEDENTARY
        accelAvailable     = true
        activeMs           = 0L
        sedentaryMs        = 0L
        stateEnteredAt     = System.currentTimeMillis()
        cadenceWindow.clear()
        prefs.edit()
            .putInt("baseline_steps", -1)
            .putInt("session_steps", 0)
            .putLong("active_ms", 0L)
            .putLong("sedentary_ms", 0L)
            .apply()
    }
}
