package com.karamay.app.presentation.devtools.sleepmonitor

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepStatus
import com.karamay.app.domain.model.SleepTrends
import com.karamay.app.presentation.components.SharedStatTile
import com.karamay.app.presentation.devtools.PermissionState

@Composable
fun SleepMonitorScreen(
    onBack: () -> Unit,
    viewModel: SleepMonitorViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort */ }

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
            val activity = context as? androidx.activity.ComponentActivity
            // Fix C: pass canRequestAgain (was canAskAgain)
            val canRequestAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.ACTIVITY_RECOGNITION
                )
            } ?: false
            viewModel.onPermissionDenied(canRequestAgain = canRequestAgain)
        }
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            SleepMonitorHeader(
                isTracking = state.isTracking,
                onBack     = onBack,
                onToggle   = {
                    when {
                        state.isTracking -> viewModel.stopTracking()
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                state.permission !is PermissionState.Granted -> {
                            viewModel.onPermissionRequested()
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                        else -> viewModel.startTracking()
                    }
                }
            )
        }

        if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                SleepPermissionDeniedCard(
                    // Fix C: read canRequestAgain (renamed field)
                    canRequestAgain = denied.canRequestAgain,
                    onRequestAgain  = {
                        viewModel.onPermissionRequested()
                        permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    },
                    onOpenSettings  = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        item { SectionLabel("Live Signal");         LiveSleepSignalRow(state.liveSignal) }
        state.latestSummary?.let { s ->
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Today's Sleep");  SleepSummaryCard(s) }
        }
        state.weeklyTrends?.let { t ->
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Weekly Trends"); WeeklyTrendsCard(t) }
        }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Raw Debug"); SleepRawDebugCard(state.liveSignal, state.isTracking) }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun SleepMonitorHeader(isTracking: Boolean, onBack: () -> Unit, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sleep_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue  = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label         = "pulseAlpha"
    )
    Column(modifier = Modifier.fillMaxWidth().background(MilkWhite).statusBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = isTracking) {
                Surface(shape = RoundedCornerShape(20.dp), color = ValenceNeutral.copy(alpha = 0.14f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ValenceNeutral.copy(alpha = pulseAlpha)))
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, fontSize = 10.sp), color = DeepSage)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
            Text("DEV TOOLS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, fontSize = 10.sp), color = DeepSage, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Sleep Monitor", style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay), color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Live API telemetry & nightly segmentation", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onToggle, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isTracking) SageDim else DeepSage, contentColor = if (isTracking) TextPrimary else MilkWhite),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(if (isTracking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isTracking) "Stop Tracking" else "Start Tracking", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Cards ────────────────────────────────────────────────────────────────────

@Composable
private fun SleepPermissionDeniedCard(canRequestAgain: Boolean, onRequestAgain: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).border(1.dp, ErrorRed.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp), color = ErrorRed.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                Text("Activity Recognition permission required", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                // Fix C: use canRequestAgain (renamed)
                if (canRequestAgain) "Sleep tracking requires motion access to detect sleep segments."
                else "Permission was permanently denied. Enable it in system settings.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (canRequestAgain) {
                    TextButton(onClick = onRequestAgain) { Text("Grant permission", color = DeepSage) }
                } else {
                    TextButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(14.dp), tint = DeepSage)
                        Spacer(Modifier.width(4.dp))
                        Text("Open settings", color = DeepSage)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSleepSignalRow(signal: SleepSignal) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SharedStatTile(Modifier.weight(1f), "Status",  signal.status.displayLabel(), if (signal.confidence > 0) "${signal.confidence}% confidence" else "No data", statusColor(signal.status))
        SharedStatTile(Modifier.weight(1f), "Motion",  signal.deviceMotion.toString(), "device motion", ArousalMid)
        SharedStatTile(Modifier.weight(1f), "Light",   "${signal.ambientLight.toInt()} lx", "ambient", ValencePositive)
    }
}

@Composable
private fun SleepSummaryCard(summary: DailySleepSummary) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (summary.isEstimated) Text("Estimated (no segments yet)", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            DebugRow("Total sleep",  formatMinutes(summary.totalSleepMinutes))
            DebugRow("Time in bed",  formatMinutes(summary.timeInBedMinutes))
            DebugRow("Awakenings",   summary.awakenings.toString())
            summary.sleepOnsetMinutes?.let { DebugRow("Sleep onset", formatMinutes(it) + " after 6 PM") }
        }
    }
}

@Composable
private fun WeeklyTrendsCard(trends: SleepTrends) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DebugRow("Days analysed", trends.daysAnalyzed.toString())
            DebugRow("Avg sleep",     formatMinutes(trends.averageSleepMinutes))
            DebugRow("Sleep debt",    formatMinutes(trends.totalSleepDebtMinutes))
            DebugRow("Consistency",   "${trends.consistencyScore}/100")
        }
    }
}

@Composable
private fun SleepRawDebugCard(signal: SleepSignal, isTracking: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DebugRow("Tracking",     if (isTracking) "Active" else "Stopped")
            DebugRow("Status",       signal.status.displayLabel())
            DebugRow("Confidence",   "${signal.confidence}%")
            DebugRow("Last updated", signal.timestamp.toLocalTime().toString().take(8))
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, fontSize = 10.sp), color = TextTertiary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
}

@Composable
private fun DebugRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key,   style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = TextSecondary)
    }
}

private fun statusColor(status: SleepStatus): Color = when (status) {
    SleepStatus.ASLEEP  -> ValenceNeutral
    SleepStatus.AWAKE   -> ValencePositive
    SleepStatus.UNKNOWN -> ArousalLow
}

private fun formatMinutes(totalMinutes: Int): String {
    val hrs  = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}
