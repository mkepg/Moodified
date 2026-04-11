package com.karamay.app.presentation.insight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.karamay.app.core.theme.*
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.Valence
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            SectionHeader("Mood This Week")
        }

        if (state.domainReadiness.mood.isReady && state.moodChartPoints.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                MoodLineChart(points = state.moodChartPoints)
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                DomainPlaceholderCard(
                    icon        = Icons.Rounded.Mood,
                    title       = "Mood insights unlocking",
                    description = buildMoodPlaceholderText(state.domainReadiness.mood),
                    progress    = state.domainReadiness.mood.progressFraction
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Sleep Duration")
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
                    title       = "Sleep tracking not yet active",
                    description = "Sleep data will appear here once tracking begins.",
                    progress    = null
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Physical Activity")
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
                    title       = "Activity insights unlocking",
                    description = buildActivityPlaceholderText(state.domainReadiness.activity),
                    progress    = state.domainReadiness.activity.progressFraction
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Screen Time")
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
                    title       = "Screen time tracking not yet active",
                    description = "Usage data will appear here once tracking begins.",
                    progress    = null
                )
            }
        }

        if (state.insightCards.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                SectionHeader("What the data says")
                Spacer(Modifier.height(12.dp))
            }

            items(state.insightCards, key = { it.id }) { card ->
                InsightCardItem(card = card)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun buildMoodPlaceholderText(readiness: DomainReadiness): String {
    val logged = readiness.daysWithData
    val needed = readiness.requiredDays
    return if (logged == 0) {
        "Log your mood a few times to unlock weekly patterns."
    } else {
        val remaining = (needed - logged).coerceAtLeast(1)
        "$logged of $needed entries logged. $remaining more to go."
    }
}

private fun buildActivityPlaceholderText(readiness: DomainReadiness): String {
    val days   = readiness.daysWithData
    val needed = readiness.requiredDays
    return if (days == 0) {
        "Activity data will appear here once tracking begins."
    } else {
        val remaining = (needed - days).coerceAtLeast(1)
        "$days of $needed days tracked. $remaining more day${if (remaining > 1) "s" else ""} to go."
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
            text  = "Insight",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Last 7 days  ·  ${state.daysWithData} days tracked",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun DomainPlaceholderCard(
    icon: ImageVector,
    title: String,
    description: String,
    progress: Float?
) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = SageSurface,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeepSage,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text      = title,
                style     = MaterialTheme.typography.titleSmall,
                color     = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text       = description,
                style      = MaterialTheme.typography.bodySmall,
                color      = TextTertiary,
                textAlign  = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (progress != null) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress         = { progress },
                    modifier         = Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp)
                        .clip(CircleShape),
                    color            = DeepSage,
                    trackColor       = SageDim,
                    strokeCap        = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun TodayMoodCard(mood: com.karamay.app.domain.model.inference.InferredMoodState) {
    val accentColor = when (mood.valence) {
        Valence.POSITIVE -> ValencePositive
        Valence.NEUTRAL  -> ValenceNeutral
        Valence.NEGATIVE -> ValenceNegative
    }

    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(24.dp),
        color           = accentColor.copy(alpha = 0.12f),
        tonalElevation  = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = moodIcon(mood.valence, mood.arousal),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = mood.interpretationLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text       = mood.explainabilityString,
                    style      = MaterialTheme.typography.bodySmall,
                    color      = TextSecondary,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentColor.copy(alpha = 0.20f)
            ) {
                Text(
                    text     = "${mood.confidenceScore}%",
                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color    = TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall.copy(
            fontFamily = DmSerifDisplay,
            fontSize   = 18.sp
        ),
        color    = TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun SleepTrendRow(trends: com.karamay.app.domain.model.sleep.SleepTrends) {
    val avgHours = trends.averageSleepMinutes / 60
    val avgMins  = trends.averageSleepMinutes % 60

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TrendPill(label = "avg", value = "${avgHours}h ${avgMins}m")

        if (trends.totalSleepDebtMinutes > 0) {
            val dh = trends.totalSleepDebtMinutes / 60
            val dm = trends.totalSleepDebtMinutes % 60
            TrendPill(label = "debt", value = "${dh}h ${dm}m", warn = true)
        }

        TrendPill(label = "consistency", value = "${trends.consistencyScore}%")
    }
}

@Composable
private fun ActivityTrendRow(trends: com.karamay.app.domain.model.activity.ActivityTrends) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TrendPill(label = "avg steps",  value = "%,d".format(trends.averageSteps))
        TrendPill(label = "active min", value = "${trends.averageActiveMinutes}m")
        TrendPill(label = "consistency", value = "${trends.consistencyScore}%")
    }
}

