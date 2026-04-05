package com.karamay.app.data.receiver.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.local.entity.SleepSegmentEntity
import com.karamay.app.data.local.entity.SleepTelemetryEntity
import com.karamay.app.domain.model.SleepStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Manifest-declared BroadcastReceiver for Sleep Recognition events from Play Services.
 *
 * Moved from [data.receiver] → [data.receiver.sleep] to sit alongside its two
 * collaborators [SleepEventBus] and [SleepSignalBus], mirroring the activity package:
 *
 *   data/receiver/activity/  →  ActivityReceiver, ActivityEventBus, ActivitySignalBus
 *   data/receiver/sleep/     →  SleepReceiver,    SleepEventBus,    SleepSignalBus
 *
 * Responsibility:
 *   - Persists raw telemetry and segment entities to Room (data layer concern).
 *   - Delegates signal state changes to [SleepEventBus], which forwards to [SleepSignalBus].
 *   - Never touches [SleepRepositoryImpl] directly.
 */
@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepSegmentDao: SleepSegmentDao
    @Inject lateinit var sleepTelemetryDao: SleepTelemetryDao
    @Inject lateinit var sleepEventBus: SleepEventBus
    @Inject lateinit var sleepSignalBus: SleepSignalBus

    override fun onReceive(context: Context, intent: Intent) {
        // Fix #17: init() before any emit() call — guarantees SharedPreferences is ready
        // even when SleepRepositoryImpl has not yet been constructed (cold restart after OS kill).
        sleepSignalBus.init(context)

        val pendingResult = goAsync()
        // Fix #13: Scoped coroutine with SupervisorJob instead of GlobalScope.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (SleepClassifyEvent.hasEvents(intent)) {
                    val events = SleepClassifyEvent.extractEvents(intent)
                    // Delegate event processing to SleepEventBus (mirrors ActivityReceiver → ActivityEventBus).
                    sleepEventBus.emit(events)

                    val telemetryEntities = events.map { event ->
                        SleepTelemetryEntity(
                            timestampMillis = event.timestampMillis,
                            confidence      = event.confidence,
                            ambientLight    = event.light.toFloat(),
                            deviceMotion    = event.motion,
                        )
                    }
                    sleepTelemetryDao.insertTelemetry(telemetryEntities)
                }

                if (SleepSegmentEvent.hasEvents(intent)) {
                    val events = SleepSegmentEvent.extractEvents(intent)
                    val segmentEntities = events.mapNotNull { event ->
                        val sleepStatus = when (event.status) {
                            SleepSegmentEvent.STATUS_SUCCESSFUL -> SleepStatus.ASLEEP.name
                            else -> return@mapNotNull null
                        }
                        SleepSegmentEntity(
                            startTime = Instant.ofEpochMilli(event.startTimeMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime().toString(),
                            endTime   = Instant.ofEpochMilli(event.endTimeMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime().toString(),
                            status    = sleepStatus,
                        )
                    }
                    if (segmentEntities.isNotEmpty()) {
                        sleepSegmentDao.insertSegments(segmentEntities)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SLEEP_DATA = "com.karamay.app.ACTION_SLEEP_DATA"
    }
}
