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
import com.karamay.app.presentation.devtools.MonitorError
import com.karamay.app.presentation.devtools.MonitorUiState
import com.karamay.app.presentation.devtools.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

typealias SleepWeeklyData     = SleepTrends
typealias SleepMonitorUiState = MonitorUiState<SleepSignal, DailySleepSummary, SleepWeeklyData>

val SleepMonitorUiState.weeklyTrends: SleepTrends? get() = weeklyData

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    private val repository:           SleepRepository,
    private val observeSignalUseCase: ObserveSleepSignalUseCase,
    private val getDailySummary:      GetDailySleepSummaryUseCase,
    private val getWeeklyTrends:      GetWeeklySleepTrendsUseCase,
) : ViewModel() {

    // FIXED: Synchronous initial state calculation to prevent "pop-in" flicker
    private val permissionState = MutableStateFlow<PermissionState>(
        if (repository.hasUsagePermission()) PermissionState.Idle else PermissionState.RequiresSystemSettings
    )
    private val errorState      = MutableStateFlow<MonitorError?>(null)

    val state: StateFlow<SleepMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date)
            ) { daily, trends -> Pair(daily, trends) }
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
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        // FIXED: Synchronous initialValue to ensure the UI renders the correct state on frame 1
        initialValue = SleepMonitorUiState(
            isLoading  = true,
            permission = if (repository.hasUsagePermission()) PermissionState.Idle else PermissionState.RequiresSystemSettings,
            isTracking = repository.isTracking,
            liveSignal = SleepSignal(
                isTracking       = repository.isTracking,
                hasActiveSession = repository.isTracking
            )
        )
    )

    init { updatePermissionState() }

    fun onResume() {
        updatePermissionState()
    }

    private fun updatePermissionState() {
        if (!repository.hasUsagePermission()) {
            permissionState.value = PermissionState.RequiresSystemSettings
        } else if (permissionState.value is PermissionState.RequiresSystemSettings
            || permissionState.value is PermissionState.Idle
        ) {
            permissionState.value = PermissionState.Idle
        }
    }

    fun onPermissionGranted()                        { permissionState.value = PermissionState.Granted }
    fun onPermissionDenied(canRequestAgain: Boolean) { permissionState.value = PermissionState.Denied(canRequestAgain) }
    fun onPermissionRequested()                      { permissionState.value = PermissionState.Requested }

    fun startTracking() {
        val started = repository.startTracking()
        if (!started) {
            permissionState.value = if (!repository.hasUsagePermission())
                PermissionState.RequiresSystemSettings else PermissionState.Denied(true)
        } else {
            errorState.value      = null
            permissionState.value = PermissionState.Granted
        }
    }

    fun stopTracking() {
        repository.stopTracking()
        if (repository.hasUsagePermission()) permissionState.value = PermissionState.Idle
    }
}