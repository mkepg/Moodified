package com.karamay.app.presentation.devtools

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

sealed interface PermissionState {
    data object Idle      : PermissionState
    data object Requested : PermissionState
    data object Granted   : PermissionState
    data class  Denied(val canRequestAgain: Boolean) : PermissionState
    // Added for Phase 3 readiness (Usage Access requires settings redirect)
    data object RequiresSystemSettings : PermissionState
}

sealed interface MonitorError {
    data object HardwareMissing   : MonitorError
    data object PermissionDenied  : MonitorError
    data class  Unknown(val msg: String) : MonitorError
}

data class MonitorUiState<Signal, DailySummary, WeeklyData>(
    val isLoading:    Boolean         = true,
    val permission:   PermissionState = PermissionState.Idle,
    val isTracking:   Boolean         = false,
    val liveSignal:   Signal,
    val todaySummary: DailySummary?   = null,
    val weeklyData:   WeeklyData?     = null,
    val error:        MonitorError?   = null,
)

/**
 * Emits the current LocalDate, re-evaluating every 60 seconds.
 * distinctUntilChanged() ensures downstream flows only trigger once at midnight.
 */
fun midnightTickerFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000L)
    }
}.distinctUntilChanged()