package com.karamay.app.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.coordination.DateSelectionCoordinator
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.repository.MoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class MoodEntryUiModel(
    val id: Long,
    val valence: Valence,
    val arousal: Arousal,
    val displayTime: String
)

data class DayMoodSummary(
    val date: LocalDate,
    val dayLabel: String,
    val dateNumber: String,
    val representativeEntry: MoodEntryUiModel?,
    val totalEntries: Int
)

data class CheckInUiState(
    val greeting: String = "",
    val todayDate: String = "",
    val todayEntries: List<MoodEntryUiModel> = emptyList(),
    val recentDaySummaries: List<DayMoodSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isIgnoringBattery: Boolean = true
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: MoodRepository,
    private val dateSelectionCoordinator: DateSelectionCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val dayLabelFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val dateNumberFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())

    init {
        loadGreeting()
        observeTodayEntries()
        observeRecentHistory()
    }

    private fun loadGreeting() {
        _uiState.update {
            it.copy(
                greeting  = DateTimeUtils.getGreeting(),
                todayDate = DateTimeUtils.formatDisplayDate(LocalDateTime.now())
            )
        }
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
                val summaries = (6 downTo 0).map { daysBack ->
                    val date = today.minusDays(daysBack.toLong())
                    val dayEntries = allEntries
                        .filter { it.timestamp.toLocalDate() == date }
                        .sortedByDescending { it.timestamp }

                    DayMoodSummary(
                        date                = date,
                        dayLabel            = date.format(dayLabelFormatter),
                        dateNumber          = date.format(dateNumberFormatter),
                        representativeEntry = dayEntries.firstOrNull()?.toUiModel(),
                        totalEntries        = dayEntries.size
                    )
                }
                _uiState.update { it.copy(recentDaySummaries = summaries) }
            }
            .launchIn(viewModelScope)
    }

    fun updateBatteryOptimizationStatus(isIgnoring: Boolean) {
        _uiState.update { it.copy(isIgnoringBattery = isIgnoring) }
    }

    // Call this from the CheckIn UI when a date is selected from the 7-day row / widget
    fun selectDateFromWidget(date: LocalDate) {
        dateSelectionCoordinator.selectDate(date)
    }

    private fun MoodEntry.toUiModel(): MoodEntryUiModel {
        return MoodEntryUiModel(
            id          = this.id,
            valence     = this.valence,
            arousal     = this.arousal,
            displayTime = DateTimeUtils.formatDisplayTime(this.timestamp)
        )
    }
}