package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class CalculateSleepSegmentsUseCase @Inject constructor() {

    companion object {
        // Onset hours: valid sleep onset is ≥ SLEEP_EARLIEST_HOUR (18:00) OR ≤ SLEEP_LATEST_ONSET_HOUR.
        // Expanded from 08:00 to 11:00 to include late-morning sleepers (night-shift workers
        // who sleep 08:00–16:00 would have previously been rejected).
        const val SLEEP_EARLIEST_HOUR     = 18
        const val SLEEP_LATEST_ONSET_HOUR = 11

        // Wake hour: valid wake is ≥ MIN_WAKE_HOUR OR ≤ WAKE_LATEST_HOUR.
        // Expanded to 20 (8 PM) so that an afternoon/evening wake for a night-shift
        // sleeper is accepted.
        const val WAKE_LATEST_HOUR        = 20

        private const val MIN_SEGMENT_MINUTES = 45L
        private const val MIN_WAKE_HOUR       = 3

        private const val BRIEF_WAKEUP_MINUTES   = 5L
        private const val AR_STILL_MIN_CONFIDENCE = 70

        // Minimum screen-off ratio within a merged block required to treat it as a
        // sleep candidate. Relaxed from 0.75 to 0.60 so that nights with several
        // brief check-ins (each adding a small screen-on spike) are not discarded.
        private const val MIN_SCREEN_OFF_RATIO = 0.60
    }

    private data class Candidate(
        val startMs: Long,
        val endMs: Long,
        val wakeups: Int,
        val totalScreenOffMs: Long
    )

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
                blockEnd    = nextEnd
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

        return candidates.filter { c ->
            val duration = c.endMs - c.startMs
            if (duration <= 0) false
            else (c.totalScreenOffMs.toDouble() / duration) >= MIN_SCREEN_OFF_RATIO
        }
    }

    private fun applyDomainConstraints(candidates: List<Candidate>, zone: ZoneId): List<Candidate> {
        val minMs = MIN_SEGMENT_MINUTES * 60_000L
        return candidates.filter { c ->
            val durationMs = c.endMs - c.startMs
            if (durationMs < minMs) return@filter false

            val onsetHour = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalTime().hour
            // Valid onset: evening/night (≥ 18:00) OR early morning (≤ 11:00).
            // This covers both standard sleepers and night-shift/irregular sleepers.
            val validOnset = onsetHour >= SLEEP_EARLIEST_HOUR || onsetHour <= SLEEP_LATEST_ONSET_HOUR
            if (!validOnset) return@filter false

            val wakeHour = Instant.ofEpochMilli(c.endMs).atZone(zone).toLocalTime().hour
            // Valid wake: after 3 AM OR before/at 8 PM (covers afternoon wakes for night workers).
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