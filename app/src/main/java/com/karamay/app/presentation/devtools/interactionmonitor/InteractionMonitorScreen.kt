package com.karamay.app.presentation.devtools.interactionmonitor

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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MobileOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karamay.app.core.theme.ArousalHigh
import com.karamay.app.core.theme.ArousalLow
import com.karamay.app.core.theme.ArousalMid
import com.karamay.app.core.theme.DeepSage
import com.karamay.app.core.theme.DmSerifDisplay
import com.karamay.app.core.theme.ErrorRed
import com.karamay.app.core.theme.MilkDeep
import com.karamay.app.core.theme.MilkWhite
import com.karamay.app.core.theme.SageDim
import com.karamay.app.core.theme.SageSurface
import com.karamay.app.core.theme.TextPrimary
import com.karamay.app.core.theme.TextSecondary
import com.karamay.app.core.theme.TextTertiary
import com.karamay.app.core.theme.ValenceNegative
import com.karamay.app.core.theme.ValenceNeutral
import com.karamay.app.core.theme.ValencePositive
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.presentation.devtools.PermissionState

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
            viewModel.onNotificationPermissionGranted()
        } else {
            val activity      = context as? androidx.activity.ComponentActivity
            val canAskAgain   = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.POST_NOTIFICATIONS
                )
            } ?: false
            viewModel.onNotificationPermissionDenied(canAskAgain)
        }
    }

    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            InteractionMonitorHeader(
                isTracking = state.isTracking,
                // FIX: Check actual metric accumulations instead of nullability
                hasData    = state.liveSignal.isTracking ||
                        state.liveSignal.totalScreenTimeTodayMs > 0L ||
                        state.liveSignal.unlocksToday > 0,
                onBack     = onBack,
                onToggle   = {
                    if (state.isTracking) {
                        viewModel.stopTracking()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            state.permission !is PermissionState.Granted
                        ) {
                            viewModel.onNotificationPermissionRequested()
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.startTracking()
                    }
                },
                onReset = { viewModel.resetSession() }
            )
        }

        if (state.permission is PermissionState.Denied) {
            item {
                val denied = state.permission as PermissionState.Denied
                NotificationPermissionDeniedCard(
                    canAskAgain    = denied.canRequestAgain,
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
            SectionLabel("Live Signals")
            LiveInteractionSignalRow(signal = state.liveSignal)
        }

        if (state.isTracking) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Current Session")
                SessionCard(signal = state.liveSignal)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Today's Summary")
            DailySummaryCard(summary = state.todaySummary)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("7-Day Trends")
            WeeklyTrendsCard(trends = state.weeklyTrends)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Weekly Screen Time")
            WeeklyBarChart(summaries = state.weeklySummaries)
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Raw Debug")
            RawDebugCard(signal = state.liveSignal, isTracking = state.isTracking)
        }

        if (!state.isTracking &&
            state.permission !is PermissionState.Denied &&
            !state.liveSignal.isTracking
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                IdleBanner()
            }
        }
    }
}

