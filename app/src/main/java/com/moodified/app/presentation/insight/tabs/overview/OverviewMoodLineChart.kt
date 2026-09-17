package com.moodified.app.presentation.insight.tabs.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValenceNegative
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.core.theme.ValencePositive
import com.moodified.app.presentation.insight.MoodChartPoint
import com.moodified.app.presentation.insight.components.InsightChartSurface
import com.moodified.app.presentation.insight.components.InsightLegendDot
import com.moodified.app.presentation.insight.components.drawInsightGridLines
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OverviewMoodLineChart(points: List<MoodChartPoint>) {
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

    InsightChartSurface {
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
            InsightLegendDot(color = ValencePositive, label = "Good")
            InsightLegendDot(color = ValenceNeutral, label = "So-so")
            InsightLegendDot(color = ValenceNegative, label = "Low")
        }
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

    drawInsightGridLines(steps = 2)

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
