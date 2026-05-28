package com.moodified.app.presentation.devtools.interactionmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.model.interaction.InteractionTrends
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.moodified.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
import com.moodified.app.presentation.devtools.MonitorUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class InteractionWeeklyData(
    val trends:    InteractionTrends?,
    val summaries: List<InteractionDailySummary>
)

typealias InteractionMonitorUiState = MonitorUiState<InteractionSignal, InteractionDailySummary, InteractionWeeklyData>

val InteractionMonitorUiState.weeklyTrends: InteractionTrends? get() = weeklyData?.trends
val InteractionMonitorUiState.weeklySummaries: List<InteractionDailySummary> get() = weeklyData?.summaries ?: emptyList()

@HiltViewModel
class InteractionMonitorViewModel @Inject constructor(
    repository: InteractionRepository,
    observeSignal: ObserveInteractionSignalUseCase,
    getDailySummary: GetDailyInteractionSummaryUseCase,
    getWeeklyTrends: GetWeeklyInteractionTrendsUseCase,
    getWeeklySummaries: GetWeeklyInteractionSummariesUseCase,
) : ViewModel() {

    val state: StateFlow<InteractionMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date),
                getWeeklySummaries(date)
            ) { daily, trends, summaries -> Triple(daily, trends, summaries) }
        },
        observeSignal()
    ) { (daily, trends, summaries), signal ->
        InteractionMonitorUiState(
            isLoading    = false,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = InteractionWeeklyData(trends, summaries)
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = InteractionMonitorUiState(
            isLoading  = true,
            isTracking = repository.isTracking,
            liveSignal = InteractionSignal(isTracking = repository.isTracking)
        )
    )
}