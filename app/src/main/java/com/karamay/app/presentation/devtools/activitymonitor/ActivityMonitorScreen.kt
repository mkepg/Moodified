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
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.DirectionsRun
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.R
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import com.karamay.app.domain.model.Arousal

@Composable
fun ActivityMonitorScreen(
    onBack: () -> Unit,
    viewModel: ActivityMonitorViewModel = hiltViewModel()
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
            val canAsk = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            viewModel.onPermissionDenied(canAskAgain = canAsk)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MilkWhite),
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
                            permissionLauncher.launch(
                                Manifest.permission.ACTIVITY_RECOGNITION
                            )
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
                    canAskAgain = denied.canAskAgain,
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

        item {
            SectionLabel("Live Signals")
            LiveSignalRow(signal = state.signal, isTracking = state.isTracking)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Activity Breakdown")
            ActivityBreakdownCard(signal = state.signal)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Arousal Estimate")
            ArousalEstimateCard(
                arousal = state.signal.intensity.toArousalEstimate()
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Intensity Gauge")
            IntensityGaugeCard(signal = state.signal)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            RawDebugCard(signal = state.signal, isTracking = state.isTracking)
        }

        if (!state.isTracking &&
            state.permission !is PermissionState.Denied &&
            state.hardwareError == null
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner()
            }
        }
    }
}

