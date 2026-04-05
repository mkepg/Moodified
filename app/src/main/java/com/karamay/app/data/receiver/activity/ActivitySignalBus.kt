package com.karamay.app.data.receiver.activity

import android.content.Context
import android.content.SharedPreferences
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the live [ActivitySignal] StateFlow and its SharedPreferences persistence.
 *
 * Mirrors [com.karamay.app.data.receiver.sleep.SleepSignalBus] exactly:
 *
 *   ActivitySignalBus ←→ SleepSignalBus   (state / persistence / Flow)
 *   ActivityEventBus  ←→ SleepEventBus    (bridge: Receiver → Repository)
 *   ActivityReceiver  ←→ SleepReceiver    (manifest BroadcastReceiver)
 *
 * Restores [ActivitySignal] state after a process kill so the UI receives a
 * meaningful value on the first Flow emission instead of zeroed-out defaults.
 *
 * Init contract: call [init] before the first [emit]. [ActivityRepositoryImpl]'s
 * init{} block is the primary call site.
 */
@Singleton
class ActivitySignalBus @Inject constructor() {

    private val _signal = MutableStateFlow(ActivitySignal())
    val signal: StateFlow<ActivitySignal> = _signal.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    // ── Initialisation ────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val restoredIntensity = runCatching {
                ActivityIntensity.valueOf(p.getString(KEY_INTENSITY, null) ?: "")
            }.getOrDefault(ActivityIntensity.SEDENTARY)

            _signal.value = ActivitySignal(
                steps               = p.getInt(KEY_SESSION_STEPS, 0),
                intensity           = restoredIntensity,
                activeMinutes       = (p.getLong(KEY_ACTIVE_MS, 0L) / 60_000L).toInt(),
                sedentaryMinutes    = (p.getLong(KEY_SEDENTARY_MS, 0L) / 60_000L).toInt(),
                stepSensorAvailable = true,
                accelAvailable      = true,
                timestamp           = LocalDateTime.now(),
                isTracking          = p.getBoolean(KEY_IS_TRACKING, false),
            )

            prefs = p
        }
    }

    // ── Emission (called by ActivityRepositoryImpl.publishSnapshot) ───────────

    fun emit(signal: ActivitySignal) {
        _signal.update { signal }
        prefs?.edit()
            ?.putInt(KEY_SESSION_STEPS, signal.steps)
            ?.putString(KEY_INTENSITY, signal.intensity.name)
            ?.putLong(KEY_ACTIVE_MS, signal.activeMinutes * 60_000L)
            ?.putLong(KEY_SEDENTARY_MS, signal.sedentaryMinutes * 60_000L)
            ?.apply()
    }

    // ── Tracking state (called by ActivityRepositoryImpl) ─────────────────────

    fun setTrackingState(isTracking: Boolean) {
        _signal.update { it.copy(isTracking = isTracking) }
        prefs?.edit()?.putBoolean(KEY_IS_TRACKING, isTracking)?.apply()
    }

    // ── Session reset (called by ActivityRepositoryImpl) ──────────────────────

    fun resetSession() {
        _signal.value = ActivitySignal()
        prefs?.edit()
            ?.remove(KEY_SESSION_STEPS)
            ?.remove(KEY_INTENSITY)
            ?.remove(KEY_ACTIVE_MS)
            ?.remove(KEY_SEDENTARY_MS)
            ?.apply()
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME        = "activity_signal_bus_prefs"
        private const val KEY_SESSION_STEPS = "session_steps"
        private const val KEY_INTENSITY     = "intensity"
        private const val KEY_ACTIVE_MS     = "active_ms"
        private const val KEY_SEDENTARY_MS  = "sedentary_ms"
        private const val KEY_IS_TRACKING   = "is_tracking"
    }
}
