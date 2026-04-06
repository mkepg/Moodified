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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepRepository: SleepRepository

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (SleepClassifyEvent.hasEvents(intent)) {
                    val events = SleepClassifyEvent.extractEvents(intent)
                    val latest = events.maxByOrNull { it.timestampMillis }

                    if (latest != null) {
                        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                        val status = if (latest.confidence >= 75) SleepStatus.ASLEEP else SleepStatus.AWAKE

                        sleepRepository.updateLiveSignal(
                            status     = status,
                            confidence = latest.confidence,
                            motion     = latest.motion,
                            time       = eventTime
                        )
                    }

                    val telemetry = events.map { event ->
                        SleepTelemetry(
                            timestamp    = Instant.ofEpochMilli(event.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                            confidence   = event.confidence,
                            deviceMotion = event.motion
                        )
                    }
                    sleepRepository.persistTelemetry(telemetry)
                }

                if (SleepSegmentEvent.hasEvents(intent)) {
                    val events = SleepSegmentEvent.extractEvents(intent)
                    val segments = events.mapNotNull { event ->
                        val sleepStatus = when (event.status) {
                            SleepSegmentEvent.STATUS_SUCCESSFUL -> SleepStatus.ASLEEP
                            else -> return@mapNotNull null
                        }
                        SleepSegment(
                            startTime = Instant.ofEpochMilli(event.startTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                            endTime   = Instant.ofEpochMilli(event.endTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                            status    = sleepStatus
                        )
                    }
                    if (segments.isNotEmpty()) {
                        sleepRepository.persistSegments(segments)
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