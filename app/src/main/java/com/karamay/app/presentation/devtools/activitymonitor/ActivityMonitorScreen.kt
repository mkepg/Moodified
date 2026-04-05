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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.DirectionsCarFilled
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SensorsOff
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
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.model.Arousal
import com.karamay.app.presentation.components.SharedStatTile
import com.karamay.app.presentation.devtools.PermissionState

@Composable
fun ActivityMonitorScreen(
    onBack: () -> Unit,
    viewModel: ActivityMonitorViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {  }

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
        modifier       = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            MonitorHeader(
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
                PermissionDeniedCard(
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
        state.hardwareError?.let {
            item {
                HardwareErrorCard(
                    stepMissing  = !state.signal.stepSensorAvailable,
                    accelMissing = !state.signal.accelAvailable
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (state.isTracking &&
            (!state.signal.stepSensorAvailable || !state.signal.accelAvailable)
        ) {
            item {
                SensorAvailabilityBanner(
                    stepAvailable  = state.signal.stepSensorAvailable,
                    accelAvailable = state.signal.accelAvailable
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item { SectionLabel("Live Signals");  LiveSignalRow(signal = state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Activity Breakdown"); ActivityBreakdownCard(state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Energy Estimate");    ArousalEstimateCard(state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Intensity Gauge");    IntensityGaugeCard(state.signal) }
        item { Spacer(Modifier.height(8.dp)); SectionLabel("Raw Debug");          RawDebugCard(state.signal, state.isTracking) }

        if (!state.isTracking &&
            state.permission !is PermissionState.Denied &&
            state.hardwareError == null
        ) {
            item { Spacer(Modifier.height(16.dp)); IdleBanner() }
        }
    }
}

@Composable
private fun MonitorHeader(isTracking: Boolean, onBack: () -> Unit, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue  = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label         = "pulseAlpha"
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

@Composable
private fun PermissionDeniedCard(canRequestAgain: Boolean, onRequestAgain: () -> Unit, onOpenSettings: () -> Unit) {
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
                if (canRequestAgain) "Grant access to enable movement tracking and energy estimation."
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
private fun HardwareErrorCard(stepMissing: Boolean, accelMissing: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = SageSurface) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.SensorsOff, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
            Column {
                Text("Sensor unavailable", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                val missing = listOfNotNull(if (stepMissing) "step counter" else null, if (accelMissing) "accelerometer" else null).joinToString(" and ")
                Text("Missing: $missing.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SensorAvailabilityBanner(stepAvailable: Boolean, accelAvailable: Boolean) {
    val missing = listOfNotNull(if (!stepAvailable) "Step counter" else null, if (!accelAvailable) "Accelerometer" else null).joinToString(", ")
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(12.dp), color = ValenceNegative.copy(alpha = 0.10f)) {
        Text("⚠ $missing unavailable — partial data only", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun LiveSignalRow(signal: ActivitySignal) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SharedStatTile(Modifier.weight(1f), "Steps",
            if (signal.stepSensorAvailable) signal.steps.toString() else "—",
            if (!signal.stepSensorAvailable) "No sensor" else "${signal.steps} today",
            ValencePositive)
        SharedStatTile(Modifier.weight(1f), "Intensity", signal.intensity.icon(), signal.intensity.displayLabel(), intensityColor(signal.intensity))
        SharedStatTile(Modifier.weight(1f), "Active", "${signal.activeMinutes}m",
            if (signal.activeMinutes >= 30) "Goal reached ✓" else "${30 - signal.activeMinutes}m to goal",
            ArousalMid)
    }
}

@Composable
private fun ActivityBreakdownCard(signal: ActivitySignal) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DebugRow("Active minutes",    "${signal.activeMinutes} min")
            DebugRow("Sedentary minutes", "${signal.sedentaryMinutes} min")
            DebugRow("Current intensity", signal.intensity.displayLabel())
        }
    }
}

@Composable
private fun ArousalEstimateCard(signal: ActivitySignal) {
    val arousal = signal.toArousalEstimate()
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Estimated arousal", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Surface(shape = RoundedCornerShape(8.dp), color = arousalColor(arousal).copy(alpha = 0.15f)) {
                Text(arousal.displayLabel(), style = MaterialTheme.typography.labelLarge, color = arousalColor(arousal), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun IntensityGaugeCard(signal: ActivitySignal) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Intensity level", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            LinearProgressIndicator(
                progress   = { intensityFraction(signal.intensity) },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = intensityColor(signal.intensity),
                trackColor = SageSurface,
                strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Text(signal.intensity.displayLabel(), style = MaterialTheme.typography.labelSmall, color = intensityColor(signal.intensity))
        }
    }
}

@Composable
private fun RawDebugCard(signal: ActivitySignal, isTracking: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = MilkDeep) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DebugRow("Tracking",     if (isTracking) "Active" else "Stopped")
            DebugRow("Step sensor",  if (signal.stepSensorAvailable) "Available" else "Missing")
            DebugRow("Accel sensor", if (signal.accelAvailable) "Available" else "Missing")
            DebugRow("Last updated", signal.timestamp.toLocalTime().toString().take(8))
        }
    }
}

@Composable
private fun IdleBanner() {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text("Start tracking to see live signals", style = MaterialTheme.typography.bodyMedium, color = TextTertiary, textAlign = TextAlign.Center)
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

// UI-LEVEL EXTENSION: Keep Compose imports out of the Domain model
private fun ActivityIntensity.icon(): ImageVector = when (this) {
    ActivityIntensity.SEDENTARY  -> Icons.Rounded.AirlineSeatReclineNormal
    ActivityIntensity.IN_VEHICLE -> Icons.Rounded.DirectionsCarFilled
    ActivityIntensity.LIGHT      -> Icons.AutoMirrored.Rounded.DirectionsWalk
    ActivityIntensity.MODERATE   -> Icons.AutoMirrored.Rounded.DirectionsRun
    ActivityIntensity.VIGOROUS   -> Icons.Rounded.LocalFireDepartment
}

private fun intensityColor(intensity: ActivityIntensity): Color = when (intensity) {
    ActivityIntensity.SEDENTARY,
    ActivityIntensity.IN_VEHICLE -> ArousalLow
    ActivityIntensity.LIGHT      -> ArousalMid
    ActivityIntensity.MODERATE   -> ValencePositive
    ActivityIntensity.VIGOROUS   -> ArousalHigh
}

private fun intensityFraction(intensity: ActivityIntensity): Float = when (intensity) {
    ActivityIntensity.SEDENTARY  -> 0.05f
    ActivityIntensity.IN_VEHICLE -> 0.10f
    ActivityIntensity.LIGHT      -> 0.35f
    ActivityIntensity.MODERATE   -> 0.65f
    ActivityIntensity.VIGOROUS   -> 1.00f
}

private fun arousalColor(arousal: Arousal): Color = when (arousal) {
    Arousal.LOW  -> ArousalLow
    Arousal.MID  -> ArousalMid
    Arousal.HIGH -> ArousalHigh
}