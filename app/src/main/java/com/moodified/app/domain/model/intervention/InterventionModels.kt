package com.moodified.app.domain.model.intervention

import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence

sealed interface InterventionAction {
    val id: String
    val priority: Int

    data class Guidance(
        override val id: String,
        override val priority: Int,
        val title: String,
        val description: String
    ) : InterventionAction

    data class Motivation(
        override val id: String,
        override val priority: Int,
        val title: String,
        val description: String
    ) : InterventionAction

    data class MicroConfirmation(
        override val id: String,
        override val priority: Int = 100,
        val prompt: String,
        val inferredValence: Valence,
        val inferredArousal: Arousal
    ) : InterventionAction

    data class MicroIntervention(
        override val id: String,
        override val priority: Int,
        val steps: List<MicroStep>,
        val wellBeingDomain: WellBeingDomain,
        val isAutoAdvance: Boolean = false
    ) : InterventionAction {
        // Dynamically compute the total duration based on the steps
        val durationSeconds: Int
            get() = steps.sumOf { it.durationSeconds }

        data class MicroStep(
            val instruction: String,
            val durationSeconds: Int
        )
    }

    data class GuidedRoutine(
        override val id: String,
        override val priority: Int,
        val phases: List<RoutinePhase>,
        val estimatedMinutes: Int,
        val routineType: RoutineType
    ) : InterventionAction

    data class TrendAlert(
        override val id: String,
        override val priority: Int,
        val domain: WellBeingDomain,
        val trendDirection: TrendDirection,
        val severityLevel: Int,
        val supportingDataPoints: List<String>
    ) : InterventionAction

    data class MotivationNudge(
        override val id: String,
        override val priority: Int,
        val streakDays: Int?,
        val achievementKey: String,
        val tone: NudgeTone
    ) : InterventionAction
}