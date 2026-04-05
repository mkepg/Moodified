package com.karamay.app.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.Arousal
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.model.Valence
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

// 1. The UI Model specifically crafted for the View
data class MoodEntryUiModel(
    val id: Long,
    val valence: Valence,
    val arousal: Arousal,
    val displayTime: String
)

data class CheckInUiState(
    val greeting: String = "",
    val todayDate: String = "",
    val latestEntry: MoodEntryUiModel? = null,
    val todayEntries: List<MoodEntryUiModel> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val getTodayEntries: GetTodayEntriesUseCase,
    private val getLatestEntry: GetLatestEntryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        loadGreeting()
        observeTodayEntries()
        observeLatestEntry()
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
                val uiModels = entries.map { it.toUiModel() }
                _uiState.update { it.copy(todayEntries = uiModels, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeLatestEntry() {
        getLatestEntry()
            .onEach { entry ->
                _uiState.update { it.copy(latestEntry = entry?.toUiModel()) }
            }
            .launchIn(viewModelScope)
    }

    // 2. Mapper function formatting data before it hits the UI
    private fun MoodEntry.toUiModel(): MoodEntryUiModel {
        return MoodEntryUiModel(
            id = this.id,
            valence = this.valence,
            arousal = this.arousal,
            displayTime = DateTimeUtils.formatDisplayTime(this.timestamp)
        )
    }
}