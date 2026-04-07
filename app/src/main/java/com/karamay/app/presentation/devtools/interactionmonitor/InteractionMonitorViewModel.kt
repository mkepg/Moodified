package com.karamay.app.presentation.devtools.interactionmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.interaction.InteractionDailySummary
import com.karamay.app.domain.model.interaction.InteractionSignal
import com.karamay.app.domain.model.interaction.InteractionTrends
import com.karamay.app.domain.repository.InteractionRepository
import com.karamay.app.domain.usecase.interaction.GetDailyInteractionSummaryUseCase
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.karamay.app.domain.usecase.interaction.GetWeeklyInteractionTrendsUseCase
import com.karamay.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
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

data class InteractionMonitorUiState(
    val permission:      PermissionState               = PermissionState.Idle,
    val isTracking:      Boolean                       = false,
    val liveSignal:      InteractionSignal             = InteractionSignal(),
    val todaySummary:    InteractionDailySummary?      = null,
    val weeklyTrends:    InteractionTrends?            = null,
    val weeklySummaries: List<InteractionDailySummary> = emptyList(),
)

@HiltViewModel
class InteractionMonitorViewModel @Inject constructor(
    private val repository:         InteractionRepository,
    private val observeSignal:      ObserveInteractionSignalUseCase,
    private val getDailySummary:    GetDailyInteractionSummaryUseCase,
    private val getWeeklyTrends:    GetWeeklyInteractionTrendsUseCase,
    private val getWeeklySummaries: GetWeeklyInteractionSummariesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InteractionMonitorUiState())
    val state: StateFlow<InteractionMonitorUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(isTracking = repository.isTracking) }

        observeSignal()
            .onEach { signal ->
                _state.update { it.copy(liveSignal = signal, isTracking = signal.isTracking) }
            }
            .launchIn(viewModelScope)

        getDailySummary(LocalDate.now())
            .onEach { summary ->
                _state.update { it.copy(todaySummary = summary) }
            }
            .launchIn(viewModelScope)

        getWeeklyTrends(LocalDate.now())
            .onEach { trends ->
                _state.update { it.copy(weeklyTrends = trends) }
            }
            .launchIn(viewModelScope)

        getWeeklySummaries(LocalDate.now())
            .onEach { summaries ->
                _state.update { it.copy(weeklySummaries = summaries) }
            }
            .launchIn(viewModelScope)
    }

    fun hasUsagePermission(): Boolean = repository.hasUsagePermission()

    fun onPermissionGranted()                        { _state.update { it.copy(permission = PermissionState.Granted) } }
    fun onPermissionDenied(canRequestAgain: Boolean) { _state.update { it.copy(permission = PermissionState.Denied(canRequestAgain)) } }
    fun onPermissionRequested()                      { _state.update { it.copy(permission = PermissionState.Requested) } }

    fun startTracking() { repository.startTracking() }
    fun stopTracking()  { repository.stopTracking() }
    fun resetSession()  { repository.resetSession() }
}