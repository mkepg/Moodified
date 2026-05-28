package com.moodified.app.presentation.insight.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.*
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.presentation.devtools.ConsistencyScoreSection
import com.moodified.app.presentation.devtools.IdleBanner
import com.moodified.app.presentation.devtools.MonitorCard
import com.moodified.app.presentation.devtools.MonitorCardEmpty
import com.moodified.app.presentation.devtools.MonitorHeader
import com.moodified.app.presentation.devtools.MonitorStatTile
import com.moodified.app.presentation.devtools.MonitorWeeklyBars
import com.moodified.app.presentation.devtools.PermissionDeniedCard
import com.moodified.app.presentation.devtools.SectionLabel
import com.moodified.app.presentation.devtools.WeeklyBarEntry
import com.moodified.app.presentation.insight.common.InsightStatus

@Composable
fun ActivityInsightScreen(
    onBack: () -> Unit,
    viewModel: ActivityInsightViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val hasActivityPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            MonitorHeader(
                title = "Activity",
                subtitle = "Your daily movement and intensity patterns",
                isTracking = state.status == InsightStatus.Ready,
                liveIndicatorColor = ValencePositive,
                eyebrow = "INSIGHTS",
                onBack = onBack,
            )
        }

        when {
            !hasActivityPermission -> item {
                PermissionDeniedCard(
                    title = "Activity Recognition needed",
                    body = "Moodified uses your device's step sensor to count steps " +
                            "and detect movement. Grant the Physical Activity permission " +
                            "in Settings to see your activity insights.",
                    canAskAgain = false,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                )
            }
            state.status == InsightStatus.Loading -> item {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            state.status == InsightStatus.TrackingOff -> item {
                IdleBanner("Activity tracking is off. Turn it on in More → Tracking Preferences.")
            }
            state.status == InsightStatus.Empty -> item {
                IdleBanner("We haven't recorded any activity yet. Keep your phone with you and check back later.")
            }
            else -> {
                item {
                    SectionLabel("Today")
                    ActivityTodayCard(state = state)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("This Week")
                    ActivityWeeklyCard(state = state)
                }
            }
        }
    }
}

@Composable
private fun ActivityTodayCard(state: ActivityInsightUiState) {
    MonitorCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Steps",
                value = state.stepsToday.toString(),
                subLabel = if (state.stepsToday >= state.stepGoal) "Goal reached" else "of ${state.stepGoal} goal",
                accentColor = ValencePositive,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Active",
                value = DateTimeUtils.formatMinutes(state.activeMinutes),
                subLabel = "Moving time",
                accentColor = ArousalMid,
            )
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Peak",
                value = state.peakIntensity.displayLabel(),
                subLabel = "Highest energy",
                accentColor = intensityColor(state.peakIntensity),
            )
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
                    .fillMaxWidth(state.stepGoalProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ValencePositive)
            )
        }
    }
}

@Composable
private fun ActivityWeeklyCard(state: ActivityInsightUiState) {
    if (state.weeklyBars.isEmpty()) {
        MonitorCardEmpty("Not enough data yet for a weekly view.")
        return
    }
    MonitorCard {
        MonitorWeeklyBars(
            entries = state.weeklyBars.map {
                WeeklyBarEntry(label = it.label, value = it.value, isToday = it.isToday)
            }
        )
        HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorStatTile(
                modifier = Modifier.weight(1f),
                label = "Avg / day",
                value = state.averageStepsThisWeek.toString(),
                subLabel = "Past 7 days",
                accentColor = ValencePositive,
            )
            if (state.bestDayDate != null) {
                MonitorStatTile(
                    modifier = Modifier.weight(1f),
                    label = "Best day",
                    value = state.bestDaySteps.toString(),
                    subLabel = state.bestDayDate.takeLast(5).replace("-", "/"),
                    accentColor = ArousalMid,
                )
            }
        }
        ConsistencyScoreSection(
            score = state.consistencyScore,
            sublabel = "How steady your daily step count is",
        )
    }
}

private fun intensityColor(intensity: ActivityIntensity) = when (intensity) {
    ActivityIntensity.SEDENTARY,
    ActivityIntensity.IN_VEHICLE -> TextTertiary
    ActivityIntensity.LIGHT -> ArousalMid
    ActivityIntensity.MODERATE -> ValencePositive
    ActivityIntensity.VIGOROUS -> ArousalHigh
}
