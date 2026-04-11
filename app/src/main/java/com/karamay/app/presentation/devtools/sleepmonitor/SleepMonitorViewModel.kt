package com.karamay.app.presentation.devtools.sleepmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.midnightTickerFlow
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSignal
import com.karamay.app.domain.model.sleep.SleepTrends
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import com.karamay.app.domain.usecase.sleep.ObserveSleepSignalUseCase
import com.karamay.app.presentation.devtools.MonitorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

typealias SleepWeeklyData     = SleepTrends
typealias SleepMonitorUiState = MonitorUiState<SleepSignal, DailySleepSummary, SleepWeeklyData>

val SleepMonitorUiState.weeklyTrends: SleepTrends? get() = weeklyData

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    repository: SleepRepository,
    observeSignalUseCase: ObserveSleepSignalUseCase,
    getDailySummary: GetDailySleepSummaryUseCase,
    getWeeklyTrends: GetWeeklySleepTrendsUseCase,
) : ViewModel() {

    val state: StateFlow<SleepMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date)
            ) { daily, trends -> Pair(daily, trends) }
        },
        observeSignalUseCase()
    ) { (daily, trends), signal ->
        SleepMonitorUiState(
            isLoading    = false,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = trends
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = SleepMonitorUiState(
            isLoading  = true,
            isTracking = repository.isTracking,
            liveSignal = SleepSignal(
                isTracking       = repository.isTracking,
                hasActiveSession = repository.isTracking
            )
        )
    )
}