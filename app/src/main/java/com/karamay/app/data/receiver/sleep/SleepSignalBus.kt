package com.karamay.app.data.receiver.sleep

import android.content.Context
import android.content.SharedPreferences
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the live [SleepSignal] StateFlow and its SharedPreferences persistence.
 *
 * Renamed from LiveSleepSignalBus and moved to [data.receiver.sleep] to mirror
 * [com.karamay.app.data.receiver.activity.ActivitySignalBus] exactly:
 *
 *   ActivitySignalBus ←→ SleepSignalBus   (state / persistence / Flow)
 *   ActivityEventBus  ←→ SleepEventBus    (bridge: Receiver → Repository)
 *   ActivityReceiver  ←→ SleepReceiver    (manifest BroadcastReceiver)
 *
 * Responsibility split:
 *   - This class holds the [StateFlow], persists [lastAsleepTimestamp] and
 *     [isTracking] to SharedPreferences, and exposes [update], [setTrackingState],
 *     and [resetSession].
 *   - [SleepEventBus] converts raw Play Services events and calls [update] here.
 *   - [SleepRepositoryImpl] reads [signals] and calls [setTrackingState] / [resetSession].
 *
 * Init contract (mirrors ActivitySignalBus):
 *   Call [init] before the first [update]. [SleepRepositoryImpl]'s init{} block
 *   is the primary call site. [SleepReceiver.onReceive] also calls [init] as a
 *   defensive guard for cold restarts where the receiver fires before the Hilt
 *   graph has constructed the repository (Fix #17).
 */
@Singleton
class SleepSignalBus @Inject constructor() {

    private val _signals = MutableStateFlow(SleepSignal())
    val signals: StateFlow<SleepSignal> = _signals.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    @Volatile
    var lastAsleepTimestamp: LocalDateTime? = null
        private set

    // ── Initialisation ────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            runCatching {
                val savedTime = p.getString(KEY_LAST_ASLEEP, null)
                if (savedTime != null) lastAsleepTimestamp = LocalDateTime.parse(savedTime)
            }

            // Restore isTracking so the StateFlow is correct on first emission.
            val wasTracking = p.getBoolean(KEY_IS_TRACKING, false)
            if (wasTracking) _signals.update { it.copy(isTracking = true) }

            prefs = p
        }
    }

    // ── State update (called by SleepEventBus) ────────────────────────────────

    /**
     * Applies a new signal snapshot derived from a [com.google.android.gms.location.SleepClassifyEvent].
     * Called exclusively by [SleepEventBus.emit] — repository code should never call this directly.
     */
    fun update(
        status: SleepStatus,
        confidence: Int,
        ambientLight: Float,
        deviceMotion: Int,
        timestamp: LocalDateTime,
    ) {
        val currentStatus = _signals.value.status

        if (status == SleepStatus.ASLEEP && currentStatus != SleepStatus.ASLEEP) {
            lastAsleepTimestamp = timestamp
            prefs?.edit()?.putString(KEY_LAST_ASLEEP, timestamp.toString())?.apply()
        }

        _signals.update { current ->
            current.copy(
                status       = status,
                confidence   = confidence,
                ambientLight = ambientLight,
                deviceMotion = deviceMotion,
                timestamp    = timestamp,
                isTracking   = current.isTracking,
            )
        }
    }

    // ── Tracking state (called by SleepRepositoryImpl) ────────────────────────

    fun setTrackingState(isTracking: Boolean) {
        _signals.update { it.copy(isTracking = isTracking) }
        prefs?.edit()?.putBoolean(KEY_IS_TRACKING, isTracking)?.apply()
    }

    // ── Session reset (called by SleepRepositoryImpl) ─────────────────────────

    fun resetSession() {
        lastAsleepTimestamp = null
        _signals.value = SleepSignal()
        prefs?.edit()
            ?.remove(KEY_LAST_ASLEEP)
            ?.putBoolean(KEY_IS_TRACKING, false)
            ?.apply()
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME      = "sleep_signal_bus_prefs"
        private const val KEY_LAST_ASLEEP = "last_asleep_time"
        private const val KEY_IS_TRACKING = "is_tracking"
    }
}
