package com.karamay.app.presentation.care

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.presentation.care.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareScreen(viewModel: CareViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedMicroIntervention by remember { mutableStateOf<InterventionAction.MicroIntervention?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize().background(MilkWhite)) {
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "careRoot"
        ) { loading ->
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DeepSage, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    state.inferredMood?.let { mood ->
                        item { CareHeader(mood) }
                    }

                    items(state.trendAlerts, key = { "alert_${it.id}" }) { alert ->
                        Column(modifier = Modifier.animateItem()) {
                            SwipeableTrendAlert(
                                alert = alert,
                                onDismiss = { viewModel.recordFeedback(alert.id, "dismissed") }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    state.motivationNudge?.let { nudge ->
                        item(key = "nudge_${nudge.id}") {
                            Column(modifier = Modifier.animateItem()) {
                                SwipeableMotivationNudge(
                                    nudge = nudge,
                                    onDismiss = { viewModel.recordFeedback(nudge.id, "dismissed") }
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    if (!state.hasEnoughMultiDayData || !state.isIntradayComplete) {
                        item {
                            state.domainReadiness?.let { readiness ->
                                CareOnboardingState(readiness, isIntradayComplete = state.isIntradayComplete)
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    items(state.activeGuidance, key = { "guidance_${it.id}" }) { guidance ->
                        Column(modifier = Modifier.animateItem()) {
                            ActiveGuidanceCard(
                                action = guidance,
                                onHelpful = { viewModel.recordFeedback(guidance.id, "helpful") },
                                onLater = { viewModel.recordFeedback(guidance.id, "not_now") }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    state.suggestedRoutine?.let { routine ->
                        item(key = "routine_${routine.id}") {
                            Column(modifier = Modifier.animateItem()) {
                                GuidedRoutineCard(
                                    routine = routine,
                                    onComplete = { viewModel.recordFeedback(routine.id, "helpful", wasCompleted = true) }
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }

                    item {
                        SectionTitle("Quick Resets")
                        Spacer(Modifier.height(12.dp))
                        WellBeingDomainRow(
                            activeDomain = state.activeDomain,
                            onSelect = viewModel::setWellBeingDomain
                        )
                        Spacer(Modifier.height(16.dp))
                        MicroInterventionRow(
                            interventions = state.microInterventions,
                            onClick = { selectedMicroIntervention = it }
                        )
                    }
                }
            }
        }

        if (selectedMicroIntervention != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedMicroIntervention = null },
                sheetState = sheetState,
                containerColor = MilkWhite,
                scrimColor = Color.Black.copy(alpha = 0.45f),
                dragHandle = { BottomSheetDefaults.DragHandle(color = SageDim) }
            ) {
                MicroInterventionSheetContent(
                    intervention = selectedMicroIntervention!!,
                    onDismiss = {
                        viewModel.recordFeedback(selectedMicroIntervention!!.id, "helpful", wasCompleted = true)
                        selectedMicroIntervention = null
                    }
                )
            }
        }
    }
}