package com.moodified.app.presentation.insight.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary

@Composable
fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: Any,
    subLabel: String,
    accentColor: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.10f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "statTileValue",
            ) { v ->
                when (v) {
                    is String ->
                        Text(
                            text = v,
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                ),
                            color = TextPrimary,
                        )
                    is ImageVector ->
                        Icon(
                            imageVector = v,
                            contentDescription = label,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                }
            }
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.8.sp,
                        fontSize = 9.sp,
                    ),
                color = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BreakdownRow(
    label: String,
    value: String,
    note: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(note, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = text.uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 9.sp,
                ),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
