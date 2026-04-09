package com.karamay.app.presentation.devtools

// midnightTickerFlow has been moved to com.karamay.app.core.utils.MidnightTickerFlow.kt
// Import it from there: import com.karamay.app.core.utils.midnightTickerFlow

sealed interface PermissionState {
    data object Idle                                   : PermissionState
    data object Requested                              : PermissionState
    data object Granted                                : PermissionState
    data class  Denied(val canRequestAgain: Boolean)   : PermissionState
    data object RequiresSystemSettings                 : PermissionState
}

sealed interface MonitorError {
    data object HardwareMissing                        : MonitorError
    data object PermissionDenied                       : MonitorError
    // Phase 3: surface Room/DB failures to the UI so users know data collection
    // is silently failing rather than staring at empty sections indefinitely.
    data object DatabaseError                          : MonitorError
    data class  Unknown(val msg: String)               : MonitorError
}

data class MonitorUiState<Signal, DailySummary, WeeklyData>(
    val isLoading:    Boolean         = false,
    val permission:   PermissionState = PermissionState.Idle,
    val isTracking:   Boolean         = false,
    val liveSignal:   Signal,
    val todaySummary: DailySummary?   = null,
    val weeklyData:   WeeklyData?     = null,
    val error:        MonitorError?   = null,
)
