// app/src/main/java/com/moodified/app/presentation/devtools/activitymonitor/ActivityMonitorScreen.kt
package com.moodified.app.presentation.devtools.activitymonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.DirectionsCarFilled
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.presentation.devtools.*
import java.time.LocalDate

@Composable
fun ActivityMonitorScreen(
    onBack:    () -> Unit,
    viewModel: ActivityMonitorViewModel = hiltViewModel(),
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
                title              = "Activity Monitor",
                subtitle           = "Step cadence and intensity data",
                isTracking         = state.isTracking,
                liveIndicatorColor = ArousalHigh,
                onBack             = onBack
            )
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
            Spacer(Modifier.height(20.dp))
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
            SectionLabel("Raw Debug")
            ActivityRawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
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
            value       = DateTimeUtils.formatMinutes(signal.activeMinutes),
            subLabel    = "Moving time",
            accentColor = ArousalMid,
        )
    }
}

@Composable
private fun ActivityDailySummaryCard(summary: ActivityDailySummary?) {
    if (summary == null) {
        MonitorCardEmpty("No activity recorded yet today.")
        return
    }
    MonitorCard {
        if (summary.isPartialDay) {
            StatusBadge(text = "PARTIAL DAY", color = ArousalMid)
        }
        BreakdownRow("Total Steps",    summary.totalSteps.toString(),                   stepNote(summary.totalSteps))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Active Time",    DateTimeUtils.formatMinutes(summary.activeMinutes),    "Movement detected")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Sedentary Time", DateTimeUtils.formatMinutes(summary.sedentaryMinutes), "Still / in vehicle")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Peak Intensity", summary.peakIntensity.displayLabel(),            "Highest energy level today")
    }
}

@Composable
private fun ActivityWeeklyOverviewCard(summaries: List<ActivityDailySummary>) {
    if (summaries.isEmpty()) {
        MonitorCardEmpty("Insufficient data for a weekly overview.")
        return
    }
    val avgSteps  = summaries.sumOf { it.totalSteps }    / summaries.size
    val avgActive = summaries.sumOf { it.activeMinutes } / summaries.size
    val bestDay   = summaries.maxByOrNull { it.totalSteps }
    val today     = LocalDate.now().toString()

    MonitorCard {
        BreakdownRow("Avg Daily Steps", avgSteps.toString(),                    "Past ${summaries.size} days")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Avg Active Time", DateTimeUtils.formatMinutes(avgActive), "Per tracked day")

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

@Composable
private fun ActivityBreakdownCard(signal: ActivitySignal) {
    MonitorCard {
        BreakdownRow("Steps (live)",          signal.steps.toString(),                         stepProgressNote(signal.steps))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Active Time (live)",    DateTimeUtils.formatMinutes(signal.activeMinutes),    "Movement detected")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Sedentary Time (live)", DateTimeUtils.formatMinutes(signal.sedentaryMinutes), "Still / in vehicle")
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        BreakdownRow("Intensity",             signal.intensity.displayLabel(),                 "Current classification")
    }
}

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

@Composable
private fun SensorAvailabilityBanner(stepAvailable: Boolean, accelAvailable: Boolean) {
    val missing = buildList {
        if (!stepAvailable)  add("Step counter absent.")
        if (!accelAvailable) add("Activity API unavailable.")
    }.joinToString(" ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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

private fun intensityColor(intensity: ActivityIntensity): Color = when (intensity) {
    ActivityIntensity.SEDENTARY  -> Color(0xFFD3D3D3)
    ActivityIntensity.IN_VEHICLE -> Color(0xFF9E9E9E)
    ActivityIntensity.LIGHT      -> ArousalMid
    ActivityIntensity.MODERATE   -> ValencePositive
    ActivityIntensity.VIGOROUS   -> ArousalHigh
}

private fun stepNote(steps: Int): String         = if (steps >= 10_000) "10k goal ✓" else "Keep moving"
private fun stepProgressNote(steps: Int): String = if (steps >= 10_000) "Goal reached ✓" else "Session started"