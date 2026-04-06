package com.karamay.app.presentation.devtools.activitymonitor

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.R
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.model.Arousal
import com.karamay.app.presentation.devtools.PermissionState
import kotlin.math.roundToInt

@Composable
fun ActivityMonitorScreen(
    onBack: () -> Unit,
    viewModel: ActivityMonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        modifier = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            MonitorHeader(
                isTracking = state.isTracking,
                hasData = state.signal.hasActiveSession,
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
                PermissionDeniedCard(
                    canAskAgain = denied.canRequestAgain,
                    onOpenSettings = {
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
        state.hardwareError?.let {
            item {
                HardwareErrorCard(
                    stepMissing = !state.signal.stepSensorAvailable,
                    accelMissing = !state.signal.accelAvailable
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (state.isTracking && (!state.signal.stepSensorAvailable || !state.signal.accelAvailable)) {
            item {
                SensorAvailabilityBanner(
                    stepAvailable = state.signal.stepSensorAvailable,
                    accelAvailable = state.signal.accelAvailable
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item { SectionLabel("Live Signals"); LiveSignalRow(signal = state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Activity Breakdown"); ActivityBreakdownCard(state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Energy Estimate"); ArousalEstimateCard(state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Intensity Gauge"); IntensityGaugeCard(state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Raw Debug"); RawDebugCard(state.signal, state.isTracking) }
        if (!state.isTracking && state.permission !is PermissionState.Denied && state.hardwareError == null && !state.signal.hasActiveSession) {
            item { Spacer(Modifier.height(16.dp)); IdleBanner() }
        }
    }
}

@Composable
private fun MonitorHeader(
    isTracking: Boolean,
    hasData: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Column(
        modifier = Modifier.fillMaxWidth().background(MilkWhite).statusBarsPadding().padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = isTracking) {
                Surface(shape = RoundedCornerShape(20.dp), color = ArousalHigh.copy(alpha = 0.14f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ArousalHigh.copy(alpha = pulseAlpha)))
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
        Text("Activity Monitor", style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay), color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Activity Recognition API & step cadence classification", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
private fun LiveSignalRow(signal: ActivitySignal) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Modifier.weight(1f), "Steps",
            if (signal.stepSensorAvailable) signal.steps.toString() else "—",
            if (!signal.stepSensorAvailable) "No sensor" else stepNote(signal.steps),
            ValencePositive)
        StatTile(Modifier.weight(1f), "Intensity", signal.intensity.icon(), signal.intensity.displayLabel(), intensityColor(signal.intensity))
        StatTile(Modifier.weight(1f), "Active", "${signal.activeMinutes}m",
            if (signal.activeMinutes >= 30) "Goal reached ✓" else "${30 - signal.activeMinutes}m to goal",
            ArousalMid)
    }
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: Any, subLabel: String, accentColor: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = accentColor.copy(alpha = 0.10f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(targetState = value, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "statValue") { v ->
                when (v) {
                    is String -> Text(text = v, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp), color = TextPrimary)
                    is ImageVector -> Icon(imageVector = v, contentDescription = label, tint = accentColor, modifier = Modifier.size(28.dp))
                }
            }
            Text(text = subLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextSecondary, textAlign = TextAlign.Center)
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontSize = 9.sp), color = TextTertiary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActivityBreakdownCard(signal: ActivitySignal) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BreakdownRow("Session steps", if (signal.stepSensorAvailable) "${signal.steps} steps" else "Sensor unavailable", if (signal.stepSensorAvailable) stepProgressNote(signal.steps) else "Step counter not present")
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow("Active minutes", "${signal.activeMinutes} min", if (signal.activeMinutes >= 30) "Daily movement goal reached ✓" else "${30 - signal.activeMinutes} min remaining")
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow("Sedentary minutes", "${signal.sedentaryMinutes} min", if (signal.sedentaryMinutes >= 90) "Extended sit — consider a walk" else "Within healthy range")
        }
    }
}

@Composable
private fun ArousalEstimateCard(signal: ActivitySignal) {
    val arousal = signal.toArousalEstimate()
    val totalMinutes = (signal.activeMinutes + signal.sedentaryMinutes).coerceAtLeast(1)
    val activeRatio = signal.activeMinutes.toFloat() / totalMinutes
    val stepCadence = if (signal.activeMinutes > 0 && signal.stepSensorAvailable) signal.steps.toFloat() / signal.activeMinutes else 0f

    val (bgColor, label, description) = when (arousal) {
        Arousal.LOW -> Triple(ArousalLow, "Low Energy", "Minimal sustained effort. Physical arousal baseline is low.")
        Arousal.MID -> Triple(ArousalMid, "Mid Energy", "Moderate sustained activity elevating baseline above resting.")
        Arousal.HIGH -> Triple(ArousalHigh, "High Energy", "Vigorous or prolonged activity indicating high arousal.")
    }

    val arousalIcon = when (arousal) {
        Arousal.LOW -> R.drawable.ic_no_energy
        Arousal.MID -> R.drawable.ic_mid_energy
        Arousal.HIGH -> R.drawable.ic_high_energy
    }

    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).border(1.dp, bgColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), color = bgColor.copy(alpha = 0.10f)) {
        Column {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(bgColor.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = arousalIcon), contentDescription = null, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = bgColor.copy(alpha = 0.20f), thickness = 0.5.dp)
            Column(modifier = Modifier.padding(18.dp, 14.dp, 18.dp, 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CONTRIBUTING FACTORS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold), color = TextTertiary)
                ArousalFactorRow("Active ratio", "${(activeRatio * 100).roundToInt()}%", activeRatio.coerceIn(0f, 1f), bgColor, if (activeRatio >= 0.40f) "high" else "low")
                ArousalFactorRow("Step cadence", if (signal.stepSensorAvailable) "${stepCadence.roundToInt()} spm" else "n/a", (stepCadence / 130f).coerceIn(0f, 1f), bgColor, if (stepCadence >= 100f) "brisk" else "light")
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ArousalFactorRow(label: String, value: String, progress: Float, barColor: Color, annotation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(annotation, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextTertiary)
                Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(barColor.copy(alpha = 0.15f))) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(barColor.copy(alpha = 0.65f)))
        }
    }
}

