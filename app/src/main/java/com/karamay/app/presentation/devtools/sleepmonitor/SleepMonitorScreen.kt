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
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onResume(hasPermission)
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
            viewModel.startTracking()
        } else {
            val activity        = context as? androidx.activity.ComponentActivity
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
                subtitle           = "Live API telemetry · nightly segmentation",
                isTracking         = state.isTracking,
                hasData            = state.liveSignal.hasActiveSession,
                liveIndicatorColor = ValenceNeutral,
                onBack             = onBack,
                onToggle           = {
                    when {
                        state.isTracking -> viewModel.stopTracking()
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                state.permission !is PermissionState.Granted -> {
                            viewModel.onPermissionRequested()
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                        else -> viewModel.startTracking()
                    }
                },
                showReset = true,
                onReset = { viewModel.resetSession() },
            )
        }

        if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                PermissionDeniedCard(
                    title          = "Permission Required",
                    body           = if (denied.canRequestAgain)
                        "Physical activity permission is needed to track sleep. Tap Start Tracking to request it."
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
            SectionLabel("Live Signals")
            LiveSleepSignalRow(signal = state.liveSignal)
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Last Night's Summary")
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
            state.permission !is PermissionState.Denied &&
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
    val isAsleep        = signal.status == SleepStatus.ASLEEP
    val statusIcon      = if (isAsleep) Icons.Rounded.DarkMode else Icons.Rounded.LightMode
    val statusColor     = if (isAsleep) ValenceNeutral else ValencePositive
    val displayConf     = if (isAsleep) signal.confidence else 100 - signal.confidence
    val motionLabel     = when {
        !signal.hasActiveSession -> "—"
        signal.deviceMotion == 0 -> "Still"
        signal.deviceMotion == 1 -> "Moving"
        else                     -> signal.deviceMotion.toString()
    }
    val motionSublabel  = when {
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
            label       = "State",
            value       = statusIcon,
            subLabel    = if (signal.hasActiveSession) signal.status.displayLabel() else "—",
            accentColor = statusColor,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Confidence",
            value       = if (signal.hasActiveSession) "${displayConf}%" else "—",
            subLabel    = if (displayConf > 80) "High" else "Low",
            accentColor = ArousalMid,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Motion",
            value       = motionLabel,
            subLabel    = motionSublabel,
            accentColor = ValencePositive,
        )
    }
}

@Composable
private fun SleepSummaryCard(summary: DailySleepSummary?) {
    if (summary == null) {
        MonitorCardEmpty("No segments finalised for today yet.")
        return
    }
    MonitorCard {
        if (summary.isEstimated) {
            StatusBadge(text = "ESTIMATED", color = ArousalMid)
        }
        BreakdownRow("Total Sleep Time", DateTimeUtils.formatMinutes(summary.totalSleepMinutes), "Finalised duration")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Time in Bed",      DateTimeUtils.formatMinutes(summary.timeInBedMinutes),  "Total segment duration")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Fragmentation",    "${summary.awakenings} times",            "Awakenings detected")
    }
}

@Composable
private fun SleepWeeklyTrendsCard(trends: SleepTrends?) {
    if (trends == null) {
        MonitorCardEmpty("Insufficient data for weekly trends.")
        return
    }
    MonitorCard {
        BreakdownRow(
            label = "Average Sleep",
            value = DateTimeUtils.formatMinutes(trends.averageSleepMinutes),
            note  = "Past ${trends.daysAnalyzed} days",
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        ConsistencyScoreSection(score = trends.consistencyScore)
    }
}

@Composable
private fun SleepRawDebugCard(signal: SleepSignal, isTracking: Boolean) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active", if (isTracking) "Yes" else "No")
        DebugRow("Confidence",      "${signal.confidence}%")
        DebugRow("Device Motion",   signal.deviceMotion.toString())
        DebugRow("Last update",     signal.timestamp.toLocalTime().toString().take(8))
    }
}