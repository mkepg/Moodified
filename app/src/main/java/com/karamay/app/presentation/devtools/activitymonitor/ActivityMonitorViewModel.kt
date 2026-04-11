package com.karamay.app.presentation.devtools.activitymonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.midnightTickerFlow
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.activity.ActivityTrends
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.karamay.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.karamay.app.presentation.devtools.MonitorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ActivityWeeklyData(
    val trends:    ActivityTrends?,
    val summaries: List<ActivityDailySummary>
)

typealias ActivityMonitorUiState = MonitorUiState<ActivitySignal, ActivityDailySummary, ActivityWeeklyData>

val ActivityMonitorUiState.weeklySummaries: List<ActivityDailySummary>
    get() = weeklyData?.summaries ?: emptyList()

@HiltViewModel
class ActivityMonitorViewModel @Inject constructor(
    repository: ActivityRepository,
    observeSignalUseCase: ObserveActivitySignalUseCase,
    getDailySummary: GetDailyActivitySummaryUseCase,
    getWeeklySummaries: GetWeeklyActivitySummariesUseCase,
    getWeeklyTrends: GetWeeklyActivityTrendsUseCase,
) : ViewModel() {

    val state: StateFlow<ActivityMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date),
                getWeeklySummaries(date)
            ) { daily, trends, summaries -> Triple(daily, trends, summaries) }
        },
        observeSignalUseCase()
    ) { (daily, trends, summaries), signal ->
        ActivityMonitorUiState(
            isLoading    = false,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = ActivityWeeklyData(trends, summaries)
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityMonitorUiState(
            isLoading  = true,
            isTracking = repository.isTracking,
            liveSignal = ActivitySignal(
                isTracking       = repository.isTracking,
                hasActiveSession = repository.isTracking
            )
        )
    )
}