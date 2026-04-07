package com.karamay.app.presentation.devtools.activitymonitor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.DirectionsCarFilled
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.core.theme.ArousalHigh
import com.karamay.app.core.theme.ArousalMid
import com.karamay.app.core.theme.MilkDeep
import com.karamay.app.core.theme.MilkWhite
import com.karamay.app.core.theme.SageDim
import com.karamay.app.core.theme.SageSurface
import com.karamay.app.core.theme.TextPrimary
import com.karamay.app.core.theme.TextSecondary
import com.karamay.app.core.theme.TextTertiary
import com.karamay.app.core.theme.ValencePositive
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.presentation.devtools.BreakdownRow
import com.karamay.app.presentation.devtools.formatMinutes
import com.karamay.app.presentation.devtools.DebugRow
import com.karamay.app.presentation.devtools.IdleBanner
import com.karamay.app.presentation.devtools.MonitorCard
import com.karamay.app.presentation.devtools.MonitorCardEmpty
import com.karamay.app.presentation.devtools.MonitorHeader
import com.karamay.app.presentation.devtools.MonitorStatTile
import com.karamay.app.presentation.devtools.MonitorWeeklyBars
import com.karamay.app.presentation.devtools.PartialDayBadge
import com.karamay.app.presentation.devtools.PermissionDeniedCard
import com.karamay.app.presentation.devtools.PermissionState
import com.karamay.app.presentation.devtools.SectionLabel
import com.karamay.app.presentation.devtools.WeeklyBarEntry
import java.time.LocalDate

@Composable
fun ActivityMonitorScreen(
    onBack:    () -> Unit,
    viewModel: ActivityMonitorViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Secondary notification permission — result intentionally ignored
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
                title              = "Activity Monitor",
                subtitle           = "Daily aggregation · step cadence · intensity classification",
                isTracking         = state.isTracking,
                hasData            = state.liveSignal.hasActiveSession,
                liveIndicatorColor = ArousalHigh,
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
                onReset = { viewModel.resetSession() },
            )
        }

        if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                PermissionDeniedCard(
                    title          = "Permission Required",
                    body           = if (denied.canRequestAgain)
                        "Activity recognition permission is needed. Tap Start Tracking to request it."
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

        state.hardwareError?.let {
            item {
                HardwareErrorCard(
                    stepMissing  = !state.liveSignal.stepSensorAvailable,
                    accelMissing = !state.liveSignal.accelAvailable,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.isTracking &&
            (!state.liveSignal.stepSensorAvailable || !state.liveSignal.accelAvailable)
        ) {
            item {
                SensorAvailabilityBanner(
                    stepAvailable  = state.liveSignal.stepSensorAvailable,
                    accelAvailable = state.liveSignal.accelAvailable,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            SectionLabel("Live Signals")
            LiveActivitySignalRow(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Today's Summary")
            ActivityDailySummaryCard(summary = state.todaySummary)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("7-Day Overview")
            ActivityWeeklyOverviewCard(summaries = state.weeklySummaries)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Activity Breakdown")
            ActivityBreakdownCard(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Intensity Gauge")
            IntensityGaugeCard(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            ActivityRawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking &&
            state.permission !is PermissionState.Denied &&
            state.hardwareError == null &&
            !state.liveSignal.hasActiveSession
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner()
            }
        }
    }
}

// ─── Live signal row ──────────────────────────────────────────────────────────

@Composable
private fun LiveActivitySignalRow(signal: ActivitySignal) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Steps",
            value       = signal.steps.toString(),
            subLabel    = stepNote(signal.steps),
            accentColor = ValencePositive,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Intensity",
            value       = signal.intensity.displayLabel(),
            subLabel    = "${signal.instantCadenceSpm} spm",
            accentColor = intensityColor(signal.intensity),
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Active",
            value       = formatMinutes(signal.activeMinutes),
            subLabel    = "Moving time",
            accentColor = ArousalMid,
        )
    }
}

// ─── Daily summary card ───────────────────────────────────────────────────────

@Composable
private fun ActivityDailySummaryCard(summary: ActivityDailySummary?) {
    if (summary == null) {
        MonitorCardEmpty("No activity recorded yet today.")
        return
    }
    MonitorCard {
        if (summary.isPartialDay) PartialDayBadge()
        BreakdownRow("Total Steps",    summary.totalSteps.toString(),          stepNote(summary.totalSteps))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Active Time",    formatMinutes(summary.activeMinutes),   "Movement detected")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Sedentary Time", formatMinutes(summary.sedentaryMinutes),"Still / in vehicle")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Peak Intensity", summary.peakIntensity.displayLabel(),   "Highest energy level today")
    }
}

// ─── Weekly overview card ─────────────────────────────────────────────────────

@Composable
private fun ActivityWeeklyOverviewCard(summaries: List<ActivityDailySummary>) {
    if (summaries.isEmpty()) {
        MonitorCardEmpty("Insufficient data for a weekly overview.")
        return
    }
    val avgSteps  = summaries.sumOf { it.totalSteps }   / summaries.size
    val avgActive = summaries.sumOf { it.activeMinutes } / summaries.size
    val bestDay   = summaries.maxByOrNull { it.totalSteps }
    val today     = LocalDate.now().toString()

    MonitorCard {
        BreakdownRow("Avg Daily Steps",  avgSteps.toString(),       "Past ${summaries.size} days")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Avg Active Time",  formatMinutes(avgActive),  "Per tracked day")
        if (bestDay != null) {
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow("Best Day", "${bestDay.totalSteps} steps", bestDay.date)
        }
        Spacer(Modifier.height(4.dp))
        MonitorWeeklyBars(
            entries = summaries.map { day ->
                WeeklyBarEntry(
                    label   = day.date.takeLast(5).replace("-", "/"),
                    value   = day.totalSteps,
                    isToday = day.date == today,
                )
            },
        )
    }
}

// ─── Activity breakdown card ──────────────────────────────────────────────────

@Composable
private fun ActivityBreakdownCard(signal: ActivitySignal) {
    MonitorCard {
        BreakdownRow("Steps (live)",         signal.steps.toString(),                stepProgressNote(signal.steps))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Active Time (live)",   formatMinutes(signal.activeMinutes),    "Movement detected")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Sedentary Time (live)", formatMinutes(signal.sedentaryMinutes),"Still / in vehicle")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Intensity",            signal.intensity.displayLabel(),        "Current classification")
    }
}

