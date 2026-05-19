package com.moodified.app.presentation.insight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.moodified.app.R

@Composable
fun InsightScreen(
    viewModel: InsightViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState    = state.isLoading,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label          = "insightRoot"
    ) { loading ->
        if (loading) {
            InsightLoadingScreen()
        } else {
            InsightContentScreen(state = state)
        }
    }
}

@Composable
private fun InsightLoadingScreen() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color       = DeepSage,
            strokeWidth = 2.dp,
            modifier    = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun InsightContentScreen(state: InsightUiState) {
    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item { InsightHeader(state = state) }

        state.todayInferredMood?.let { mood ->
            item {
                Spacer(Modifier.height(8.dp))
                TodayMoodCard(mood = mood)
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Today's Rhythm")
            Spacer(Modifier.height(12.dp))
            DailyPassiveTimeline(events = state.todayTimeline)
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Your mood this week")
        }

        if (state.domainReadiness.mood.isReady && state.moodChartPoints.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                MoodLineChart(points = state.moodChartPoints)
            }

            if (state.moodStability != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    MoodStabilityCard(stability = state.moodStability!!)
                }
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                DomainPlaceholderCard(
                    icon        = Icons.Rounded.Mood,
                    title       = "Getting to know you",
                    description = buildMoodPlaceholderText(state.domainReadiness.mood),
                    progress    = state.domainReadiness.mood.progressFraction
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("How you moved")
        }

        if (state.domainReadiness.activity.isReady && state.activityBarPoints.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                state.activityTrends?.let { ActivityTrendRow(it) }
                Spacer(Modifier.height(12.dp))
                ActivityStackedBarChart(points = state.activityBarPoints)
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                DomainPlaceholderCard(
                    icon        = Icons.Rounded.DirectionsRun,
                    title       = "Learning your pace",
                    description = buildActivityPlaceholderText(state.domainReadiness.activity),
                    progress    = state.domainReadiness.activity.progressFraction
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("How you rested")
        }

        if (state.sleepBarPoints.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                state.sleepTrends?.let { SleepTrendRow(it) }
                Spacer(Modifier.height(12.dp))
                SleepBarChart(points = state.sleepBarPoints)
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                DomainPlaceholderCard(
                    icon        = Icons.Rounded.Bedtime,
                    title       = "Rest easy",
                    description = "Once you're ready to track your sleep, your nightly reflections will appear here.",
                    progress    = null
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Time to unplug")
        }

        if (state.screenTimePoints.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                state.interactionTrends?.let { ScreenTimeTrendRow(it) }
                Spacer(Modifier.height(12.dp))
                ScreenTimeBarChart(points = state.screenTimePoints)
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                DomainPlaceholderCard(
                    icon        = Icons.Rounded.Smartphone,
                    title       = "Digital reflection",
                    description = "Once you enable tracking, we'll help you reflect on your digital habits here.",
                    progress    = null
                )
            }
        }

        if (state.insightCards.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Gentle observations")
                Spacer(Modifier.height(12.dp))
            }
            items(state.insightCards, key = { it.id }) { card ->
                InsightCardItem(card = card)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun DailyPassiveTimeline(events: List<IntradayTimelineEvent>) {
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MilkDeep,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (events.isEmpty()) {
                Text(
                    text = "No passive events or logs recorded yet today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                events.forEachIndexed { index, event ->
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(36.dp)
                        ) {
                            // Use Any so we can hold either an ImageVector or a Drawable Resource ID
                            val iconData: Any
                            val bg: Color
                            val tint: Color

                            when (event) {
                                is IntradayTimelineEvent.SleepOnset -> {
                                    iconData = Icons.Rounded.NightsStay
                                    bg = ValenceNeutral.copy(alpha=0.15f)
                                    tint = ValenceNeutral
                                }
                                is IntradayTimelineEvent.SleepWakeUp -> {
                                    iconData = Icons.Rounded.WbSunny
                                    bg = ValencePositive.copy(alpha=0.15f)
                                    tint = ValencePositive
                                }
                                is IntradayTimelineEvent.ActivitySpike -> {
                                    val intensity = runCatching { ActivityIntensity.valueOf(event.intensityName.uppercase()) }.getOrDefault(ActivityIntensity.MODERATE)
                                    val color = when(intensity) {
                                        ActivityIntensity.VIGOROUS -> ArousalHigh
                                        ActivityIntensity.MODERATE -> ValencePositive
                                        ActivityIntensity.LIGHT    -> ArousalMid
                                        else                       -> SageDim
                                    }
                                    iconData = when(intensity) {
                                        ActivityIntensity.VIGOROUS -> Icons.Rounded.LocalFireDepartment
                                        ActivityIntensity.MODERATE -> Icons.Rounded.DirectionsRun
                                        else                       -> Icons.Rounded.DirectionsWalk
                                    }
                                    bg = color.copy(alpha=0.15f)
                                    tint = color
                                }
                                is IntradayTimelineEvent.ScreenTimeBlock -> {
                                    iconData = Icons.Rounded.Smartphone
                                    bg = ArousalLow.copy(alpha=0.15f)
                                    tint = ArousalLow
                                }
                                is IntradayTimelineEvent.MoodLog -> {
                                    val v = Valence.entries.getOrNull(event.valenceOrdinal) ?: Valence.NEUTRAL
                                    val a = Arousal.entries.getOrNull(event.arousalOrdinal) ?: Arousal.MID

                                    // Map to the facial expression drawables used in Today's Log
                                    iconData = when (v) {
                                        Valence.NEGATIVE -> R.drawable.ic_sad
                                        Valence.NEUTRAL  -> R.drawable.ic_meh
                                        Valence.POSITIVE -> R.drawable.ic_happy
                                    }

                                    // Use Arousal for the background color, matching the Check-in cards
                                    val color = when (a) {
                                        Arousal.LOW  -> ArousalLow
                                        Arousal.MID  -> ArousalMid
                                        Arousal.HIGH -> ArousalHigh
                                    }

                                    bg = color.copy(alpha=0.15f)
                                    tint = color
                                }
                            }

                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(bg),
                                contentAlignment = Alignment.Center
                            ) {
                                // Smart rendering based on whether it's a Vector or a Drawable
                                when (iconData) {
                                    is ImageVector -> Icon(iconData, null, tint = tint, modifier = Modifier.size(18.dp))
                                    is Int -> Image(painterResource(id = iconData), contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }

                            if (index < events.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .weight(1f)
                                        .background(SageDim.copy(alpha=0.3f))
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f).padding(bottom = if (index < events.size - 1) 28.dp else 0.dp)) {
                            Text(
                                text = event.timestamp.format(timeFmt),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                                color = TextTertiary
                            )
                            Spacer(Modifier.height(4.dp))
                            when (event) {
                                is IntradayTimelineEvent.SleepOnset -> {
                                    Text("Fell Asleep", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("Drifted off to rest", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                is IntradayTimelineEvent.SleepWakeUp -> {
                                    Text("Woke Up", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("Slept for ${DateTimeUtils.formatMinutes(event.durationMinutes)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                is IntradayTimelineEvent.ActivitySpike -> {
                                    Text("Got Moving", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    val intensity = runCatching { ActivityIntensity.valueOf(event.intensityName.uppercase()) }.getOrDefault(ActivityIntensity.MODERATE)
                                    val desc = when (intensity) {
                                        ActivityIntensity.VIGOROUS  -> "High-energy activity for ${event.activeMinutes}m"
                                        ActivityIntensity.MODERATE  -> "Brisk movement for ${event.activeMinutes}m"
                                        ActivityIntensity.LIGHT     -> "Light activity for ${event.activeMinutes}m"
                                        ActivityIntensity.SEDENTARY -> "Gentle movement for ${event.activeMinutes}m"
                                        ActivityIntensity.IN_VEHICLE -> "Rode a vehicle for ${event.activeMinutes}m"
                                    }
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                is IntradayTimelineEvent.ScreenTimeBlock -> {
                                    Text(if (event.isLateNight) "Late Night Screen" else "Screen Time", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    val sessionDesc = if (event.isLateNight) "${DateTimeUtils.formatMinutes(event.durationMinutes)} session" else "${DateTimeUtils.formatMinutes(event.durationMinutes)} of screen time"
                                    Text(sessionDesc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                is IntradayTimelineEvent.MoodLog -> {
                                    val title = if (event.isManual) "Checked In" else "Mood Logged"
                                    Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)

                                    val valence = Valence.entries.getOrNull(event.valenceOrdinal) ?: Valence.NEUTRAL
                                    val arousal = Arousal.entries.getOrNull(event.arousalOrdinal) ?: Arousal.MID

                                    val description = getMoodDescription(valence, arousal, event.timestamp)
                                    Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodStabilityCard(stability: MoodStability) {
    val (color, icon, subtitle) = when {
        stability.score >= 75 -> Triple(DeepSage, Icons.Rounded.Waves, "Your emotional rhythm has been beautifully steady. This kind of balance is a great foundation for your well-being.")
        stability.score >= 40 -> Triple(ValenceNeutral, Icons.Rounded.Insights, "You've been navigating some natural ups and downs. Remember that a bit of fluctuation is completely normal.")
        else -> Triple(ErrorRed, Icons.Rounded.TrendingFlat, "You've experienced quite a few shifts in your mood recently. Be gentle with yourself as you ride these waves.")
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Emotional Rhythm", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold), color = color)
                    Text(stability.stateLabel, style = MaterialTheme.typography.titleMedium.copy(fontFamily = DmSerifDisplay, fontSize = 22.sp), color = TextPrimary)
                }
                Text(
                    text = "${stability.score}",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                    color = color
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (stability.score / 100f).coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

private fun buildMoodPlaceholderText(readiness: DomainReadiness): String {
    val days = readiness.daysWithData
    val needed = readiness.requiredDays
    return if (days == 0) {
        "We need a little more time to learn your rhythms. Log your mood for a few days to unlock these insights."
    } else {
        val remaining = (needed - days).coerceAtLeast(1)
        "$days of $needed days logged. $remaining more day${if (remaining > 1) "s" else ""} to go."
    }
}

private fun buildActivityPlaceholderText(readiness: DomainReadiness): String {
    val days   = readiness.daysWithData
    val needed = readiness.requiredDays
    return if (days == 0) {
        "We need a little more time to see how you move. Keep your phone with you to unlock these insights."
    } else {
        val remaining = (needed - days).coerceAtLeast(1)
        "$days of $needed days tracked. $remaining more day${if (remaining > 1) "s" else ""} to go."
    }
}

private fun getDynamicChartMaxMinutes(maxValue: Int): Int {
    val maxHours = (maxValue + 59) / 60
    var chartMaxHours = maxHours
    while (chartMaxHours % 3 != 0) {
        chartMaxHours++
    }
    if (chartMaxHours == 0) chartMaxHours = 3
    return chartMaxHours * 60
}

private fun DrawScope.drawGridLines(steps: Int = 3) {
    val step = size.height / steps
    for (i in 0..steps) {
        val y = i * step
        drawLine(
            color       = Color(0xFF465940).copy(alpha = 0.08f),
            start       = Offset(0f, y),
            end         = Offset(size.width, y),
            strokeWidth = 1f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
    }
}

@Composable
private fun InsightHeader(state: InsightUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilkWhite)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text  = "Your Insights",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "A look back at your week. You've tracked ${state.daysWithData} days.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun DomainPlaceholderCard(icon: ImageVector, title: String, description: String, progress: Float?) {
    Surface(
        modifier        = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = SageSurface,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = DeepSage, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextTertiary, textAlign = TextAlign.Center, lineHeight = 18.sp)

            if (progress != null) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress   = { progress },
                    modifier   = Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape),
                    color      = DeepSage,
                    trackColor = SageDim,
                    strokeCap  = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun TodayMoodCard(mood: com.moodified.app.domain.model.inference.InferredMoodState) {
    val accentColor = when (mood.valence) {
        Valence.POSITIVE -> ValencePositive
        Valence.NEUTRAL  -> ValenceNeutral
        Valence.NEGATIVE -> if (mood.arousal == Arousal.HIGH) ErrorRed else ValenceNegative
    }

    Surface(
        modifier        = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(24.dp),
        color           = accentColor.copy(alpha = 0.12f),
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = moodIcon(mood.valence, mood.arousal), contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY'S ENERGY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = accentColor
                )
                Spacer(Modifier.height(4.dp))
                Text(text = mood.interpretationLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(text = mood.explainabilityString, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = accentColor.copy(alpha = 0.20f)) {
                Text(text = "${mood.confidenceScore}%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall.copy(fontFamily = DmSerifDisplay, fontSize = 18.sp),
        color    = TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun SleepTrendRow(trends: com.moodified.app.domain.model.sleep.SleepTrends) {
    val avgHours = trends.averageSleepMinutes / 60
    val avgMins  = trends.averageSleepMinutes % 60
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TrendPill(label = "avg", value = "${avgHours}h ${avgMins}m")
        if (trends.totalSleepDebtMinutes > 0) {
            val dh = trends.totalSleepDebtMinutes / 60
            val dm = trends.totalSleepDebtMinutes % 60
            TrendPill(label = "lost rest", value = "${dh}h ${dm}m", warn = true)
        }
        TrendPill(label = "consistency", value = "${trends.consistencyScore}%")
    }
}

@Composable
private fun ActivityTrendRow(trends: com.moodified.app.domain.model.activity.ActivityTrends) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TrendPill(label = "avg steps",  value = "%,d".format(trends.averageSteps))
        TrendPill(label = "active min", value = "${trends.averageActiveMinutes}m")
        TrendPill(label = "consistency", value = "${trends.consistencyScore}%")
    }
}

@Composable
private fun ScreenTimeTrendRow(trends: com.moodified.app.domain.model.interaction.InteractionTrends) {
    val sh = trends.averageScreenTimeMinutes / 60
    val sm = trends.averageScreenTimeMinutes % 60
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TrendPill(label = "avg screen", value = "${sh}h ${sm}m")
        if (trends.averageLateNightMinutes > 0) {
            TrendPill(label = "late night", value = "${trends.averageLateNightMinutes}m", warn = trends.averageLateNightMinutes > 30)
        }
    }
}

@Composable
private fun TrendPill(label: String, value: String, warn: Boolean = false) {
    Column {
        Text(text = value, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (warn) ErrorRed else TextPrimary)
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp), color = TextTertiary)
    }
}

@Composable
private fun MoodLineChart(points: List<MoodChartPoint>) {
    if (points.isEmpty()) return

    val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val endDate = LocalDate.now()
    val last7Days = (6 downTo 0).map { endDate.minusDays(it.toLong()) }

    val pointsByDate = points.groupBy { it.date }
    val averagedByDate = last7Days.associateWith { date ->
        val pts = pointsByDate[date]
        if (pts.isNullOrEmpty()) null else pts.map { it.valenceOrdinal }.average().toFloat()
    }

    val chartHeight = 160.dp

    ChartSurface {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.fillMaxHeight().width(38.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.fillMaxSize().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Good",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("So-so", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("Low",   style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(" ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .drawBehind { drawMoodLine(averagedByDate, size.width, size.height) }
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    last7Days.forEach { date ->
                        Text(
                            text      = date.format(dayFormatter),
                            style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color     = TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            LegendDot(color = ValencePositive, label = "Good")
            LegendDot(color = ValenceNeutral,  label = "So-so")
            LegendDot(color = ValenceNegative, label = "Low")
        }
    }
}

private fun DrawScope.drawMoodLine(
    averagedByDate: Map<LocalDate, Float?>,
    width: Float,
    height: Float
) {
    val entries = averagedByDate.entries.toList()
    val step = width / entries.size.coerceAtLeast(1)

    fun xFor(index: Int): Float = (index * step) + (step / 2f)
    fun yFor(ordinal: Float): Float = height - (ordinal / 2f) * height

    val validPts = entries.mapIndexedNotNull { i, entry -> entry.value?.let { v -> Offset(xFor(i), yFor(v)) } }

    drawGridLines(steps = 2)

    for (i in 0 until validPts.size - 1) {
        drawLine(
            color = Color(0xFF465940),
            start = validPts[i],
            end = validPts[i + 1],
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    entries.forEachIndexed { i, entry ->
        val v = entry.value
        if (v != null) {
            val pt = Offset(xFor(i), yFor(v))
            val dotColor = when {
                v >= 1.5f -> ValencePositive
                v >= 0.5f -> ValenceNeutral
                else      -> ValenceNegative
            }
            drawCircle(color = Color.White, radius = 12f, center = pt)
            drawCircle(color = dotColor, radius = 9f, center = pt)
        }
    }
}

@Composable
private fun SleepBarChart(points: List<SleepBarPoint>) {
    val maxDataMinutes = points.maxOfOrNull { it.totalSleepMinutes } ?: 0
    val maxMinutes = getDynamicChartMaxMinutes(maxDataMinutes)
    val maxHours   = maxMinutes / 60
    val goalLine   = 420
    val dayFmt     = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val chartHeight = 160.dp

    ChartSurface {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.fillMaxHeight().width(38.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.fillMaxSize().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${maxHours}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("${maxHours * 2 / 3}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("${maxHours / 3}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("0h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(" ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }

            Row(
                modifier              = Modifier.weight(1f).fillMaxHeight().drawBehind { drawGridLines() },
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                points.forEach { pt ->
                    val fraction  = (pt.totalSleepMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                    val isGoalMet = pt.totalSleepMinutes >= goalLine

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val barColor = if (isGoalMet) Color(0xAA67C967) else ValenceNegative.copy(alpha = 0.6f)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(barColor)
                            )
                            if (pt.totalSleepMinutes > 0) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(fraction),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Text(
                                        text     = DateTimeUtils.formatMinutes(pt.totalSleepMinutes),
                                        style    = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color    = TextTertiary,
                                        modifier = Modifier.offset(y = (-14).dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text      = pt.date.format(dayFmt),
                            style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color     = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = Color(0xAA67C967),  label = "Restful sleep")
            LegendDot(color = ValenceNegative.copy(alpha = 0.6f), label = "Short sleep")
        }
    }
}

private val ColorLight     = ArousalMid
private val ColorModerate  = ValencePositive
private val ColorVigorous  = ArousalHigh

@Composable
private fun ActivityStackedBarChart(points: List<ActivityBarPoint>) {
    if (points.isEmpty()) return

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val maxActiveMinutes = points.maxOfOrNull { it.activeMinutes } ?: 0
    val maxMinutes = getDynamicChartMaxMinutes(maxActiveMinutes.coerceAtLeast(60))
    val maxHours   = maxMinutes / 60
    val dayFmt      = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val chartHeight = 160.dp

    ChartSurface {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.fillMaxHeight().width(38.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.fillMaxSize().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${maxHours}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("${maxHours * 2 / 3}h",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("${maxHours / 3}h",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("0h",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(" ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }

            Row(
                modifier              = Modifier.weight(1f).fillMaxHeight().drawBehind { drawGridLines() },
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                points.forEach { pt ->
                    val activeMinutes = pt.activeMinutes
                    val totalFraction = (activeMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedDate = if (selectedDate == pt.date) null else pt.date }
                            )
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (activeMinutes > 0) {
                                Column(
                                    modifier            = Modifier
                                        .fillMaxWidth(0.55f)
                                        .fillMaxHeight(totalFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    val safeActive = activeMinutes.toFloat()

                                    if (pt.vigorousMinutes > 0) {
                                        Box(modifier = Modifier.fillMaxWidth().weight(pt.vigorousMinutes / safeActive).background(ColorVigorous))
                                    }
                                    if (pt.moderateMinutes > 0) {
                                        Box(modifier = Modifier.fillMaxWidth().weight(pt.moderateMinutes / safeActive).background(ColorModerate))
                                    }
                                    if (pt.lightMinutes > 0) {
                                        Box(modifier = Modifier.fillMaxWidth().weight(pt.lightMinutes / safeActive).background(ColorLight))
                                    }
                                }
                            }

                            if (pt.totalSteps > 0) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(totalFraction),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Text(
                                        text     = formatSteps(pt.totalSteps),
                                        style    = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color    = TextTertiary,
                                        modifier = Modifier.offset(y = (-14).dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier.height(32.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedContent(targetState = selectedDate == pt.date, label = "activityBreakdown") { isSelected ->
                                if (isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (pt.vigorousMinutes > 0) Text("${pt.vigorousMinutes}m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ColorVigorous))
                                        if (pt.moderateMinutes > 0) Text("${pt.moderateMinutes}m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ColorModerate))
                                        if (pt.lightMinutes > 0) Text("${pt.lightMinutes}m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ColorLight))
                                    }
                                } else {
                                    Text(
                                        text      = pt.date.format(dayFmt),
                                        style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color     = TextTertiary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            LegendDot(color = ColorLight,     label = "Light")
            LegendDot(color = ColorModerate,  label = "Moderate")
            LegendDot(color = ColorVigorous,  label = "Vigorous")
            Spacer(modifier = Modifier.weight(1f))
            LegendDot(color = MilkDeep,   label = "\"0.0k\" Step count")
        }
    }
}

private fun formatSteps(steps: Int): String = when {
    steps >= 10_000 -> "${steps / 1000}k"
    steps >= 1_000  -> "${"%.1f".format(steps / 1000f)}k"
    else            -> "$steps"
}

@Composable
private fun ScreenTimeBarChart(points: List<ScreenTimeBarPoint>) {
    if (points.isEmpty()) return

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val maxDataMinutes = points.maxOfOrNull { it.totalScreenMinutes } ?: 0
    val maxMinutes = getDynamicChartMaxMinutes(maxDataMinutes)
    val maxHours   = maxMinutes / 60
    val dayFmt     = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val chartHeight = 160.dp

    ChartSurface {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.fillMaxHeight().width(38.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.fillMaxSize().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${maxHours}h", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("${maxHours * 2 / 3}h",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("${maxHours / 3}h",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("0h",  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(" ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }

            Row(
                modifier              = Modifier.weight(1f).fillMaxHeight().drawBehind { drawGridLines() },
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                points.forEach { pt ->
                    val totalFraction = (pt.totalScreenMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                    val isHigh = pt.totalScreenMinutes > 240

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedDate = if (selectedDate == pt.date) null else pt.date }
                            )
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(
                                modifier            = Modifier
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight(totalFraction)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                val safeTotal = pt.totalScreenMinutes.coerceAtLeast(1).toFloat()
                                val safeLate = minOf(pt.lateNightMinutes, pt.totalScreenMinutes).toFloat()
                                val remaining = safeTotal - safeLate

                                if (safeLate > 0) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(safeLate / safeTotal).background(ValenceNeutral.copy(alpha = 0.6f)))
                                }
                                if (remaining > 0) {
                                    val barColor = if (isHigh) ArousalLow.copy(alpha = 0.6f) else SageDim
                                    Box(modifier = Modifier.fillMaxWidth().weight(remaining / safeTotal).background(barColor))
                                }
                            }

                            if (pt.totalScreenMinutes > 0) {
                                Column(
                                    modifier = Modifier.fillMaxHeight(totalFraction),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Text(
                                        text     = DateTimeUtils.formatMinutes(pt.totalScreenMinutes),
                                        style    = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color    = TextTertiary,
                                        modifier = Modifier.offset(y = (-14).dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier.height(32.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedContent(targetState = selectedDate == pt.date, label = "screenBreakdown") { isSelected ->
                                if (isSelected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (pt.lateNightMinutes > 0) Text("${DateTimeUtils.formatMinutes(pt.lateNightMinutes)}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = ValenceNeutral))
                                        val dayMins = pt.totalScreenMinutes - pt.lateNightMinutes
                                        if (dayMins > 0) Text("${DateTimeUtils.formatMinutes(dayMins)}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = TextSecondary))
                                    }
                                } else {
                                    Text(
                                        text      = pt.date.format(dayFmt),
                                        style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color     = TextTertiary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = ArousalLow.copy(alpha = 0.6f),     label = "Screen time")
            LegendDot(color = ValenceNeutral.copy(alpha = 0.6f), label = "Late night")
            Spacer(modifier = Modifier.weight(1f))
            LegendDot(color = MilkDeep, label = "Tap bar")
        }
    }
}

@Composable
private fun InsightCardItem(card: InsightCard) {
    val borderColor = when (card.priority) {
        InsightPriority.HIGH   -> ErrorRed.copy(alpha = 0.25f)
        InsightPriority.MEDIUM -> DeepSage.copy(alpha = 0.15f)
        InsightPriority.LOW    -> SageDim.copy(alpha = 0.4f)
    }
    val bgColor = when (card.category) {
        InsightCategory.SLEEP       -> Color(0xFFEDF2EA)
        InsightCategory.ACTIVITY    -> ValencePositive.copy(alpha = 0.08f)
        InsightCategory.PHONE       -> ValenceNeutral.copy(alpha = 0.08f)
        InsightCategory.MOOD        -> ArousalLow.copy(alpha = 0.08f)
        InsightCategory.CORRELATION -> SageSurface
    }
    val iconTint = if (card.priority == InsightPriority.HIGH) ErrorRed else DeepSage

    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        shape           = RoundedCornerShape(20.dp),
        color           = bgColor,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = card.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(top = 2.dp).size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = card.headline,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = card.body,
                    style      = MaterialTheme.typography.bodySmall,
                    color      = TextSecondary,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun ChartSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content  = content
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextTertiary
        )
    }
}

private fun moodIcon(valence: Valence, arousal: Arousal): ImageVector = when (valence) {
    Valence.POSITIVE -> when (arousal) {
        Arousal.HIGH -> Icons.Rounded.Bolt
        Arousal.MID  -> Icons.Rounded.SentimentSatisfiedAlt
        Arousal.LOW  -> Icons.Rounded.Spa
    }
    Valence.NEUTRAL -> when (arousal) {
        Arousal.HIGH -> Icons.Rounded.Air
        Arousal.MID  -> Icons.Rounded.SentimentNeutral
        Arousal.LOW  -> Icons.Rounded.Bedtime
    }
    Valence.NEGATIVE -> when (arousal) {
        Arousal.HIGH -> Icons.Rounded.Warning
        Arousal.MID  -> Icons.Rounded.SentimentDissatisfied
        Arousal.LOW  -> Icons.Rounded.BatteryAlert
    }
}

private fun getMoodDescription(valence: Valence, arousal: Arousal, timestamp: java.time.LocalDateTime): String {
    // Using the minute of the timestamp ensures the text feels varied across different entries
    // but remains completely stable during UI recompositions (so the text doesn't change when scrolling).
    val variant = timestamp.minute % 3

    return when (valence) {
        Valence.POSITIVE -> when (arousal) {
            Arousal.HIGH -> listOf("Vibrant and full of energy", "Riding a wave of good energy", "Feeling upbeat and active")[variant]
            Arousal.MID  -> listOf("Steady and content", "Navigating the day with ease", "Feeling balanced and good")[variant]
            Arousal.LOW  -> listOf("Calm, relaxed, and at peace", "Winding down comfortably", "Enjoying a quiet, positive moment")[variant]
        }
        Valence.NEUTRAL -> when (arousal) {
            Arousal.HIGH -> listOf("A bit restless but pushing through", "High energy, just taking it in", "Wired but holding steady")[variant]
            Arousal.MID  -> listOf("Taking things as they come", "Cruising along at a steady pace", "A perfectly okay moment")[variant]
            Arousal.LOW  -> listOf("A bit foggy and slow-moving", "Low energy, keeping it mellow", "Quiet, calm, and neutral")[variant]
        }
        Valence.NEGATIVE -> when (arousal) {
            Arousal.HIGH -> listOf("Feeling tense or overwhelmed", "Navigating a stressful moment", "Wired and on edge")[variant]
            Arousal.MID  -> listOf("Feeling a bit heavy", "Not the easiest moment", "Navigating a dip in mood")[variant]
            Arousal.LOW  -> listOf("Running on empty", "Feeling completely drained", "Exhausted and needing rest")[variant]
        }
    }
}