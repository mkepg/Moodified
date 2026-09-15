package com.moodified.app.presentation.insight.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValenceNegative
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.core.theme.ValencePositive
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.presentation.insight.InsightUiState
import com.moodified.app.presentation.insight.MoodChartPoint
import com.moodified.app.presentation.insight.MoodStability
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OverviewTab(state: InsightUiState) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            val unit = if (state.daysWithData == 1) "day" else "days"
            val daysLabel =
                if (state.daysWithData > 0) {
                    "A look back at your week. You've tracked ${state.daysWithData} $unit."
                } else {
                    "A look back at your week."
                }
            val titleStyle =
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = DmSerifDisplay,
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Your Insights", style = titleStyle, color = TextPrimary)
                Text(text = daysLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        state.todayInferredMood?.let { mood ->
            item {
                Spacer(Modifier.height(8.dp))
                OverviewTodayMoodCard(mood = mood)
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            OverviewSectionHeader("Your mood this week")
        }

        if (state.domainReadiness.mood.isReady && state.moodChartPoints.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                OverviewMoodLineChart(points = state.moodChartPoints)
            }

            if (state.moodStability != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    OverviewMoodStabilityCard(stability = state.moodStability!!)
                }
            }
        } else {
            item {
                Spacer(Modifier.height(12.dp))
                OverviewEmptyMoodCard()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers (duplicated from InsightScreen.kt — Task 5 removes them there)
// ---------------------------------------------------------------------------

@Composable
private fun OverviewSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = DmSerifDisplay, fontSize = 18.sp),
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun OverviewEmptyMoodCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Getting to know you",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "We need a little more time to learn your rhythms. Log your mood for a few days to unlock these insights.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OverviewTodayMoodCard(mood: InferredMoodState) {
    val accentColor =
        when (mood.valence) {
            Valence.POSITIVE -> ValencePositive
            Valence.NEUTRAL -> ValenceNeutral
            Valence.NEGATIVE -> if (mood.arousal == Arousal.HIGH) ErrorRed else ValenceNegative
        }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = accentColor.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = overviewMoodIcon(mood.valence, mood.arousal),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY'S ENERGY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = accentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mood.interpretationLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mood.explainabilityString,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = accentColor.copy(alpha = 0.20f)) {
                Text(
                    text = "${mood.confidenceScore}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewMoodStabilityCard(stability: MoodStability) {
    val (color, icon, subtitle) =
        when {
            stability.score >= 75 ->
                Triple(
                    DeepSage,
                    Icons.Rounded.Waves,
                    "Your emotional rhythm has been beautifully steady. This kind of balance is a great foundation for your well-being.",
                )
            stability.score >= 40 ->
                Triple(
                    ValenceNeutral,
                    Icons.Rounded.Insights,
                    "You've been navigating some natural ups and downs. Remember that a bit of fluctuation is completely normal.",
                )
            else ->
                Triple(
                    ErrorRed,
                    Icons.Rounded.TrendingFlat,
                    "You've experienced quite a few shifts in your mood recently. Be gentle with yourself as you ride these waves.",
                )
        }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Emotional Rhythm",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = color,
                    )
                    Text(
                        stability.stateLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = DmSerifDisplay, fontSize = 22.sp),
                        color = TextPrimary,
                    )
                }
                Text(
                    text = "${stability.score}",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                    color = color,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(20.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction = (stability.score / 100f).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                )
            }
        }
    }
}

@Composable
private fun OverviewMoodLineChart(points: List<MoodChartPoint>) {
    if (points.isEmpty()) return

    val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val endDate = LocalDate.now()
    val last7Days = (6 downTo 0).map { endDate.minusDays(it.toLong()) }

    val pointsByDate = points.groupBy { it.date }
    val averagedByDate =
        last7Days.associateWith { date ->
            val pts = pointsByDate[date]
            if (pts.isNullOrEmpty()) null else pts.map { it.valenceOrdinal }.average().toFloat()
        }

    val chartHeight = 160.dp

    OverviewChartSurface {
        Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Column(modifier = Modifier.fillMaxHeight().width(38.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxSize().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Good", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("So-so", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                        Text("Low", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(" ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .drawBehind { overviewDrawMoodLine(averagedByDate, size.width, size.height) },
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    last7Days.forEach { date ->
                        Text(
                            text = date.format(dayFormatter),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverviewLegendDot(color = ValencePositive, label = "Good")
            OverviewLegendDot(color = ValenceNeutral, label = "So-so")
            OverviewLegendDot(color = ValenceNegative, label = "Low")
        }
    }
}

private fun DrawScope.overviewDrawGridLines(steps: Int = 3) {
    val step = size.height / steps
    for (i in 0..steps) {
        val y = i * step
        drawLine(
            color = Color(0xFF465940).copy(alpha = 0.08f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

private fun DrawScope.overviewDrawMoodLine(
    averagedByDate: Map<LocalDate, Float?>,
    width: Float,
    height: Float,
) {
    val entries = averagedByDate.entries.toList()
    val step = width / entries.size.coerceAtLeast(1)

    fun xFor(index: Int): Float = (index * step) + (step / 2f)

    fun yFor(ordinal: Float): Float = height - (ordinal / 2f) * height

    val validPts = entries.mapIndexedNotNull { i, entry -> entry.value?.let { v -> Offset(xFor(i), yFor(v)) } }

    overviewDrawGridLines(steps = 2)

    for (i in 0 until validPts.size - 1) {
        drawLine(
            color = Color(0xFF465940),
            start = validPts[i],
            end = validPts[i + 1],
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }

    entries.forEachIndexed { i, entry ->
        val v = entry.value
        if (v != null) {
            val pt = Offset(xFor(i), yFor(v))
            val dotColor =
                when {
                    v >= 1.5f -> ValencePositive
                    v >= 0.5f -> ValenceNeutral
                    else -> ValenceNegative
                }
            drawCircle(color = Color.White, radius = 12f, center = pt)
            drawCircle(color = dotColor, radius = 9f, center = pt)
        }
    }
}

@Composable
private fun OverviewChartSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun OverviewLegendDot(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextTertiary,
        )
    }
}

private fun overviewMoodIcon(
    valence: Valence,
    arousal: Arousal,
): ImageVector =
    when (valence) {
        Valence.POSITIVE ->
            when (arousal) {
                Arousal.HIGH -> Icons.Rounded.Bolt
                Arousal.MID -> Icons.Rounded.SentimentSatisfiedAlt
                Arousal.LOW -> Icons.Rounded.Spa
            }
        Valence.NEUTRAL ->
            when (arousal) {
                Arousal.HIGH -> Icons.Rounded.Air
                Arousal.MID -> Icons.Rounded.SentimentNeutral
                Arousal.LOW -> Icons.Rounded.Bedtime
            }
        Valence.NEGATIVE ->
            when (arousal) {
                Arousal.HIGH -> Icons.Rounded.Warning
                Arousal.MID -> Icons.Rounded.SentimentDissatisfied
                Arousal.LOW -> Icons.Rounded.BatteryAlert
            }
    }
