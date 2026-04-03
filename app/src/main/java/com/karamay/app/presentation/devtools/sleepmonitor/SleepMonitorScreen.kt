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
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    viewModel: SleepMonitorViewModel = hiltViewModel()
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
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
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            SleepHeader(
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
                    canAskAgain    = denied.canAskAgain,
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

        item {
            SectionLabel("Live Signals (Updates ~10m)")
            LiveSleepSignalRow(signal = state.liveSignal, isTracking = state.isTracking)
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionLabel("Last Night's Summary")
            SleepSummaryCard(summary = state.latestSummary)
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionLabel("7-Day Trends & Debt")
            SleepTrendsCard(trends = state.weeklyTrends)
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionLabel("Raw Debug")
            RawSleepDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking && state.permission !is PermissionState.Denied) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepHeader(
    isTracking: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilkWhite)   // matches MonitorHeader
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
                // Sleep LIVE badge uses ValenceNeutral (moon-tone) to stay
                // domain-distinct from the green activity badge.
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ValenceNeutral.copy(alpha = 0.14f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                                fontSize      = 10.sp
                            ),
                            color = ValenceNeutral
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
            text  = "Sleep Monitor",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Play Services Sleep API & Ambient Sensors",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick   = onToggle,
            modifier  = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape     = RoundedCornerShape(14.dp),
            colors    = ButtonDefaults.buttonColors(
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

// ─────────────────────────────────────────────────────────────────────────────
// Section label
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Live signal tiles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveSleepSignalRow(signal: SleepSignal, isTracking: Boolean) {
    val statusIcon  = if (signal.status == SleepStatus.ASLEEP) Icons.Rounded.DarkMode
    else Icons.Rounded.LightMode
    val statusColor = if (signal.status == SleepStatus.ASLEEP) ValenceNeutral
    else ValencePositive
    // Confidence displayed as "awake confidence" when AWAKE, "asleep confidence" when ASLEEP.
    val displayConfidence = if (signal.status == SleepStatus.ASLEEP) signal.confidence
    else 100 - signal.confidence

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            modifier    = Modifier.weight(1f),
            label       = "State",
            value       = statusIcon,
            subLabel    = if (isTracking) signal.status.displayLabel() else "—",
            accentColor = statusColor
        )
        StatTile(
            modifier    = Modifier.weight(1f),
            label       = "Confidence",
            value       = if (isTracking) "${displayConfidence}%" else "—",
            subLabel    = if (displayConfidence > 80) "High" else "Low",
            accentColor = ArousalMid
        )
        StatTile(
            modifier    = Modifier.weight(1f),
            label       = "Light (Lux)",
            value       = if (isTracking) signal.ambientLight.toInt().toString() else "—",
            subLabel    = if (signal.ambientLight < 10) "Dark" else "Bright",
            accentColor = ValencePositive
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat tile
// ─────────────────────────────────────────────────────────────────────────────

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
                targetState    = value,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label          = "statValue"
            ) { v ->
                when (v) {
                    is String -> Text(
                        text  = v,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp
                        ),
                        color = TextPrimary
                    )
                    is ImageVector -> Icon(
                        imageVector        = v,
                        contentDescription = label,
                        tint               = accentColor,
                        modifier           = Modifier.size(28.dp)
                    )
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

// ─────────────────────────────────────────────────────────────────────────────
// Sleep summary & trends
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepSummaryCard(summary: DailySleepSummary?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep
    ) {
        if (summary != null) {
            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BreakdownRow(
                    label = "Total Sleep Time",
                    value = formatMinutes(summary.totalSleepMinutes),
                    note  = "Finalised asleep duration"
                )
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow(
                    label = "Time in Bed",
                    value = formatMinutes(summary.timeInBedMinutes),
                    note  = "Total duration of sleep segment"
                )
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow(
                    label = "Fragmentation",
                    value = "${summary.awakenings} times",
                    note  = "Number of mid-segment awakenings detected"
                )
            }
        } else {
            Box(
                modifier         = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = "No sleep segments finalised for today yet. " +
                            "Data usually arrives shortly after waking up.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SleepTrendsCard(trends: SleepTrends?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep
    ) {
        if (trends != null) {
            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BreakdownRow(
                    label = "Average Sleep",
                    value = formatMinutes(trends.averageSleepMinutes),
                    note  = "Across ${trends.daysAnalyzed} recorded days"
                )
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow(
                    label = "Total Sleep Debt",
                    value = formatMinutes(trends.totalSleepDebtMinutes),
                    note  = "Cumulative deficit against 8h baseline"
                )
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                BreakdownRow(
                    label = "Consistency Score",
                    value = "${trends.consistencyScore}/100",
                    note  = "Calculated via sleep duration variance"
                )
            }
        } else {
            Box(
                modifier         = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = "Insufficient data to calculate weekly trends.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Raw debug
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RawSleepDebugCard(signal: SleepSignal, isTracking: Boolean) {
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
            DebugRow("Tracking active", if (isTracking) "Yes" else "No")
            DebugRow("Inferred State",  signal.status.name)
            DebugRow("Confidence Score", "${signal.confidence} / 100")
            DebugRow("Device Motion",   signal.deviceMotion.toString())
            DebugRow("Ambient Light",   "${signal.ambientLight} lux")
            DebugRow("Last Telemetry",  signal.timestamp.toLocalTime().withNano(0).toString())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// State cards
// ─────────────────────────────────────────────────────────────────────────────

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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text  = if (canAskAgain)
                    "Activity recognition permission is needed to detect sleep and wake states. Tap Start Tracking to request it."
                else
                    "Permission was permanently denied. Enable 'Physical activity' in app Settings to use sleep tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (!canAskAgain) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick  = onOpenSettings,
                    shape    = RoundedCornerShape(10.dp),
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
                imageVector        = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint               = SageLight,
                modifier           = Modifier.size(22.dp)
            )
            Text(
                text  = "Tap Start Tracking to begin monitoring sleep signals. " +
                        "Data shown reflects the last active session.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared row helpers
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMinutes(totalMinutes: Int): String {
    val hrs  = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}