package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class CalculateSleepSegmentsUseCase @Inject constructor() {
    companion object {
        const val SLEEP_EARLIEST_HOUR     = 18
        const val SLEEP_LATEST_ONSET_HOUR = 8
        const val WAKE_LATEST_HOUR        = 17

        // FIXED: Lowered to 45 minutes to prevent starvation of legitimate, fragmented sleep chunks.
        private const val MIN_SEGMENT_MINUTES = 45L

        private const val MIN_WAKE_HOUR = 3

        // FIXED: Reduced from 20 to 5 to prevent active usage (like gaming) from being swallowed.
        private const val BRIEF_WAKEUP_MINUTES   = 5L
        private const val AR_STILL_MIN_CONFIDENCE = 70
    }

    // FIXED: Added totalScreenOffMs to validate the density of the sleep block
    private data class Candidate(val startMs: Long, val endMs: Long, val wakeups: Int, val totalScreenOffMs: Long)

    operator fun invoke(
        targetDate:        LocalDate,
        rawGaps:           List<Pair<Long, Long>>,
        hasMotionSensor:   Boolean,
        arStillConfidence: Int
    ): List<SleepSegment> {
        if (rawGaps.isEmpty()) return emptyList()

        val zone       = ZoneId.systemDefault()
        val candidates = mergeGapsIntoCandidates(rawGaps)
        val filtered   = applyDomainConstraints(candidates, zone)

        if (filtered.isEmpty()) return emptyList()

        val scored = scoreWithSupplementarySignals(filtered, hasMotionSensor, arStillConfidence, zone)
        val best   = scored.maxByOrNull { it.second }?.first ?: return emptyList()

        return listOf(best)
    }

    private fun mergeGapsIntoCandidates(rawGaps: List<Pair<Long, Long>>): List<Candidate> {
        val briefMs    = BRIEF_WAKEUP_MINUTES * 60_000L
        val candidates = mutableListOf<Candidate>()

        var blockStart  = rawGaps.first().first
        var blockEnd    = rawGaps.first().second
        var wakeups     = 0
        var screenOffMs = blockEnd - blockStart

        for (i in 1 until rawGaps.size) {
            val (nextStart, nextEnd) = rawGaps[i]
            val screenOnGap = nextStart - blockEnd

            if (screenOnGap <= briefMs) {
                wakeups++
                blockEnd = nextEnd
                screenOffMs += (nextEnd - nextStart)
            } else {
                candidates.add(Candidate(blockStart, blockEnd, wakeups, screenOffMs))
                blockStart  = nextStart
                blockEnd    = nextEnd
                wakeups     = 0
                screenOffMs = nextEnd - nextStart
            }
        }
        candidates.add(Candidate(blockStart, blockEnd, wakeups, screenOffMs))

        // FIXED: Enforce that a sleep block must be predominantly screen-off time (>= 75%)
        return candidates.filter { c ->
            val duration = c.endMs - c.startMs
            if (duration <= 0) false else (c.totalScreenOffMs.toDouble() / duration) >= 0.75
        }
    }

    private fun applyDomainConstraints(candidates: List<Candidate>, zone: ZoneId): List<Candidate> {
        val minMs = MIN_SEGMENT_MINUTES * 60_000L
        return candidates.filter { c ->
            val durationMs = c.endMs - c.startMs
            if (durationMs < minMs) return@filter false

            val onsetHour = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalTime().hour
            val validOnset = onsetHour >= SLEEP_EARLIEST_HOUR || onsetHour <= SLEEP_LATEST_ONSET_HOUR
            if (!validOnset) return@filter false

            val wakeHour = Instant.ofEpochMilli(c.endMs).atZone(zone).toLocalTime().hour
            wakeHour >= MIN_WAKE_HOUR || wakeHour <= WAKE_LATEST_HOUR
        }
    }

    private fun scoreWithSupplementarySignals(
        candidates:        List<Candidate>,
        hasMotionSensor:   Boolean,
        arStillConfidence: Int,
        zone:              ZoneId
    ): List<Pair<SleepSegment, Int>> {
        val sensorBonus = if (!hasMotionSensor) 10 else 0

        return candidates.map { c ->
            val durationMin   = (c.endMs - c.startMs) / 60_000L
            val durationScore = (durationMin.toFloat() / 480f * 100f).coerceIn(0f, 100f).toInt()

            val arBonus = if (arStillConfidence >= AR_STILL_MIN_CONFIDENCE) {
                ((arStillConfidence / 100f) * 15f).toInt()
            } else 0

            val score = (durationScore + arBonus + sensorBonus).coerceAtMost(100)

            val segment = SleepSegment(
                startTime = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalDateTime(),
                endTime   = Instant.ofEpochMilli(c.endMs).atZone(zone).toLocalDateTime(),
                status    = SleepStatus.ASLEEP
            )
            segment to score
        }
    }
}