// ─── Intensity gauge card ─────────────────────────────────────────────────────

@Composable
private fun IntensityGaugeCard(signal: ActivitySignal) {
    MonitorCard(verticalSpacing = 14) {
        Text(
            text  = "Current Intensity",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityIntensity.entries.forEach { level ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (level == signal.intensity) intensityColor(level)
                            else SageDim.copy(alpha = 0.35f)
                        )
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityIntensity.entries.forEach { level ->
                Text(
                    text     = level.displayLabel(),
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color    = if (level == signal.intensity) TextPrimary else TextTertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─── Raw debug card ───────────────────────────────────────────────────────────

@Composable
private fun ActivityRawDebugCard(signal: ActivitySignal, isTracking: Boolean) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active", if (isTracking) "Yes" else "No")
        DebugRow("Steps (session)", signal.steps.toString())
        DebugRow("Active (min)",    signal.activeMinutes.toString())
        DebugRow("Sedentary (min)", signal.sedentaryMinutes.toString())
        DebugRow("Last updated",    signal.timestamp.toLocalTime().toString().take(8))
    }
}

// ─── Hardware error & sensor banners ─────────────────────────────────────────

@Composable
private fun HardwareErrorCard(stepMissing: Boolean, accelMissing: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(20.dp),
        color    = MilkDeep,
    ) {
        Row(
            modifier              = Modifier.padding(18.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.SensorsOff, null, tint = TextTertiary, modifier = Modifier.size(22.dp))
            Column {
                Text(
                    text  = "Sensor Unavailable",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                val missing = buildList {
                    if (stepMissing)  add("step counter")
                    if (accelMissing) add("Activity API")
                }.joinToString(" and ")
                Text(
                    text  = "This device is missing $missing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SensorAvailabilityBanner(stepAvailable: Boolean, accelAvailable: Boolean) {
    val missing = buildList {
        if (!stepAvailable)  add("Step counter absent.")
        if (!accelAvailable) add("Activity API unavailable.")
    }.joinToString(" ")
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(14.dp),
        color    = SageSurface,
    ) {
        Text(
            text     = "⚠️ $missing",
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.padding(14.dp),
        )
    }
}

// ─── Local helpers ────────────────────────────────────────────────────────────

private fun intensityColor(intensity: ActivityIntensity): Color = when (intensity) {
    ActivityIntensity.SEDENTARY  -> Color(0xFFD3D3D3)
    ActivityIntensity.IN_VEHICLE -> Color(0xFF9E9E9E)
    ActivityIntensity.LIGHT      -> ArousalMid
    ActivityIntensity.MODERATE   -> ValencePositive
    ActivityIntensity.VIGOROUS   -> ArousalHigh
}

private fun ActivityIntensity.icon(): ImageVector = when (this) {
    ActivityIntensity.SEDENTARY  -> Icons.Rounded.AirlineSeatReclineNormal
    ActivityIntensity.IN_VEHICLE -> Icons.Rounded.DirectionsCarFilled
    ActivityIntensity.LIGHT      -> Icons.AutoMirrored.Rounded.DirectionsWalk
    ActivityIntensity.MODERATE   -> Icons.AutoMirrored.Rounded.DirectionsRun
    ActivityIntensity.VIGOROUS   -> Icons.Rounded.LocalFireDepartment
}

private fun stepNote(steps: Int): String         = if (steps >= 10_000) "10k goal ✓" else "Keep moving"
private fun stepProgressNote(steps: Int): String = if (steps >= 10_000) "Goal reached ✓" else "Session started"
