package com.karamay.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.presentation.checkin.MoodEntryUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val datesWithEntries: Set<LocalDate> = emptySet(),
    val selectedDateEntries: List<MoodEntryUiModel> = emptyList(),
    val isLoadingEntries: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // A dedicated flow for the selected date decouples the flatMapLatest chain from
    // the broader _uiState. Without this, any _uiState emission (e.g. datesWithEntries
    // updating from getAllEntries) would re-trigger the flatMapLatest, causing
    // isLoadingEntries to toggle true → false and flicker the entry list.
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    init {
        repository.getAllEntries()
            .onEach { allEntries ->
                val dates = allEntries.map { it.timestamp.toLocalDate() }.toSet()
                _uiState.update { it.copy(datesWithEntries = dates) }
            }
            .launchIn(viewModelScope)

        _selectedDate
            .onEach { _uiState.update { it.copy(isLoadingEntries = true) } }
            .flatMapLatest { date -> repository.getEntriesForDate(date) }
            .onEach { entries ->
                val uiModels = entries
                    .sortedByDescending { it.timestamp }
                    .map { it.toUiModel() }
                _uiState.update { it.copy(selectedDateEntries = uiModels, isLoadingEntries = false) }
            }
            .launchIn(viewModelScope)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun goToPreviousMonth() {
        _uiState.update { it.copy(displayedMonth = it.displayedMonth.minusMonths(1)) }
    }

    fun goToNextMonth() {
        val next = _uiState.value.displayedMonth.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _uiState.update { it.copy(displayedMonth = next) }
        }
    }

    private fun MoodEntry.toUiModel() = MoodEntryUiModel(
        id          = this.id,
        valence     = this.valence,
        arousal     = this.arousal,
        displayTime = DateTimeUtils.formatDisplayTime(this.timestamp)
    )
}
