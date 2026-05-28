package com.moodified.app.presentation.devtools

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.*

data class WeeklyBarEntry(
    val label:   String,
    val value:   Int,
    val isToday: Boolean,
)

fun consistencyColor(score: Int): Color = when {
    score >= 70 -> ValencePositive
    score >= 40 -> ArousalLow
    else        -> ArousalHigh
}

private fun consistencyNote(score: Int): String = when {
    score >= 70 -> "Highly consistent this week."
    score >= 40 -> "Some variation — try to keep a daily routine."
    else        -> "High variability detected. Consider building a schedule."
}

@Composable
fun MonitorHeader(
    title:              String,
    subtitle:           String,
    isTracking:         Boolean,
    liveIndicatorColor: Color = ValenceNeutral,
    eyebrow:            String = "DEV TOOLS",
    onBack:             () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "monitor_pulse")
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
                    color = liveIndicatorColor.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(liveIndicatorColor.copy(alpha = pulseAlpha))
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
                text     = eyebrow,
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
        Text(title,    style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay), color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MonitorStatTile(
    modifier:    Modifier,
    label:       String,
    value:       Any,
    subLabel:    String,
    accentColor: Color,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        color    = accentColor.copy(alpha = 0.10f),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedContent(
                targetState    = value,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label          = "statTileValue",
            ) { v ->
                when (v) {
                    is String      -> Text(
                        text  = v,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                        ),
                        color = TextPrimary,
                    )
                    is ImageVector -> Icon(
                        imageVector        = v,
                        contentDescription = label,
                        tint               = accentColor,
                        modifier           = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text      = subLabel,
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color     = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.8.sp,
                    fontSize      = 9.sp,
                ),
                color     = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MonitorCard(
    modifier:        Modifier = Modifier,
    verticalSpacing: Int      = 16,
    content:         @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier        = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
            content             = content,
        )
    }
}

@Composable
fun MonitorCardEmpty(
    message:  String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier        = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodySmall,
            color     = TextSecondary,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
    }
}

@Composable
fun SectionLabel(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.4.sp,
            fontWeight    = FontWeight.SemiBold,
            fontSize      = 10.sp,
        ),
        color    = TextTertiary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun BreakdownRow(label: String, value: String, note: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,  color = TextSecondary)
            Text(note,  style = MaterialTheme.typography.labelSmall,   color = TextTertiary)
        }
        Text(
            text  = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
    }
}

@Composable
fun DebugRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key,   style = MaterialTheme.typography.bodySmall,                                 color = TextTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = TextSecondary)
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text     = text.uppercase(),
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontSize      = 9.sp,
            ),
            color    = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun MonitorWeeklyBars(
    entries:      List<WeeklyBarEntry>,
    maxBarHeight: Int = 64,
) {
    val safeMax = entries.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        entries.forEach { entry ->
            val fraction = (entry.value.toFloat() / safeMax).coerceIn(0f, 1f)

            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((maxBarHeight * fraction).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (entry.isToday) ValencePositive.copy(alpha = 0.75f)
                            else              ArousalMid.copy(alpha = 0.4f),
                        )
                )
                Text(
                    text  = entry.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (entry.isToday) TextPrimary else TextTertiary,
                )
            }
        }
    }
}

@Composable
fun ConsistencyScoreSection(
    score:    Int,
    sublabel: String = "Duration variance score",
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text("Consistency", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(sublabel,      style = MaterialTheme.typography.labelSmall,  color = TextTertiary)
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.TrendingUp, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                AnimatedContent(
                    targetState    = "$score/100",
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label          = "consistencyScore",
                ) { s ->
                    Text(
                        text  = s,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = consistencyColor(score),
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
                    .fillMaxWidth(fraction = (score / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(consistencyColor(score))
            )
        }
        Text(
            text  = consistencyNote(score),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
    }
}

@Composable
fun PermissionDeniedCard(
    title:          String = "Permission Required",
    body:           String,
    canAskAgain:    Boolean,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, ValenceNegative.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        shape    = RoundedCornerShape(20.dp),
        color    = ValenceNegative.copy(alpha = 0.07f),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (!canAskAgain) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick  = onOpenSettings,
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open Settings") }
            }
        }
    }
}

@Composable
fun IdleBanner(text: String = "Tap Start Tracking to begin monitoring signals.") {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape    = RoundedCornerShape(16.dp),
        color    = SageSurface,
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.padding(16.dp),
        )
    }
}