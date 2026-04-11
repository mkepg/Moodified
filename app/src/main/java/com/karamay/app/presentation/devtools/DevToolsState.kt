package com.karamay.app.presentation.devtools

// midnightTickerFlow has been moved to com.karamay.app.core.utils.MidnightTickerFlow.kt
// Import it from there: import com.karamay.app.core.utils.midnightTickerFlow

data class MonitorUiState<Signal, DailySummary, WeeklyData>(
    val isLoading:    Boolean         = false,
    val isTracking:   Boolean         = false,
    val liveSignal:   Signal,
    val todaySummary: DailySummary?   = null,
    val weeklyData:   WeeklyData?     = null,
)
