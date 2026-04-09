package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class CalculateSleepSegmentsUseCase @Inject constructor() {

    companion object {
        const val SLEEP_EARLIEST_HOUR     = 18  // 6 PM — earliest plausible sleep onset
        const val SLEEP_LATEST_ONSET_HOUR = 8   // 8 AM — latest plausible sleep onset
        const val WAKE_LATEST_HOUR        = 17  // 5 PM — latest plausible wake time

        // Raised from 150 min (2.5 h) to 210 min (3.5 h). The original threshold
        // was too permissive: any 2.5-hour inactivity window (e.g. a long commute
        // or a movie) could pass domain constraints and be stored as sleep.
        private const val MIN_SLEEP_MINUTES = 210L

        // Wakeup must occur no earlier than 3 AM. Rejects gaps that end in the
        // middle of the night (e.g. phone left charging at midnight then picked
        // up at 1 AM), which are screen-off windows but not sleep sessions.
        private const val MIN_WAKE_HOUR = 3

        private const val BRIEF_WAKEUP_MINUTES   = 20L
        private const val AR_STILL_MIN_CONFIDENCE = 70
    }

    private data class Candidate(val startMs: Long, val endMs: Long, val wakeups: Int)

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
        var blockStart = rawGaps.first().first
        var blockEnd   = rawGaps.first().second
        var wakeups    = 0
        for (i in 1 until rawGaps.size) {
            val (nextStart, nextEnd) = rawGaps[i]
            val screenOnGap = nextStart - blockEnd
            if (screenOnGap <= briefMs) {
                wakeups++
                blockEnd = nextEnd
            } else {
                candidates.add(Candidate(blockStart, blockEnd, wakeups))
                blockStart = nextStart
                blockEnd   = nextEnd
                wakeups    = 0
            }
        }
        candidates.add(Candidate(blockStart, blockEnd, wakeups))
        return candidates
    }

    private fun applyDomainConstraints(candidates: List<Candidate>, zone: ZoneId): List<Candidate> {
        val minMs = MIN_SLEEP_MINUTES * 60_000L
        return candidates.filter { c ->
            // 1. Duration must meet the minimum threshold.
            val durationMs = c.endMs - c.startMs
            if (durationMs < minMs) return@filter false

            // 2. Sleep onset must be in a plausible night-time window:
            //    18:00–23:59 (evening) OR 00:00–08:00 (after midnight / early morning).
            val onsetHour = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalTime().hour
            val validOnset = onsetHour >= SLEEP_EARLIEST_HOUR || onsetHour <= SLEEP_LATEST_ONSET_HOUR
            if (!validOnset) return@filter false

            // 3. Wake time must be at least MIN_WAKE_HOUR (3 AM). This rejects
            //    gaps that start and end entirely within the first half of the
            //    night (e.g. phone put down at midnight, picked up at 1 AM),
            //    which are real screen-off windows but not sleep sessions.
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