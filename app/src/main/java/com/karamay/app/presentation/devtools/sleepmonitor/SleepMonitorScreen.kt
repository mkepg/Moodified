// app/src/main/java/com/karamay/app/presentation/devtools/sleepmonitor/SleepMonitorScreen.kt
package com.karamay.app.presentation.devtools.sleepmonitor

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
import com.karamay.app.core.theme.*
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSignal
import com.karamay.app.domain.model.sleep.SleepStatus
import com.karamay.app.domain.model.sleep.SleepTrends
import com.karamay.app.presentation.devtools.*

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
                subtitle           = "Inferred via screen inactivity · UsageStats",
                isTracking         = state.isTracking,
                liveIndicatorColor = ValenceNeutral,
                onBack             = onBack
            )
        }

        item {
            SectionLabel("Live Screen State")
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

    val stateLabel  = when (signal.status) {
        SleepStatus.ASLEEP  -> "Inferred asleep"
        SleepStatus.UNKNOWN -> "Screen off"
        SleepStatus.AWAKE   -> "Screen on"
    }

    val confidenceLabel = when {
        !signal.hasActiveSession -> "—"
        signal.confidence == 0   -> "Pending"
        else                     -> "${signal.confidence}%"
    }

    val confidenceSublabel = when {
        !signal.hasActiveSession                                      -> "No session"
        signal.confidence == 100
                && signal.status == SleepStatus.AWAKE
                && signal.deviceMotion == 0                           -> "Awake"
        else                                                          -> "Inference score"
    }

    val motionLabel = when {
        !signal.hasActiveSession -> "—"
        signal.deviceMotion == 0 -> "Still"
        else                     -> "Active"
    }

    val motionSublabel = when {
        !signal.hasActiveSession -> "No data"
        signal.deviceMotion == 0 -> "Screen off"
        else                     -> "Screen on"
    }

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
            accentColor = ArousalMid,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Motion",
            value       = motionLabel,
            subLabel    = motionSublabel,
            accentColor = if (isScreenOff) ArousalLow else ValencePositive,
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
        DebugRow("Status",           signal.status.displayLabel())
        DebugRow("Confidence",       "${signal.confidence}%")
        DebugRow(
            key   = "Screen state",
            value = if (signal.deviceMotion > 0) "Active (screen on)" else "Off"
        )
        DebugRow("Last updated",     signal.timestamp.toLocalTime().toString().take(8))
    }
}