package com.moodified.app.presentation.care.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.moodified.app.R
import com.moodified.app.core.theme.*
import com.moodified.app.domain.model.intervention.InterventionAction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTrendAlert(
    alert: InterventionAction.TrendAlert,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDismiss()
                true
            } else false
        }
    )
    val coroutineScope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled)
                    ErrorRed.copy(alpha = 0.2f) else Color.Transparent,
                label = "dismiss_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Dismiss",
                        tint = ErrorRed,
                        modifier = Modifier.padding(end = 24.dp)
                    )
                }
            }
        },
        content = {
            TrendAlertBanner(alert, onDismiss = {
                coroutineScope.launch { dismissState.dismiss(SwipeToDismissBoxValue.EndToStart) }
            })
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMotivationNudge(
    nudge: InterventionAction.MotivationNudge,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDismiss()
                true
            } else false
        }
    )
    val coroutineScope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled)
                    SageDim.copy(alpha = 0.5f) else Color.Transparent,
                label = "dismiss_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Acknowledge",
                        tint = DeepSage,
                        modifier = Modifier.padding(end = 24.dp)
                    )
                }
            }
        },
        content = {
            MotivationNudgeCard(nudge, onDismiss = {
                coroutineScope.launch { dismissState.dismiss(SwipeToDismissBoxValue.EndToStart) }
            })
        }
    )
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