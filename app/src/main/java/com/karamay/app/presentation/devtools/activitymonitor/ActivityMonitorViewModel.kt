package com.karamay.app.presentation.devtools.activitymonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
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

data class ActivityMonitorUiState(
    val permission:      PermissionState         = PermissionState.Idle,
    val isTracking:      Boolean                 = false,
    val signal:          ActivitySignal           = ActivitySignal(),
    val hardwareError:   String?                 = null,
    val todaySummary:    ActivityDailySummary?   = null,
    val weeklySummaries: List<ActivityDailySummary> = emptyList(),
)

@HiltViewModel
class ActivityMonitorViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val getDailySummary: GetDailyActivitySummaryUseCase,
    private val getWeeklySummaries: GetWeeklyActivitySummariesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityMonitorUiState())
    val state: StateFlow<ActivityMonitorUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(isTracking = repository.isTracking) }

        repository.observeSignal()
            .onEach { signal ->
                _state.update { it.copy(signal = signal, isTracking = signal.isTracking) }
            }
            .launchIn(viewModelScope)

        getDailySummary(LocalDate.now())
            .onEach { summary ->
                _state.update { it.copy(todaySummary = summary) }
            }
            .launchIn(viewModelScope)

        getWeeklySummaries(LocalDate.now())
            .onEach { summaries ->
                _state.update { it.copy(weeklySummaries = summaries) }
            }
            .launchIn(viewModelScope)
    }

    fun onPermissionGranted() {
        _state.update { it.copy(permission = PermissionState.Granted) }
    }

    fun onPermissionDenied(canRequestAgain: Boolean) {
        _state.update { it.copy(permission = PermissionState.Denied(canRequestAgain)) }
    }

    fun onPermissionRequested() {
        _state.update { it.copy(permission = PermissionState.Requested) }
    }

    fun startTracking() {
        val started = repository.startTracking()
        if (!started) {
            _state.update {
                it.copy(hardwareError = "No compatible sensors found on this device.")
            }
        } else {
            _state.update { it.copy(hardwareError = null) }
        }
    }

    fun stopTracking() {
        repository.stopTracking()
    }

    fun resetSession() {
        repository.resetSession()
    }
}
