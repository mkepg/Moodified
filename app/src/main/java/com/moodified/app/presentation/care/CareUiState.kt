package com.moodified.app.presentation.care

import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.WellBeingDomain
import com.moodified.app.presentation.insight.InsightDomainReadiness

data class CareUiState(
    val isLoading: Boolean = true,
    val hasEnoughMultiDayData: Boolean = false,
    val isIntradayComplete: Boolean = false,
    val domainReadiness: InsightDomainReadiness? = null,
    val inferredMood: InferredMoodState? = null,
    val activeGuidance: List<InterventionAction> = emptyList(),
    val trendAlerts: List<InterventionAction.TrendAlert> = emptyList(),
    val suggestedRoutine: InterventionAction.GuidedRoutine? = null,
    val microInterventions: List<InterventionAction.MicroIntervention> = emptyList(),
    val motivationNudge: InterventionAction.MotivationNudge? = null,
    val activeDomain: WellBeingDomain = WellBeingDomain.MENTAL
)