package com.moodified.app.presentation.insight.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.presentation.insight.InsightUiState
import com.moodified.app.presentation.insight.tabs.overview.OverviewMoodLineChart
import com.moodified.app.presentation.insight.tabs.overview.OverviewMoodStabilityCard
import com.moodified.app.presentation.insight.tabs.overview.OverviewTodayMoodCard

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
