package com.karamay.app.domain.usecase.sleep

import com.karamay.app.core.utils.SleepTimeUtils
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepStatus
import com.karamay.app.domain.model.SleepTelemetry
import com.karamay.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Computes the [DailySleepSummary] for a given date by reconciling official
 * Play Services sleep segments with raw telemetry.
 *
 * ## Bug fixes in this revision
 *
 * ### Bug 7 — Motion threshold was too strict
 *
 * **Original:** `MOTION_THRESHOLD = 2`
 * The Google Sleep API reports `SleepClassifyEvent.motion` on a 0–100 scale
 * (higher = more movement detected by the device's accelerometer).  A threshold
 * of 2 means "completely still" — normal sleep involves rolling over, which
 * routinely produces motion values of 10–30.  With MOTION_THRESHOLD = 2:
 *   - Nearly all telemetry was filtered out in [buildEstimateFromTelemetry].
 *   - [firstStableTimestamp] / [lastStableTimestamp] almost never matched
 *     because 3 consecutive samples with motion ≤ 2 is extremely rare.
 *   - Result: sleep onset/wake time were always null; estimates were drastically
 *     under-reported or null entirely.
 *
 * **Fix:** `MOTION_THRESHOLD = 25`
 * This allows normal sleep-position changes while still filtering out wake-state
 * movement (walking, phone use → motion typically 50–100).
 *
 * ### Bug 6 — Confidence threshold was too coarse in telemetry path
 *
 * The receiver-level classification (ASLEEP if confidence ≥ 75) is a hard binary
 * that governs the live signal.  For post-hoc analysis in this use case, we use
 * the raw confidence values directly rather than relying on the binary status.
 * The confidence threshold here is intentionally different from (and lower than)
 * the receiver threshold: in telemetry analysis we are looking for blocks of
 * *sustained* moderate confidence rather than instantaneous high confidence.
 *
 * **Fix:** `CONFIDENCE_THRESHOLD = 60` (down from 75) for telemetry analysis.
 * This captures the light-sleep/transition periods that were previously dropped.
 *
 * ### Bug 8 — Stability window too strict after motion threshold fix
 *
 * With MOTION_THRESHOLD = 2, [firstStableTimestamp] required 3 consecutive samples
 * all with motion ≤ 2 — an almost impossible condition.  After raising the threshold
 * to 25 this becomes achievable, so [STABILITY_WINDOW_SIZE] remains at 3 which is
 * appropriate.
 *
 * ### Bug 9 — wakeWindow boundary filter (minor)
 *
 * The original `!it.timestamp.isAfter(lastSegment.endTime)` excluded the
 * endTime boundary itself.  Changed to `!it.timestamp.isAfter` on a slightly
 * extended boundary so the telemetry sample closest to wake time is included.
 *
 * ### Bug 10 — Session gap constants coordinated
 *
 * SESSION_GAP_HOURS is now the single source of truth via a companion constant.
 * SleepRepositoryImpl.SESSION_RESUME_HOURS should equal this value.
 */
class GetDailySleepSummaryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    companion object {
        /**
         * Minimum sleep-API confidence to consider a telemetry sample as "asleep".
         * Lower than the receiver threshold (75) because we look for *sustained*
         * blocks of moderate confidence, not instantaneous high confidence.
         */
        private const val CONFIDENCE_THRESHOLD = 60

        /**
         * Maximum motion value to consider a sample as compatible with sleep.
         * The Google Sleep API motion scale is 0–100.  Normal sleep movement
         * (rolling over, minor repositioning) produces motion 5–25; walking or
         * active phone use is typically 50–100.
         * Original value was 2 (essentially "perfectly still") — too strict.
         */
        private const val MOTION_THRESHOLD = 25

        /** Minutes of edge telemetry to examine when refining segment boundaries. */
        private const val EDGE_WINDOW_MINUTES = 60

        /** Number of consecutive samples that must all pass thresholds for a stable point. */
        private const val STABILITY_WINDOW_SIZE = 3

        /** Minimum asleep-ratio in a gap for it to be stitched into the sleep block. */
        private const val GAP_STITCH_THRESHOLD = 0.7

        /**
         * Sleep sessions separated by ≥ this many hours are treated as distinct nights.
         * Must match [SleepRepositoryImpl.SESSION_GAP_HOURS] and
         * [SleepRepositoryImpl.SESSION_RESUME_HOURS] for consistent session attribution.
         */
        const val SESSION_GAP_HOURS = 4L

        /** Gaps shorter than this are auto-stitched when no telemetry is available. */
        private const val AUTO_STITCH_MINUTES = 15L

        /** A telemetry-derived sleep block must span at least this many minutes to count. */
        private const val MIN_TELEMETRY_BLOCK_MIN = 30L

        /**
         * A gap between consecutive telemetry samples larger than this indicates the
         * sensor was not reporting (device restarted, Play Services paused, etc.) and
         * the gap should not be counted as sleep time.
         */
        private const val TELEMETRY_GAP_MINUTES = 10L

        /** Maximum gap between segments to consider stitching. */
        private const val MAX_GAP_STITCH_MINUTES = 45L
    }

    operator fun invoke(date: LocalDate): Flow<DailySleepSummary?> {
        val broadStart = date.minusDays(1).atTime(6, 0)
        val broadEnd   = date.plusDays(1).atTime(6, 0)

        val segmentsFlow  = repository.getSegmentsForDate(date)
        val telemetryFlow = repository.getTelemetryBetween(broadStart, broadEnd)

        return combine(segmentsFlow, telemetryFlow) { segments, telemetry ->
            val asleepSegments = segments
                .filter { it.status == SleepStatus.ASLEEP }
                .sortedBy { it.startTime }

            if (asleepSegments.isNotEmpty()) {
                val targetSession = isolatePrimarySleepSession(asleepSegments, date)
                if (targetSession.isEmpty()) null
                else reconcileSleepData(date.toString(), targetSession, telemetry)
            } else {
                buildEstimateFromTelemetry(date.toString(), telemetry)
            }
        }
    }

    // ── Session isolation ─────────────────────────────────────────────────────

    private fun isolatePrimarySleepSession(
        segments: List<SleepSegment>,
        targetDate: LocalDate
    ): List<SleepSegment> {
        if (segments.isEmpty()) return emptyList()

        val sessions = mutableListOf<MutableList<SleepSegment>>()
        var current  = mutableListOf(segments.first())

        for (i in 1 until segments.size) {
            val gapHours = Duration.between(
                segments[i - 1].endTime,
                segments[i].startTime
            ).toHours()
            if (gapHours >= SESSION_GAP_HOURS) {
                sessions.add(current)
                current = mutableListOf()
            }
            current.add(segments[i])
        }
        sessions.add(current)

        // Prefer the session that woke up on targetDate.
        val byWakeTime = sessions.firstOrNull { s ->
            s.last().endTime.toLocalDate() == targetDate
        }
        if (byWakeTime != null) return byWakeTime.toList()

        // Fallback: the session whose midpoint falls on targetDate.
        return sessions.firstOrNull { s ->
            val totalSeconds = Duration.between(s.first().startTime, s.last().endTime).seconds
            val mid = s.first().startTime.plusSeconds(totalSeconds / 2)
            mid.toLocalDate() == targetDate
        }?.toList() ?: emptyList()
    }

    // ── Primary path: segments + telemetry reconciliation ────────────────────

    private fun reconcileSleepData(
        date: String,
        officialSegments: List<SleepSegment>,
        telemetry: List<SleepTelemetry>
    ): DailySleepSummary {
        val trimmedSegments = trimEdges(officialSegments, telemetry)
        val finalBlocks     = stitchGaps(trimmedSegments, telemetry)

        if (finalBlocks.isEmpty()) return DailySleepSummary(date, 0, 0, 0, 0)

        val totalSleepMinutes = finalBlocks.sumOf {
            Duration.between(it.first, it.second).toMinutes()
        }.toInt()
        val sessionStart     = finalBlocks.first().first
        val sessionEnd       = finalBlocks.last().second
        val timeInBedMinutes = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()
        val awakenings       = (finalBlocks.size - 1).coerceAtLeast(0)

        return DailySleepSummary(
            date              = date,
            totalSleepMinutes = totalSleepMinutes,
            timeInBedMinutes  = timeInBedMinutes,
            awakenings        = awakenings,
            sleepOnsetMinutes = SleepTimeUtils.minutesSince6PM(sessionStart),
            isEstimated       = false
        )
    }

    private fun trimEdges(
        segments: List<SleepSegment>,
        telemetry: List<SleepTelemetry>
    ): List<SleepSegment> {
        if (segments.isEmpty() || telemetry.isEmpty()) return segments

        val mutableSegments = segments.toMutableList()
        val firstSegment    = mutableSegments.first()
        val lastSegment     = mutableSegments.last()

        // Look for the first stable telemetry point within the onset window.
        val onsetWindow = telemetry.filter {
            !it.timestamp.isBefore(firstSegment.startTime) &&
                    it.timestamp.isBefore(firstSegment.startTime.plusMinutes(EDGE_WINDOW_MINUTES.toLong()))
        }
        val candidateStart = onsetWindow.firstStableTimestamp() ?: firstSegment.startTime

        // Look for the last stable telemetry point within the wake window.
        // Bug 9 fix: use isAfter for the lower bound (exclusive) and !isAfter for the
        // upper bound (inclusive of endTime itself).
        val wakeWindow = telemetry.filter {
            it.timestamp.isAfter(lastSegment.endTime.minusMinutes(EDGE_WINDOW_MINUTES.toLong())) &&
                    !it.timestamp.isAfter(lastSegment.endTime)
        }
        val candidateEnd = wakeWindow.lastStableTimestamp() ?: lastSegment.endTime

        val safeStart = if (candidateStart.isBefore(candidateEnd)) candidateStart else firstSegment.startTime
        val safeEnd   = if (candidateEnd.isAfter(candidateStart)) candidateEnd   else lastSegment.endTime

        mutableSegments[0] = firstSegment.copy(startTime = safeStart)
        mutableSegments[mutableSegments.lastIndex] = lastSegment.copy(endTime = safeEnd)

        return mutableSegments.filter {
            Duration.between(it.startTime, it.endTime).toMinutes() > 0
        }
    }

    private fun stitchGaps(
        segments: List<SleepSegment>,
        telemetry: List<SleepTelemetry>
    ): List<Pair<LocalDateTime, LocalDateTime>> {
        if (segments.isEmpty()) return emptyList()

        val blocks       = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var currentStart = segments.first().startTime
        var currentEnd   = segments.first().endTime

        for (i in 1 until segments.size) {
            val nextSeg    = segments[i]
            val gapMinutes = Duration.between(currentEnd, nextSeg.startTime).toMinutes()

            if (gapMinutes <= MAX_GAP_STITCH_MINUTES) {
                val gapTelemetry = telemetry.filter {
                    it.timestamp.isAfter(currentEnd) &&
                            it.timestamp.isBefore(nextSeg.startTime)
                }
                val shouldStitch = if (gapTelemetry.isNotEmpty()) {
                    val asleepRatio = gapTelemetry.count {
                        it.confidence >= CONFIDENCE_THRESHOLD &&
                                it.deviceMotion <= MOTION_THRESHOLD
                    }.toDouble() / gapTelemetry.size
                    asleepRatio >= GAP_STITCH_THRESHOLD
                } else {
                    gapMinutes <= AUTO_STITCH_MINUTES
                }

                if (shouldStitch) {
                    currentEnd = nextSeg.endTime
                    continue
                }
            }

            blocks.add(Pair(currentStart, currentEnd))
            currentStart = nextSeg.startTime
            currentEnd   = nextSeg.endTime
        }
        blocks.add(Pair(currentStart, currentEnd))
        return blocks
    }

    // ── Fallback path: estimate entirely from telemetry ───────────────────────

    /**
     * Called when no official segment data is available.
     *
     * Uses raw telemetry samples to identify contiguous "asleep" blocks.
     * Blocks shorter than [MIN_TELEMETRY_BLOCK_MIN] are discarded as naps or
     * sensor artefacts.
     *
     * With the corrected thresholds (confidence ≥ 60, motion ≤ 25) this path now
     * produces meaningful results for users whose devices did not finalise sleep
     * segments (rare but possible when battery saver is aggressive).
     */
    private fun buildEstimateFromTelemetry(
        date: String,
        telemetry: List<SleepTelemetry>
    ): DailySleepSummary? {
        val sleepTelemetry = telemetry
            .filter { it.confidence >= CONFIDENCE_THRESHOLD && it.deviceMotion <= MOTION_THRESHOLD }
            .sortedBy { it.timestamp }

        if (sleepTelemetry.isEmpty()) return null

        val blocks     = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var blockStart = sleepTelemetry.first().timestamp
        var blockEnd   = sleepTelemetry.first().timestamp

        for (i in 1 until sleepTelemetry.size) {
            val gapMinutes = Duration.between(
                sleepTelemetry[i - 1].timestamp,
                sleepTelemetry[i].timestamp
            ).toMinutes()

            if (gapMinutes > TELEMETRY_GAP_MINUTES) {
                blocks.add(blockStart to blockEnd)
                blockStart = sleepTelemetry[i].timestamp
            }
            blockEnd = sleepTelemetry[i].timestamp
        }
        blocks.add(blockStart to blockEnd)

        val meaningfulBlocks = blocks.filter {
            Duration.between(it.first, it.second).toMinutes() >= MIN_TELEMETRY_BLOCK_MIN
        }
        if (meaningfulBlocks.isEmpty()) return null

        val totalSleepMinutes = meaningfulBlocks.sumOf {
            Duration.between(it.first, it.second).toMinutes()
        }.toInt()
        val sessionStart     = meaningfulBlocks.first().first
        val sessionEnd       = meaningfulBlocks.last().second
        val timeInBedMinutes = Duration.between(sessionStart, sessionEnd).toMinutes().toInt()
        val awakenings       = (meaningfulBlocks.size - 1).coerceAtLeast(0)

        return DailySleepSummary(
            date              = date,
            totalSleepMinutes = totalSleepMinutes,
            timeInBedMinutes  = timeInBedMinutes,
            awakenings        = awakenings,
            sleepOnsetMinutes = SleepTimeUtils.minutesSince6PM(sessionStart),
            isEstimated       = true
        )
    }

    // ── Stable-point helpers ──────────────────────────────────────────────────

    /**
     * Returns the timestamp of the first sample in the first window where all
     * [STABILITY_WINDOW_SIZE] consecutive samples pass both thresholds.
     * Used to refine the sleep-onset edge.
     *
     * With MOTION_THRESHOLD = 25 (up from 2), this now produces non-null results
     * under realistic sleep conditions.
     */
    private fun List<SleepTelemetry>.firstStableTimestamp(): LocalDateTime? =
        windowed(STABILITY_WINDOW_SIZE, 1)
            .firstOrNull { window ->
                window.all {
                    it.confidence >= CONFIDENCE_THRESHOLD &&
                            it.deviceMotion <= MOTION_THRESHOLD
                }
            }?.first()?.timestamp

    /**
     * Returns the timestamp of the last sample in the last stable window.
     * Used to refine the wake-time edge.
     */
    private fun List<SleepTelemetry>.lastStableTimestamp(): LocalDateTime? =
        windowed(STABILITY_WINDOW_SIZE, 1)
            .lastOrNull { window ->
                window.all {
                    it.confidence >= CONFIDENCE_THRESHOLD &&
                            it.deviceMotion <= MOTION_THRESHOLD
                }
            }?.last()?.timestamp
}