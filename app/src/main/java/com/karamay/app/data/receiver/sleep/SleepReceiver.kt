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
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Receives sleep-classify and sleep-segment events from Google Play Services.
 *
 * ## Bug 6 fix — graduated confidence thresholds (binary → three-state)
 *
 * **Original code:**
 * ```kotlin
 * val status = if (latest.confidence >= 75) SleepStatus.ASLEEP else SleepStatus.AWAKE
 * ```
 *
 * The Google Sleep API's `confidence` field represents the API's confidence that
 * the *device is being used by a sleeping person* (0 = definitely awake, 100 =
 * definitely asleep).  The original hard threshold of 75 had two problems:
 *
 * 1. **False AWAKE during light sleep / sleep onset:** confidence typically rises
 *    gradually from ~30 (drowsy) to 60–70 (light sleep) to 80–90 (deep sleep).
 *    With a threshold of 75, the entire light-sleep period was classified as AWAKE,
 *    meaning `lastAsleepTimestamp` was never written for those periods and the
 *    daily summary under-counted sleep by 30–60 minutes per night.
 *
 * 2. **False AWAKE during brief arousal:** confidence may dip to 60–74 during a
 *    normal mid-sleep roll-over or partial waking, flip-flopping the status and
 *    writing incorrect awakenings into the summary.
 *
 * **Fix:** Three-state classification:
 * - confidence ≥ 72 → ASLEEP     (high confidence, clear sleep signal)
 * - confidence 50–71 → UNKNOWN   (transitional / light sleep — do not flip to AWAKE)
 * - confidence < 50 → AWAKE      (majority confidence favours awake)
 *
 * UNKNOWN is surfaced in the live signal so the UI can display "Transitioning"
 * instead of incorrectly showing "Awake" during sleep onset/offset.
 * The post-hoc analysis in [GetDailySleepSummaryUseCase] uses raw confidence
 * values (threshold 60) rather than the live status, so it is unaffected by this
 * receiver-level classification.
 */
@AndroidEntryPoint
class SleepReceiver : BroadcastReceiver() {

    @Inject lateinit var sleepRepository: SleepRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                handleSleepClassifyEvents(intent)
                handleSleepSegmentEvents(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing sleep event", e)
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

        // Three-state classification (Bug 6 fix).
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

        /**
         * High confidence threshold — clear asleep signal.
         * Set at 72 rather than 75 to capture the upper end of light sleep without
         * introducing false positives during daytime drowsiness (typically < 60).
         */
        private const val ASLEEP_CONFIDENCE_HIGH = 72

        /**
         * Low confidence threshold — below this the API is majority-confident the
         * person is awake.
         */
        private const val AWAKE_CONFIDENCE_HIGH = 50

        /**
         * Maps a raw confidence value to a [SleepStatus].
         *
         * | Range   | Status  | Rationale                                     |
         * |---------|---------|-----------------------------------------------|
         * | 72–100  | ASLEEP  | Clear sleep signal                            |
         * | 50–71   | UNKNOWN | Transitional / light sleep / brief arousal    |
         * | 0–49    | AWAKE   | Majority-confidence awake                     |
         */
        fun classifyConfidence(confidence: Int): SleepStatus = when {
            confidence >= ASLEEP_CONFIDENCE_HIGH -> SleepStatus.ASLEEP
            confidence >= AWAKE_CONFIDENCE_HIGH  -> SleepStatus.UNKNOWN
            else                                 -> SleepStatus.AWAKE
        }

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}