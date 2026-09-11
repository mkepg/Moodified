package com.moodified.app.domain.usecase.sleep

import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class CalculateSleepSegmentsUseCase
    @Inject
    constructor() {
        companion object {
            const val SLEEP_EARLIEST_HOUR = 18
            const val SLEEP_LATEST_ONSET_HOUR = 11
            const val WAKE_LATEST_HOUR = 20

            private const val MIN_WAKE_HOUR = 3 // RESTORED MISSING CONSTANT

            private const val MIN_SEGMENT_MINUTES = 45L
            private const val BRIEF_WAKEUP_MAX_MINUTES = 10L // Generous allowance for true awakenings
            private const val SIGNIFICANT_BLOCK_MINUTES = 60L // A block must be at least 1hr to be merged into the Anchor

            private const val MIN_SCREEN_OFF_RATIO = 0.60
            private const val PLAUSIBILITY_THRESHOLD_MINS = 12 * 60
        }

        private data class Candidate(
            val startMs: Long,
            val endMs: Long,
            val wakeups: Int,
            val totalScreenOffMs: Long,
        )

        operator fun invoke(
            targetDate: LocalDate,
            rawGaps: List<Pair<Long, Long>>,
        ): List<SleepSegment> {
            if (rawGaps.isEmpty()) return emptyList()

            val zone = ZoneId.systemDefault()

            // 1. Isolate the true sleep session using Anchor Logic instead of linear merging
            val anchorCandidate = isolateCoreSleepSession(rawGaps) ?: return emptyList()

            // 2. Validate against domain constraints (time of day, minimum length)
            if (!isValidDomainConstraint(anchorCandidate, zone)) return emptyList()

            // 3. Calculate Confidence & Build Segment
            val durationMin = ((anchorCandidate.endMs - anchorCandidate.startMs) / 60_000L).toInt()
            val screenOffRatio = anchorCandidate.totalScreenOffMs.toFloat() / (anchorCandidate.endMs - anchorCandidate.startMs).coerceAtLeast(1)

            var calculatedConfidence = screenOffRatio * 100f

            if (durationMin > PLAUSIBILITY_THRESHOLD_MINS) {
                val hoursOver = (durationMin - PLAUSIBILITY_THRESHOLD_MINS) / 60f
                calculatedConfidence -= (hoursOver * 10f)
            }

            val segment =
                SleepSegment(
                    startTime = Instant.ofEpochMilli(anchorCandidate.startMs).atZone(zone).toLocalDateTime(),
                    endTime = Instant.ofEpochMilli(anchorCandidate.endMs).atZone(zone).toLocalDateTime(),
                    status = SleepStatus.ASLEEP,
                    awakenings = anchorCandidate.wakeups,
                    timeInBedMinutes = durationMin,
                    totalSleepMinutes = (anchorCandidate.totalScreenOffMs / 60_000L).toInt(),
                    confidence = calculatedConfidence.toInt().coerceIn(0, 100),
                )

            return listOf(segment)
        }

        /**
         * ANCHOR-BASED CORE ISOLATION
         * Instead of merging left-to-right, find the heaviest sleep block first,
         * then selectively absorb adjacent blocks only if they are heavy enough to
         * represent true fragmented sleep. This natively rejects 30-40 min pre-bed routines.
         */
        private fun isolateCoreSleepSession(rawGaps: List<Pair<Long, Long>>): Candidate? {
            if (rawGaps.isEmpty()) return null

            // 1. Find the Anchor Block (The longest single screen-off gap)
            val anchorIndex = rawGaps.indices.maxByOrNull { rawGaps[it].second - rawGaps[it].first } ?: 0

            var sessionStartMs = rawGaps[anchorIndex].first
            var sessionEndMs = rawGaps[anchorIndex].second
            var totalScreenOff = sessionEndMs - sessionStartMs
            var wakeups = 0

            // 2. Expand Backwards (Look for prior sleep blocks interrupted by an awakening)
            for (i in anchorIndex - 1 downTo 0) {
                val gap = rawGaps[i]
                val screenOnTime = sessionStartMs - gap.second
                val gapDuration = gap.second - gap.first

                // If the user was awake for less than 10 mins AND the preceding gap was substantial (> 1 hr)
                // It's a fragmented sleep session. Merge it.
                if (screenOnTime <= BRIEF_WAKEUP_MAX_MINUTES * 60_000L && gapDuration >= SIGNIFICANT_BLOCK_MINUTES * 60_000L) {
                    sessionStartMs = gap.first
                    totalScreenOff += gapDuration
                    wakeups++
                } else {
                    // The moment we hit a gap that is too small (e.g., 38m washing up) OR a wake time too long,
                    // we stop expanding backwards. This explicitly drops pre-bed routines.
                    break
                }
            }

            // 3. Expand Forwards (Look for snoozing / falling back asleep after morning alarm)
            for (i in anchorIndex + 1 until rawGaps.size) {
                val gap = rawGaps[i]
                val screenOnTime = gap.first - sessionEndMs
                val gapDuration = gap.second - gap.first

                if (screenOnTime <= BRIEF_WAKEUP_MAX_MINUTES * 60_000L && gapDuration >= SIGNIFICANT_BLOCK_MINUTES * 60_000L) {
                    sessionEndMs = gap.second
                    totalScreenOff += gapDuration
                    wakeups++
                } else {
                    break
                }
            }

            return Candidate(sessionStartMs, sessionEndMs, wakeups, totalScreenOff)
        }

        private fun isValidDomainConstraint(
            candidate: Candidate,
            zone: ZoneId,
        ): Boolean {
            val durationMs = candidate.endMs - candidate.startMs
            if (durationMs < MIN_SEGMENT_MINUTES * 60_000L) return false

            val onsetHour = Instant.ofEpochMilli(candidate.startMs).atZone(zone).toLocalTime().hour
            val validOnset = onsetHour >= SLEEP_EARLIEST_HOUR || onsetHour <= SLEEP_LATEST_ONSET_HOUR
            if (!validOnset) return false

            val wakeHour = Instant.ofEpochMilli(candidate.endMs).atZone(zone).toLocalTime().hour
            val validWake = wakeHour >= MIN_WAKE_HOUR && wakeHour <= WAKE_LATEST_HOUR
            if (!validWake) return false

            val ratio = candidate.totalScreenOffMs.toDouble() / durationMs.toDouble()
            return ratio >= MIN_SCREEN_OFF_RATIO
        }
    }
