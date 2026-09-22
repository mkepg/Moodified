package com.moodified.app.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.coordination.DateSelectionCoordinator
import com.moodified.app.core.coordination.TrackingCoordinator
import com.moodified.app.core.permission.PermissionDenialTracker
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.domain.usecase.intervention.ObserveTopCareInterventionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class MoodEntryUiModel(
    val id: Long,
    val valence: Valence,
    val arousal: Arousal,
    val displayTime: String,
    val note: String? = null,
)

data class DayMoodSummary(
    val date: LocalDate,
    val dayLabel: String,
    val dateNumber: String,
    val representativeEntry: MoodEntryUiModel?,
    val totalEntries: Int,
)

data class CheckInUiState(
    val greeting: String = "",
    val todayDate: String = "",
    val todayEntries: List<MoodEntryUiModel> = emptyList(),
    val recentDaySummaries: List<DayMoodSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isIgnoringBattery: Boolean = true,
    val activityDenials: Int = 0,
    val notificationDenials: Int = 0,
)

val CheckInUiState.isActivityPermanentlyDenied: Boolean
    get() = activityDenials >= PermissionDenialTracker.MAX_DENIALS

val CheckInUiState.isNotifPermanentlyDenied: Boolean
    get() = notificationDenials >= PermissionDenialTracker.MAX_DENIALS

@HiltViewModel
class CheckInViewModel
    @Inject
    constructor(
        private val repository: MoodRepository,
        private val dateSelectionCoordinator: DateSelectionCoordinator,
        private val trackingCoordinator: TrackingCoordinator,
        private val interactionRepository: InteractionRepository,
        private val permissionDenialTracker: PermissionDenialTracker,
        private val observeTopCareInterventionUseCase: ObserveTopCareInterventionUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CheckInUiState())
        val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

        val topCareIntervention: StateFlow<InterventionAction?> =
            observeTopCareInterventionUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = null,
                )

        private val dayLabelFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        private val dateNumberFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())

        init {
            observeDateChanges()
            observeTodayEntries()
            observeRecentHistory()
            observeDenialCounts()
        }

        private fun observeDateChanges() {
            midnightTickerFlow()
                .onEach { today ->
                    _uiState.update {
                        it.copy(
                            greeting = DateTimeUtils.getGreeting(),
                            todayDate = DateTimeUtils.formatDisplayDate(LocalDateTime.now()),
                        )
                    }
                    observeRecentHistory()
                }
                .launchIn(viewModelScope)
        }

        val hasUsageAccess: Boolean
            get() = interactionRepository.hasUsagePermission()

        fun startAllTracking() {
            trackingCoordinator.startActivity()
            trackingCoordinator.startSleep()
            trackingCoordinator.startInteraction()
        }

        fun stopAllTracking() {
            trackingCoordinator.stopActivity()
            trackingCoordinator.stopSleep()
            trackingCoordinator.stopInteraction()
        }

        fun syncTrackingState(
            hasActivity: Boolean,
            hasNotif: Boolean,
            hasUsage: Boolean,
        ) {
            if (!hasNotif) {
                stopAllTracking()
                return
            }
            if (!hasActivity) {
                trackingCoordinator.stopActivity()
            }
            if (!hasUsage) {
                trackingCoordinator.stopSleep()
                trackingCoordinator.stopInteraction()
            }
        }

        fun recordActivityRecognitionDenial() = permissionDenialTracker.recordActivityRecognitionDenial()

        fun recordPostNotificationDenial() = permissionDenialTracker.recordPostNotificationDenial()

        fun resetActivityRecognitionDenial() = permissionDenialTracker.resetActivityRecognition()

        fun resetPostNotificationDenial() = permissionDenialTracker.resetPostNotification()

        private fun observeDenialCounts() {
            combine(
                permissionDenialTracker.activityRecognitionDenials,
                permissionDenialTracker.postNotificationDenials,
            ) { activity, notification ->
                _uiState.update { it.copy(activityDenials = activity, notificationDenials = notification) }
            }.launchIn(viewModelScope)
        }

        private fun observeTodayEntries() {
            repository.getTodayEntries()
                .onEach { entries ->
                    val uiModels = entries.map { it.toUiModel() }
                    _uiState.update { it.copy(todayEntries = uiModels, isLoading = false) }
                }
                .launchIn(viewModelScope)
        }

        private fun observeRecentHistory() {
            repository.getAllEntries()
                .onEach { allEntries ->
                    val today = LocalDate.now()
                    val summaries =
                        (6 downTo 0).map { daysBack ->
                            val date = today.minusDays(daysBack.toLong())
                            val dayEntries =
                                allEntries
                                    .filter { it.timestamp.toLocalDate() == date }
                                    .sortedByDescending { it.timestamp }

                            DayMoodSummary(
                                date = date,
                                dayLabel = date.format(dayLabelFormatter),
                                dateNumber = date.format(dateNumberFormatter),
                                representativeEntry = dayEntries.firstOrNull()?.toUiModel(),
                                totalEntries = dayEntries.size,
                            )
                        }
                    _uiState.update { it.copy(recentDaySummaries = summaries) }
                }
                .launchIn(viewModelScope)
        }

        fun updateBatteryOptimizationStatus(isIgnoring: Boolean) {
            _uiState.update { it.copy(isIgnoringBattery = isIgnoring) }
        }

        fun selectDateFromWidget(date: LocalDate) {
            dateSelectionCoordinator.selectDate(date)
        }

        private fun MoodEntry.toUiModel(): MoodEntryUiModel {
            return MoodEntryUiModel(
                id = this.id,
                valence = this.valence,
                arousal = this.arousal,
                displayTime = DateTimeUtils.formatDisplayTime(this.timestamp),
                note = this.note,
            )
        }
    }
