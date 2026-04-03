package com.karamay.app.domain.usecase.sleep

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

class GetDailySleepSummaryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    companion object {
        private const val CONFIDENCE_THRESHOLD    = 75
        private const val MOTION_THRESHOLD        = 2
        private const val EDGE_WINDOW_MINUTES     = 60
        private const val STABILITY_WINDOW_SIZE   = 3
        private const val GAP_STITCH_THRESHOLD    = 0.7
        // Sessions separated by >= 4 hours are considered distinct sleep periods.
        private const val SESSION_GAP_HOURS       = 4L
        // Short gaps with no telemetry are treated as sensor dropouts and stitched.
        private const val AUTO_STITCH_MINUTES     = 15L
        // Minimum meaningful sleep block when estimating from telemetry only.
        private const val MIN_TELEMETRY_BLOCK_MIN = 30L
        // Maximum gap between telemetry readings before starting a new block.
        private const val TELEMETRY_GAP_MINUTES   = 10L
    }

    operator fun invoke(date: LocalDate): Flow<DailySleepSummary?> {
        // Fix #10 + irregular schedule: 48h window (6 AM previous day → 6 AM next day).
        // The old 23:59:59 cutoff truncated post-midnight telemetry for anyone who
        // sleeps across midnight (i.e., almost everyone). The old noon-to-noon window
        // failed night-shift workers. This window is wide enough for all schedules;
        // session attribution is handled by isolatePrimarySleepSession() below.
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

    // Fix irregular schedule: Replaced "pick longest session" with wake-time attribution.
    // Sessions are split on 4h gaps; the session whose wake time (endTime) falls on
    // targetDate is selected. Falls back to midpoint attribution for same-day sessions
    // (e.g. an afternoon nap that starts and ends on the same calendar day).
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

        // Primary: wake time lands on targetDate
        val byWakeTime = sessions.firstOrNull { s ->
            s.last().endTime.toLocalDate() == targetDate
        }
        if (byWakeTime != null) return byWakeTime.toList()

        // Fallback: session midpoint lands on targetDate (covers same-day naps)
        return sessions.firstOrNull { s ->
            val totalSeconds = Duration.between(s.first().startTime, s.last().endTime).seconds
            val mid = s.first().startTime.plusSeconds(totalSeconds / 2)
            mid.toLocalDate() == targetDate
        }?.toList() ?: emptyList()
    }

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
            sleepOnsetMinutes = minutesSince6PM(sessionStart),
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

        val onsetWindow = telemetry.filter {
            !it.timestamp.isBefore(firstSegment.startTime) &&
                    it.timestamp.isBefore(firstSegment.startTime.plusMinutes(EDGE_WINDOW_MINUTES.toLong()))
        }
        val candidateStart = onsetWindow.firstStableTimestamp() ?: firstSegment.startTime

        val wakeWindow = telemetry.filter {
            it.timestamp.isAfter(lastSegment.endTime.minusMinutes(EDGE_WINDOW_MINUTES.toLong())) &&
                    !it.timestamp.isAfter(lastSegment.endTime)
        }
        val candidateEnd = wakeWindow.lastStableTimestamp() ?: lastSegment.endTime

        // Fix A1: Guard against time inversion, which can occur when there is only
        // one segment and the onset/wake windows overlap or when sparse telemetry
        // produces a candidateStart after candidateEnd. An inverted trim would cause
        // stitchGaps() to receive an empty list and report zero sleep despite valid segments.
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

            if (gapMinutes <= 45) {
                val gapTelemetry = telemetry.filter {
                    it.timestamp.isAfter(currentEnd) &&
                            it.timestamp.isBefore(nextSeg.startTime)
                }

                // Fix A2: Invert the no-telemetry default.
                // Original code treated an empty gapTelemetry as "don't stitch",
                // effectively classifying sensor dropouts as awakenings.
                // A gap with no telemetry is far more likely a Doze/charging dropout
                // than a real awakening, especially under 15 minutes.
                val shouldStitch = if (gapTelemetry.isNotEmpty()) {
                    val asleepRatio = gapTelemetry.count {
                        it.confidence >= CONFIDENCE_THRESHOLD &&
                                it.deviceMotion <= MOTION_THRESHOLD
                    }.toDouble() / gapTelemetry.size
                    asleepRatio >= GAP_STITCH_THRESHOLD
                } else {
                    // No telemetry: auto-stitch short gaps, preserve boundary for longer ones.
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

    // Fix #11: Actually compute sleep duration from contiguous high-confidence
    // telemetry runs. The original returned all-zero DailySleepSummary, making
    // the entire telemetry-only fallback path useless.
    private fun buildEstimateFromTelemetry(
        date: String,
        telemetry: List<SleepTelemetry>
    ): DailySleepSummary? {
        val sleepTelemetry = telemetry
            .filter { it.confidence >= CONFIDENCE_THRESHOLD && it.deviceMotion <= MOTION_THRESHOLD }
            .sortedBy { it.timestamp }

        if (sleepTelemetry.isEmpty()) return null

        // Group into contiguous runs. SleepClassifyEvent fires roughly every minute;
        // a gap > 10 minutes means a real break in coverage, not just a missing event.
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

        // Discard blocks shorter than 30 minutes — too brief to be meaningful sleep.
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
            sleepOnsetMinutes = minutesSince6PM(sessionStart),
            isEstimated       = true
        )
    }

    // ── Telemetry extension helpers ──────────────────────────────────────────

    private fun List<SleepTelemetry>.firstStableTimestamp(): LocalDateTime? =
        windowed(STABILITY_WINDOW_SIZE, 1)
            .firstOrNull { window ->
                window.all {
                    it.confidence >= CONFIDENCE_THRESHOLD &&
                            it.deviceMotion <= MOTION_THRESHOLD
                }
            }?.first()?.timestamp

    private fun List<SleepTelemetry>.lastStableTimestamp(): LocalDateTime? =
        windowed(STABILITY_WINDOW_SIZE, 1)
            .lastOrNull { window ->
                window.all {
                    it.confidence >= CONFIDENCE_THRESHOLD &&
                            it.deviceMotion <= MOTION_THRESHOLD
                }
            }?.last()?.timestamp

    // Fix A6: 6 PM anchor is more meaningful than midnight for sleep onset across
    // all schedules. A normal sleeper at 11 PM → 300 min, a night-shift worker
    // sleeping at 8 AM → 840 min. Both are expressed on the same consistent scale
    // without requiring UI special-casing for large post-midnight clock values.
    private fun minutesSince6PM(dateTime: LocalDateTime): Int {
        val anchor = if (dateTime.hour >= 18) {
            dateTime.toLocalDate().atTime(18, 0)
        } else {
            dateTime.toLocalDate().minusDays(1).atTime(18, 0)
        }
        return Duration.between(anchor, dateTime).toMinutes().toInt()
    }
}