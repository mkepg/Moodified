package com.karamay.app.presentation.devtools.sleepmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSignal
import com.karamay.app.domain.model.sleep.SleepTrends
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import com.karamay.app.domain.usecase.sleep.ObserveSleepSignalUseCase
import com.karamay.app.presentation.devtools.MonitorError
import com.karamay.app.presentation.devtools.MonitorUiState
import com.karamay.app.presentation.devtools.PermissionState
import com.karamay.app.presentation.devtools.midnightTickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// --- Bridge Data Structures to Prevent UI Breakage ---
typealias SleepWeeklyData = SleepTrends
typealias SleepMonitorUiState = MonitorUiState<SleepSignal, DailySleepSummary, SleepWeeklyData>

val SleepMonitorUiState.weeklyTrends: SleepTrends? get() = weeklyData
// ---------------------------------------------------

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    private val repository: SleepRepository,
    private val observeSignalUseCase: ObserveSleepSignalUseCase,
    private val getDailySummary: GetDailySleepSummaryUseCase,
    private val getWeeklyTrends: GetWeeklySleepTrendsUseCase,
) : ViewModel() {

    private val permissionState = MutableStateFlow<PermissionState>(PermissionState.Idle)
    private val errorState = MutableStateFlow<MonitorError?>(null)

    val state: StateFlow<SleepMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date)
            ) { daily, trends ->
                Pair(daily, trends)
            }
        },
        observeSignalUseCase(),
        permissionState,
        errorState
    ) { (daily, trends), signal, perm, err ->
        SleepMonitorUiState(
            isLoading    = false,
            permission   = perm,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = trends,
            error        = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SleepMonitorUiState(
            isTracking = repository.isTracking,
            liveSignal = SleepSignal(isTracking = repository.isTracking, hasActiveSession = repository.isTracking)
        )
    )

    fun onResume(hasPermission: Boolean) {
        if (hasPermission) {
            if (permissionState.value !is PermissionState.Granted) {
                permissionState.value = if (repository.isTracking) PermissionState.Granted else PermissionState.Idle
            }
        } else {
            if (permissionState.value is PermissionState.Granted) {
                permissionState.value = PermissionState.Denied(true)
            }
        }
    }

    fun onPermissionGranted() { permissionState.value = PermissionState.Granted }
    fun onPermissionDenied(canRequestAgain: Boolean) { permissionState.value = PermissionState.Denied(canRequestAgain) }
    fun onPermissionRequested() { permissionState.value = PermissionState.Requested }

    fun startTracking() { repository.startTracking() }
    fun stopTracking()  { repository.stopTracking() }
    fun resetSession()  { repository.resetSession() }
}