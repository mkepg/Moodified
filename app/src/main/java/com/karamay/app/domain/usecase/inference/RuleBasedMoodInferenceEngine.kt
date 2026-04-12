package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.inference.ScoringEvent
import com.karamay.app.domain.model.mood.Arousal
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

    private fun runPassiveInference(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        var valenceScore = InferenceConstants.BASE_SCORE
        var arousalScore = InferenceConstants.BASE_SCORE
        val events       = mutableListOf<ScoringEvent>()

        snapshot.sleepSummary?.let { sleep ->
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

            val durationAlreadyNegative = sleep.totalSleepMinutes < InferenceConstants.POOR_SLEEP_MINUTES
            if (!durationAlreadyNegative &&
                sleep.sleepEfficiencyPercent < InferenceConstants.POOR_SLEEP_EFFICIENCY) {
                valenceScore -= 8
                arousalScore -= 5
                events += ScoringEvent("poor sleep quality", -8, -5)
            }

            val efficiencyAlreadyNegative = !durationAlreadyNegative &&
                    sleep.sleepEfficiencyPercent < InferenceConstants.POOR_SLEEP_EFFICIENCY
            if (!efficiencyAlreadyNegative &&
                sleep.awakenings > InferenceConstants.AWAKENING_THRESHOLD) {
                valenceScore -= 5
                arousalScore += 7
                events += ScoringEvent("frequent awakenings", -5, 7)
            }
        }

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

            val totalTrackedMinutes = activity.activeMinutes + activity.sedentaryMinutes
            val vehicleRatio = if (totalTrackedMinutes > 0)
                commuteMins.toFloat() / totalTrackedMinutes else 0f

            if (commuteMins > InferenceConstants.LONG_COMMUTE_MINUTES && vehicleRatio < 0.25f) {
                valenceScore -= 5
                events += ScoringEvent("long commute", -5, 0)
            }
        }

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

        snapshot.sleepTrends?.let { trends ->
            if (trends.totalSleepDebtMinutes > InferenceConstants.SLEEP_DEBT_PENALTY_THRESHOLD) {
                val debtPenalty = (trends.totalSleepDebtMinutes / 60).coerceIn(10, 20)
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

    private fun deriveFromManualEntries(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        val entries = snapshot.moodEntries
        val primary = entries.maxByOrNull { it.timestamp } ?: entries.first()
        val finalValence: Valence
        val finalArousal: Arousal
        val explainabilityString: String

        if (entries.size == 1) {
            finalValence = primary.valence
            finalArousal = primary.arousal
            explainabilityString = "Based on the moment you took to reflect today."
        } else {
            val valenceCounts = entries.groupingBy { it.valence }.eachCount()
            val arousalCounts = entries.groupingBy { it.arousal }.eachCount()
            val modalValence  = valenceCounts.maxByOrNull { it.value }?.key ?: primary.valence
            val modalArousal  = arousalCounts.maxByOrNull { it.value }?.key ?: primary.arousal
            finalValence = if (primary.valence == modalValence) modalValence else primary.valence
            finalArousal = if (primary.arousal == modalArousal) modalArousal else primary.arousal

            val allSameValence = valenceCounts.size == 1
            explainabilityString = if (allSameValence) {
                "You've been feeling consistently this way across your ${entries.size} check-ins today."
            } else {
                "Your energy has shifted a bit across your ${entries.size} check-ins, settling here."
            }
        }

        val confidence = (40 + entries.size.coerceAtMost(3) * 15).coerceAtMost(100)

        return InferredMoodState(
            valence              = finalValence,
            arousal              = finalArousal,
            interpretationLabel  = interpreter.interpret(finalValence, finalArousal),
            confidenceScore      = confidence,
            explainabilityString = explainabilityString,
            isFallback           = false
        )
    }

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