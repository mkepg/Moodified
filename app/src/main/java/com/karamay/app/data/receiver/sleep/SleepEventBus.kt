package com.karamay.app.data.receiver.sleep

import com.google.android.gms.location.SleepClassifyEvent
import com.karamay.app.data.local.datasource.SleepPreferencesDataSource
import com.karamay.app.domain.model.SleepStatus
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepEventBus @Inject constructor(
    private val sleepSignalBus: SleepSignalBus,
    private val preferencesDataSource: SleepPreferencesDataSource
) {
    fun emit(events: List<SleepClassifyEvent>) {
        if (events.isEmpty()) return

        val latest = events.maxByOrNull { it.timestampMillis } ?: return
        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val newStatus = if (latest.confidence >= ASLEEP_CONFIDENCE_THRESHOLD) {
            SleepStatus.ASLEEP
        } else {
            SleepStatus.AWAKE
        }

        // Persist the timestamp if the user just fell asleep
        if (newStatus == SleepStatus.ASLEEP && sleepSignalBus.signals.value.status != SleepStatus.ASLEEP) {
            preferencesDataSource.lastAsleepTimestamp = eventTime
        }

        sleepSignalBus.update(
            status       = newStatus,
            confidence   = latest.confidence,
            ambientLight = latest.light.toFloat(),
            deviceMotion = latest.motion,
            timestamp    = eventTime,
        )
    }

    companion object {
        private const val ASLEEP_CONFIDENCE_THRESHOLD = 75
    }
}