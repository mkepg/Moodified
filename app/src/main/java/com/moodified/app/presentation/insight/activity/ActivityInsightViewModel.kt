package com.moodified.app.presentation.insight.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.moodified.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.moodified.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.moodified.app.presentation.insight.common.InsightStatus
import com.moodified.app.presentation.insight.common.WeeklyBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

private const val STEP_GOAL = 10_000

data class ActivityInsightUiState(
    val status: InsightStatus = InsightStatus.Loading,
    val stepsToday: Int = 0,
    val stepGoal: Int = STEP_GOAL,
    val activeMinutes: Int = 0,
    val sedentaryMinutes: Int = 0,
    val peakIntensity: ActivityIntensity = ActivityIntensity.SEDENTARY,
    val isPartialDay: Boolean = false,
    val weeklyBars: List<WeeklyBar> = emptyList(),
    val averageStepsThisWeek: Int = 0,
    val bestDayDate: String? = null,
    val bestDaySteps: Int = 0,
    val consistencyScore: Int = 0,
) {
    val stepGoalProgress: Float
        get() = if (stepGoal <= 0) 0f else (stepsToday.toFloat() / stepGoal).coerceIn(0f, 1f)
}

@HiltViewModel
class ActivityInsightViewModel @Inject constructor(
    private val repository: ActivityRepository,
    getDailySummary: GetDailyActivitySummaryUseCase,
    getWeeklySummaries: GetWeeklyActivitySummariesUseCase,
    getWeeklyTrends: GetWeeklyActivityTrendsUseCase,
) : ViewModel() {

    val state: StateFlow<ActivityInsightUiState> = midnightTickerFlow()
        .flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklySummaries(date),
                getWeeklyTrends(date),
                repository.observeSignal(),
            ) { daily, summaries, trends, signal ->
                val today = LocalDate.now().toString()
                val bars = summaries.map { day ->
                    WeeklyBar(
                        label = day.date.takeLast(5).replace("-", "/"),
                        value = day.totalSteps,
                        isToday = day.date == today,
                    )
                }
                val status = when {
                    !signal.isTracking && daily == null && summaries.isEmpty() ->
                        InsightStatus.TrackingOff
                    daily == null && summaries.isEmpty() ->
                        InsightStatus.Empty
                    else ->
                        InsightStatus.Ready
                }
                ActivityInsightUiState(
                    status = status,
                    stepsToday = daily?.totalSteps ?: 0,
                    activeMinutes = daily?.activeMinutes ?: 0,
                    sedentaryMinutes = daily?.sedentaryMinutes ?: 0,
                    peakIntensity = daily?.peakIntensity ?: ActivityIntensity.SEDENTARY,
                    isPartialDay = daily?.isPartialDay ?: false,
                    weeklyBars = bars,
                    averageStepsThisWeek = trends?.averageSteps ?: 0,
                    bestDayDate = trends?.bestDayDate,
                    bestDaySteps = trends?.bestDaySteps ?: 0,
                    consistencyScore = trends?.consistencyScore ?: 0,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityInsightUiState(status = InsightStatus.Loading),
        )
}
