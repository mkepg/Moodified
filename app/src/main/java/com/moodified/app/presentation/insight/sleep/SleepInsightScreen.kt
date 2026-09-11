package com.moodified.app.presentation.insight.sleep

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.presentation.devtools.BreakdownRow
import com.moodified.app.presentation.devtools.ConsistencyScoreSection
import com.moodified.app.presentation.devtools.IdleBanner
import com.moodified.app.presentation.devtools.MonitorCard
import com.moodified.app.presentation.devtools.MonitorHeader
import com.moodified.app.presentation.devtools.MonitorStatTile
import com.moodified.app.presentation.devtools.PermissionDeniedCard
import com.moodified.app.presentation.devtools.SectionLabel
import com.moodified.app.presentation.devtools.StatusBadge
import com.moodified.app.presentation.insight.common.InsightStatus

@Composable
fun SleepInsightScreen(
    onBack: () -> Unit,
    viewModel: SleepInsightViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            MonitorHeader(
                title = "Sleep",
                subtitle = "How long and how well you've been resting",
                isTracking = state.status == InsightStatus.Ready,
                liveIndicatorColor = ValenceNeutral,
                eyebrow = "INSIGHTS",
                onBack = onBack,
            )
        }

        when (state.status) {
            InsightStatus.PermissionRequired ->
                item {
                    PermissionDeniedCard(
                        title = "Usage Access needed",
                        body =
                            "Moodified estimates your sleep window from quiet device " +
                                "periods. Grant Usage Access so we can see when your phone " +
                                "is idle overnight. We never read app contents.",
                        canAskAgain = false,
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                    )
                }
            InsightStatus.Loading ->
                item {
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(
                        color = DeepSage,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            InsightStatus.TrackingOff ->
                item {
                    IdleBanner("Sleep tracking is off. Turn it on in More → Tracking Preferences.")
                }
            InsightStatus.Empty ->
                item {
                    IdleBanner("We need a few nights to learn your patterns. Check back tomorrow.")
                }
            InsightStatus.Ready -> {
                item {
                    SectionLabel("Last Night")
                    SleepLastNightCard(state = state)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("This Week")
                    SleepWeeklyCard(state = state)
                }
            }
        }
    }
}

@Composable
private fun SleepLastNightCard(state: SleepInsightUiState) {
    MonitorCard {
        if (state.isEstimated) {
            StatusBadge(text = "ESTIMATED", color = ArousalMid)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Slept",
                value = DateTimeUtils.formatMinutes(state.totalSleepMinutes),
                subLabel = "Asleep duration",
                accentColor = ValenceNeutral,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "In bed",
                value = DateTimeUtils.formatMinutes(state.timeInBedMinutes),
                subLabel = "Total time",
                accentColor = ArousalLow,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Efficiency",
                value = "${state.efficiencyPercent}%",
                subLabel = "Asleep ÷ in bed",
                accentColor = ValencePositive,
            )
        }
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SageDim.copy(alpha = 0.35f)),
        ) {
            val progress =
                if (state.sleepGoalMinutes <= 0) {
                    0f
                } else {
                    (state.totalSleepMinutes.toFloat() / state.sleepGoalMinutes).coerceIn(0f, 1f)
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ValenceNeutral),
            )
        }
        BreakdownRow(
            label = "Awakenings",
            value = state.awakenings.toString(),
            note = "Times stirred during the night",
        )
    }
}

@Composable
private fun SleepWeeklyCard(state: SleepInsightUiState) {
    MonitorCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Avg / night",
                value = DateTimeUtils.formatMinutes(state.averageSleepMinutes),
                subLabel = "Past ${state.daysAnalyzed} days",
                accentColor = ValenceNeutral,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Sleep debt",
                value = DateTimeUtils.formatMinutes(state.sleepDebtMinutes),
                subLabel = "vs. your goal",
                accentColor = if (state.sleepDebtMinutes > 0) ArousalHigh else ValencePositive,
            )
        }
        ConsistencyScoreSection(
            score = state.consistencyScore,
            sublabel = "How steady your sleep duration is",
        )
    }
}
