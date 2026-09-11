package com.moodified.app.presentation.care.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
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
import kotlinx.coroutines.delay

@Composable
fun ActiveGuidanceCard(
    action: InterventionAction,
    onHelpful: () -> Unit,
    onLater: () -> Unit,
) {
    val title =
        when (action) {
            is InterventionAction.Guidance -> action.title
            is InterventionAction.Motivation -> action.title
            else -> ""
        }
    val description =
        when (action) {
            is InterventionAction.Guidance -> action.description
            is InterventionAction.Motivation -> action.description
            else -> ""
        }

    var isCommitted by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(300) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isCommitted) {
        if (isCommitted) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = SageSurface,
    ) {
        AnimatedContent(targetState = isCommitted, label = "guidance_state") { committed ->
            if (!committed) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DmSerifDisplay),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { isCommitted = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSage),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text("Try this", style = MaterialTheme.typography.labelMedium, color = MilkWhite)
                        }
                        TextButton(
                            onClick = onLater,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text("Not right now", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "IN PROGRESS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = DeepSage,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Take the time you need to do this now. We'll hold this space for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))

                    Box(
                        modifier = Modifier.size(80.dp).border(2.dp, DeepSage.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val mins = timeLeft / 60
                        val secs = timeLeft % 60
                        Text(
                            text = String.format(java.util.Locale.US, "%d:%02d", mins, secs),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepSage,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = onHelpful,
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSage),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text("I'm Done", style = MaterialTheme.typography.labelLarge, color = MilkWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun GuidedRoutineCard(
    routine: InterventionAction.GuidedRoutine,
    onComplete: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPhaseIndex by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPlaying, currentPhaseIndex) {
        if (isPlaying && currentPhaseIndex < routine.phases.size) {
            timeLeft = routine.phases[currentPhaseIndex].durationSeconds
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        color = DeepSage,
    ) {
        AnimatedContent(targetState = isPlaying, label = "routineState") { playing ->
            if (!playing) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MilkWhite.copy(alpha = 0.15f)) {
                            Text(
                                "GUIDED ROUTINE",
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = MilkWhite,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            "${routine.estimatedMinutes} min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MilkWhite.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = routine.routineType.name.replace("_", " "),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay),
                        color = MilkWhite,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A sequenced flow to help you transition intentionally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MilkDim.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            isPlaying = true
                            currentPhaseIndex = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MilkWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = phase.instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MilkDim.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    Box(
                        modifier = Modifier.size(80.dp).border(2.dp, MilkWhite.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${timeLeft}s",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MilkWhite,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(onClick = { isPlaying = false }) {
                            Text("End Early", color = MilkWhite.copy(alpha = 0.7f))
                        }
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (currentPhaseIndex < routine.phases.size - 1) {
                                currentPhaseIndex++
                            } else {
                                isPlaying = false
                                onComplete()
                            }
                        }) {
                            Text(
                                text = if (currentPhaseIndex < routine.phases.size - 1) "Skip to Next" else "Finish",
                                color = MilkWhite,
                            )
                        }
                    }
                }
            }
        }
    }
}