@Composable
private fun IntensityGaugeCard(signal: ActivitySignal) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Current Intensity", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActivityIntensity.entries.forEach { level ->
                    Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (level == signal.intensity) intensityColor(level) else SageDim.copy(alpha = 0.35f)))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActivityIntensity.entries.forEach { level ->
                    Text(level.displayLabel(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = if (level == signal.intensity) TextPrimary else TextTertiary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RawDebugCard(signal: ActivitySignal, isTracking: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DebugRow("Tracking active", if (isTracking) "Yes" else "No")
            DebugRow("Steps this session", signal.steps.toString())
            DebugRow("Active (min)", signal.activeMinutes.toString())
            DebugRow("Last updated", signal.timestamp.toLocalTime().toString().take(8))
        }
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
private fun HardwareErrorCard(stepMissing: Boolean, accelMissing: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.SensorsOff, null, tint = TextTertiary, modifier = Modifier.size(22.dp))
            Column {
                Text("Sensor Unavailable", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                val missing = buildList { if (stepMissing) add("step counter"); if (accelMissing) add("Activity API") }.joinToString(" and ")
                Text("This device is missing $missing.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SensorAvailabilityBanner(stepAvailable: Boolean, accelAvailable: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp), color = SageSurface) {
        val missing = buildList { if (!stepAvailable) add("Step counter absent."); if (!accelAvailable) add("Activity API unavailable.") }.joinToString(" ")
        Text("⚠️ $missing", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun IdleBanner() {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp), color = SageSurface) {
        Text("Tap Start Tracking to begin monitoring signals.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(16.dp))
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

private fun ActivityIntensity.icon(): ImageVector = when (this) {
    ActivityIntensity.SEDENTARY -> Icons.Rounded.AirlineSeatReclineNormal
    ActivityIntensity.IN_VEHICLE -> Icons.Rounded.DirectionsCarFilled
    ActivityIntensity.LIGHT -> Icons.AutoMirrored.Rounded.DirectionsWalk
    ActivityIntensity.MODERATE -> Icons.AutoMirrored.Rounded.DirectionsRun
    ActivityIntensity.VIGOROUS -> Icons.Rounded.LocalFireDepartment
}

private fun intensityColor(intensity: ActivityIntensity): Color = when (intensity) {
    ActivityIntensity.SEDENTARY -> Color(0xFFD3D3D3)
    ActivityIntensity.LIGHT -> ArousalMid
    ActivityIntensity.MODERATE -> ValencePositive
    ActivityIntensity.VIGOROUS -> ArousalHigh
    ActivityIntensity.IN_VEHICLE -> Color(0xFF9E9E9E)
}

private fun stepNote(steps: Int): String = if (steps >= 10000) "10k goal ✓" else "Keep moving"
private fun stepProgressNote(steps: Int): String = if (steps >= 10000) "Goal reached ✓" else "Session started"