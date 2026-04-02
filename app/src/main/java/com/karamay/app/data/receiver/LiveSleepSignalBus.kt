package com.karamay.app.data.receiver

import com.google.android.gms.location.SleepClassifyEvent
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * In-process bus for live SleepClassifyEvents emitted by the Play Services Sleep API.
 *
 * Fix 5 addition: [lastAsleepTimestamp] records the wall-clock time of the *first*
 * ASLEEP signal in the current tracking session.  This lets SleepRepositoryImpl build
 * a temporary "morning estimate" summary when the user opens the app right after waking
 * up, before the finalized SleepSegmentEvent Broadcast has fired.
 *
 * The timestamp is reset whenever [resetSession] is called (i.e. every time the user
 * starts a new tracking session via [setTrackingState](false) → startTracking).
 */
object LiveSleepSignalBus {

    private val _signals = MutableStateFlow(SleepSignal())
    val signals: StateFlow<SleepSignal> = _signals.asStateFlow()

    /**
     * Wall-clock time of the earliest ASLEEP signal seen in the current tracking session.
     * Null until the first ASLEEP event arrives.
     * NOT reset when the user wakes up — we need it to persist until startTracking() is
     * called again so the morning-estimate logic can read it.
     */
    @Volatile
    var lastAsleepTimestamp: LocalDateTime? = null
        private set

    // ────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────

    fun emit(events: List<SleepClassifyEvent>) {
        if (events.isEmpty()) return
        val latest = events.maxByOrNull { it.timestampMillis } ?: return
        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val newStatus = if (latest.confidence >= 90) SleepStatus.ASLEEP else SleepStatus.AWAKE

        // Fix 5: capture the onset of the first sleep period this session
        if (newStatus == SleepStatus.ASLEEP && lastAsleepTimestamp == null) {
            lastAsleepTimestamp = eventTime
        }

        _signals.update { current ->
            current.copy(
                status       = newStatus,
                confidence   = latest.confidence,
                ambientLight = latest.light.toFloat(),
                deviceMotion = latest.motion,
                timestamp    = eventTime,
                isTracking   = current.isTracking
            )
        }
    }

    /**
     * Called by SleepRepositoryImpl when tracking is started or stopped.
     * Sets the [isTracking] flag on the live signal.
     */
    fun setTrackingState(isTracking: Boolean) {
        _signals.update { it.copy(isTracking = isTracking) }
    }

    /**
     * Clears session-scoped state.  Must be called at the start of every new tracking
     * session so stale onset timestamps from a previous night don't bleed through.
     */
    fun resetSession() {
        lastAsleepTimestamp = null
        _signals.value = SleepSignal()
    }
}