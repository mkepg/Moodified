package com.karamay.app.presentation.devtools.sleepmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.DailySleepSummary
import com.karamay.app.domain.model.SleepSignal
import com.karamay.app.domain.model.SleepTrends
import com.karamay.app.domain.repository.SleepRepository
import com.karamay.app.domain.usecase.sleep.GetDailySleepSummaryUseCase
import com.karamay.app.domain.usecase.sleep.GetWeeklySleepTrendsUseCase
import com.karamay.app.presentation.devtools.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class SleepMonitorUiState(
    val permission:    PermissionState    = PermissionState.Idle,
    val isTracking:    Boolean            = false,
    val liveSignal:    SleepSignal        = SleepSignal(),
    val latestSummary: DailySleepSummary? = null,
    val weeklyTrends:  SleepTrends?       = null,
)

@HiltViewModel
class SleepMonitorViewModel @Inject constructor(
    // REPLACED: Injected the Repository directly instead of the deleted pass-through Use Cases.
    // Notice we KEPT GetDailySleepSummaryUseCase and GetWeeklySleepTrendsUseCase because they have real logic.
    private val repository: SleepRepository,
    private val getDailySummary:   GetDailySleepSummaryUseCase,
    private val getWeeklyTrends:   GetWeeklySleepTrendsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SleepMonitorUiState())
    val state: StateFlow<SleepMonitorUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(isTracking = repository.isTracking) }

        repository.observeLiveSignal()
            .onEach { signal ->
                _state.update { it.copy(liveSignal = signal, isTracking = signal.isTracking) }
            }
            .launchIn(viewModelScope)

        getDailySummary(LocalDate.now())
            .onEach { summary -> _state.update { it.copy(latestSummary = summary) } }
            .launchIn(viewModelScope)

        getWeeklyTrends(LocalDate.now())
            .onEach { trends -> _state.update { it.copy(weeklyTrends = trends) } }
            .launchIn(viewModelScope)
    }

    fun onPermissionGranted() {
        _state.update { it.copy(permission = PermissionState.Granted) }
    }

    // Fix C: renamed canAskAgain → canRequestAgain to match DevToolsState.PermissionState.Denied.
    fun onPermissionDenied(canRequestAgain: Boolean) {
        _state.update { it.copy(permission = PermissionState.Denied(canRequestAgain)) }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(permission = PermissionState.Requested) }
    }

    fun startTracking() {
        repository.startTracking()
    }

    fun stopTracking() {
        repository.stopTracking()
    }
}