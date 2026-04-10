package com.karamay.app.domain.usecase.sleep

import com.karamay.app.core.utils.SleepTimeUtils
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepStatus
import com.karamay.app.domain.model.sleep.SleepTelemetry
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
        private const val CONFIDENCE_THRESHOLD = 60
        private const val MOTION_THRESHOLD = 25
        private const val EDGE_WINDOW_MINUTES = 60
        private const val STABILITY_WINDOW_SIZE = 3
        private const val GAP_STITCH_THRESHOLD = 0.7
        const val SESSION_GAP_HOURS = 4L
        private const val AUTO_STITCH_MINUTES = 15L
        private const val MIN_TELEMETRY_BLOCK_MIN = 30L
        private const val TELEMETRY_GAP_MINUTES = 90L
        private const val MAX_GAP_STITCH_MINUTES = 45L
    }

    operator fun invoke(date: LocalDate): Flow<DailySleepSummary?> {
        // FIXED: Constrained to a strict 24-hour window (Noon yesterday to 6PM today)
        val broadStart = date.minusDays(1).atTime(12, 0)
        val broadEnd   = date.atTime(18, 0)

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

        val byWakeTime = sessions.firstOrNull { s ->
            s.last().endTime.toLocalDate() == targetDate
        }
        if (byWakeTime != null) return byWakeTime.toList()

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

        // FIXED: Enforce absolute real-time ceilings on calculations
        val now = LocalDateTime.now()
        val sessionStart     = finalBlocks.first().first
        val rawSessionEnd    = finalBlocks.last().second

        val cappedSessionEnd = if (rawSessionEnd.isAfter(now)) now else rawSessionEnd
        val timeInBedMinutes = Duration.between(sessionStart, cappedSessionEnd).toMinutes().toInt().coerceAtLeast(0)

        var totalSleepMinutes = finalBlocks.sumOf {
            Duration.between(it.first, it.second).toMinutes()
        }.toInt()

        // FIXED: Prevent total sleep from exceeding total elapsed time since onset
        totalSleepMinutes = totalSleepMinutes.coerceAtMost(timeInBedMinutes)

        val awakenings = (finalBlocks.size - 1).coerceAtLeast(0)

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

        val safeStart = if (candidateStart.isBefore(candidateEnd)) candidateStart else firstSegment.startTime
        val safeEnd   = if (candidateEnd.isAfter(candidateStart)) candidateEnd   else lastSegment.endTime

        // FIXED: Prevent array overwrite when the night consists of a single continuous segment
        if (mutableSegments.size == 1) {
            mutableSegments[0] = firstSegment.copy(startTime = safeStart, endTime = safeEnd)
        } else {
            mutableSegments[0] = firstSegment.copy(startTime = safeStart)
            mutableSegments[mutableSegments.lastIndex] = lastSegment.copy(endTime = safeEnd)
        }

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
            val gapMinutes = Duration.between(blockEnd, sleepTelemetry[i].timestamp).toMinutes()

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

        // FIXED: Enforce absolute real-time ceilings on calculations for telemetry fallback
        val now = LocalDateTime.now()
        val sessionStart = meaningfulBlocks.first().first
        val rawSessionEnd = meaningfulBlocks.last().second
        val cappedEnd = if (rawSessionEnd.isAfter(now)) now else rawSessionEnd

        val maxPossibleSleep = Duration.between(sessionStart, cappedEnd).toMinutes().toInt().coerceAtLeast(0)

        var totalSleepMinutes = meaningfulBlocks.sumOf {
            Duration.between(it.first, it.second).toMinutes()
        }.toInt()

        totalSleepMinutes = totalSleepMinutes.coerceAtMost(maxPossibleSleep)

        return DailySleepSummary(
            date              = date,
            totalSleepMinutes = totalSleepMinutes,
            timeInBedMinutes  = maxPossibleSleep,
            awakenings        = (meaningfulBlocks.size - 1).coerceAtLeast(0),
            sleepOnsetMinutes = SleepTimeUtils.minutesSince6PM(sessionStart),
            isEstimated       = true
        )
    }

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
}