@Composable
private fun ScreenTimeTrendRow(trends: com.karamay.app.domain.model.interaction.InteractionTrends) {
    val sh = trends.averageScreenTimeMinutes / 60
    val sm = trends.averageScreenTimeMinutes % 60

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TrendPill(label = "avg screen", value = "${sh}h ${sm}m")
        if (trends.averageLateNightMinutes > 0) {
            TrendPill(
                label = "late night",
                value = "${trends.averageLateNightMinutes}m",
                warn  = trends.averageLateNightMinutes > 30
            )
        }
    }
}

@Composable
private fun TrendPill(label: String, value: String, warn: Boolean = false) {
    Column {
        Text(
            text  = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (warn) ErrorRed else TextPrimary
        )
        Text(
            text  = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize      = 9.sp,
                letterSpacing = 0.8.sp
            ),
            color = TextTertiary
        )
    }
}

@Composable
private fun MoodLineChart(points: List<MoodChartPoint>) {
    if (points.isEmpty()) return

    val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val byDate = points
        .groupBy { it.date }
        .mapValues { (_, pts) ->
            pts.groupingBy { it.valenceOrdinal }.eachCount().maxByOrNull { it.value }?.key ?: 1f
        }
        .entries
        .sortedBy { it.key }

    val chartHeight = 100.dp

    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(20.dp),
        color           = MilkDeep,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier            = Modifier
                        .width(40.dp)
                        .height(chartHeight),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Good", "So-so", "Bad").forEach { label ->
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextTertiary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(chartHeight)
                        .drawBehind { drawMoodLine(byDate, size.width, size.height) }
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                byDate.forEach { (date, _) ->
                    Text(
                        text      = date.format(dayFormatter),
                        style     = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color     = TextTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                LegendDot(color = ValencePositive, label = "Positive")
                LegendDot(color = ValenceNeutral,  label = "Neutral")
                LegendDot(color = ValenceNegative, label = "Low")
            }
        }
    }
}

private fun DrawScope.drawMoodLine(
    byDate: List<Map.Entry<java.time.LocalDate, Float>>,
    width:  Float,
    height: Float
) {
    if (byDate.size < 2) return

    val step = width / (byDate.size - 1).coerceAtLeast(1)

    fun yFor(ordinal: Float): Float = height - (ordinal / 2f) * height

    val pts = byDate.mapIndexed { i, (_, v) -> Offset(i * step, yFor(v)) }

    listOf(0f, 0.5f, 1f).forEach { frac ->
        drawLine(
            color       = Color(0xFF465940).copy(alpha = 0.08f),
            start       = Offset(0f, frac * height),
            end         = Offset(width, frac * height),
            strokeWidth = 1f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
    }

    for (i in 0 until pts.size - 1) {
        drawLine(
            color       = Color(0xFF465940),
            start       = pts[i],
            end         = pts[i + 1],
            strokeWidth = 2.5f,
            cap         = StrokeCap.Round
        )
    }

    pts.forEachIndexed { i, pt ->
        val dotColor = when {
            byDate[i].value >= 1.5f -> Color(0xFFFFC867)
            byDate[i].value >= 0.5f -> Color(0xFFD49FFF)
            else                    -> Color(0xFF66D1F2)
        }
        drawCircle(color = Color.White, radius = 6f,   center = pt)
        drawCircle(color = dotColor,    radius = 4.5f, center = pt)
    }
}

@Composable
private fun SleepBarChart(points: List<SleepBarPoint>) {
    val maxMinutes = points.maxOf { it.totalSleepMinutes }.coerceAtLeast(480)
    val goalLine   = 420
    val dayFmt     = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    ChartSurface {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                val fraction  = pt.totalSleepMinutes.toFloat() / maxMinutes
                val isGoalMet = pt.totalSleepMinutes >= goalLine

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier            = Modifier.weight(1f)
                ) {
                    val barColor = when {
                        isGoalMet && !pt.isEstimated -> DeepSage
                        isGoalMet && pt.isEstimated  -> SageLight
                        else                         -> ValenceNegative.copy(alpha = 0.6f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height((100 * fraction).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barColor)
                    )

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
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = DeepSage,  label = "Goal met")
            LegendDot(color = SageLight, label = "Estimated")
            LegendDot(color = ValenceNegative.copy(alpha = 0.6f), label = "Short night")
        }
    }
}

