package com.karamay.app.presentation.devtools.sleepmonitor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.karamay.app.presentation.devtools.PermissionState

@Composable
fun SleepMonitorScreen(
    onBack: () -> Unit,
    viewModel: SleepMonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
            viewModel.startTracking()
        } else {
            val activity = context as? androidx.activity.ComponentActivity
            val canRequestAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACTIVITY_RECOGNITION)
            } ?: false
            viewModel.onPermissionDenied(canRequestAgain = canRequestAgain)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            SleepMonitorHeader(
                isTracking = state.isTracking,
                hasData = state.liveSignal.hasActiveSession,
                onBack = onBack,
                onToggle = {
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
                onReset = { viewModel.resetSession() }
            )
        }
        if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                PermissionDeniedCard(canAskAgain = denied.canRequestAgain, onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) })
                })
                Spacer(Modifier.height(8.dp))
            }
        }
        item { SectionLabel("Live Signals"); LiveSleepSignalRow(signal = state.liveSignal, isTracking = state.isTracking) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Last Night's Summary"); SleepSummaryCard(state.latestSummary) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("7-Day Trends & Debt"); WeeklyTrendsCard(state.weeklyTrends) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Raw Debug"); RawSleepDebugCard(state.liveSignal, state.isTracking) }
        if (!state.isTracking && state.permission !is PermissionState.Denied && !state.liveSignal.hasActiveSession) {
            item { Spacer(Modifier.height(16.dp)); IdleBanner() }
        }
    }
}

@Composable
private fun SleepMonitorHeader(
    isTracking: Boolean,
    hasData: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "sleep_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
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
        Spacer(Modifier.height(10.dp))
        Text("Sleep Monitor", style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay), color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Live API telemetry & nightly segmentation", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(20.dp))

        if (!isTracking && hasData) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onToggle, modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSage, contentColor = MilkWhite),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Resume", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = if (isTracking) TextPrimary else MilkWhite)
                }
                OutlinedButton(
                    onClick = onReset, modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
                }
            }
        } else {
            Button(
                onClick = onToggle, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isTracking) SageDim else DeepSage, contentColor = if (isTracking) TextPrimary else MilkWhite),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(if (isTracking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isTracking) "Pause Tracking" else "Start Tracking", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = if (isTracking) TextPrimary else MilkWhite)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LiveSleepSignalRow(signal: SleepSignal, isTracking: Boolean) {
    val statusIcon = if (signal.status == SleepStatus.ASLEEP) Icons.Rounded.DarkMode else Icons.Rounded.LightMode
    val statusColor = if (signal.status == SleepStatus.ASLEEP) ValenceNeutral else ValencePositive
    val displayConfidence = if (signal.status == SleepStatus.ASLEEP) signal.confidence else 100 - signal.confidence
    val motionLabel = when {
        !signal.hasActiveSession -> "—"
        signal.deviceMotion == 0 -> "Still"
        signal.deviceMotion == 1 -> "Moving"
        else -> signal.deviceMotion.toString()
    }
    val motionSubLabel = when {
        !signal.hasActiveSession -> "No data"
        signal.deviceMotion == 0 -> "No movement"
        else -> "Movement detected"
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Modifier.weight(1f), "State", statusIcon, if (signal.hasActiveSession) signal.status.displayLabel() else "—", statusColor)
        StatTile(Modifier.weight(1f), "Confidence", if (signal.hasActiveSession) "${displayConfidence}%" else "—", if (displayConfidence > 80) "High" else "Low", ArousalMid)
        StatTile(Modifier.weight(1f), "Motion", motionLabel, motionSubLabel, ValencePositive)
    }
}

@Composable
private fun SleepSummaryCard(summary: DailySleepSummary?) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        if (summary != null) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BreakdownRow("Total Sleep Time", formatMinutes(summary.totalSleepMinutes), "Finalised duration")
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow("Time in Bed", formatMinutes(summary.timeInBedMinutes), "Total duration of segment")
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow("Fragmentation", "${summary.awakenings} times", "Awakenings detected")
            }
        } else {
            Text("No segments finalised for today yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun WeeklyTrendsCard(trends: SleepTrends?) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        if (trends != null) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BreakdownRow("Average Sleep", formatMinutes(trends.averageSleepMinutes), "Past ${trends.daysAnalyzed} days")
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow("Consistency", "${trends.consistencyScore}/100", "Duration variance score")
            }
        } else {
            Text("Insufficient data for weekly trends.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun RawSleepDebugCard(signal: SleepSignal, isTracking: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DebugRow("Tracking active", if (isTracking) "Yes" else "No")
            DebugRow("Confidence", "${signal.confidence}%")
            DebugRow("Device Motion", signal.deviceMotion.toString())
            DebugRow("Last update", signal.timestamp.toLocalTime().toString().take(8))
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: Any, subLabel: String, accentColor: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = accentColor.copy(alpha = 0.10f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(targetState = value, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "statValue") { v ->
                when (v) {
                    is String -> Text(text = v, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = TextPrimary)
                    is ImageVector -> Icon(imageVector = v, contentDescription = label, tint = accentColor, modifier = Modifier.size(22.dp))
                }
            }
            Text(text = subLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextSecondary, textAlign = TextAlign.Center)
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontSize = 9.sp), color = TextTertiary, textAlign = TextAlign.Center)
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
        Text(key, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = TextSecondary)
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, note: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(note, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
    }
}

@Composable
private fun PermissionDeniedCard(canAskAgain: Boolean, onOpenSettings: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).border(1.dp, ValenceNegative.copy(alpha = 0.35f), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), color = ValenceNegative.copy(alpha = 0.07f)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Text("Permission Required", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (canAskAgain) "Activity recognition permission is needed. Tap Start Tracking to request it." else "Permission denied. Enable 'Physical activity' in app Settings.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (!canAskAgain) {
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("Open Settings") }
            }
        }
    }
}

@Composable
private fun IdleBanner() {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp), color = SageSurface) {
        Text("Tap Start Tracking to begin monitoring signals.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(16.dp))
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val hrs = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}