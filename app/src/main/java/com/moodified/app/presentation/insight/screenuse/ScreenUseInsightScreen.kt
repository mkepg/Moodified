package com.moodified.app.presentation.insight.screenuse

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.moodified.app.presentation.devtools.MonitorCardEmpty
import com.moodified.app.presentation.devtools.MonitorHeader
import com.moodified.app.presentation.devtools.MonitorStatTile
import com.moodified.app.presentation.devtools.MonitorWeeklyBars
import com.moodified.app.presentation.devtools.PermissionDeniedCard
import com.moodified.app.presentation.devtools.SectionLabel
import com.moodified.app.presentation.devtools.WeeklyBarEntry
import com.moodified.app.presentation.insight.common.InsightStatus

@Composable
fun ScreenUseInsightScreen(
    onBack: () -> Unit,
    viewModel: ScreenUseInsightViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            MonitorHeader(
                title = "Screen Use",
                subtitle = "How and when you reach for your phone",
                isTracking = state.status == InsightStatus.Ready,
                liveIndicatorColor = ValenceNegative,
                eyebrow = "INSIGHTS",
                onBack = onBack,
            )
        }

        when (state.status) {
            InsightStatus.PermissionRequired -> item {
                PermissionDeniedCard(
                    title = "Usage Access needed",
                    body = "Moodified reads aggregate screen-on times to surface " +
                            "your screen-use patterns. We never see what's on screen " +
                            "or which apps you open.",
                    canAskAgain = false,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                )
            }
            InsightStatus.Loading -> item {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            InsightStatus.TrackingOff -> item {
                IdleBanner("Screen-use tracking is off. Turn it on in More → Tracking Preferences.")
            }
            InsightStatus.Empty -> item {
                IdleBanner("Not enough data yet. Check back after a full day of use.")
            }
            InsightStatus.Ready -> {
                item {
                    SectionLabel("Today")
                    ScreenUseTodayCard(state = state)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("This Week")
                    ScreenUseWeeklyCard(state = state)
                }
            }
        }
    }
}

@Composable
private fun ScreenUseTodayCard(state: ScreenUseInsightUiState) {
    MonitorCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Screen time",
                value = DateTimeUtils.formatMinutes(state.totalScreenMinutesToday),
                subLabel = "Today total",
                accentColor = ValenceNegative,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Late night",
                value = DateTimeUtils.formatMinutes(state.lateNightMinutesToday),
                subLabel = "9 PM – 6 AM",
                accentColor = ArousalHigh,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Unlocks",
                value = state.unlockCountToday.toString(),
                subLabel = "Pickups today",
                accentColor = ArousalMid,
            )
        }
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Sessions",
            value = state.sessionCountToday.toString(),
            note = "Distinct on-screen periods",
        )
        BreakdownRow(
            label = "Avg session",
            value = DateTimeUtils.formatMinutes(state.avgSessionMinutesToday),
            note = "Typical pickup length",
        )
    }
}

@Composable
private fun ScreenUseWeeklyCard(state: ScreenUseInsightUiState) {
    if (state.weeklyBars.isEmpty()) {
        MonitorCardEmpty("Not enough data yet for a weekly view.")
        return
    }
    MonitorCard {
        MonitorWeeklyBars(
            entries = state.weeklyBars.map {
                WeeklyBarEntry(label = it.label, value = it.value, isToday = it.isToday)
            }
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Avg / day",
                value = DateTimeUtils.formatMinutes(state.averageScreenMinutesThisWeek),
                subLabel = "Past 7 days",
                accentColor = ValenceNegative,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Avg late-night",
                value = DateTimeUtils.formatMinutes(state.averageLateNightMinutesThisWeek),
                subLabel = "After 9 PM",
                accentColor = ArousalHigh,
            )
        }
        ConsistencyScoreSection(
            score = state.consistencyScore,
            sublabel = "How steady your daily screen time is",
        )
    }
}