private val ColorSedentary = SageDim.copy(alpha = 0.5f)
private val ColorLight     = Color(0xFF8EC5A8)
private val ColorModerate  = DeepSage.copy(alpha = 0.75f)
private val ColorVigorous  = Color(0xFF2E4A33)

@Composable
private fun ActivityStackedBarChart(points: List<ActivityBarPoint>) {
    if (points.isEmpty()) return

    val dayFmt   = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val maxTotal = points.maxOf {
        it.sedentaryMinutes + it.lightMinutes + it.moderateMinutes + it.vigorousMinutes
    }.coerceAtLeast(60)

    val chartMaxPx = 100

    ChartSurface {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                val totalMinutes = pt.sedentaryMinutes + pt.lightMinutes +
                        pt.moderateMinutes + pt.vigorousMinutes
                val totalFraction = totalMinutes.toFloat() / maxTotal

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier            = Modifier.weight(1f)
                ) {
                    if (pt.totalSteps > 0) {
                        Text(
                            text  = formatSteps(pt.totalSteps),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = TextTertiary
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    val barHeight = (chartMaxPx * totalFraction).dp.coerceAtLeast(4.dp)
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth(0.55f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (pt.vigorousMinutes > 0) {
                            val frac = pt.vigorousMinutes.toFloat() / totalMinutes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(frac.coerceAtLeast(0.01f))
                                    .background(ColorVigorous)
                            )
                        }
                        if (pt.moderateMinutes > 0) {
                            val frac = pt.moderateMinutes.toFloat() / totalMinutes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(frac.coerceAtLeast(0.01f))
                                    .background(ColorModerate)
                            )
                        }
                        if (pt.lightMinutes > 0) {
                            val frac = pt.lightMinutes.toFloat() / totalMinutes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(frac.coerceAtLeast(0.01f))
                                    .background(ColorLight)
                            )
                        }
                        if (pt.sedentaryMinutes > 0) {
                            val frac = pt.sedentaryMinutes.toFloat() / totalMinutes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(frac.coerceAtLeast(0.01f))
                                    .background(ColorSedentary)
                            )
                        }

                        if (totalMinutes == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(ColorSedentary)
                            )
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
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            LegendDot(color = ColorSedentary, label = "Sedentary")
            LegendDot(color = ColorLight,     label = "Light")
            LegendDot(color = ColorModerate,  label = "Moderate")
            LegendDot(color = ColorVigorous,  label = "Vigorous")
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
    val maxMins = points.maxOf { it.totalScreenMinutes }.coerceAtLeast(240)
    val dayFmt  = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    ChartSurface {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                val totalFraction     = pt.totalScreenMinutes.toFloat() / maxMins
                val lateNightFraction = pt.lateNightMinutes.toFloat() / maxMins
                val isHigh = pt.totalScreenMinutes > 240

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier            = Modifier.weight(1f)
                ) {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth(0.55f)
                            .height((100 * totalFraction).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (pt.lateNightMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(lateNightFraction.coerceAtLeast(0.05f))
                                    .background(ValenceNeutral.copy(alpha = 0.6f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight((totalFraction - lateNightFraction).coerceAtLeast(0.05f))
                                .background(if (isHigh) ArousalLow.copy(alpha = 0.6f) else SageDim)
                        )
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
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = ArousalLow.copy(alpha = 0.6f),     label = "Screen time")
            LegendDot(color = ValenceNeutral.copy(alpha = 0.6f), label = "Late night")
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
        InsightCategory.MOOD        -> MilkDeep
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