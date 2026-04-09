package com.karamay.app.presentation.devtools.sleepmonitor

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val state          by viewModel.state.collectAsStateWithLifecycle()
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // This is the Android Lifecycle onResume (e.g., returning from Settings app),
                // NOT a "Resume Tracking" button.
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                // FIX: Setting hasData to false entirely disables the "Resume" button logic
                // inside MonitorSharedComponents.kt, making it act exactly like the Interaction Monitor.
                hasData            = false,
                liveIndicatorColor = ValenceNeutral,
                activeLabel        = "Stop Tracking",
                inactiveLabel      = "Start Tracking",
                onBack             = onBack,
                onToggle           = {
                    if (state.isTracking) {
                        viewModel.stopTracking()
                    } else {
                        when (state.permission) {
                            is PermissionState.RequiresSystemSettings -> {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }
                            else -> viewModel.startTracking()
                        }
                    }
                },
                // FIX: Ensures the reset button does not render
                showReset = false,
            )
        }

        if (state.permission is PermissionState.RequiresSystemSettings) {
            item {
                PermissionDeniedCard(
                    title          = "Usage Access Required",
                    body           = "Sleep inference requires Usage Access permission. " +
                            "Tap 'Start Tracking' to open Settings → Apps → Special app access → Usage access → Karamay.",
                    canAskAgain    = false,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            SectionLabel("Live Screen State")
            LiveSleepSignalRow(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Last Night's Estimate")
            SleepSummaryCard(summary = state.todaySummary)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("7-Day Trends & Debt")
            SleepWeeklyTrendsCard(trends = state.weeklyTrends)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            SleepRawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking &&
            state.permission !is PermissionState.RequiresSystemSettings &&
            !state.liveSignal.hasActiveSession
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner()
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
        !signal.hasActiveSession               -> "No session"
        signal.confidence == 100
                && signal.status == SleepStatus.AWAKE
                && signal.deviceMotion == 0        -> "Awake"
        else                                   -> "Inference score"
    }

    val motionLabel = when {
        !signal.hasActiveSession -> "—"
        signal.deviceMotion == 0 -> "Still"
        signal.deviceMotion == 1 -> "Moving"
        else                     -> signal.deviceMotion.toString()
    }

    val motionSublabel = when {
        !signal.hasActiveSession -> "No data"
        signal.deviceMotion == 0 -> "No movement"
        else                     -> "Movement detected"
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
private fun SleepSummaryCard(summary: DailySleepSummary?) {
    if (summary == null) {
        MonitorCardEmpty("No sleep estimate yet for last night.")
        return
    }

    MonitorCard {
        if (summary.isEstimated) {
            StatusBadge(text = "ESTIMATED", color = ValenceNeutral)
        }

        BreakdownRow(
            "Total Sleep",
            DateTimeUtils.formatMinutes(summary.totalSleepMinutes),
            "Inferred from screen inactivity"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            "Time in Bed",
            DateTimeUtils.formatMinutes(summary.timeInBedMinutes),
            "Screen off window"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            "Awakenings",
            summary.awakenings.toString(),
            "Brief screen-on events"
        )

        if (summary.sleepOnsetMinutes != null) {
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow(
                "Sleep Onset",
                DateTimeUtils.formatMinutes(summary.sleepOnsetMinutes),
                "Minutes after 6 PM"
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
            "Avg Sleep",
            DateTimeUtils.formatMinutes(trends.averageSleepMinutes),
            "${trends.daysAnalyzed} nights"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            "Sleep Debt",
            DateTimeUtils.formatMinutes(trends.totalSleepDebtMinutes),
            "vs 8 h baseline"
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            "Consistency",
            "${trends.consistencyScore}%",
            "Duration + onset regularity"
        )
    }
}

@Composable
private fun SleepRawDebugCard(signal: SleepSignal, isTracking: Boolean) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active",  if (isTracking) "Yes" else "No")
        DebugRow("Screen state",     signal.status.displayLabel())
        DebugRow("Confidence",       "${signal.confidence}%")
        DebugRow("Device motion",    signal.deviceMotion.toString())
        DebugRow("Last updated",     signal.timestamp.toLocalTime().toString().take(8))
    }
}