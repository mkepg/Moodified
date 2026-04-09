package com.karamay.app.presentation.devtools.activitymonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.midnightTickerFlow
import com.karamay.app.domain.model.activity.ActivityDailySummary
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.activity.ActivityTrends
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.usecase.activity.GetDailyActivitySummaryUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.karamay.app.domain.usecase.activity.GetWeeklyActivityTrendsUseCase
import com.karamay.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.karamay.app.presentation.devtools.MonitorError
import com.karamay.app.presentation.devtools.MonitorUiState
import com.karamay.app.presentation.devtools.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ActivityWeeklyData(
    val trends:    ActivityTrends?,
    val summaries: List<ActivityDailySummary>
)

typealias ActivityMonitorUiState = MonitorUiState<ActivitySignal, ActivityDailySummary, ActivityWeeklyData>

val ActivityMonitorUiState.weeklySummaries: List<ActivityDailySummary>
    get() = weeklyData?.summaries ?: emptyList()
val ActivityMonitorUiState.hardwareError: String?
    get() = (error as? MonitorError.Unknown)?.msg

@HiltViewModel
class ActivityMonitorViewModel @Inject constructor(
    private val repository:          ActivityRepository,
    private val observeSignalUseCase: ObserveActivitySignalUseCase,
    private val getDailySummary:      GetDailyActivitySummaryUseCase,
    private val getWeeklySummaries:   GetWeeklyActivitySummariesUseCase,
    private val getWeeklyTrends:      GetWeeklyActivityTrendsUseCase,
) : ViewModel() {

    private val permissionState = MutableStateFlow<PermissionState>(PermissionState.Idle)
    private val errorState      = MutableStateFlow<MonitorError?>(null)

    val state: StateFlow<ActivityMonitorUiState> = combine(
        // Phase 2: midnightTickerFlow moved to core/utils — imported from there.
        midnightTickerFlow().flatMapLatest { date ->
            combine(
                getDailySummary(date),
                getWeeklyTrends(date),
                getWeeklySummaries(date)
            ) { daily, trends, summaries -> Triple(daily, trends, summaries) }
        },
        observeSignalUseCase(),
        permissionState,
        errorState
    ) { (daily, trends, summaries), signal, perm, err ->
        ActivityMonitorUiState(
            // Phase 2: isLoading = false only after the first real emission;
            // initial value below uses isLoading = true so screens can show a spinner.
            isLoading    = false,
            permission   = perm,
            isTracking   = signal.isTracking,
            liveSignal   = signal,
            todaySummary = daily,
            weeklyData   = ActivityWeeklyData(trends, summaries),
            error        = err
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        // Phase 2: initialValue uses isLoading = true so the screen shows a
        // skeleton/spinner while the first flow emission is in flight, instead
        // of flashing empty sections.
        initialValue = ActivityMonitorUiState(
            isLoading    = true,
            isTracking   = repository.isTracking,
            liveSignal   = ActivitySignal(
                isTracking       = repository.isTracking,
                hasActiveSession = repository.isTracking
            )
        )
    )

    fun onResume(hasPermission: Boolean) {
        if (hasPermission) {
            if (permissionState.value !is PermissionState.Granted) {
                permissionState.value =
                    if (repository.isTracking) PermissionState.Granted else PermissionState.Idle
            }
        } else {
            if (permissionState.value is PermissionState.Granted) {
                permissionState.value = PermissionState.Denied(true)
            }
        }
    }

    fun onPermissionGranted()                         { permissionState.value = PermissionState.Granted }
    fun onPermissionDenied(canRequestAgain: Boolean)  { permissionState.value = PermissionState.Denied(canRequestAgain) }
    fun onPermissionRequested()                       { permissionState.value = PermissionState.Requested }

    fun startTracking() {
        val started = repository.startTracking()
        errorState.value = if (!started) MonitorError.HardwareMissing else null
    }

    fun stopTracking()   { repository.stopTracking() }
    fun resetSession()   { repository.resetSession() }
}
