package com.karamay.app.data.receiver.sleep

import com.google.android.gms.location.SleepClassifyEvent
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [SleepReceiver] and [SleepRepositoryImpl].
 *
 * Mirrors [com.karamay.app.data.receiver.activity.ActivityEventBus] exactly:
 *   - A manifest-declared BroadcastReceiver cannot inject a repository directly.
 *   - This @Singleton bus decouples the receiver from the repository without
 *     requiring a global object or static state.
 *
 * Responsibility split with [SleepSignalBus]:
 *   - SleepEventBus  → receives raw Play Services events and converts them to domain
 *                      signals, then forwards to SleepSignalBus for state management.
 *   - SleepSignalBus → owns the StateFlow, SharedPreferences persistence, and
 *                      exposes the observable signal to the rest of the app.
 *
 * Lifecycle: [SleepReceiver.onReceive] calls [emit] after ensuring [SleepSignalBus.init]
 * has run. [SleepRepositoryImpl] never needs to call emit directly.
 */
@Singleton
class SleepEventBus @Inject constructor(
    private val sleepSignalBus: SleepSignalBus,
) {
    /**
     * Processes a batch of [SleepClassifyEvent]s from the Play Services API,
     * converts the latest event to a [SleepSignal], and forwards it to [SleepSignalBus].
     *
     * Mirrors [com.karamay.app.data.receiver.activity.ActivityEventBus.emit]:
     * the receiver calls this; the repository observes via [SleepSignalBus.signals].
     */
    fun emit(events: List<SleepClassifyEvent>) {
        if (events.isEmpty()) return
        val latest    = events.maxByOrNull { it.timestampMillis } ?: return
        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val newStatus = if (latest.confidence >= ASLEEP_CONFIDENCE_THRESHOLD)
            SleepStatus.ASLEEP else SleepStatus.AWAKE

        sleepSignalBus.update(
            status       = newStatus,
            confidence   = latest.confidence,
            ambientLight = latest.light.toFloat(),
            deviceMotion = latest.motion,
            timestamp    = eventTime,
        )
    }

    companion object {
        /** Play Services confidence threshold above which the user is classified as asleep. */
        private const val ASLEEP_CONFIDENCE_THRESHOLD = 75
    }
}
