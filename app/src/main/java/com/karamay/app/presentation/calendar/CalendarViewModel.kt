package com.karamay.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.presentation.checkin.MoodEntryUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
class CalendarViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _displayedMonth = MutableStateFlow(YearMonth.now())

    // Combine serves as the single source of truth. Any change to the database,
    // the selected date, or the displayed month instantly recalculates the exact UI state.
    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getAllEntries(),
        _selectedDate,
        _displayedMonth
    ) { allEntries, selectedDate, displayedMonth ->

        // 1. Calculate the exact count of entries per day
        val dailyCounts = allEntries.groupingBy { it.timestamp.toLocalDate() }.eachCount()

        // 2. Instantly filter the existing list for the selected date
        val selectedEntries = allEntries
            .filter { it.timestamp.toLocalDate() == selectedDate }
            .sortedByDescending { it.timestamp }
            .map { it.toUiModel() }

        CalendarUiState(
            displayedMonth = displayedMonth,
            selectedDate = selectedDate,
            dailyEntryCounts = dailyCounts,
            selectedDateEntries = selectedEntries,
            isLoadingEntries = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState(isLoadingEntries = true)
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
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

    private fun MoodEntry.toUiModel() = MoodEntryUiModel(
        id          = this.id,
        valence     = this.valence,
        arousal     = this.arousal,
        displayTime = DateTimeUtils.formatDisplayTime(this.timestamp)
    )
}