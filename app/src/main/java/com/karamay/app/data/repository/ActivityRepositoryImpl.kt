package com.karamay.app.data.repository

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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

/**
 * Activity tracking repository.
 *
 * ## Bug fixes in this revision
 *
 * ### Bug 1 — Cadence window staleness (primary active/sedentary mislabelling fix)
 *
 * **Root cause:** [TYPE_STEP_COUNTER] is silent when the user is not walking.
 * [onSensorChanged] is never called, so the cadence window is never pruned.
 * Old walking entries stay in the window indefinitely, causing [CalculateActivityIntensityUseCase]
 * to compute a non-zero SPM and hold intensity at LIGHT/MODERATE even while the user
 * is completely stationary. Active minutes accumulated continuously; sedentary minutes
 * never did.
 *
 * **Fix:** The cadence ticker now calls [pruneCadenceWindow] before passing the
 * window to the use case. Any entry older than [CADENCE_WINDOW_MS] is removed, and
 * if the *newest* entry is older than [STALENESS_THRESHOLD_MS] relative to the current
 * time, the entire window is treated as stale and an empty list is passed to the use
 * case. The use case returns SEDENTARY for an empty window, correctly transitioning the
 * intensity and switching time accumulation to sedentaryMs.
 *
 * ### Bug 2 — IN_VEHICLE time accumulation
 *
 * **Root cause:** [flushCurrentState] discarded all IN_VEHICLE time entirely.
 * A user travelling by car or bus for 30 minutes would have 30 minutes unaccounted
 * for in both active and sedentary totals.
 *
 * **Fix:** IN_VEHICLE time now accrues to sedentaryMs. Being in a vehicle is a
 * sedentary activity (you are not voluntarily moving), and vehicle vibration that
 * occasionally crosses the step-counter threshold is filtered out by the staleness
 * guard in [pruneCadenceWindow].
 *
 * ### Bug 3 — Play Services and cadence ticker source conflict
 *
 * **Root cause:** Play Services Activity Recognition fires every 60 s. The cadence
 * ticker fires every 10 s. When Play Services correctly detected STILL and called
 * [updateActivityIntensity] with SEDENTARY, the cadence ticker would overwrite it
 * 10 s later with LIGHT/MODERATE based on a stale window.
 *
 * **Fix:** [updateActivityIntensity] now sets [lastRecognitionTimestamp] whenever
 * Play Services provides an authoritative SEDENTARY or IN_VEHICLE signal. The cadence
 * ticker respects a [RECOGNITION_AUTHORITY_MS] window after such a signal, during which
 * it does not override the intensity. Play Services is the ground truth for these states;
 * cadence-derived overrides only resume after the authority window expires.
 *
 * ### Bug 4 — Step cadence vs. session average
 *
 * This is fixed in [ActivitySignal] — [instantCadenceSpm] is now a separate field
 * carrying the live cadence from the window, distinct from the session-step total.
 */
