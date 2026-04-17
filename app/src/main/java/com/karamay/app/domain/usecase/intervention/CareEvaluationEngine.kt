package com.karamay.app.domain.usecase.intervention

import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.inference.DailyBehaviorSnapshot
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.domain.repository.InterventionRepository
import javax.inject.Inject

class CareEvaluationEngine @Inject constructor(
    private val evaluateBaseGuidanceUseCase: EvaluateBaseGuidanceUseCase,
    private val detectNegativeTrendsUseCase: DetectNegativeTrendsUseCase,
    private val selectGuidedRoutineUseCase: SelectGuidedRoutineUseCase,
    private val interventionRepository: InterventionRepository
) {
    companion object {
        private const val ROUTINE_COOLDOWN_MS = 4 * 60 * 60 * 1000L
        private const val TREND_COOLDOWN_MS   = 24 * 60 * 60 * 1000L
    }

    suspend operator fun invoke(
        moodState: InferredMoodState?,
        snapshot: DailyBehaviorSnapshot?,
        liveActivity: ActivitySignal,
        liveInteraction: InteractionSignal,
        historicalSleep: List<com.karamay.app.domain.model.sleep.DailySleepSummary>,
        historicalActivity: List<com.karamay.app.domain.model.activity.ActivityDailySummary>,
        historicalInteraction: List<com.karamay.app.domain.model.interaction.InteractionDailySummary>,
        historicalMoods: List<com.karamay.app.domain.model.mood.MoodEntry>
    ): List<InterventionAction> {
        val actions = mutableListOf<InterventionAction>()
        if (moodState == null || snapshot == null) return actions

        val baseActions = evaluateBaseGuidanceUseCase(moodState, snapshot, liveActivity, liveInteraction)
        actions.addAll(baseActions)

        // Routine selection now receives its cooldown constraint centrally from the Engine
        val suggestedRoutine = selectGuidedRoutineUseCase(moodState, ROUTINE_COOLDOWN_MS)
        if (suggestedRoutine != null) {
            actions.add(suggestedRoutine)
        }

        val trendAlerts = detectNegativeTrendsUseCase(
            sleepSummaries = historicalSleep,
            activitySummaries = historicalActivity,
            interactionSummaries = historicalInteraction,
            moodEntries = historicalMoods
        )

        trendAlerts.forEach { alert ->
            if (!interventionRepository.isOnCooldown(alert.id, TREND_COOLDOWN_MS)) {
                actions.add(alert)
            }
        }

        return actions.sortedByDescending { it.priority }
    }
}