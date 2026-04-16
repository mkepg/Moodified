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

        // Core guidance and routines evaluate immediately, bypassing the completeness gate
        val baseActions = evaluateBaseGuidanceUseCase(moodState, snapshot, liveActivity, liveInteraction)
        actions.addAll(baseActions)

        val suggestedRoutine = selectGuidedRoutineUseCase(moodState)
        if (suggestedRoutine != null && !interventionRepository.isOnCooldown(suggestedRoutine.id, 4 * 60 * 60 * 1000L)) {
            actions.add(suggestedRoutine)
        }

        // Trend Alerts
        val trendAlerts = detectNegativeTrendsUseCase(
            sleepSummaries = historicalSleep,
            activitySummaries = historicalActivity,
            interactionSummaries = historicalInteraction,
            moodEntries = historicalMoods
        )

        trendAlerts.forEach { alert ->
            if (!interventionRepository.isOnCooldown(alert.id, 24 * 60 * 60 * 1000L)) {
                actions.add(alert)
            }
        }

        return actions.sortedByDescending { it.priority }
    }
}