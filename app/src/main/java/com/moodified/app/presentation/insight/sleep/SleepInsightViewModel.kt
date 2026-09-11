package com.moodified.app.presentation.insight.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.repository.SleepRepository
import com.moodified.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import com.moodified.app.presentation.insight.common.InsightStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SleepInsightUiState(
    val status: InsightStatus = InsightStatus.Loading,
    val totalSleepMinutes: Int = 0,
    val timeInBedMinutes: Int = 0,
    val efficiencyPercent: Int = 0,
    val awakenings: Int = 0,
    val isEstimated: Boolean = false,
    val averageSleepMinutes: Int = 0,
    val sleepDebtMinutes: Int = 0,
    val sleepGoalMinutes: Int = 480,
    val consistencyScore: Int = 0,
    val daysAnalyzed: Int = 0,
)

@HiltViewModel
class SleepInsightViewModel
    @Inject
    constructor(
        private val repository: SleepRepository,
        getDailySummary: GetDailySleepSummaryUseCase,
        getWeeklyTrends: GetWeeklySleepTrendsUseCase,
    ) : ViewModel() {
        fun refreshPermissionStatus(): Boolean = repository.hasUsagePermission()

        val state: StateFlow<SleepInsightUiState> =
            midnightTickerFlow()
                .flatMapLatest { date ->
                    combine(
                        getDailySummary(date),
                        getWeeklyTrends(date),
                        repository.observeLiveSignal(),
                    ) { daily, trends, signal ->
                        val hasPermission = repository.hasUsagePermission()
                        val status =
                            when {
                                !hasPermission -> InsightStatus.PermissionRequired
                                !signal.isTracking && daily == null && (trends == null || trends.daysAnalyzed == 0) ->
                                    InsightStatus.TrackingOff
                                daily == null && (trends == null || trends.daysAnalyzed == 0) ->
                                    InsightStatus.Empty
                                else -> InsightStatus.Ready
                            }
                        SleepInsightUiState(
                            status = status,
                            totalSleepMinutes = daily?.totalSleepMinutes ?: 0,
                            timeInBedMinutes = daily?.timeInBedMinutes ?: 0,
                            efficiencyPercent = daily?.sleepEfficiencyPercent ?: 0,
                            awakenings = daily?.awakenings ?: 0,
                            isEstimated = daily?.isEstimated ?: false,
                            averageSleepMinutes = trends?.averageSleepMinutes ?: 0,
                            sleepDebtMinutes = trends?.totalSleepDebtMinutes ?: 0,
                            sleepGoalMinutes = trends?.sleepGoalMinutes ?: 480,
                            consistencyScore = trends?.consistencyScore ?: 0,
                            daysAnalyzed = trends?.daysAnalyzed ?: 0,
                        )
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = SleepInsightUiState(status = InsightStatus.Loading),
                )
    }
