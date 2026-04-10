package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import javax.inject.Inject

/**
 * Mood Inference Engine: A transparent rule-based model estimating emotional state
 * using weighted behavioral inputs.
 */
class RuleBasedMoodInferenceEngine @Inject constructor(
    private val interpreter: MoodStateInterpreter,
    private val explainer: MoodExplainabilityGenerator
) {
    companion object {
        // Base thresholds for scoring
        private const val BASE_SCORE = 50
        private const val POOR_SLEEP_THRESHOLD_MINUTES = 360
        private const val HIGH_ACTIVITY_THRESHOLD_MINUTES = 45
        private const val HIGH_LATE_NIGHT_USAGE_MINUTES = 30
    }

    operator fun invoke(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        // Fallback if data is critically low
        if (snapshot.dataCompletenessScore < 30) {
            return generateFallbackState(snapshot)
        }

        var valenceScore = BASE_SCORE
        var arousalScore = BASE_SCORE

        // --- 1. Evaluate Sleep Patterns [cite: 4] ---
        snapshot.sleepSummary?.let { sleep ->
            // Sleep heavily impacts Valence (mood stability)
            if (sleep.totalSleepMinutes < POOR_SLEEP_THRESHOLD_MINUTES) {
                valenceScore -= 15
                arousalScore -= 10 // Fatigue lowers arousal
            } else if (sleep.totalSleepMinutes in 420..540 && sleep.sleepEfficiencyPercent > 85) {
                valenceScore += 10
            }

            if (sleep.awakenings > 3) {
                valenceScore -= 5
                arousalScore += 10 // Fragmented sleep can increase restlessness/jittery arousal
            }
        }

        // --- 2. Evaluate Physical Activity [cite: 3] ---
        snapshot.activitySummary?.let { activity ->
            // Activity heavily impacts Arousal (energy)
            if (activity.activeMinutes > HIGH_ACTIVITY_THRESHOLD_MINUTES) {
                arousalScore += 20
                valenceScore += 10 // Endorphin boost
            } else if (activity.sedentaryMinutes > 480) {
                arousalScore -= 15
                valenceScore -= 5
            }

            // Factor in specific intensity distributions
            val vigorousMins = activity.minutesPerIntensityBand[ActivityIntensity.VIGOROUS] ?: 0
            if (vigorousMins > 15) {
                arousalScore += 15
            }
        }

        // --- 3. Evaluate Phone Interaction [cite: 2] ---
        snapshot.interactionSummary?.let { interaction ->
            // Late night usage negatively impacts valence
            if (interaction.lateNightUsageMinutes > HIGH_LATE_NIGHT_USAGE_MINUTES) {
                valenceScore -= 10
                arousalScore += 5 // Often correlated with anxious/restless scrolling
            }

            // High fragmentation (high sessions, low duration) implies distraction
            if (interaction.sessionCount > 60 && interaction.averageSessionDurationMinutes < 3) {
                valenceScore -= 5
                arousalScore += 10
            }
        }

        // --- Map Scores to Domains ---
        val finalValence = mapScoreToValence(valenceScore)
        val finalArousal = mapScoreToArousal(arousalScore)

        return InferredMoodState(
            valence = finalValence,
            arousal = finalArousal,
            interpretationLabel = interpreter.interpret(finalValence, finalArousal),
            confidenceScore = snapshot.dataCompletenessScore,
            explainabilityString = explainer.generateExplanation(snapshot, finalValence, finalArousal),
            isFallback = false
        )
    }

    private fun mapScoreToValence(score: Int): Valence {
        return when {
            score < 40 -> Valence.NEGATIVE
            score > 60 -> Valence.POSITIVE
            else       -> Valence.NEUTRAL
        }
    }

    private fun mapScoreToArousal(score: Int): Arousal {
        return when {
            score < 40 -> Arousal.LOW
            score > 60 -> Arousal.HIGH
            else       -> Arousal.MID
        }
    }

    private fun generateFallbackState(snapshot: DailyBehaviorSnapshot): InferredMoodState {
        return InferredMoodState(
            valence = Valence.NEUTRAL,
            arousal = Arousal.MID,
            interpretationLabel = "Neutral / Unknown",
            confidenceScore = snapshot.dataCompletenessScore,
            explainabilityString = "Insufficient data to form a confident inference.",
            isFallback = true
        )
    }
}