@Composable
private fun MonitorHeader(
    isTracking: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilkWhite)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint               = TextSecondary,
                    modifier           = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = isTracking) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ArousalHigh.copy(alpha = 0.14f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ArousalHigh.copy(alpha = pulseAlpha))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                                fontSize      = 10.sp
                            ),
                            color = DeepSage
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DeepSage.copy(alpha = 0.08f)
        ) {
            Text(
                text     = "DEV TOOLS",
                style    = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    fontSize      = 10.sp
                ),
                color    = DeepSage,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text  = "Activity Monitor",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Activity Recognition API & step cadence classification",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick  = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) SageDim else DeepSage,
                contentColor   = if (isTracking) TextPrimary else MilkWhite
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(
                imageVector        = if (isTracking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = if (isTracking) "Stop Tracking" else "Start Tracking",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.4.sp,
            fontWeight    = FontWeight.SemiBold,
            fontSize      = 10.sp
        ),
        color    = TextTertiary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun LiveSignalRow(signal: ActivitySignal, isTracking: Boolean) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            modifier  = Modifier.weight(1f),
            label     = "Steps",
            value     = if (signal.stepSensorAvailable) signal.steps.toString() else "—",
            subLabel  = if (!signal.stepSensorAvailable) "No sensor" else stepNote(signal.steps),
            accentColor = ValencePositive
        )
        StatTile(
            modifier  = Modifier.weight(1f),
            label     = "Intensity",
            value     = signal.intensity.icon(),
            subLabel  = signal.intensity.displayLabel(),
            accentColor = intensityColor(signal.intensity)
        )
        StatTile(
            modifier  = Modifier.weight(1f),
            label     = "Active",
            value     = "${signal.activeMinutes}m",
            subLabel  = if (signal.activeMinutes >= 30) "Goal reached ✓" else "${30 - signal.activeMinutes}m to goal",
            accentColor = ArousalMid
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label: String,
    value: Any,
    subLabel: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        color    = accentColor.copy(alpha = 0.10f)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState   = value,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "statValue"
            ) { v ->
                when (v) {
                    is String -> {
                        Text(
                            text  = v,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize   = 22.sp
                            ),
                            color = TextPrimary
                        )
                    }
                    is ImageVector -> {
                        Icon(
                            imageVector = v,
                            contentDescription = label,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Text(
                text      = subLabel,
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.8.sp,
                    fontSize      = 9.sp
                ),
                color     = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActivityBreakdownCard(signal: ActivitySignal) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(20.dp),
        color    = MilkDeep
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BreakdownRow(
                label = "Session steps",
                value = if (signal.stepSensorAvailable) "${signal.steps} steps" else "Sensor unavailable",
                note  = if (signal.stepSensorAvailable) stepProgressNote(signal.steps) else "Step counter not present on this device"
            )
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow(
                label = "Active minutes",
                value = "${signal.activeMinutes} min",
                note  = if (signal.activeMinutes >= 30) "Daily movement goal reached ✓"
                else "${30 - signal.activeMinutes} min remaining to 30-min goal"
            )
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            BreakdownRow(
                label = "Sedentary minutes",
                value = "${signal.sedentaryMinutes} min",
                note  = when {
                    signal.sedentaryMinutes >= 90 -> "Extended sit — consider a short walk"
                    signal.sedentaryMinutes >= 60 -> "Prolonged sitting detected"
                    else                          -> "Within healthy range"
                }
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, note: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = note,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text  = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}

@Composable
private fun ArousalEstimateCard(arousal: Arousal) {
    val (bgColor, label, description) = when (arousal) {
        Arousal.LOW -> Triple(
            ArousalLow,
            "Low Energy",
            "Minimal physical exertion. Activity is classified as resting, driving, or a casual walk (under 100 steps/min). Represents a low physical arousal baseline."
        )
        Arousal.MID -> Triple(
            ArousalMid,
            "Mid Energy",
            "Moderate physical exertion. Activity is classified as a brisk walk (over 100 steps/min) or cycling. Represents an elevated physical arousal baseline."
        )
        Arousal.HIGH -> Triple(
            ArousalHigh,
            "High Energy",
            "Vigorous physical exertion. Activity is classified as running or intense movement. Represents a high physical arousal baseline."
        )
    }

    val arousalIcon = when (arousal) {
        Arousal.LOW  -> R.drawable.ic_no_energy
        Arousal.MID  -> R.drawable.ic_mid_energy
        Arousal.HIGH -> R.drawable.ic_high_energy
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, bgColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = bgColor.copy(alpha = 0.10f)
    ) {
        Column {
            Row(
                modifier              = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(bgColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = arousalIcon),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = bgColor.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )

            Text(
                text = "Note: This estimate currently relies exclusively on physical activity tracking. Full inference (including sleep, phone usage, and manual mood entries) arrives in Phase 2.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondary,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun IntensityGaugeCard(signal: ActivitySignal) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text  = "Current Intensity",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )

            val levels = ActivityIntensity.entries
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                levels.forEach { level ->
                    val isActive = level == signal.intensity
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isActive) intensityColor(level)
                                else SageDim.copy(alpha = 0.35f)
                            )
                    )
                }
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActivityIntensity.entries.forEach { level ->
                    Text(
                        text     = level.displayLabel(),
                        style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color    = if (level == signal.intensity) TextPrimary else TextTertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SageSurface
            ) {
                Text(
                    text     = "Classified using Google Play Services Activity Recognition API (10s batched intervals, 50% confidence threshold). Car rides are filtered to Sedentary. Walking intensity scales to Moderate above 100 steps/min.",
                    style    = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun RawDebugCard(signal: ActivitySignal, isTracking: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DebugRow("Tracking active",     if (isTracking) "Yes" else "No")
            DebugRow("Step sensor",         if (signal.stepSensorAvailable) "Present" else "Absent")
            DebugRow("Google Activity API", if (signal.accelAvailable) "Connected" else "Failed")
            DebugRow("Committed intensity", signal.intensity.name)
            DebugRow("Arousal estimate",    signal.intensity.toArousalEstimate().name)
            DebugRow("Active (min)",        signal.activeMinutes.toString())
            DebugRow("Sedentary (min)",     signal.sedentaryMinutes.toString())
            DebugRow("Steps this session",  signal.steps.toString())
            DebugRow("Last update",         signal.timestamp.toLocalTime().withNano(0).toString())
        }
    }
}

@Composable
private fun DebugRow(key: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text  = key,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = TextSecondary
        )
    }
}

@Composable
private fun PermissionDeniedCard(
    canAskAgain: Boolean,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, ValenceNegative.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = ValenceNegative.copy(alpha = 0.07f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint               = TextSecondary,
                    modifier           = Modifier.size(18.dp)
                )
                Text(
                    text  = "Permission Required",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text  = if (canAskAgain)
                    "Activity recognition permission is needed to count steps and classify movement. Tap Start Tracking to request it."
                else
                    "Permission was permanently denied. Enable 'Physical activity' in app Settings to use this feature.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (!canAskAgain) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape   = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HardwareErrorCard(stepMissing: Boolean, accelMissing: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep
    ) {
        Row(
            modifier              = Modifier.padding(18.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.SensorsOff,
                contentDescription = null,
                tint               = TextTertiary,
                modifier           = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text  = "Sensor Unavailable",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                val missing = buildList {
                    if (stepMissing)  add("step counter")
                    if (accelMissing) add("Activity Recognition support")
                }.joinToString(" and ")
                Text(
                    text  = "This device is missing the $missing. Tracking is unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SensorAvailabilityBanner(stepAvailable: Boolean, accelAvailable: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = SageSurface
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("⚠️", fontSize = 16.sp)
            val missing = buildList {
                if (!stepAvailable)  add("Step counter absent — step data unavailable.")
                if (!accelAvailable) add("Activity API unavailable — intensity classification degraded.")
            }.joinToString(" ")
            Text(
                text  = missing,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun IdleBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = SageSurface
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.DirectionsRun,
                contentDescription = null,
                tint               = SageLight,
                modifier           = Modifier.size(22.dp)
            )
            Text(
                text  = "Tap Start Tracking to begin reading sensor data. Data shown reflects the last active session.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

private fun intensityColor(intensity: ActivityIntensity): Color = when (intensity) {
    ActivityIntensity.SEDENTARY -> ArousalLow
    ActivityIntensity.LIGHT     -> ArousalMid
    ActivityIntensity.MODERATE  -> ValencePositive
    ActivityIntensity.VIGOROUS  -> ArousalHigh
}

private fun stepNote(steps: Int): String = when {
    steps >= 10_000 -> "10k goal ✓"
    steps >= 5_000  -> "${10_000 - steps} to 10k"
    else            -> "Keep moving"
}

private fun stepProgressNote(steps: Int): String = when {
    steps >= 10_000 -> "10 000-step goal reached ✓"
    steps >= 8_000  -> "${10_000 - steps} steps to 10k goal"
    steps >= 5_000  -> "Good progress — keep going"
    else            -> "Session just started"
}