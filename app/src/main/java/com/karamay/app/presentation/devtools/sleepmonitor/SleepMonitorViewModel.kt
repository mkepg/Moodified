package com.karamay.app.presentation.devtools.sleepmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.sleep.DailySleepSummary
import com.karamay.app.domain.model.sleep.SleepSignal
import com.karamay.app.domain.model.sleep.SleepStatus
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

typealias SleepWeeklyData = SleepTrends
typealias SleepMonitorUiState = MonitorUiState<SleepSignal, DailySleepSummary, SleepWeeklyData>

val SleepMonitorUiState.weeklyTrends: SleepTrends? get() = weeklyData

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    private val repository:          SleepRepository,
    private val observeSignalUseCase: ObserveSleepSignalUseCase,
    private val getDailySummary:      GetDailySleepSummaryUseCase,
    private val getWeeklyTrends:      GetWeeklySleepTrendsUseCase,
) : ViewModel() {

    private val permissionState  = MutableStateFlow<PermissionState>(PermissionState.Idle)
    private val errorState       = MutableStateFlow<MonitorError?>(null)

    private val _awakeOverride = MutableStateFlow<SleepSignal?>(null)

    private val effectiveSignal: Flow<SleepSignal> = combine(
        observeSignalUseCase(),
        _awakeOverride
    ) { real, override ->
        if (override != null && real.isTracking && real.confidence == 0) override else real
    }

    val state: StateFlow<SleepMonitorUiState> = combine(
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date)
            ) { daily, trends -> Pair(daily, trends) }
        },
        effectiveSignal,
        permissionState,
        errorState
    ) { (daily, trends), signal, perm, err ->
        SleepMonitorUiState(
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
            isTracking = repository.isTracking,
            liveSignal = SleepSignal(
                isTracking       = repository.isTracking,
                hasActiveSession = repository.isTracking
            )
        )
    )

    init {
        updatePermissionState()
    }

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

    fun onPermissionGranted() {
        permissionState.value = PermissionState.Granted
    }

    fun onPermissionDenied(canRequestAgain: Boolean) {
        permissionState.value = PermissionState.Denied(canRequestAgain)
    }

    fun onPermissionRequested() {
        permissionState.value = PermissionState.Requested
    }

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
            _awakeOverride.value  = SleepSignal(
                isTracking       = true,
                hasActiveSession = true,
                status           = SleepStatus.AWAKE,
                confidence       = 100,
                deviceMotion     = 0,
            )
        }
    }

    fun stopTracking() {
        _awakeOverride.value = null
        repository.stopTracking()
        if (repository.hasUsagePermission()) {
            permissionState.value = PermissionState.Idle
        }
    }
}