package com.moodified.app.presentation.insight.tabs.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.presentation.insight.MoodStability

@Composable
fun OverviewMoodStabilityCard(stability: MoodStability) {
    val (color, icon, subtitle) =
        when {
            stability.score >= 75 ->
                Triple(
                    DeepSage,
                    Icons.Rounded.Waves,
                    "Your emotional rhythm has been beautifully steady. This kind of balance is a great foundation for your well-being.",
                )
            stability.score >= 40 ->
                Triple(
                    ValenceNeutral,
                    Icons.Rounded.Insights,
                    "You've been navigating some natural ups and downs. Remember that a bit of fluctuation is completely normal.",
                )
            else ->
                Triple(
                    ErrorRed,
                    Icons.Rounded.TrendingFlat,
                    "You've experienced quite a few shifts in your mood recently. Be gentle with yourself as you ride these waves.",
                )
        }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Emotional Rhythm",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = color,
                    )
                    Text(
                        stability.stateLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = DmSerifDisplay, fontSize = 22.sp),
                        color = TextPrimary,
                    )
                }
                Text(
                    text = "${stability.score}",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                    color = color,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(20.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction = (stability.score / 100f).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                )
            }
        }
    }
}
