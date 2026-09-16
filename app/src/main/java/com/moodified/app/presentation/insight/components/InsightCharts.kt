package com.moodified.app.presentation.insight.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.ArousalHigh
import com.moodified.app.core.theme.ArousalLow
import com.moodified.app.core.theme.ArousalMid
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValencePositive

data class WeeklyBarEntry(
    val label: String,
    val value: Int,
    val isToday: Boolean,
)

fun consistencyColor(score: Int): Color {
    val s = score.coerceIn(0, 100)
    return when {
        s >= 70 -> ValencePositive
        s >= 40 -> ArousalLow
        else -> ArousalHigh
    }
}

private fun consistencyNote(score: Int): String =
    when {
        score >= 70 -> "Highly consistent this week."
        score >= 40 -> "Some variation — try to keep a daily routine."
        else -> "High variability detected. Consider building a schedule."
    }

@Composable
fun WeeklyBarChart(
    entries: List<WeeklyBarEntry>,
    maxBarHeight: Int = 64,
) {
    val safeMax = entries.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        entries.forEach { entry ->
            val fraction = (entry.value.toFloat() / safeMax).coerceIn(0f, 1f)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((maxBarHeight * fraction).coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (entry.isToday) {
                                    ValencePositive.copy(alpha = 0.75f)
                                } else {
                                    ArousalMid.copy(alpha = 0.4f)
                                },
                            ),
                )
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (entry.isToday) TextPrimary else TextTertiary,
                )
            }
        }
    }
}

@Composable
fun ConsistencyScoreSection(
    score: Int,
    sublabel: String = "Duration variance score",
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Consistency", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(sublabel, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.TrendingUp, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                AnimatedContent(
                    targetState = "$score/100",
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "consistencyScore",
                ) { s ->
                    Text(
                        text = s,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = consistencyColor(score),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SageDim.copy(alpha = 0.35f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(consistencyColor(score)),
            )
        }
        Text(
            text = consistencyNote(score),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
    }
}
