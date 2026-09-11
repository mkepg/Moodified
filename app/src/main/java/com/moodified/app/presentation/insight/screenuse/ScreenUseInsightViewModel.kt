package com.moodified.app.presentation.insight.screenuse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.moodified.app.presentation.insight.common.InsightStatus
import com.moodified.app.presentation.insight.common.WeeklyBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class ScreenUseInsightUiState(
    val status: InsightStatus = InsightStatus.Loading,
    val totalScreenMinutesToday: Int = 0,
    val lateNightMinutesToday: Int = 0,
    val unlockCountToday: Int = 0,
    val sessionCountToday: Int = 0,
    val avgSessionMinutesToday: Int = 0,
    val weeklyBars: List<WeeklyBar> = emptyList(),
    val averageScreenMinutesThisWeek: Int = 0,
    val averageLateNightMinutesThisWeek: Int = 0,
    val consistencyScore: Int = 0,
)

@HiltViewModel
class ScreenUseInsightViewModel
    @Inject
    constructor(
        private val repository: InteractionRepository,
        getDailySummary: GetDailyInteractionSummaryUseCase,
        getWeeklySummaries: GetWeeklyInteractionSummariesUseCase,
        getWeeklyTrends: GetWeeklyInteractionTrendsUseCase,
    ) : ViewModel() {
        val state: StateFlow<ScreenUseInsightUiState> =
            midnightTickerFlow()
                .flatMapLatest { date ->
                    combine(
                        getDailySummary(date),
                        getWeeklySummaries(date),
                        getWeeklyTrends(date),
                        repository.observeLiveSignal(),
                    ) { daily, summaries, trends, signal ->
                        val hasPermission = repository.hasUsagePermission()
                        val today = LocalDate.now().toString()
                        val bars =
                            summaries.map {
                                WeeklyBar(
                                    label = it.date.takeLast(5).replace("-", "/"),
                                    value = it.totalScreenTimeMinutes,
                                    isToday = it.date == today,
                                )
                            }
                        val status =
                            when {
                                !hasPermission -> InsightStatus.PermissionRequired
                                !signal.isTracking && daily == null && summaries.isEmpty() ->
                                    InsightStatus.TrackingOff
                                daily == null && summaries.isEmpty() -> InsightStatus.Empty
                                else -> InsightStatus.Ready
                            }
                        ScreenUseInsightUiState(
                            status = status,
                            totalScreenMinutesToday = daily?.totalScreenTimeMinutes ?: 0,
                            lateNightMinutesToday = daily?.lateNightUsageMinutes ?: 0,
                            unlockCountToday = daily?.unlockCount ?: 0,
                            sessionCountToday = daily?.sessionCount ?: 0,
                            avgSessionMinutesToday = daily?.averageSessionDurationMinutes ?: 0,
                            weeklyBars = bars,
                            averageScreenMinutesThisWeek = trends?.averageScreenTimeMinutes ?: 0,
                            averageLateNightMinutesThisWeek = trends?.averageLateNightMinutes ?: 0,
                            consistencyScore = trends?.consistencyScore ?: 0,
                        )
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ScreenUseInsightUiState(status = InsightStatus.Loading),
                )
    }
