package com.karamay.app.data.receiver.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepRepository: SleepRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            // FIX BUG-08: Same pattern as ActivityReceiver BUG-04. Sleep segment payloads
            // can be larger than activity payloads (they include full segment histories), so
            // the risk of exceeding Android's ~10-second async window is even higher here.
            // The finally block guarantees pendingResult.finish() is always called.
            try {
                withTimeout(8_000L) {
                    try {
                        handleSleepClassifyEvents(intent)
                        handleSleepSegmentEvents(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing sleep event", e)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSleepClassifyEvents(intent: Intent) {
        if (!SleepClassifyEvent.hasEvents(intent)) return
        val events = SleepClassifyEvent.extractEvents(intent)
        val latest = events.maxByOrNull { it.timestampMillis } ?: return
        val eventTime = Instant.ofEpochMilli(latest.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val status = classifyConfidence(latest.confidence)
        sleepRepository.updateLiveSignal(
            status     = status,
            confidence = latest.confidence,
            motion     = latest.motion,
            time       = eventTime
        )
        val telemetry = events.map { event ->
            SleepTelemetry(
                timestamp    = Instant.ofEpochMilli(event.timestampMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                confidence   = event.confidence,
                deviceMotion = event.motion
            )
        }
        sleepRepository.persistTelemetry(telemetry)
    }

    private suspend fun handleSleepSegmentEvents(intent: Intent) {
        if (!SleepSegmentEvent.hasEvents(intent)) return
        val events   = SleepSegmentEvent.extractEvents(intent)
        val segments = events.mapNotNull { event ->
            val sleepStatus = when (event.status) {
                SleepSegmentEvent.STATUS_SUCCESSFUL -> SleepStatus.ASLEEP
                else -> return@mapNotNull null
            }
            SleepSegment(
                startTime = Instant.ofEpochMilli(event.startTimeMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                endTime   = Instant.ofEpochMilli(event.endTimeMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                status    = sleepStatus
            )
        }
        if (segments.isNotEmpty()) {
            sleepRepository.persistSegments(segments)
        }
    }

    companion object {
        private const val TAG = "SleepReceiver"
        const val ACTION_SLEEP_DATA = "com.karamay.app.ACTION_SLEEP_DATA"
        private const val ASLEEP_CONFIDENCE_HIGH = 72
        private const val AWAKE_CONFIDENCE_HIGH  = 50
        fun classifyConfidence(confidence: Int): SleepStatus = when {
            confidence >= ASLEEP_CONFIDENCE_HIGH -> SleepStatus.ASLEEP
            confidence >= AWAKE_CONFIDENCE_HIGH  -> SleepStatus.UNKNOWN
            else                                 -> SleepStatus.AWAKE
        }
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
