package com.karamay.app.domain.model.intervention

import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence

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
        val durationSeconds: Int,
        val steps: List<String>,
        val wellBeingDomain: WellBeingDomain
    ) : InterventionAction

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