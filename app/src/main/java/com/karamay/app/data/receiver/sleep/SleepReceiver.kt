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
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepRepository: SleepRepository

    // INJECT DAOS DIRECTLY for raw data writing
    @Inject lateinit var sleepSegmentDao: SleepSegmentDao
    @Inject lateinit var sleepTelemetryDao: SleepTelemetryDao

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SleepClassifyEvent.hasEvents(intent)) {
                    val events = SleepClassifyEvent.extractEvents(intent)

                    // Update in-memory live signal
                    val latest = events.maxByOrNull { it.timestampMillis }
                    if (latest != null) {
                        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()

                        val status = if (latest.confidence >= 75) SleepStatus.ASLEEP else SleepStatus.AWAKE

                        sleepRepository.updateLiveSignal(
                            status = status,
                            confidence = latest.confidence,
                            light = latest.light.toFloat(),
                            motion = latest.motion,
                            time = eventTime
                        )
                    }

                    // Map directly to Entities and insert
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
                    val segmentEntities = events.mapNotNull { event ->
                        val sleepStatus = when (event.status) {
                            SleepSegmentEvent.STATUS_SUCCESSFUL -> SleepStatus.ASLEEP
                            else -> return@mapNotNull null
                        }
                        SleepSegmentEntity(
                            startTimeMillis = event.startTimeMillis,
                            endTimeMillis   = event.endTimeMillis,
                            status          = sleepStatus.name
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