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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.ValenceNegative
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.core.theme.ValencePositive
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence

@Composable
fun OverviewTodayMoodCard(mood: InferredMoodState) {
    val accentColor =
        when (mood.valence) {
            Valence.POSITIVE -> ValencePositive
            Valence.NEUTRAL -> ValenceNeutral
            Valence.NEGATIVE -> if (mood.arousal == Arousal.HIGH) ErrorRed else ValenceNegative
        }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = accentColor.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = overviewMoodIcon(mood.valence, mood.arousal),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY'S ENERGY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = accentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mood.interpretationLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mood.explainabilityString,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = accentColor.copy(alpha = 0.20f)) {
                Text(
                    text = "${mood.confidenceScore}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

private fun overviewMoodIcon(
    valence: Valence,
    arousal: Arousal,
): ImageVector =
    when (valence) {
        Valence.POSITIVE ->
            when (arousal) {
                Arousal.HIGH -> Icons.Rounded.Bolt
                Arousal.MID -> Icons.Rounded.SentimentSatisfiedAlt
                Arousal.LOW -> Icons.Rounded.Spa
            }
        Valence.NEUTRAL ->
            when (arousal) {
                Arousal.HIGH -> Icons.Rounded.Air
                Arousal.MID -> Icons.Rounded.SentimentNeutral
                Arousal.LOW -> Icons.Rounded.Bedtime
            }
        Valence.NEGATIVE ->
            when (arousal) {
                Arousal.HIGH -> Icons.Rounded.Warning
                Arousal.MID -> Icons.Rounded.SentimentDissatisfied
                Arousal.LOW -> Icons.Rounded.BatteryAlert
            }
    }