@Composable
private fun InteractionMonitorHeader(
    isTracking: Boolean,
    hasData:    Boolean,
    onBack:     () -> Unit,
    onToggle:   () -> Unit,
    onReset:    () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.25f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = LinearEasing),
            RepeatMode.Reverse
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
                    color = ValenceNeutral.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
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
            text  = "Interaction Monitor",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Screen time · unlock frequency · late-night usage",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        if (!isTracking && hasData) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick   = onToggle,
                    modifier  = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor = DeepSage,
                        contentColor   = MilkWhite
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Resume",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = MilkWhite
                    )
                }

                OutlinedButton(
                    onClick  = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                    )
                }
            }
        } else {
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
                    if (isTracking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (isTracking) "Pause Tracking" else "Start Tracking",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    color = if (isTracking) TextPrimary else MilkWhite
                )
            }
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            label    = "Screen Time",
            value    = formatMs(signal.totalScreenTimeTodayMs),
            sub      = "today",
            color    = ValencePositive
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label    = "Unlocks",
            value    = signal.unlocksToday.toString(),
            sub      = "today",
            color    = ValenceNeutral
        )
        StatTile(
            modifier    = Modifier.weight(1f),
            label       = "Screen",
            value       = if (signal.isScreenOn) Icons.Rounded.PhoneAndroid
            else Icons.Rounded.MobileOff,
            valueSub    = if (signal.isScreenOn) "On" else "Off",
            color       = if (signal.isScreenOn) ArousalMid else SageDim
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label:    String,
    value:    String,
    sub:      String,
    color:    Color
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        color    = color.copy(alpha = 0.10f)
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
                label          = "stat"
            ) { v ->
                Text(
                    v,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    ),
                    color = TextPrimary
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.SemiBold,
                    fontSize      = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color = TextSecondary
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label:    String,
    value:    ImageVector,
    valueSub: String,
    color:    Color
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        color    = color.copy(alpha = 0.10f)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector        = value,
                contentDescription = label,
                tint               = color,
                modifier           = Modifier.size(22.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.SemiBold,
                    fontSize      = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                valueSub,
                style     = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SessionCard(signal: InteractionSignal) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(20.dp),
        color    = MilkDeep
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint               = TextTertiary,
                    modifier           = Modifier.size(16.dp)
                )
                Text(
                    text  = "Current session",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextSecondary
                )
            }
            AnimatedContent(
                targetState    = formatMs(signal.currentSessionDurationMs),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label          = "sessionDuration"
            ) { duration ->
                Text(
                    text  = duration,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
            Text(
                text  = if (signal.isScreenOn) "Screen is on — session in progress"
                else "Screen is off — session ended",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun DailySummaryCard(summary: InteractionDailySummary?) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (summary != null) {
            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (summary.isPartialDay) {
                    PartialDayBadge()
                }

                BreakdownRow(
                    label = "Screen Time",
                    value = formatMinutes(summary.totalScreenTimeMinutes),
                    note  = screenTimeNote(summary.totalScreenTimeMinutes)
                )

                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

                BreakdownRow(
                    label = "Unlocks",
                    value = summary.unlocks.toString(),
                    note  = unlockNote(summary.unlocks)
                )

                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

                BreakdownRow(
                    label = "Day type",
                    value = if (summary.isPartialDay) "Partial" else "Full",
                    note  = if (summary.isPartialDay) "Still in progress" else "Day complete"
                )
            }
        } else {
            Text(
                text      = "No interaction data recorded yet today.",
                style     = MaterialTheme.typography.bodySmall,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeeklyTrendsCard(trends: InteractionTrends?) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (trends != null) {
            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BreakdownRow(
                    label = "Avg Screen Time",
                    value = formatMinutes(trends.averageScreenTimeMinutes),
                    note  = "Past ${trends.daysAnalyzed} days"
                )

                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

                BreakdownRow(
                    label = "Avg Daily Unlocks",
                    value = trends.averageUnlocks.toString(),
                    note  = "Per tracked day"
                )

                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text  = "Consistency",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text  = "Duration variance score",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Rounded.TrendingUp,
                                contentDescription = null,
                                tint               = TextTertiary,
                                modifier           = Modifier.size(14.dp)
                            )
                            AnimatedContent(
                                targetState    = "${trends.consistencyScore}/100",
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label          = "consistencyScore"
                            ) { score ->
                                Text(
                                    text  = score,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = consistencyColor(trends.consistencyScore)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SageDim.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (trends.consistencyScore / 100f).coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(consistencyColor(trends.consistencyScore))
                        )
                    }
                    Text(
                        text  = consistencyNote(trends.consistencyScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            Text(
                text      = "Insufficient data for weekly trends.",
                style     = MaterialTheme.typography.bodySmall,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeeklyBarChart(summaries: List<InteractionDailySummary>) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (summaries.isEmpty()) {
            Text(
                text      = "Insufficient data for a weekly overview.",
                style     = MaterialTheme.typography.bodySmall,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
            )
        } else {
            val safeMax = summaries.maxOf { it.totalScreenTimeMinutes }.coerceAtLeast(1)

            Column(
                modifier            = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val avgScreenTime = summaries.sumOf { it.totalScreenTimeMinutes } / summaries.size
                val bestDay       = summaries.maxByOrNull { it.totalScreenTimeMinutes }

                BreakdownRow(
                    label = "Avg Daily Screen Time",
                    value = formatMinutes(avgScreenTime),
                    note  = "Past ${summaries.size} days"
                )

                if (bestDay != null) {
                    HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                    BreakdownRow(
                        label = "Highest Day",
                        value = formatMinutes(bestDay.totalScreenTimeMinutes),
                        note  = bestDay.date
                    )
                }

                Spacer(Modifier.height(4.dp))
                WeeklyScreenTimeBars(summaries = summaries, safeMax = safeMax)
            }
        }
    }
}

@Composable
private fun WeeklyScreenTimeBars(
    summaries: List<InteractionDailySummary>,
    safeMax:   Int
) {
    val today = java.time.LocalDate.now().toString()

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.Bottom
    ) {
        summaries.forEach { day ->
            val fraction = (day.totalScreenTimeMinutes.toFloat() / safeMax).coerceIn(0f, 1f)
            val isToday  = day.date == today

            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((64 * fraction).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (isToday) ValencePositive.copy(alpha = 0.75f)
                            else         ArousalMid.copy(alpha = 0.4f)
                        )
                )
                Text(
                    text  = day.date.takeLast(5).replace("-", "/"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (isToday) TextPrimary else TextTertiary
                )
            }
        }
    }
}

@Composable
private fun RawDebugCard(signal: InteractionSignal, isTracking: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(20.dp),
        color    = MilkDeep
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DebugRow("Tracking active",      if (isTracking) "Yes" else "No")
            DebugRow("Screen on",            if (signal.isScreenOn) "Yes" else "No")
            DebugRow("Unlocks today",        signal.unlocksToday.toString())
            DebugRow("Total screen time",    formatMs(signal.totalScreenTimeTodayMs))
            DebugRow("Session duration",     formatMs(signal.currentSessionDurationMs))
            DebugRow("Last event",           signal.lastEventType?.displayLabel() ?: "—")
            DebugRow("Last updated",         signal.timestamp.toLocalTime().toString().take(8))
        }
    }
}

@Composable
private fun NotificationPermissionDeniedCard(
    canAskAgain:    Boolean,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, ValenceNegative.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        shape    = RoundedCornerShape(20.dp),
        color    = ValenceNegative.copy(alpha = 0.07f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Text(
                    "Notification Permission",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text  = if (canAskAgain)
                    "Notification permission lets you see when background tracking is active."
                else
                    "Permission denied. Enable notifications for Karamay in app Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (!canAskAgain) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick  = onOpenSettings,
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Settings") }
            }
        }
    }
}

