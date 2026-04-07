package com.karamay.app.presentation.devtools.interactionmonitor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MobileOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.core.theme.ArousalMid
import com.karamay.app.core.theme.MilkWhite
import com.karamay.app.core.theme.SageDim
import com.karamay.app.core.theme.TextPrimary
import com.karamay.app.core.theme.TextSecondary
import com.karamay.app.core.theme.TextTertiary
import com.karamay.app.core.theme.ValenceNeutral
import com.karamay.app.core.theme.ValencePositive
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.presentation.devtools.BreakdownRow
import com.karamay.app.presentation.devtools.ConsistencyScoreSection
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
import com.karamay.app.presentation.devtools.formatMs
import com.karamay.app.presentation.devtools.formatMinutes
import java.time.LocalDate

@Composable
fun InteractionMonitorScreen(
    onBack:    () -> Unit,
    viewModel: InteractionMonitorViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            val activity        = context as? androidx.activity.ComponentActivity
            val canAskAgain     = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.POST_NOTIFICATIONS
                )
            } ?: false
            viewModel.onPermissionDenied(canAskAgain)
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
            val hasData = state.liveSignal.isTracking ||
                    state.liveSignal.totalScreenTimeTodayMs > 0L ||
                    state.liveSignal.lateNightScreenTimeTodayMs > 0L

            MonitorHeader(
                title              = "Interaction Monitor",
                subtitle           = "Screen time · late-night usage",
                isTracking         = state.isTracking,
                hasData            = hasData,
                liveIndicatorColor = ValenceNeutral,
                onBack             = onBack,
                onToggle           = {
                    if (state.isTracking) {
                        viewModel.stopTracking()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            state.permission !is PermissionState.Granted
                        ) {
                            viewModel.onPermissionRequested()
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.startTracking()
                    }
                },
                onReset = { viewModel.resetSession() },
            )
        }

        if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                PermissionDeniedCard(
                    title          = "Notification Permission",
                    body           = if (denied.canRequestAgain)
                        "Notification permission lets you see when background tracking is active."
                    else
                        "Permission denied. Enable notifications for Karamay in app Settings.",
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
            LiveInteractionSignalRow(signal = state.liveSignal)
        }

        item {
            Spacer(Modifier.height(8.dp))
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

        if (!state.isTracking &&
            state.permission !is PermissionState.Denied &&
            !state.liveSignal.isTracking
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner("Tap Start Tracking to begin monitoring screen interactions.")
            }
        }
    }
}

@Composable
private fun LiveInteractionSignalRow(signal: InteractionSignal) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Screen",
            value       = if (signal.isScreenOn) Icons.Rounded.PhoneAndroid else Icons.Rounded.MobileOff,
            subLabel    = if (signal.isScreenOn) "On" else "Off",
            accentColor = if (signal.isScreenOn) ArousalMid else SageDim,
        )

        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Current Session",
            value       = formatMs(signal.currentSessionDurationMs),
            subLabel    = if (signal.isScreenOn) "In progress" else "Ended",
            accentColor = ValenceNeutral,
        )

        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Last Event",
            value       = signal.lastEventType?.displayLabel()?.replace("Device ", "") ?: "—",
            subLabel    = "Trigger",
            accentColor = SageDim,
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
        if (summary.isPartialDay) PartialDayBadge()

        BreakdownRow(
            label = "Screen Time",
            value = formatMinutes(summary.totalScreenTimeMinutes),
            note  = screenTimeNote(summary.totalScreenTimeMinutes),
        )

        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            label = "Late Night Usage",
            value = formatMinutes(summary.lateNightUsageMinutes),
            note  = lateNightNote(summary.lateNightUsageMinutes),
        )

        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            label = "Day type",
            value = if (summary.isPartialDay) "Partial" else "Full",
            note  = if (summary.isPartialDay) "Still in progress" else "Day complete",
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
            value = formatMinutes(trends.averageScreenTimeMinutes),
            note  = "Past ${trends.daysAnalyzed} days",
        )

        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

        BreakdownRow(
            label = "Avg Late Night",
            value = formatMinutes(trends.averageLateNightMinutes),
            note  = "00:00–05:00 per day",
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
    val bestDay       = summaries.maxByOrNull { it.totalScreenTimeMinutes }
    val today         = LocalDate.now().toString()

    MonitorCard {
        BreakdownRow(
            label = "Avg Daily Screen Time",
            value = formatMinutes(avgScreenTime),
            note  = "Past ${summaries.size} days",
        )

        if (bestDay != null) {
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

            BreakdownRow(
                label = "Highest Day",
                value = formatMinutes(bestDay.totalScreenTimeMinutes),
                note  = bestDay.date,
            )
        }

        Spacer(Modifier.height(4.dp))

        MonitorWeeklyBars(
            entries = summaries.map { day ->
                WeeklyBarEntry(
                    label   = day.date.takeLast(5).replace("-", "/"),
                    value   = day.totalScreenTimeMinutes,
                    isToday = day.date == today,
                )
            },
        )
    }
}

@Composable
private fun InteractionRawDebugCard(signal: InteractionSignal, isTracking: Boolean) {
    MonitorCard(verticalSpacing = 10) {
        DebugRow("Tracking active",   if (isTracking) "Yes" else "No")
        DebugRow("Screen on",         if (signal.isScreenOn) "Yes" else "No")
        DebugRow("Total screen time", formatMs(signal.totalScreenTimeTodayMs))
        DebugRow("Late night (today)",formatMs(signal.lateNightScreenTimeTodayMs))
        DebugRow("Session duration",  formatMs(signal.currentSessionDurationMs))
        DebugRow("Last event",        signal.lastEventType?.displayLabel() ?: "—")
        DebugRow("Last updated",      signal.timestamp.toLocalTime().toString().take(8))
    }
}

private fun screenTimeNote(minutes: Int): String = when {
    minutes == 0  -> "No usage recorded"
    minutes < 60  -> "Under an hour"
    minutes < 120 -> "Light usage"
    minutes < 240 -> "Moderate usage"
    else          -> "Heavy usage"
}

private fun lateNightNote(minutes: Int): String = when {
    minutes == 0  -> "No late-night usage"
    minutes < 15  -> "Light late-night use"
    minutes < 60  -> "Moderate late-night use"
    else          -> "High late-night use"
}