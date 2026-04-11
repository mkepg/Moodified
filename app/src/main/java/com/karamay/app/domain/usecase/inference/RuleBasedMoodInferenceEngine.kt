package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.inference.ScoringEvent
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.mood.Valence
import javax.inject.Inject
import kotlin.math.abs

class RuleBasedMoodInferenceEngine @Inject constructor(
    private val interpreter: MoodStateInterpreter,
    private val explainer:   MoodExplainabilityGenerator
) {
    operator fun invoke(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        if (snapshot.moodEntries.isNotEmpty()) {
            return deriveFromManualEntries(snapshot)
        }
        if (snapshot.dataCompletenessScore < InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE) {
            return generateFallbackState(snapshot)
        }
        return runPassiveInference(snapshot)
    }

    // ---------------------------------------------------------------------------
    // Passive inference
    // ---------------------------------------------------------------------------

    private fun runPassiveInference(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        var valenceScore = InferenceConstants.BASE_SCORE
        var arousalScore = InferenceConstants.BASE_SCORE
        val events       = mutableListOf<ScoringEvent>()

        // ── Sleep ──────────────────────────────────────────────────────────────
        snapshot.sleepSummary?.let { sleep ->
            // Duration-based rule
            when {
                sleep.totalSleepMinutes < InferenceConstants.POOR_SLEEP_MINUTES -> {
                    valenceScore -= 12
                    arousalScore -= 8
                    events += ScoringEvent("insufficient sleep duration", -12, -8)
                }
                sleep.totalSleepMinutes in
                        InferenceConstants.GOOD_SLEEP_MINUTES_MIN..InferenceConstants.GOOD_SLEEP_MINUTES_MAX
                        && sleep.sleepEfficiencyPercent >= InferenceConstants.GOOD_SLEEP_EFFICIENCY -> {
                    valenceScore += 15
                    events += ScoringEvent("restful sleep", 15, 0)
                }
            }

            // Efficiency rule — only apply if the duration rule did NOT already fire a
            // negative penalty to avoid double-counting on nights that are both short and
            // inefficient (the most common co-occurrence pattern).
            val durationAlreadyNegative = sleep.totalSleepMinutes < InferenceConstants.POOR_SLEEP_MINUTES
            if (!durationAlreadyNegative &&
                sleep.sleepEfficiencyPercent < InferenceConstants.POOR_SLEEP_EFFICIENCY) {
                valenceScore -= 8
                arousalScore -= 5
                events += ScoringEvent("poor sleep quality", -8, -5)
            }

            // Awakening rule — only fire if efficiency was not the primary negative signal
            // (awakenings and poor efficiency are strongly correlated; firing both would
            // over-penalise fragmented nights).
            val efficiencyAlreadyNegative = !durationAlreadyNegative &&
                    sleep.sleepEfficiencyPercent < InferenceConstants.POOR_SLEEP_EFFICIENCY
            if (!efficiencyAlreadyNegative &&
                sleep.awakenings > InferenceConstants.AWAKENING_THRESHOLD) {
                valenceScore -= 5
                arousalScore += 7
                events += ScoringEvent("frequent awakenings", -5, 7)
            }
        }

        // ── Activity ───────────────────────────────────────────────────────────
        snapshot.activitySummary?.let { activity ->
            val vigorousMins = activity.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0
            val commuteMins  = activity.minutesPerIntensityBand[ActivityIntensity.IN_VEHICLE] ?: 0

            when {
                activity.activeMinutes > InferenceConstants.HIGH_ACTIVITY_MINUTES -> {
                    arousalScore += 20
                    valenceScore += 15
                    events += ScoringEvent("high physical activity", 15, 20)
                }
                activity.sedentaryMinutes > InferenceConstants.SEDENTARY_MINUTES_THRESHOLD -> {
                    arousalScore -= 12
                    valenceScore -= 5
                    events += ScoringEvent("prolonged sedentary period", -5, -12)
                }
            }

            if (vigorousMins > InferenceConstants.VIGOROUS_MINUTES_THRESHOLD) {
                arousalScore += 15
                events += ScoringEvent("vigorous exercise", 0, 15)
            }

            if (activity.totalSteps > InferenceConstants.HIGH_STEPS_THRESHOLD) {
                valenceScore += 5
                events += ScoringEvent("high step count", 5, 0)
            }

            // Commute penalty: only meaningful when the vehicle time is distributed across
            // a plausible commute pattern (i.e. not a single continuous block that looks
            // more like a road trip). Proxy: if IN_VEHICLE minutes are < 25 % of total
            // waking minutes, it is likely discrete trips rather than all-day transit.
            val totalTrackedMinutes = activity.activeMinutes + activity.sedentaryMinutes
            val vehicleRatio = if (totalTrackedMinutes > 0)
                commuteMins.toFloat() / totalTrackedMinutes else 0f
            if (commuteMins > InferenceConstants.LONG_COMMUTE_MINUTES && vehicleRatio < 0.25f) {
                valenceScore -= 5
                events += ScoringEvent("long commute", -5, 0)
            }
        }

        // ── Interaction ────────────────────────────────────────────────────────
        snapshot.interactionSummary?.let { interaction ->
            if (interaction.lateNightUsageMinutes > InferenceConstants.LATE_NIGHT_MINUTES_THRESHOLD) {
                valenceScore -= 10
                arousalScore += 5
                events += ScoringEvent("late-night screen usage", -10, 5)
            }

            if (interaction.sessionCount > InferenceConstants.HIGH_SESSION_COUNT &&
                interaction.averageSessionDurationMinutes < InferenceConstants.SHORT_SESSION_DURATION_MINUTES
            ) {
                valenceScore -= 5
                arousalScore += 10
                events += ScoringEvent("fragmented phone usage", -5, 10)
            }
        }

        // ── Trend penalties ────────────────────────────────────────────────────
        snapshot.sleepTrends?.let { trends ->
            if (trends.totalSleepDebtMinutes > InferenceConstants.SLEEP_DEBT_PENALTY_THRESHOLD) {
                // Scale the penalty with debt magnitude rather than applying a flat -10.
                // Cap at -20 to avoid an outsized single-signal swing.
                val debtPenalty = (trends.totalSleepDebtMinutes / 60)
                    .coerceIn(10, 20)
                valenceScore -= debtPenalty
                events += ScoringEvent("accumulated sleep debt", -debtPenalty, 0)
            }
        }

        snapshot.activityTrends?.let { trends ->
            if (trends.consistencyScore < InferenceConstants.LOW_ACTIVITY_CONSISTENCY_THRESHOLD) {
                valenceScore -= 5
                events += ScoringEvent("low weekly activity consistency", -5, 0)
            }
        }

        val finalValence = mapScoreToValence(valenceScore)
        val finalArousal = mapScoreToArousal(arousalScore)

        // Sort events by absolute impact (largest valence delta first) so the
        // explainability string leads with the most significant driver.
        val sortedEvents = events.sortedByDescending { abs(it.valenceDelta) + abs(it.arousalDelta) }

        return InferredMoodState(
            valence              = finalValence,
            arousal              = finalArousal,
            interpretationLabel  = interpreter.interpret(finalValence, finalArousal),
            confidenceScore      = calculateConfidence(snapshot),
            explainabilityString = explainer.generateExplanation(sortedEvents),
            isFallback           = false
        )
    }

    // ---------------------------------------------------------------------------
    // Manual-entry derivation
    // ---------------------------------------------------------------------------

    private fun deriveFromManualEntries(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        val entries = snapshot.moodEntries

        // When there are multiple entries, preserve temporal resolution by taking the
        // most recent entry as the primary signal (it reflects current state better than
        // an average that regresses toward NEUTRAL across opposite-mood days).
        // For a single entry, behaviour is unchanged.
        val primary = entries.maxByOrNull { it.timestamp } ?: entries.first()

        val finalValence: Valence
        val finalArousal: Arousal

        if (entries.size == 1) {
            // Single entry — use it directly; no averaging distortion.
            finalValence = primary.valence
            finalArousal = primary.arousal
        } else {
            // Multiple entries: use the most-recent as anchor, blend with the modal
            // valence/arousal across all entries so that one outlier doesn't dominate
            // but the day's direction is still represented.
            val valenceCounts = entries.groupingBy { it.valence }.eachCount()
            val arousalCounts = entries.groupingBy { it.arousal }.eachCount()
            val modalValence  = valenceCounts.maxByOrNull { it.value }?.key ?: primary.valence
            val modalArousal  = arousalCounts.maxByOrNull { it.value }?.key ?: primary.arousal

            // Agree: recent and modal match → use that value.
            // Disagree: recent entry wins (captures end-of-day state).
            finalValence = if (primary.valence == modalValence) modalValence else primary.valence
            finalArousal = if (primary.arousal == modalArousal) modalArousal else primary.arousal
        }

        val confidence = (40 + entries.size.coerceAtMost(3) * 15).coerceAtMost(100)
        val label      = if (entries.size == 1) "1 manual check-in" else "${entries.size} manual check-ins"

        return InferredMoodState(
            valence              = finalValence,
            arousal              = finalArousal,
            interpretationLabel  = interpreter.interpret(finalValence, finalArousal),
            confidenceScore      = confidence,
            explainabilityString = "Derived from $label today.",
            isFallback           = false
        )
    }

    // ---------------------------------------------------------------------------
    // Fallback
    // ---------------------------------------------------------------------------

    private fun generateFallbackState(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        return InferredMoodState(
            valence              = Valence.NEUTRAL,
            arousal              = Arousal.MID,
            interpretationLabel  = "Neutral / Unknown",
            confidenceScore      = snapshot.dataCompletenessScore,
            explainabilityString = "Insufficient data to form a confident inference.",
            isFallback           = true
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun calculateConfidence(snapshot: DailyBehaviorSnapshot): Int {
        var score = snapshot.dataCompletenessScore

        if (snapshot.sleepSummary?.isEstimated == true) {
            score -= InferenceConstants.ESTIMATED_SLEEP_PENALTY
        }
        if (snapshot.activitySummary?.isPartialDay == true) {
            score -= InferenceConstants.PARTIAL_DAY_ACTIVITY_PENALTY
        }

        val bonusEntries = snapshot.moodEntries.size
            .coerceAtMost(InferenceConstants.MANUAL_ENTRY_BONUS_MAX_ENTRIES)
        score += bonusEntries * InferenceConstants.MANUAL_ENTRY_BONUS_PER_ENTRY

        return score.coerceIn(0, 100)
    }

    private fun mapScoreToValence(score: Int): Valence = when {
        score < InferenceConstants.VALENCE_NEGATIVE_THRESHOLD -> Valence.NEGATIVE
        score > InferenceConstants.VALENCE_POSITIVE_THRESHOLD -> Valence.POSITIVE
        else                                                   -> Valence.NEUTRAL
    }

    private fun mapScoreToArousal(score: Int): Arousal = when {
        score < InferenceConstants.AROUSAL_LOW_THRESHOLD  -> Arousal.LOW
        score > InferenceConstants.AROUSAL_HIGH_THRESHOLD -> Arousal.HIGH
        else                                               -> Arousal.MID
    }
}