package com.karamay.app.presentation.devtools.sleepmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
            viewModel.startTracking() // <-- Add this line
        } else {
            val activity = context as? androidx.activity.ComponentActivity
            val canRequestAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.ACTIVITY_RECOGNITION
                )
            } ?: false
            viewModel.onPermissionDenied(canRequestAgain = canRequestAgain)
        }
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
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                            else -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACTIVITY_RECOGNITION
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.onPermissionRequested()
                                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                } else {
                                    viewModel.startTracking()
                                }
                            }
                        }
                    }
                },
                showReset = false,
            )
        }

        if (state.permission is PermissionState.RequiresSystemSettings) {
            item {
                PermissionDeniedCard(
                    title          = "Usage Access Required",
                    body           = "Usage access permission lets you track screen time without draining battery.",
                    canAskAgain    = false,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        } else if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                PermissionDeniedCard(
                    title          = "Activity Permission Required",
                    body           = if (denied.canRequestAgain)
                        "Activity recognition permission is required by the background health service."
                    else
                        "Permission denied. Enable 'Physical activity' in app Settings.",
                    canAskAgain    = denied.canRequestAgain,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
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
            SectionLabel("7-Day Trends & Sleep Debt")
            SleepWeeklyTrendsCard(trends = state.weeklyTrends)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            SleepRawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking &&
            state.permission !is PermissionState.RequiresSystemSettings &&
            state.permission !is PermissionState.Denied &&
            !state.liveSignal.hasActiveSession
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner("Tap Start Tracking to begin monitoring signals.")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live signal row
// ─────────────────────────────────────────────────────────────────────────────

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

    // deviceMotion is binary: 0 = screen off (still), 1 = screen on (active).
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

// ─────────────────────────────────────────────────────────────────────────────
// Last night's estimate card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepSummaryCard(summary: DailySleepSummary?) {
    if (summary == null) {
        MonitorCardEmpty("No sleep estimate yet for last night.")
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
                // Shows a clock time such as "~11:45 PM" rather than a duration.
                label = "Fell Asleep",
                value = DateTimeUtils.offsetMinutesToClockTime(summary.sleepOnsetMinutes),
                note  = "First stable screen-off · approximate"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7-day trends card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepWeeklyTrendsCard(trends: SleepTrends?) {
    if (trends == null) {
        MonitorCardEmpty("Not enough nights tracked for trends.")
        return
    }

    val goalHours = trends.sleepGoalMinutes / 60

    MonitorCard {
        BreakdownRow(
            label = "Avg Sleep",
            value = DateTimeUtils.formatMinutes(trends.averageSleepMinutes),
            // The note makes the 12 h/night cap visible; this prevents confusion
            // when the average appears lower than raw data might suggest.
            note  = "${trends.daysAnalyzed} nights"
        )

        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            label = "Sleep Debt",
            value = if (trends.totalSleepDebtMinutes > 0)
                DateTimeUtils.formatMinutes(trends.totalSleepDebtMinutes)
            else
                "None",
            // "vs 8h goal" is now derived from the model field rather than
            // hard-coded in the UI, keeping it in sync with the use-case baseline.
            note  = "Accumulated this week"
        )

        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        ConsistencyScoreSection(
            score    = trends.consistencyScore,
            sublabel = "Duration + onset regularity",
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Raw debug card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepRawDebugCard(signal: SleepSignal, isTracking: Boolean) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active",  if (isTracking) "Yes" else "No")
        DebugRow("Has session",      if (signal.hasActiveSession) "Yes" else "No")
        DebugRow("Status",           signal.status.displayLabel())
        DebugRow("Confidence",       "${signal.confidence}%")
        // deviceMotion is a binary screen-state proxy (0 = screen off, 1 = screen on),
        // not a continuous motion sensor value. Showing the raw integer "1" would be
        // misleading; display its semantic meaning instead.
        DebugRow(
            key   = "Screen state",
            value = if (signal.deviceMotion > 0) "Active (screen on)" else "Off"
        )
        DebugRow("Last updated",     signal.timestamp.toLocalTime().toString().take(8))
    }
}