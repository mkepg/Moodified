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
    data object RequiresSystemSettings : PermissionState
}

sealed interface MonitorError {
    data object HardwareMissing   : MonitorError
    data object PermissionDenied  : MonitorError
    data class  Unknown(val msg: String) : MonitorError
}

// FIX #1 (UI Flickering): isLoading now defaults to FALSE.
//
// Previously `isLoading = true` was the default, which meant every time a new
// MonitorUiState was constructed (e.g. on ViewModel subscription restart or
// recomposition) the UI would briefly show a "loading" / zeroed state before
// real data arrived from the StateFlow. Because stateIn(...) already holds the
// last known value in its replay cache, there is no genuine loading phase on
// re-entry — the first emission is immediate. Defaulting to false eliminates
// the flicker entirely; screens that want an explicit loading skeleton can set
// it to true only in their *actual* initialValue when no cached data exists.
data class MonitorUiState<Signal, DailySummary, WeeklyData>(
    val isLoading:    Boolean         = false,
    val permission:   PermissionState = PermissionState.Idle,
    val isTracking:   Boolean         = false,
    val liveSignal:   Signal,
    val todaySummary: DailySummary?   = null,
    val weeklyData:   WeeklyData?     = null,
    val error:        MonitorError?   = null,
)

fun midnightTickerFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000L)
    }
}.distinctUntilChanged()