package com.karamay.app.domain.usecase.sleep

import com.karamay.app.core.utils.SleepTimeUtils
import com.karamay.app.domain.model.SleepTrends
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
        // Fix #24: Extracted from inline val baselineMinutes = 480.
        // This is the default 8-hour target. Phase 2 personalization should replace this
        // with a value read from a user preferences source so users can set their own goal.
        private const val DEFAULT_BASELINE_MINUTES = 480

        // Fix #24: The consistency score formula maps stdDev → score via:
        //   score = (100.0 - (stdDev / CONSISTENCY_NORMALIZER_MINUTES)).coerceIn(0, 100)
        // A stdDev of CONSISTENCY_NORMALIZER_MINUTES maps exactly to score 0.
        // Calibration note: 120 minutes (2 hours) is a reasonable "floor" — users with
        // higher variance than that all receive 0, which is intentional (they are severely
        // irregular). Duration and onset use separate normalizers because they operate
        // on different units and have different meaningful variance ranges.
        private const val DURATION_NORMALIZER_MINUTES = 120.0  // 2h stdDev → score 0
        private const val ONSET_NORMALIZER_MINUTES    = 120.0  // 2h onset variance → score 0

        // Fix #24 / issue V: Sleep debt recovery efficiency.
        // Each night slept over baseline recovers debt at 50% efficiency.
        // Basis: sleep debt is not 1:1 recoverable (Belenky et al., 2003).
        // This value is extracted here so it is visible to the Phase 2 Explainability Layer.
        private const val SLEEP_DEBT_RECOVERY_RATE = 0.5
    }

    operator fun invoke(endDate: LocalDate): Flow<SleepTrends?> {
        return repository.getWeeklySummaries(endDate).map { summaries ->
            if (summaries.isEmpty()) return@map null

            var runningDebt = 0
            summaries
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

            val avgSleep = summaries.sumOf { it.totalSleepMinutes } / summaries.size

            val durationVariance = summaries.sumOf {
                (it.totalSleepMinutes - avgSleep).toDouble().pow(2.0)
            } / summaries.size
            val durationStdDev = sqrt(durationVariance)

            val onsetMinutes = summaries.mapNotNull { it.sleepOnsetMinutes }
            val onsetStdDev  = if (onsetMinutes.size >= 2) {
                val avgOnset      = onsetMinutes.average()
                val onsetVariance = onsetMinutes.sumOf {
                    (it.toDouble() - avgOnset).pow(2.0)
                } / onsetMinutes.size
                sqrt(onsetVariance)
            } else {
                0.0
            }

            // Duration and onset each contribute 50% to the consistency score.
            // Separate normalizers allow tuning each dimension independently in Phase 2.
            val durationScore    = (100.0 - (durationStdDev / DURATION_NORMALIZER_MINUTES * 100.0)).coerceIn(0.0, 100.0)
            val onsetScore       = (100.0 - (onsetStdDev    / ONSET_NORMALIZER_MINUTES    * 100.0)).coerceIn(0.0, 100.0)
            val consistencyScore = ((durationScore * 0.5) + (onsetScore * 0.5)).toInt()

            SleepTrends(
                daysAnalyzed          = summaries.size,
                averageSleepMinutes   = avgSleep,
                totalSleepDebtMinutes = runningDebt,
                consistencyScore      = consistencyScore
            )
        }
    }
}