@Composable
private fun PartialDayBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ArousalMid.copy(alpha = 0.12f)
    ) {
        Text(
            text     = "PARTIAL DAY",
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontSize      = 9.sp
            ),
            color    = ArousalMid,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun IdleBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(16.dp),
        color    = SageSurface
    ) {
        Text(
            text     = "Tap Start Tracking to begin monitoring screen interactions.",
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.padding(16.dp)
        )
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
private fun DebugRow(key: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key,   style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = TextSecondary)
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(note,  style = MaterialTheme.typography.labelSmall,  color = TextTertiary)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0m"
    val totalMinutes = ms / 60_000L
    val hours        = totalMinutes / 60
    val minutes      = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val hrs  = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}

private fun screenTimeNote(minutes: Int): String = when {
    minutes == 0   -> "No usage recorded"
    minutes < 60   -> "Under an hour"
    minutes < 120  -> "Light usage"
    minutes < 240  -> "Moderate usage"
    else           -> "Heavy usage"
}

private fun unlockNote(unlocks: Int): String = when {
    unlocks == 0   -> "No unlocks recorded"
    unlocks < 20   -> "Normal usage"
    unlocks < 50   -> "Frequent checks"
    else           -> "Very high frequency"
}

private fun consistencyColor(score: Int): Color = when {
    score >= 70 -> ValencePositive
    score >= 40 -> ArousalLow
    else        -> ArousalHigh
}

private fun consistencyNote(score: Int): String = when {
    score >= 70 -> "Your screen time is highly consistent this week."
    score >= 40 -> "Some variation in screen time — try to keep a routine."
    else        -> "High variability detected. Consider setting daily limits."
}