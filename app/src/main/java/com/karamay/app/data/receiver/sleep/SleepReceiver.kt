package com.karamay.app.data.receiver.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepStatus
import com.karamay.app.domain.model.SleepTelemetry
import com.karamay.app.domain.repository.SleepRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var sleepEventBus: SleepEventBus
    @Inject lateinit var sleepSignalBus: SleepSignalBus

    override fun onReceive(context: Context, intent: Intent) {
        sleepSignalBus.init(context)
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                if (SleepClassifyEvent.hasEvents(intent)) {
                    val events = SleepClassifyEvent.extractEvents(intent)
                    sleepEventBus.emit(events)

                    val telemetry = events.map { event ->
                        SleepTelemetry(
                            timestamp    = Instant.ofEpochMilli(event.timestampMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime(),
                            confidence   = event.confidence,
                            ambientLight = event.light.toFloat(),
                            deviceMotion = event.motion,
                        )
                    }

                    sleepRepository.insertTelemetry(telemetry)
                }

                if (SleepSegmentEvent.hasEvents(intent)) {
                    val events = SleepSegmentEvent.extractEvents(intent)

                    val segments = events.mapNotNull { event ->
                        val sleepStatus = when (event.status) {
                            SleepSegmentEvent.STATUS_SUCCESSFUL -> SleepStatus.ASLEEP
                            else -> return@mapNotNull null
                        }

                        SleepSegment(
                            startTime = Instant.ofEpochMilli(event.startTimeMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime(),
                            endTime   = Instant.ofEpochMilli(event.endTimeMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime(),
                            status    = sleepStatus,
                        )
                    }

                    if (segments.isNotEmpty()) {
                        sleepRepository.insertSegments(segments)
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