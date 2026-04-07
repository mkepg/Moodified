package com.karamay.app.presentation.devtools.interactionmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.karamay.app.core.theme.ArousalLow
import com.karamay.app.core.theme.DeepSage
import com.karamay.app.core.theme.DmSerifDisplay
import com.karamay.app.core.theme.MilkWhite
import com.karamay.app.core.theme.SageDim
import com.karamay.app.core.theme.TextPrimary
import com.karamay.app.core.theme.TextSecondary
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
import com.karamay.app.presentation.devtools.MonitorStatTile
import com.karamay.app.presentation.devtools.MonitorWeeklyBars
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsagePermission by remember { mutableStateOf(viewModel.hasUsagePermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = viewModel.hasUsagePermission()
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
            InteractionHeader(
                isTracking = state.isTracking,
                onBack     = onBack,
                onToggle   = {
                    if (state.isTracking) {
                        viewModel.stopTracking()
                    } else {
                        if (!hasUsagePermission) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                            viewModel.onPermissionRequested()
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            viewModel.startTracking()
                        }
                    }
                }
            )
        }

        if (!hasUsagePermission) {
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

        if (!state.isTracking && hasUsagePermission && state.permission !is PermissionState.Denied) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner("Tap Start Tracking to begin monitoring screen interactions.")
            }
        }
    }
}

@Composable
private fun InteractionHeader(
    isTracking: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "interaction_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label         = "pulseAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilkWhite)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint               = TextSecondary,
                    modifier           = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = isTracking) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ValenceNeutral.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ValenceNeutral.copy(alpha = pulseAlpha))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                                fontSize      = 10.sp,
                            ),
                            color = DeepSage,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
            Text(
                text     = "DEV TOOLS",
                style    = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    fontSize      = 10.sp,
                ),
                color    = DeepSage,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text  = "Interaction Monitor",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Screen time · late-night usage",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick   = onToggle,
            modifier  = Modifier.fillMaxWidth().height(52.dp),
            shape     = RoundedCornerShape(14.dp),
            colors    = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) SageDim else DeepSage,
                contentColor   = if (isTracking) TextPrimary else MilkWhite,
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp),
        ) {
            Icon(
                imageVector        = if (isTracking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = if (isTracking) "Stop Tracking" else "Start Tracking",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = if (isTracking) TextPrimary else MilkWhite
            )
        }
        Spacer(Modifier.height(24.dp))
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
            label       = "Unlocks",
            value       = signal.unlockCount.toString(),
            subLabel    = "Today",
            accentColor = ValencePositive,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Screen Time",
            value       = formatMs(signal.totalScreenTimeTodayMs),
            subLabel    = "Today",
            accentColor = ArousalLow,
        )
        MonitorStatTile(
            modifier    = Modifier.weight(1f),
            label       = "Late-Night",
            value       = formatMs(signal.lateNightScreenTimeTodayMs),
            subLabel    = "00:00–05:00",
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
            label = "Unlocks",
            value = summary.unlockCount.toString(),
            note  = "Total authentications"
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
        DebugRow("Total screen time", formatMs(signal.totalScreenTimeTodayMs))
        DebugRow("Late night (today)",formatMs(signal.lateNightScreenTimeTodayMs))
        DebugRow("Session duration",  formatMs(signal.currentSessionDurationMs))
        DebugRow("Total unlocks",     signal.unlockCount.toString())
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