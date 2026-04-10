package com.karamay.app.domain.usecase.inference

import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import javax.inject.Inject

/**
 * Explainability Layer: Provides short explanations to justify inferred mood states.
 */
class MoodExplainabilityGenerator @Inject constructor() {

    fun generateExplanation(
        snapshot: DailyBehaviorSnapshot,
        inferredValence: Valence,
        inferredArousal: Arousal
    ): String {
        val reasons = mutableListOf<String>()

        // 1. Analyze Sleep contribution
        snapshot.sleepSummary?.let { sleep ->
            if (sleep.sleepEfficiencyPercent < 75 || sleep.totalSleepMinutes < 360) {
                reasons.add("low sleep quality")
            } else if (sleep.totalSleepMinutes >= 420 && sleep.sleepEfficiencyPercent >= 85) {
                reasons.add("restful sleep")
            }
        }

        // 2. Analyze Activity contribution
        snapshot.activitySummary?.let { activity ->
            if (activity.activeMinutes < 20) {
                reasons.add("low physical activity")
            } else if (activity.activeMinutes > 60) {
                reasons.add("high physical activity")
            }
        }

        // 3. Analyze Interaction contribution
        snapshot.interactionSummary?.let { interaction ->
            if (interaction.lateNightUsageMinutes > 30) {
                reasons.add("late-night screen usage")
            } else if (interaction.totalScreenTimeMinutes > 360) {
                reasons.add("heavy screen time")
            }
        }

        if (reasons.isEmpty()) {
            return "Based on your general behavioral baseline today."
        }

        // e.g., "Inferred due to low sleep quality and heavy screen time." [cite: 9]
        return "Inferred due to " + reasons.joinToString(" and ") + "."
    }
}