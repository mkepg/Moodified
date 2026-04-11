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

        // Recovery from sleep debt is partial: each surplus hour repays only 50 % of
        // the outstanding debt, consistent with the homeostatic model used previously.
        private const val SLEEP_DEBT_RECOVERY_RATE = 0.5

        // Debt is clamped at 600 min (10 h) so that extreme multi-day deficits don't
        // produce an unbounded running total that would permanently suppress valence
        // even after the user catches up on sleep for several nights.
        private const val MAX_RUNNING_DEBT_MINUTES = 600
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

            // Accumulate sleep debt chronologically.
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
                    // Clamp so ancient deficits don't accumulate without bound.
                    runningDebt = runningDebt.coerceAtMost(MAX_RUNNING_DEBT_MINUTES)
                }

            val avgSleep = cappedSummaries.sumOf { it.totalSleepMinutes } / cappedSummaries.size

            val durationVariance = cappedSummaries.sumOf {
                (it.totalSleepMinutes - avgSleep).toDouble().pow(2.0)
            } / cappedSummaries.size
            val durationStdDev = sqrt(durationVariance)
            val durationScore  = (100.0 - (durationStdDev / DURATION_NORMALIZER_MINUTES * 100.0))
                .coerceIn(0.0, 100.0)

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
                0.0 to 0.0
            }

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