@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityTelemetryDao: ActivityTelemetryDao,
    private val preferencesDataSource: ActivityPreferencesDataSource,
    private val calculateIntensity: CalculateActivityIntensityUseCase
) : ActivityRepository {

    companion object {
        private const val TAG = "ActivityRepo"

        // Play Services update interval
        private const val UPDATE_INTERVAL_MS = 60_000L

        // Step sensor batch latency
        private const val MAX_REPORT_LATENCY_US = 5 * 60 * 1_000_000L

        // Cadence window: keep entries from the last 60 s
        private const val CADENCE_WINDOW_MS = 60_000L

        // Cadence ticker interval
        private const val CADENCE_TICK_MS = 10_000L

        /**
         * If the newest cadence window entry is older than this, the window is
         * considered stale and SPM is forced to 0 → SEDENTARY.
         * 35 s is generous enough to bridge pauses (traffic lights, elevator waits)
         * without treating a genuine stop as still-walking.
         */
        private const val STALENESS_THRESHOLD_MS = 35_000L

        /**
         * After Play Services delivers a SEDENTARY or IN_VEHICLE signal, the cadence
         * ticker will not override the committed intensity for this many milliseconds.
         * This prevents stale cadence window entries from immediately overwriting
         * an authoritative recognition result.
         * Value: 70 s — slightly longer than one Play Services update cycle.
         */
        private const val RECOGNITION_AUTHORITY_MS = 70_000L
    }

    private val scope         = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex    = Mutex()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val activityRecognitionClient = ActivityRecognition.getClient(context)
    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ActivityReceiver::class.java).apply {
                action = ActivityReceiver.ACTION_PROCESS_ACTIVITY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private var cadenceJob        : Job? = null
    private var committedIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY
    private var accelAvailable    : Boolean = true
    private var stateEnteredAt    : Long = 0L

    /** Timestamp of the last Play Services recognition result that granted authority. */
    private var lastRecognitionTimestamp: Long = 0L

    /** True while Play Services authority window is active. */
    private val isRecognitionAuthoritative: Boolean
        get() = (System.currentTimeMillis() - lastRecognitionTimestamp) < RECOGNITION_AUTHORITY_MS

    private val cadenceWindow = ArrayDeque<Pair<Long, Int>>()

    /** Live SPM from the cadence window — published in each snapshot. */
    private var instantCadenceSpm: Int = 0

    private val _isTracking = AtomicBoolean(false)
    override val isTracking: Boolean get() = preferencesDataSource.isTracking

    private val _signal = MutableStateFlow(buildInitialSignal())
    override fun observeSignal(): Flow<ActivitySignal> = _signal.asStateFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

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
            hasActiveSession    = preferencesDataSource.isTracking || activeMs > 0L || sedentaryMs > 0L || steps > 0
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

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
                stateEnteredAt = System.currentTimeMillis()
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
            sensorManager.registerListener(
                stepListener, it,
                SensorManager.SENSOR_DELAY_NORMAL,
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
                committedIntensity        = ActivityIntensity.SEDENTARY
                accelAvailable            = true
                stateEnteredAt            = if (currentlyTracking) System.currentTimeMillis() else 0L
                lastRecognitionTimestamp  = 0L
                instantCadenceSpm         = 0
                cadenceWindow.clear()
                _signal.value = ActivitySignal(
                    isTracking          = currentlyTracking,
                    hasActiveSession    = currentlyTracking,
                    instantCadenceSpm   = 0
                )
            }
        }
    }

    override suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long) {
        activityTelemetryDao.deleteOlderThan(cutoffMillis)
    }

    /**
     * Called by [ActivityReceiver] when Play Services delivers an activity recognition result.
     *
     * ### Source conflict fix (Bug 3)
     * When Play Services authoritatively says SEDENTARY or IN_VEHICLE, we record the
     * timestamp so the cadence ticker will not override it for [RECOGNITION_AUTHORITY_MS].
     * When Play Services says LIGHT/MODERATE/VIGOROUS, we allow it to update immediately
     * but do not grant authority — the cadence ticker remains the primary driver for
     * active states (it has a much shorter update interval).
     */
    override suspend fun updateActivityIntensity(intensity: ActivityIntensity, confidence: Int) {
        val nowMs = System.currentTimeMillis()

        // Use a higher confidence bar for upgrades to reduce false-positive active time.
        val minimumConfidence = if (intensity > committedIntensity) 65 else 50
        if (confidence < minimumConfidence) return

        stateMutex.withLock {
            // Grant recognition authority for still/vehicle states — these are ground truth
            // and the cadence ticker must not override them immediately.
            if (intensity == ActivityIntensity.SEDENTARY ||
                intensity == ActivityIntensity.IN_VEHICLE) {
                lastRecognitionTimestamp = nowMs
            }

            if (intensity != committedIntensity) {
                flushCurrentState(nowMs)
                committedIntensity         = intensity
                stateEnteredAt             = nowMs
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

    // ── Step sensor ───────────────────────────────────────────────────────────

    private val stepListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            scope.launch {
                stateMutex.withLock {
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
            preferencesDataSource.baselineSteps  = newBaseline
            preferencesDataSource.bootEpochMillis = currentBootEpoch
            Log.d(TAG, "Step baseline reset. total=$sensorTotal prev=$previousSessionSteps new=$newBaseline")
        }

        preferencesDataSource.sessionSteps =
            (sensorTotal - preferencesDataSource.baselineSteps).coerceAtLeast(0)

        val now = System.currentTimeMillis()
        cadenceWindow.addLast(now to preferencesDataSource.sessionSteps)

        // Prune old entries — this is the existing pruning inside onSensorChanged.
        // Note: this only runs when steps arrive.  The staleness check in
        // pruneCadenceWindow() handles the case where the user stops stepping.
        while (cadenceWindow.isNotEmpty() && now - cadenceWindow.first().first > CADENCE_WINDOW_MS) {
            cadenceWindow.removeFirst()
        }

        publishSnapshot()
    }

    // ── Cadence ticker ────────────────────────────────────────────────────────

    private fun startCadenceTicker() {
        cadenceJob?.cancel()
        cadenceJob = scope.launch {
            while (isActive) {
                delay(CADENCE_TICK_MS)
                stateMutex.withLock {
                    val nowMs = System.currentTimeMillis()

                    // Prune stale cadence entries before evaluating intensity.
                    // This is the primary fix for Bug 1.
                    val freshWindow = pruneCadenceWindow(nowMs)

                    // Respect Play Services authority for SEDENTARY/IN_VEHICLE.
                    // If the cadence ticker would try to move us away from an
                    // authoritatively-set still/vehicle state, skip the override.
                    if (isRecognitionAuthoritative &&
                        (committedIntensity == ActivityIntensity.SEDENTARY ||
                                committedIntensity == ActivityIntensity.IN_VEHICLE)) {
                        // Still in the authority window — don't touch committedIntensity.
                        publishSnapshot(nowMs)
                        return@withLock
                    }

                    val newIntensity = calculateIntensity(freshWindow, committedIntensity)

                    if (newIntensity != committedIntensity) {
                        flushCurrentState(nowMs)
                        committedIntensity         = newIntensity
                        stateEnteredAt             = nowMs
                        preferencesDataSource.intensity = committedIntensity.name
                    }
                    publishSnapshot(nowMs)
                }
            }
        }
    }

    /**
     * Removes entries older than [CADENCE_WINDOW_MS] from [cadenceWindow] and
     * returns the pruned list to pass to [CalculateActivityIntensityUseCase].
     *
     * **Staleness gate (Bug 1 fix):** If the newest remaining entry is older than
     * [STALENESS_THRESHOLD_MS] relative to [nowMs], the user has not taken any steps
     * recently. We clear the window entirely and return an empty list. The use case
     * will return SEDENTARY for an empty window, correctly switching time accumulation
     * to sedentaryMs.
     */
    private fun pruneCadenceWindow(nowMs: Long): List<Pair<Long, Int>> {
        // Remove entries outside the rolling window.
        while (cadenceWindow.isNotEmpty() &&
            nowMs - cadenceWindow.first().first > CADENCE_WINDOW_MS) {
            cadenceWindow.removeFirst()
        }

        // Staleness check: if the newest entry is too old, treat window as empty.
        if (cadenceWindow.isNotEmpty() &&
            nowMs - cadenceWindow.last().first > STALENESS_THRESHOLD_MS) {
            cadenceWindow.clear()
        }

        return cadenceWindow.toList()
    }

    // ── State accumulation ────────────────────────────────────────────────────

    /**
     * Commits the elapsed time in the current intensity state to the appropriate
     * preference bucket before a state transition.
     *
     * ### IN_VEHICLE fix (Bug 2)
     * The original code discarded IN_VEHICLE time entirely. Being in a vehicle is a
     * sedentary activity — vehicle vibration is excluded from step counting by the
     * staleness gate. IN_VEHICLE time now accrues to sedentaryMs.
     */
    private fun flushCurrentState(nowMs: Long) {
        if (stateEnteredAt == 0L) return
        val elapsed = (nowMs - stateEnteredAt).coerceAtLeast(0L)
        when (committedIntensity) {
            ActivityIntensity.SEDENTARY,
            ActivityIntensity.IN_VEHICLE -> preferencesDataSource.sedentaryMs += elapsed
            else                         -> preferencesDataSource.activeMs    += elapsed
        }
        stateEnteredAt = nowMs
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    private fun publishSnapshot(nowMs: Long = System.currentTimeMillis()) {
        val liveElapsed = if (stateEnteredAt > 0L) (nowMs - stateEnteredAt).coerceAtLeast(0L) else 0L

        val isSedentaryLike = committedIntensity == ActivityIntensity.SEDENTARY ||
                committedIntensity == ActivityIntensity.IN_VEHICLE

        val liveSedentary = preferencesDataSource.sedentaryMs + if (isSedentaryLike) liveElapsed else 0L
        val liveActive    = preferencesDataSource.activeMs    + if (!isSedentaryLike) liveElapsed else 0L

        // Recompute instantCadenceSpm for the snapshot without modifying the window.
        val latestSpm = if (cadenceWindow.size >= 2) {
            val oldest    = cadenceWindow.first()
            val newest    = cadenceWindow.last()
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
                hasActiveSession    = stateEnteredAt > 0L || liveActive > 0L ||
                        liveSedentary > 0L || preferencesDataSource.sessionSteps > 0
            )
        }
    }
}