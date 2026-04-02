package com.karamay.app.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.usecase.mood.GetLatestEntryUseCase
import com.karamay.app.domain.usecase.mood.GetTodayEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckInUiState(
    val greeting: String       = "",
    val todayDate: String      = "",
    val latestEntry: MoodEntry? = null,
    val todayEntries: List<MoodEntry> = emptyList(),
    val isLoading: Boolean     = true
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val getTodayEntries: GetTodayEntriesUseCase,
    private val getLatestEntry: GetLatestEntryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        loadGreeting()
        observeTodayEntries()
        loadLatestEntry()
    }

    private fun loadGreeting() {
        _uiState.update {
            it.copy(
                greeting  = DateTimeUtils.getGreeting(),
                todayDate = DateTimeUtils.formatDisplayDate(java.time.LocalDateTime.now())
            )
        }
    }

    private fun observeTodayEntries() {
        viewModelScope.launch {
            getTodayEntries().collect { entries ->
                _uiState.update {
                    it.copy(todayEntries = entries, isLoading = false)
                }
            }
        }
    }

    private fun loadLatestEntry() {
        viewModelScope.launch {
            val latest = getLatestEntry()
            _uiState.update { it.copy(latestEntry = latest) }
        }
    }
}
