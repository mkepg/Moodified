package com.karamay.app.domain.usecase.sleep

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
    operator fun invoke(endDate: LocalDate): Flow<SleepTrends?> {
        return repository.getWeeklySummaries(endDate).map { summaries ->
            if (summaries.isEmpty()) return@map null

            val baselineMinutes = 480   // 8 hours

            // ────────────────────────────────────────────────────────────
            // Fix 3: Sleep debt with decay
            //
            // Previous logic: simply accumulated deficit, never allowing
            // surplus nights to reduce the debt.
            //
            // New logic: for each day in chronological order —
            //   • Deficit day  → add the shortfall to running debt
            //   • Surplus day  → reduce debt by 0.5 × surplus minutes
            //     (partial recovery; you cannot fully repay debt 1-for-1)
            //   Debt is clamped to ≥ 0 (you cannot "pre-bank" future sleep).
            // ────────────────────────────────────────────────────────────
            var runningDebt = 0
            summaries
                .sortedBy { it.date }   // oldest → newest
                .forEach { summary ->
                    val delta = summary.totalSleepMinutes - baselineMinutes
                    if (delta < 0) {
                        // Deficit: add shortfall
                        runningDebt += (-delta)
                    } else {
                        // Surplus: partial debt repayment at 50% efficiency
                        val recovery = (delta * 0.5).toInt()
                        runningDebt  = (runningDebt - recovery).coerceAtLeast(0)
                    }
                }

            // ────────────────────────────────────────────────────────────
            // Average sleep (unchanged)
            // ────────────────────────────────────────────────────────────
            val avgSleep = summaries.sumOf { it.totalSleepMinutes } / summaries.size

            // ────────────────────────────────────────────────────────────
            // Fix 4: Consistency score = weighted blend of two dimensions
            //
            // Dimension 1 — Duration consistency (what we had before)
            //   Low std-dev in total sleep minutes → stable sleep quantity
            //
            // Dimension 2 — Onset consistency (new)
            //   Low std-dev in sleep start time (minutes from midnight)
            //   → stable circadian anchor point
            //   e.g. always sleeping at 23:00 scores 100 even if duration varies
            //        slightly; sleeping 10 PM one night and 4 AM the next tanks
            //        the score even if both nights were 8 hours long.
            //
            // Weighting: 50 / 50  (equal importance).
            //   Could be tuned to 60 / 40 duration / onset in a future phase
            //   based on user feedback or clinical guidance.
            // ────────────────────────────────────────────────────────────

            // Dimension 1: duration std-dev
            val durationVariance = summaries.sumOf {
                (it.totalSleepMinutes - avgSleep).toDouble().pow(2.0)
            } / summaries.size
            val durationStdDev = sqrt(durationVariance)

            // Dimension 2: onset std-dev (only use days where onset is known)
            val onsetMinutes  = summaries.mapNotNull { it.sleepOnsetMinutes }
            val onsetStdDev   = if (onsetMinutes.size >= 2) {
                val avgOnset      = onsetMinutes.average()
                val onsetVariance = onsetMinutes.sumOf {
                    (it.toDouble() - avgOnset).pow(2.0)
                } / onsetMinutes.size
                sqrt(onsetVariance)
            } else {
                // Not enough onset data to penalize — treat as perfectly consistent
                0.0
            }

            // Map each std-dev to a 0-100 score (higher deviation → lower score).
            // Divisor of 1.2 keeps a 60-minute std-dev at ~50 points — same
            // calibration factor used in the original implementation.
            val durationScore = (100.0 - (durationStdDev / 1.2)).coerceIn(0.0, 100.0)
            val onsetScore    = (100.0 - (onsetStdDev    / 1.2)).coerceIn(0.0, 100.0)

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