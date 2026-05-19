package com.moodified.app.presentation.care.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.*
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.WellBeingDomain
import kotlinx.coroutines.delay

@Composable
fun MicroInterventionRow(interventions: List<InterventionAction.MicroIntervention>, onClick: (InterventionAction.MicroIntervention) -> Unit) {
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

                    val mins = micro.durationSeconds / 60
                    val durationText = if (mins > 0) "$mins min" else "${micro.durationSeconds} sec"
                    Text(durationText, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun MicroInterventionSheetContent(
    intervention: InterventionAction.MicroIntervention,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val isLastStep = currentStep == intervention.steps.size - 1
    val haptic = LocalHapticFeedback.current

    // Auto-advance timer logic tied strictly to the current step's duration
    var timeLeft by remember(currentStep) {
        mutableIntStateOf(intervention.steps[currentStep].durationSeconds)
    }

    if (intervention.isAutoAdvance) {
        LaunchedEffect(currentStep) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            if (!isLastStep) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentStep++
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = intervention.id.split("_").drop(1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            intervention.steps.forEachIndexed { index, _ ->
                val color = if (index == currentStep) DeepSage else SageDim
                val width by animateDpAsState(if (index == currentStep) 20.dp else 8.dp, label = "dot")
                Box(
                    modifier = Modifier
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally { width -> width / 2 }) togetherWith
                        (fadeOut(tween(300)) + slideOutHorizontally { width -> -width / 2 })
            },
            label = "step_transition"
        ) { stepIndex ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = intervention.steps[stepIndex].instruction,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        AnimatedContent(targetState = intervention.isAutoAdvance, label = "controls") { autoAdvance ->
            if (autoAdvance) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .border(2.dp, DeepSage.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val mins = timeLeft / 60
                        val secs = timeLeft % 60
                        Text(
                            text = String.format(java.util.Locale.US, "%d:%02d", mins, secs),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepSage
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    if (isLastStep && timeLeft == 0) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSage)
                        ) {
                            Text("Finish & Record", style = MaterialTheme.typography.labelLarge, color = MilkWhite)
                        }
                    } else {
                        TextButton(onClick = onDismiss, modifier = Modifier.height(54.dp)) {
                            Text("End Early", color = TextSecondary)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isLastStep) onDismiss() else currentStep++
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSage)
                ) {
                    Text(
                        text = if (isLastStep) "Finish & Record" else "Next Step",
                        style = MaterialTheme.typography.labelLarge,
                        color = MilkWhite
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}