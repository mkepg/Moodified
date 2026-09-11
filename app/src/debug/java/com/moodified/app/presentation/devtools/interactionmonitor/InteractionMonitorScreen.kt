// app/src/main/java/com/moodified/app/presentation/devtools/interactionmonitor/InteractionMonitorScreen.kt
package com.moodified.app.presentation.devtools.interactionmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.model.interaction.InteractionTrends
import com.moodified.app.presentation.devtools.*
import java.time.LocalDate

@Composable
fun InteractionMonitorScreen(
    onBack: () -> Unit,
    viewModel: InteractionMonitorViewModel = hiltViewModel(),
) {
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
                title = "Interaction Monitor",
                subtitle = "Screen session and usage data",
                isTracking = state.isTracking,
                liveIndicatorColor = ValenceNeutral,
                onBack = onBack,
            )
        }

        item {
            SectionLabel("Live Signals")
            LiveInteractionSignalRow(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Today's Summary")
            InteractionDailySummaryCard(summary = state.todaySummary)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("7-Day Trends")
            InteractionWeeklyTrendsCard(trends = state.weeklyTrends)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Weekly Screen Time")
            InteractionWeeklyBarChartCard(summaries = state.weeklySummaries)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            InteractionRawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner("Tracking is disabled. Enable it in Settings.")
            }
        }
    }
}

@Composable
private fun LiveInteractionSignalRow(signal: InteractionSignal) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonitorStatTile(
            modifier = Modifier.weight(1f),
            label = "Unlocks",
            value = signal.unlockCount.toString(),
            subLabel = "Today",
            accentColor = ValencePositive,
        )
        MonitorStatTile(
            modifier = Modifier.weight(1f),
            label = "Screen Time",
            value = DateTimeUtils.formatMs(signal.totalScreenTimeTodayMs),
            subLabel = "Today",
            accentColor = ArousalLow,
        )
        MonitorStatTile(
            modifier = Modifier.weight(1f),
            label = "Late-Night",
            value = DateTimeUtils.formatMs(signal.lateNightScreenTimeTodayMs),
            subLabel = "00:00–05:00",
            accentColor = ValenceNeutral,
        )
    }
}

@Composable
private fun InteractionDailySummaryCard(summary: InteractionDailySummary?) {
    if (summary == null) {
        MonitorCardEmpty("No interaction data recorded yet today.")
        return
    }
    MonitorCard {
        BreakdownRow(
            label = "Screen Time",
            value = DateTimeUtils.formatMinutes(summary.totalScreenTimeMinutes),
            note = screenTimeNote(summary.totalScreenTimeMinutes),
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Late Night Usage",
            value = DateTimeUtils.formatMinutes(summary.lateNightUsageMinutes),
            note = lateNightNote(summary.lateNightUsageMinutes),
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Unlocks",
            value = summary.unlockCount.toString(),
            note = "Total authentications",
        )
    }
}

@Composable
private fun InteractionWeeklyTrendsCard(trends: InteractionTrends?) {
    if (trends == null) {
        MonitorCardEmpty("Insufficient data for weekly trends.")
        return
    }
    MonitorCard {
        BreakdownRow(
            label = "Avg Screen Time",
            value = DateTimeUtils.formatMinutes(trends.averageScreenTimeMinutes),
            note = "Past ${trends.daysAnalyzed} days",
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Avg Late Night",
            value = DateTimeUtils.formatMinutes(trends.averageLateNightMinutes),
            note = "00:00–05:00 per day",
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        ConsistencyScoreSection(score = trends.consistencyScore)
    }
}

@Composable
private fun InteractionWeeklyBarChartCard(summaries: List<InteractionDailySummary>) {
    if (summaries.isEmpty()) {
        MonitorCardEmpty("Insufficient data for a weekly overview.")
        return
    }
    val avgScreenTime = summaries.sumOf { it.totalScreenTimeMinutes } / summaries.size
    val bestDay = summaries.maxByOrNull { it.totalScreenTimeMinutes }
    val today = LocalDate.now().toString()

    MonitorCard {
        BreakdownRow(
            label = "Avg Daily Screen Time",
            value = DateTimeUtils.formatMinutes(avgScreenTime),
            note = "Past ${summaries.size} days",
        )
        if (bestDay != null) {
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow(
                label = "Highest Day",
                value = DateTimeUtils.formatMinutes(bestDay.totalScreenTimeMinutes),
                note = bestDay.date,
            )
        }
        Spacer(Modifier.height(4.dp))
        MonitorWeeklyBars(
            entries =
                summaries.map { day ->
                    WeeklyBarEntry(
                        label = day.date.takeLast(5).replace("-", "/"),
                        value = day.totalScreenTimeMinutes,
                        isToday = day.date == today,
                    )
                },
        )
    }
}

@Composable
private fun InteractionRawDebugCard(
    signal: InteractionSignal,
    isTracking: Boolean,
) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active", if (isTracking) "Yes" else "No")
        DebugRow("Total screen time", DateTimeUtils.formatMs(signal.totalScreenTimeTodayMs))
        DebugRow("Late night (today)", DateTimeUtils.formatMs(signal.lateNightScreenTimeTodayMs))
        DebugRow("Session duration", DateTimeUtils.formatMs(signal.currentSessionDurationMs))
        DebugRow("Total unlocks", signal.unlockCount.toString())
        DebugRow("Last updated", signal.timestamp.toLocalTime().toString().take(8))
    }
}

private fun screenTimeNote(minutes: Int): String =
    when {
        minutes == 0 -> "No usage recorded"
        minutes < 60 -> "Under an hour"
        minutes < 120 -> "Light usage"
        minutes < 240 -> "Moderate usage"
        else -> "Heavy usage"
    }

private fun lateNightNote(minutes: Int): String =
    when {
        minutes == 0 -> "No late-night usage"
        minutes < 15 -> "Light late-night use"
        minutes < 60 -> "Moderate late-night use"
        else -> "High late-night use"
    }
