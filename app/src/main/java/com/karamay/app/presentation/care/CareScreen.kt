package com.karamay.app.presentation.care

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.karamay.app.R
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.inference.InferredMoodState
import com.karamay.app.domain.model.intervention.InterventionAction
import com.karamay.app.domain.model.intervention.WellBeingDomain
import com.karamay.app.presentation.insight.InsightDomainReadiness
import kotlinx.coroutines.delay

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

                    items(state.trendAlerts, key = { it.id }) { alert ->
                        TrendAlertBanner(alert, onDismiss = { viewModel.recordFeedback(alert.id, "dismissed") })
                        Spacer(Modifier.height(12.dp))
                    }

                    state.motivationNudge?.let { nudge ->
                        item {
                            MotivationNudgeCard(nudge, onDismiss = { viewModel.recordFeedback(nudge.id, "dismissed") })
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // informational onboarding card [cite: 83]
                    if (!state.hasEnoughMultiDayData || !state.isIntradayComplete) {
                        item {
                            state.domainReadiness?.let { readiness ->
                                CareOnboardingState(readiness, isIntradayComplete = state.isIntradayComplete)
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    // core guidance cards [cite: 66, 67]
                    items(state.activeGuidance, key = { it.id }) { guidance ->
                        ActiveGuidanceCard(
                            action = guidance,
                            onHelpful = { viewModel.recordFeedback(guidance.id, "helpful") },
                            onLater = { viewModel.recordFeedback(guidance.id, "not_now") }
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // guided routine flow [cite: 70, 71]
                    state.suggestedRoutine?.let { routine ->
                        item {
                            GuidedRoutineCard(
                                routine = routine,
                                onComplete = { viewModel.recordFeedback(routine.id, "helpful", wasCompleted = true) }
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    // micro-intervention row [cite: 73, 76]
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

        // Corrected Modal: Parameter 'windowInsets' removed.
        // Insets are now handled via navigationBarsPadding() in the content column.
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

@Composable
private fun MicroInterventionSheetContent(
    intervention: InterventionAction.MicroIntervention,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Fixes the overlap with the user bar
            .navigationBarsPadding()
            // Ensures content isn't cut off [cite: 71]
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = intervention.id.split("_").drop(1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Take ${intervention.durationSeconds / 60} minute${if (intervention.durationSeconds >= 120) "s" else ""} for yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            intervention.steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = SageSurface, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepSage)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepSage)
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge, color = MilkWhite)
        }
        // Additional safe space for system gestures
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CareHeader(mood: InferredMoodState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = DeepSage.copy(alpha = 0.08f)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DeepSage))
                Text(
                    text = "ADAPTIVE CARE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = DeepSage
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = mood.interpretationLabel,
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = DmSerifDisplay, fontSize = 34.sp),
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = mood.explainabilityString,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TrendAlertBanner(alert: InterventionAction.TrendAlert, onDismiss: () -> Unit) {
    val (bgColor, tintColor, icon) = when (alert.severityLevel) {
        3 -> Triple(ErrorRed.copy(alpha = 0.1f), ErrorRed, Icons.Rounded.TrendingDown)
        2 -> Triple(ValencePositive.copy(alpha = 0.15f), DeepSage, Icons.Rounded.TrendingFlat)
        else -> Triple(DeepSage.copy(alpha = 0.08f), DeepSage, Icons.Rounded.Insights)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Trend Detected", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = tintColor)
                Spacer(Modifier.height(4.dp))
                alert.supportingDataPoints.forEach { point ->
                    Text(text = point, style = MaterialTheme.typography.bodySmall, color = TextPrimary, lineHeight = 18.sp)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, tint = tintColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ActiveGuidanceCard(
    action: InterventionAction,
    onHelpful: () -> Unit,
    onLater: () -> Unit
) {
    val title = when (action) {
        is InterventionAction.Guidance -> action.title
        is InterventionAction.Motivation -> action.title
        else -> ""
    }
    val description = when (action) {
        is InterventionAction.Guidance -> action.description
        is InterventionAction.Motivation -> action.description
        else -> ""
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = SageSurface
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onHelpful,
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSage),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Try this", style = MaterialTheme.typography.labelMedium, color = MilkWhite)
                }
                TextButton(
                    onClick = onLater,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Not right now", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun GuidedRoutineCard(routine: InterventionAction.GuidedRoutine, onComplete: () -> Unit) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPhaseIndex by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(0) }

    LaunchedEffect(isPlaying, currentPhaseIndex) {
        if (isPlaying && currentPhaseIndex < routine.phases.size) {
            timeLeft = routine.phases[currentPhaseIndex].durationSeconds
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            if (currentPhaseIndex < routine.phases.size - 1) {
                currentPhaseIndex++
            } else {
                isPlaying = false
                onComplete()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = DeepSage
    ) {
        AnimatedContent(targetState = isPlaying, label = "routineState") { playing ->
            if (!playing) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MilkWhite.copy(alpha = 0.15f)) {
                            Text("GUIDED ROUTINE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = MilkWhite, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Text("${routine.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = MilkWhite.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = routine.routineType.name.replace("_", " "),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay),
                        color = MilkWhite
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A sequenced flow to help you transition intentionally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MilkDim.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { isPlaying = true; currentPhaseIndex = 0 },
                        colors = ButtonDefaults.buttonColors(containerColor = MilkWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = DeepSage, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Routine", style = MaterialTheme.typography.labelLarge, color = DeepSage)
                    }
                }
            } else {
                val phase = routine.phases[currentPhaseIndex]
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        routine.phases.forEachIndexed { index, _ ->
                            val color = if (index <= currentPhaseIndex) MilkWhite else MilkWhite.copy(alpha = 0.3f)
                            Box(modifier = Modifier.padding(horizontal = 4.dp).height(4.dp).weight(1f).clip(CircleShape).background(color))
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = phase.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay),
                        color = MilkWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = phase.instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MilkDim.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    Box(
                        modifier = Modifier.size(80.dp).border(2.dp, MilkWhite.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${timeLeft}s",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MilkWhite
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { isPlaying = false }) {
                            Text("End Early", color = MilkWhite.copy(alpha = 0.7f))
                        }
                        TextButton(onClick = {
                            if (currentPhaseIndex < routine.phases.size - 1) {
                                currentPhaseIndex++
                            } else {
                                isPlaying = false
                                onComplete()
                            }
                        }) {
                            Text(
                                text = if (currentPhaseIndex < routine.phases.size - 1) "Skip to Next" else "Finish",
                                color = MilkWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WellBeingDomainRow(activeDomain: WellBeingDomain, onSelect: (WellBeingDomain) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(WellBeingDomain.entries.toTypedArray()) { domain ->
            val isSelected = domain == activeDomain
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(domain) },
                color = if (isSelected) DeepSage else MilkDeep,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = domain.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = if (isSelected) MilkWhite else TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MicroInterventionRow(interventions: List<InterventionAction.MicroIntervention>, onClick: (InterventionAction.MicroIntervention) -> Unit) {
    if (interventions.isEmpty()) {
        Text("No quick resets available for this category right now.", style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.padding(horizontal = 24.dp))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(interventions, key = { it.id }) { micro ->
            Surface(
                modifier = Modifier.width(140.dp).clip(RoundedCornerShape(20.dp)).clickable { onClick(micro) },
                color = SageSurface,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val icon = when (micro.wellBeingDomain) {
                        WellBeingDomain.MENTAL -> Icons.Rounded.SelfImprovement
                        WellBeingDomain.PHYSICAL -> Icons.Rounded.DirectionsRun
                        WellBeingDomain.SLEEP -> Icons.Rounded.Bedtime
                        WellBeingDomain.DIGITAL -> Icons.Rounded.Smartphone
                        WellBeingDomain.SOCIAL -> Icons.Rounded.Favorite
                    }
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MilkWhite), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = DeepSage, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    val title = micro.id.split("_").drop(1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("${micro.durationSeconds / 60} min", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MotivationNudgeCard(nudge: InterventionAction.MotivationNudge, onDismiss: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.girl_exploring))
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LottieAnimation(composition = composition, iterations = LottieConstants.IterateForever, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val prefix = nudge.streakDays?.let { "🔥 $it Day Streak! " } ?: ""
                Text(nudge.achievementKey, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Text("$prefix${nudge.tone.name.lowercase().replaceFirstChar { it.uppercase() }} going.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CareOnboardingState(readiness: InsightDomainReadiness, isIntradayComplete: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.SelfImprovement, null, tint = DeepSage, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Cultivating Care", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay), color = TextPrimary)
            Spacer(Modifier.height(12.dp))

            val message = if (!isIntradayComplete && readiness.anyReady) {
                "We need a bit more data today to provide personalized guidance. Keep tracking and check back later."
            } else {
                "We are silently learning your rhythms to provide guidance tailored to you. Insights will bloom soon."
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            if (!readiness.anyReady) {
                Spacer(Modifier.height(32.dp))
                val progress = (readiness.activity.progressFraction + readiness.mood.progressFraction) / 2f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape),
                    color = DeepSage,
                    trackColor = SageDim
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = DmSerifDisplay, fontSize = 20.sp),
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}