package com.moodified.app.presentation.devtools

// midnightTickerFlow has been moved to com.moodified.app.core.utils.MidnightTickerFlow.kt
// Import it from there: import com.moodified.app.core.utils.midnightTickerFlow

data class MonitorUiState<Signal, DailySummary, WeeklyData>(
    val isLoading:    Boolean         = false,
    val isTracking:   Boolean         = false,
    val liveSignal:   Signal,
    val todaySummary: DailySummary?   = null,
    val weeklyData:   WeeklyData?     = null,
)
