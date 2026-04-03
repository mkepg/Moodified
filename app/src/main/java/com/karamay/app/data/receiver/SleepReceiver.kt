package com.karamay.app.data.receiver

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepSegmentDao: SleepSegmentDao
    @Inject lateinit var sleepTelemetryDao: SleepTelemetryDao

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        // Fix #12: Use GlobalScope tied to pendingResult's finally block instead of a
        // persistent CoroutineScope on the receiver instance. BroadcastReceiver objects
        // are created fresh per broadcast — a persistent scope is never cancelled and leaks.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (SleepClassifyEvent.hasEvents(intent)) {
                    val events = SleepClassifyEvent.extractEvents(intent)
                    LiveSleepSignalBus.emit(events)
                    val telemetryEntities = events.map { event ->
                        SleepTelemetryEntity(
                            timestampMillis = event.timestampMillis,
                            confidence      = event.confidence,
                            ambientLight    = event.light.toFloat(),
                            deviceMotion    = event.motion
                        )
                    }
                    sleepTelemetryDao.insertTelemetry(telemetryEntities)
                }

                if (SleepSegmentEvent.hasEvents(intent)) {
                    val events = SleepSegmentEvent.extractEvents(intent)

                    // Fix #7: SleepSegmentEvent.status is a data quality flag, not sleep/wake.
                    // Every segment in this API represents a period the user was asleep.
                    // STATUS_SUCCESSFUL means the data is reliable → store as ASLEEP.
                    // STATUS_MISSING_DATA means the classifier lacked sensor data → discard.
                    // Storing missing-data segments as AWAKE was semantically wrong and would
                    // corrupt daily summaries with phantom "awake" blocks inside sleep sessions.
                    val segmentEntities = events.mapNotNull { event ->
                        val sleepStatus = when (event.status) {
                            SleepSegmentEvent.STATUS_SUCCESSFUL -> SleepStatus.ASLEEP.name
                            else -> return@mapNotNull null  // discard unreliable segments
                        }
                        SleepSegmentEntity(
                            startTime = java.time.Instant.ofEpochMilli(event.startTimeMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDateTime().toString(),
                            endTime   = java.time.Instant.ofEpochMilli(event.endTimeMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDateTime().toString(),
                            status    = sleepStatus
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