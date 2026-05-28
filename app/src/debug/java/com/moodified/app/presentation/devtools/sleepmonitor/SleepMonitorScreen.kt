package com.moodified.app.presentation.devtools.sleepmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.model.sleep.SleepSignal
import com.moodified.app.domain.model.sleep.SleepStatus
import com.moodified.app.domain.model.sleep.SleepTrends
import com.moodified.app.presentation.devtools.*

@Composable
fun SleepMonitorScreen(
    onBack:    () -> Unit,
    viewModel: SleepMonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            MonitorHeader(
                title              = "Sleep Monitor",
                subtitle           = "Inactivity and sleep inference data",
                isTracking         = state.isTracking,
                liveIndicatorColor = ValenceNeutral,
                onBack             = onBack
            )
        }

        item {
            SectionLabel("Live State")
            LiveSleepSignalRow(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Last Night's Estimate")
            SleepSummaryCard(
                summary          = state.todaySummary,
                isTracking       = state.isTracking,
                hasActiveSession = state.liveSignal.hasActiveSession
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("7-Day Trends & Sleep Debt")
            SleepWeeklyTrendsCard(trends = state.weeklyTrends)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            SleepRawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking && !state.liveSignal.hasActiveSession) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner("Tracking is disabled. Enable it in Settings.")
            }
        }
    }
}

@Composable
private fun LiveSleepSignalRow(signal: SleepSignal) {
    val isScreenOff = signal.status == SleepStatus.UNKNOWN || signal.status == SleepStatus.ASLEEP
    val statusIcon  = if (isScreenOff) Icons.Rounded.DarkMode else Icons.Rounded.PhoneAndroid
    val stateLabel  = if (isScreenOff) "Screen off" else "Screen on"

    val trackerLabel = if (signal.isTracking) "Active" else "Idle"
    val trackerSublabel = if (signal.hasActiveSession) "Monitoring" else "Waiting"

    val confidenceLabel = if (signal.confidence > 0) "${signal.confidence}%" else "Pending"
    val confidenceSublabel = "Last inference"

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Screen",
            value       = statusIcon,
            subLabel    = stateLabel,
            accentColor = if (isScreenOff) ValenceNeutral else ValencePositive,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Confidence",
            value       = confidenceLabel,
            subLabel    = confidenceSublabel,
            accentColor = ArousalLow,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Service",
            value       = trackerLabel,
            subLabel    = trackerSublabel,
            accentColor = if (signal.isTracking) ValencePositive else ArousalLow,
        )
    }
}

@Composable
private fun SleepSummaryCard(
    summary: DailySleepSummary?,
    isTracking: Boolean,
    hasActiveSession: Boolean
) {
    if (summary == null) {
        val emptyMessage = if (isTracking && hasActiveSession) {
            "Currently monitoring tonight's sleep. Estimate will appear in the morning."
        } else {
            "No sleep estimate yet for last night."
        }
        MonitorCardEmpty(emptyMessage)
        return
    }

    MonitorCard {
        BreakdownRow(
            label = "Total Sleep",
            value = DateTimeUtils.formatMinutes(summary.totalSleepMinutes),
            note  = "Inferred from screen inactivity"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Time in Bed",
            value = DateTimeUtils.formatMinutes(summary.timeInBedMinutes),
            note  = "Screen-off window"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Awakenings",
            value = summary.awakenings.toString(),
            note  = "Brief screen-on events during night"
        )
        if (summary.sleepOnsetMinutes != null) {
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow(
                label = "Fell Asleep",
                value = DateTimeUtils.offsetMinutesToClockTime(summary.sleepOnsetMinutes),
                note  = "First stable screen-off · approximate"
            )
        }
    }
}

@Composable
private fun SleepWeeklyTrendsCard(trends: SleepTrends?) {
    if (trends == null) {
        MonitorCardEmpty("Not enough nights tracked for trends.")
        return
    }

    MonitorCard {
        BreakdownRow(
            label = "Avg Sleep",
            value = DateTimeUtils.formatMinutes(trends.averageSleepMinutes),
            note  = "${trends.daysAnalyzed} nights"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow(
            label = "Sleep Debt",
            value = if (trends.totalSleepDebtMinutes > 0)
                DateTimeUtils.formatMinutes(trends.totalSleepDebtMinutes)
            else
                "None",
            note  = "Accumulated this week"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        ConsistencyScoreSection(
            score    = trends.consistencyScore,
            sublabel = "Duration + onset regularity",
        )
    }
}

@Composable
private fun SleepRawDebugCard(signal: SleepSignal, isTracking: Boolean) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active",  if (isTracking) "Yes" else "No")
        DebugRow("Has session",      if (signal.hasActiveSession) "Yes" else "No")
        DebugRow("Screen state",     if (signal.status == SleepStatus.AWAKE) "On" else "Off")
        DebugRow("Confidence",       "${signal.confidence}%")
        DebugRow("Last updated",     signal.timestamp.toLocalTime().toString().take(8))
    }
}