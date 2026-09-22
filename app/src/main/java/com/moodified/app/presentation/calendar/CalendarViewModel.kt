package com.moodified.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.coordination.DateSelectionCoordinator
import com.moodified.app.core.utils.DateTimeUtils
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.repository.MoodRepository
import com.moodified.app.presentation.checkin.MoodEntryUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val dailyEntryCounts: Map<LocalDate, Int> = emptyMap(),
    val selectedDateEntries: List<MoodEntryUiModel> = emptyList(),
    val isLoadingEntries: Boolean = false,
)

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val repository: MoodRepository,
        private val dateSelectionCoordinator: DateSelectionCoordinator,
    ) : ViewModel() {
        private val _displayedMonth = MutableStateFlow(YearMonth.from(dateSelectionCoordinator.selectedDate.value))

        init {
            viewModelScope.launch {
                dateSelectionCoordinator.selectedDate.collectLatest { date ->
                    val targetMonth = YearMonth.from(date)
                    if (_displayedMonth.value != targetMonth) {
                        _displayedMonth.value = targetMonth
                    }
                }
            }
        }

        val uiState: StateFlow<CalendarUiState> =
            combine(
                repository.getAllEntries(),
                dateSelectionCoordinator.selectedDate,
                _displayedMonth,
            ) { allEntries, selectedDate, displayedMonth ->
                val dailyCounts = allEntries.groupingBy { it.timestamp.toLocalDate() }.eachCount()
                val selectedEntries =
                    allEntries
                        .filter { it.timestamp.toLocalDate() == selectedDate }
                        .sortedByDescending { it.timestamp }
                        .map { it.toUiModel() }

                CalendarUiState(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    dailyEntryCounts = dailyCounts,
                    selectedDateEntries = selectedEntries,
                    isLoadingEntries = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    CalendarUiState(
                        selectedDate = dateSelectionCoordinator.selectedDate.value,
                        displayedMonth = YearMonth.from(dateSelectionCoordinator.selectedDate.value),
                        isLoadingEntries = true,
                    ),
            )

        fun selectDate(date: LocalDate) {
            dateSelectionCoordinator.selectDate(date)
        }

        fun goToPreviousMonth() {
            _displayedMonth.value = _displayedMonth.value.minusMonths(1)
        }

        fun goToNextMonth() {
            val next = _displayedMonth.value.plusMonths(1)
            if (!next.isAfter(YearMonth.now())) {
                _displayedMonth.value = next
            }
        }

        private fun MoodEntry.toUiModel() =
            MoodEntryUiModel(
                id = this.id,
                valence = this.valence,
                arousal = this.arousal,
                displayTime = DateTimeUtils.formatDisplayTime(this.timestamp),
                note = this.note,
            )
    }
