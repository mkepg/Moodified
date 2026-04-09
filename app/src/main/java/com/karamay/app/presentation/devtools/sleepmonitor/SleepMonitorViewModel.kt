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

    private val permissionState = MutableStateFlow<PermissionState>(PermissionState.Idle)
    private val errorState      = MutableStateFlow<MonitorError?>(null)

    // P5 / Phase 2: _awakeOverride removed. With the P0 fix in place the real
    // SleepSignal is populated from SharedPreferences at instantiation and kept
    // live by SleepReceiver + the 5-minute inference poll loop. No fake signal
    // injection is needed, and there is no risk of a leaked override on process death.

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
        initialValue = SleepMonitorUiState(
            isLoading  = true,
            isTracking = repository.isTracking,
            liveSignal = SleepSignal(
                isTracking       = repository.isTracking,
                hasActiveSession = repository.isTracking
            )
        )
    )

    init { updatePermissionState() }

    fun onResume() { updatePermissionState() }

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
        if (!repository.hasUsagePermission()) {
            errorState.value      = MonitorError.PermissionDenied
            permissionState.value = PermissionState.RequiresSystemSettings
            return
        }
        val started = repository.startTracking()
        if (!started) {
            errorState.value      = MonitorError.PermissionDenied
            permissionState.value = PermissionState.RequiresSystemSettings
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
