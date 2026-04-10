package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.SleepTrends
import com.karamay.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GetWeeklySleepTrendsUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    companion object {
        private const val DEFAULT_BASELINE_MINUTES    = 480
        private const val MAX_BACKFILL_SLEEP_MINUTES  = 600
        private const val DURATION_NORMALIZER_MINUTES = 120.0
        private const val ONSET_NORMALIZER_MINUTES    = 120.0
        private const val SLEEP_DEBT_RECOVERY_RATE    = 0.5
    }

    operator fun invoke(endDate: LocalDate): Flow<SleepTrends?> {
        return repository.getWeeklySummaries(endDate).map { summaries ->
            if (summaries.isEmpty()) return@map null

            val cappedSummaries = summaries.map { s ->
                if (s.isEstimated)
                    s.copy(totalSleepMinutes = s.totalSleepMinutes.coerceAtMost(MAX_BACKFILL_SLEEP_MINUTES))
                else
                    s
            }

            // --- Sleep debt accumulation ---
            var runningDebt = 0
            cappedSummaries
                .sortedBy { it.date }
                .forEach { summary ->
                    val delta = summary.totalSleepMinutes - DEFAULT_BASELINE_MINUTES
                    if (delta < 0) {
                        runningDebt += (-delta)
                    } else {
                        val recovery = (delta * SLEEP_DEBT_RECOVERY_RATE).toInt()
                        runningDebt  = (runningDebt - recovery).coerceAtLeast(0)
                    }
                }

            // --- Duration consistency sub-score ---
            val avgSleep = cappedSummaries.sumOf { it.totalSleepMinutes } / cappedSummaries.size
            val durationVariance = cappedSummaries.sumOf {
                (it.totalSleepMinutes - avgSleep).toDouble().pow(2.0)
            } / cappedSummaries.size
            val durationStdDev = sqrt(durationVariance)
            val durationScore  = (100.0 - (durationStdDev / DURATION_NORMALIZER_MINUTES * 100.0))
                .coerceIn(0.0, 100.0)

            // --- Onset consistency sub-score ---
            // Fix 1: when fewer than 2 nights have onset data, exclude this dimension
            // entirely rather than silently defaulting stdDev to 0 (which would
            // artificially inflate the blended score by up to 50 points).
            val onsetMinutes = cappedSummaries.mapNotNull { it.sleepOnsetMinutes }
            val (onsetScore, onsetWeight) = if (onsetMinutes.size >= 2) {
                val avgOnset      = onsetMinutes.average()
                val onsetVariance = onsetMinutes.sumOf {
                    (it.toDouble() - avgOnset).pow(2.0)
                } / onsetMinutes.size
                val stdDev = sqrt(onsetVariance)
                val score  = (100.0 - (stdDev / ONSET_NORMALIZER_MINUTES * 100.0))
                    .coerceIn(0.0, 100.0)
                score to 0.5
            } else {
                0.0 to 0.0  // not enough onset data — exclude from blend
            }

            // --- Blended consistency score ---
            // Fix 2: use roundToInt() instead of toInt() to avoid systematic
            // truncation bias (e.g. 69.9 being displayed as 69 instead of 70).
            val durationWeight   = 1.0 - onsetWeight
            val consistencyScore = ((durationScore * durationWeight) + (onsetScore * onsetWeight))
                .roundToInt()

            SleepTrends(
                daysAnalyzed          = cappedSummaries.size,
                averageSleepMinutes   = avgSleep,
                totalSleepDebtMinutes = runningDebt,
                consistencyScore      = consistencyScore,
                sleepGoalMinutes      = DEFAULT_BASELINE_MINUTES,
            )
        }
    }
}