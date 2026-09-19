package com.moodified.app.presentation.checkin.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.domain.model.intervention.InterventionAction

@Composable
fun TodaysCareCard(
    action: InterventionAction,
    onOpenCareAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenCareAll),
        color = SageSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "TODAY'S CARE",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 10.sp,
                    ),
                color = DeepSage,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = action.displayTitle(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = action.displayBody(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = DeepSage,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = MilkWhite,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun InterventionAction.displayTitle(): String =
    when (this) {
        is InterventionAction.Guidance -> title
        is InterventionAction.Motivation -> title
        is InterventionAction.TrendAlert -> "Something's shifting"
        is InterventionAction.MicroIntervention -> "Micro-routine ready"
        is InterventionAction.GuidedRoutine -> "Guided routine"
        is InterventionAction.MotivationNudge -> "You're doing well"
        is InterventionAction.MicroConfirmation -> prompt
    }

private fun InterventionAction.displayBody(): String =
    when (this) {
        is InterventionAction.Guidance -> description
        is InterventionAction.Motivation -> description
        is InterventionAction.TrendAlert -> "Check trends in ${domain.name.lowercase()}"
        is InterventionAction.MicroIntervention -> "${steps.size} steps · ${durationSeconds}s"
        is InterventionAction.GuidedRoutine -> "$estimatedMinutes min · ${routineType.name.lowercase()}"
        is InterventionAction.MotivationNudge -> "Keep it up"
        is InterventionAction.MicroConfirmation -> "Tap to confirm"
    }
