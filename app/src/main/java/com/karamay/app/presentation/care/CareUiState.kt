package com.karamay.app.presentation.care

import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.domain.model.intervention.WellBeingDomain
import com.karamay.app.presentation.insight.InsightDomainReadiness

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