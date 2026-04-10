package com.karamay.app.domain.usecase.sleep

import com.karamay.app.domain.model.sleep.SleepTrends
import com.karamay.app.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

class GetWeeklySleepTrendsUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    companion object {
        /**
         * Nightly sleep target used for debt accumulation (8 hours).
         */
        private const val DEFAULT_BASELINE_MINUTES = 480

        /**
         * Per-day cap applied **only to backfilled (estimated) summaries** before
         * computing average and debt.
         *
         * Backfilled data is derived from screen inactivity, which means a phone left
         * sitting on a table (or charging overnight while unused) can produce artificially
         * high sleep durations — sometimes 14–16 h. Without a cap:
         *   • averageSleepMinutes is inflated by outlier days.
         *   • The 50 % partial-recovery model means surplus minutes only recover debt at
         *     half rate, so a single short night can leave residual debt that an inflated
         *     "average" makes look inconsistent.
         *
         * 10 h (600 min) is a conservative but realistic ceiling for inferred sleep.
         * Observed (non-estimated) summaries are intentionally left uncapped.
         */
        private const val MAX_BACKFILL_SLEEP_MINUTES = 600

        private const val DURATION_NORMALIZER_MINUTES = 120.0
        private const val ONSET_NORMALIZER_MINUTES    = 120.0

        /**
         * Sleep debt recovery rate for surplus nights.
         * Scientific literature (Mollicone et al., 2007) suggests partial recovery:
         * excess sleep does not eliminate deficit 1-for-1.
         */
        private const val SLEEP_DEBT_RECOVERY_RATE = 0.5
    }

    operator fun invoke(endDate: LocalDate): Flow<SleepTrends?> {
        return repository.getWeeklySummaries(endDate).map { summaries ->
            if (summaries.isEmpty()) return@map null

            // Cap only backfilled (estimated) summaries so that backfill artefacts
            // do not skew average and debt. Observed summaries are left untouched —
            // a genuine long sleep should not be penalised. Both metrics are then
            // computed from the same dataset, keeping them internally consistent.
            val cappedSummaries = summaries.map { s ->
                if (s.isEstimated)
                    s.copy(totalSleepMinutes = s.totalSleepMinutes.coerceAtMost(MAX_BACKFILL_SLEEP_MINUTES))
                else
                    s
            }

            // Running-debt model: iterate chronologically so early deficits are
            // partially recovered by subsequent surplus nights.
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

            val avgSleep = cappedSummaries.sumOf { it.totalSleepMinutes } / cappedSummaries.size

            // Consistency: blended score of duration variance and sleep-onset variance.
            val durationVariance = cappedSummaries.sumOf {
                (it.totalSleepMinutes - avgSleep).toDouble().pow(2.0)
            } / cappedSummaries.size
            val durationStdDev = sqrt(durationVariance)

            val onsetMinutes = cappedSummaries.mapNotNull { it.sleepOnsetMinutes }
            val onsetStdDev  = if (onsetMinutes.size >= 2) {
                val avgOnset      = onsetMinutes.average()
                val onsetVariance = onsetMinutes.sumOf {
                    (it.toDouble() - avgOnset).pow(2.0)
                } / onsetMinutes.size
                sqrt(onsetVariance)
            } else {
                0.0
            }

            val durationScore    = (100.0 - (durationStdDev / DURATION_NORMALIZER_MINUTES * 100.0)).coerceIn(0.0, 100.0)
            val onsetScore       = (100.0 - (onsetStdDev    / ONSET_NORMALIZER_MINUTES    * 100.0)).coerceIn(0.0, 100.0)
            val consistencyScore = ((durationScore * 0.5) + (onsetScore * 0.5)).toInt()

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
