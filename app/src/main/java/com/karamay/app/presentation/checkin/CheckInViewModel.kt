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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import javax.inject.Inject

data class CheckInUiState(
    val greeting: String           = "",
    val todayDate: String          = "",
    val latestEntry: MoodEntry?    = null,
    val todayEntries: List<MoodEntry> = emptyList(),
    val isLoading: Boolean         = true
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val getTodayEntries: GetTodayEntriesUseCase,
    // Fix #34: GetLatestEntryUseCase now returns Flow<MoodEntry?>.
    private val getLatestEntry: GetLatestEntryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        loadGreeting()
        observeTodayEntries()
        observeLatestEntry()   // Fix #34: reactive observation replaces one-shot load
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
        getTodayEntries()
            .onEach { entries ->
                _uiState.update { it.copy(todayEntries = entries, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    // Fix #34: Replaced one-shot viewModelScope.launch { val latest = getLatestEntry() }
    // with a continuous Flow collector. latestEntry now updates automatically whenever
    // a new MoodEntry is saved (e.g. via QuickLogSheet) while this screen is visible.
    private fun observeLatestEntry() {
        getLatestEntry()
            .onEach { entry -> _uiState.update { it.copy(latestEntry = entry) } }
            .launchIn(viewModelScope)
    }
}
