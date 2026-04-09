package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.SleepSegment
import com.karamay.app.domain.model.sleep.SleepStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class CalculateSleepSegmentsUseCase @Inject constructor() {
    companion object {
        const val SLEEP_EARLIEST_HOUR = 18          // Expanded: Allow sleep starting at 6:00 PM
        const val SLEEP_LATEST_ONSET_HOUR = 8       // Expanded: Allow falling asleep as late as 8:00 AM
        const val WAKE_LATEST_HOUR = 17             // Expanded: Query usage stats up until 5:00 PM
        private const val MIN_SLEEP_MINUTES = 150L
        private const val BRIEF_WAKEUP_MINUTES = 20L
        private const val AR_STILL_MIN_CONFIDENCE = 70
    }

    private data class Candidate(val startMs: Long, val endMs: Long, val wakeups: Int)

    operator fun invoke(
        targetDate: LocalDate,
        rawGaps: List<Pair<Long, Long>>,
        hasMotionSensor: Boolean,
        arStillConfidence: Int
    ): List<SleepSegment> {
        if (rawGaps.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val candidates = mergeGapsIntoCandidates(rawGaps)
        val filtered = applyDomainConstraints(candidates, zone)

        if (filtered.isEmpty()) return emptyList()

        val scored = scoreWithSupplementarySignals(filtered, hasMotionSensor, arStillConfidence, zone)
        val best = scored.maxByOrNull { it.second }?.first ?: return emptyList()

        return listOf(best)
    }

    private fun mergeGapsIntoCandidates(rawGaps: List<Pair<Long, Long>>): List<Candidate> {
        val briefMs = BRIEF_WAKEUP_MINUTES * 60_000L
        val candidates = mutableListOf<Candidate>()
        var blockStart = rawGaps.first().first
        var blockEnd = rawGaps.first().second
        var wakeups = 0

        for (i in 1 until rawGaps.size) {
            val (nextStart, nextEnd) = rawGaps[i]
            val screenOnGap = nextStart - blockEnd

            if (screenOnGap <= briefMs) {
                wakeups++
                blockEnd = nextEnd
            } else {
                candidates.add(Candidate(blockStart, blockEnd, wakeups))
                blockStart = nextStart
                blockEnd = nextEnd
                wakeups = 0
            }
        }
        candidates.add(Candidate(blockStart, blockEnd, wakeups))
        return candidates
    }

    private fun applyDomainConstraints(candidates: List<Candidate>, zone: ZoneId): List<Candidate> {
        val minMs = MIN_SLEEP_MINUTES * 60_000L
        return candidates.filter { c ->
            val durationMs = c.endMs - c.startMs
            if (durationMs < minMs) return@filter false

            val onsetHour = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalTime().hour
            onsetHour >= SLEEP_EARLIEST_HOUR || onsetHour <= SLEEP_LATEST_ONSET_HOUR
        }
    }

    private fun scoreWithSupplementarySignals(
        candidates: List<Candidate>,
        hasMotionSensor: Boolean,
        arStillConfidence: Int,
        zone: ZoneId
    ): List<Pair<SleepSegment, Int>> {
        val sensorBonus = if (!hasMotionSensor) 10 else 0

        return candidates.map { c ->
            val durationMin = (c.endMs - c.startMs) / 60_000L
            val durationScore = (durationMin.toFloat() / 480f * 100f).coerceIn(0f, 100f).toInt()

            val arBonus = if (arStillConfidence >= AR_STILL_MIN_CONFIDENCE) {
                ((arStillConfidence / 100f) * 15f).toInt()
            } else 0

            val score = (durationScore + arBonus + sensorBonus).coerceAtMost(100)

            val segment = SleepSegment(
                startTime = Instant.ofEpochMilli(c.startMs).atZone(zone).toLocalDateTime(),
                endTime = Instant.ofEpochMilli(c.endMs).atZone(zone).toLocalDateTime(),
                status = SleepStatus.ASLEEP
            )

            segment to score
        }
    }
}