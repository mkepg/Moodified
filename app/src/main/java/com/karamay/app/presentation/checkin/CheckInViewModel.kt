package com.karamay.app.presentation.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.LocalDateTime
import javax.inject.Inject

data class MoodEntryUiModel(
    val id: Long,
    val valence: Valence,
    val arousal: Arousal,
    val displayTime: String
)

data class CheckInUiState(
    val greeting: String = "",
    val todayDate: String = "",
    val todayEntries: List<MoodEntryUiModel> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        loadGreeting()
        observeTodayEntries()
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

    private fun MoodEntry.toUiModel(): MoodEntryUiModel {
        return MoodEntryUiModel(
            id = this.id,
            valence = this.valence,
            arousal = this.arousal,
            displayTime = DateTimeUtils.formatDisplayTime(this.timestamp)
        )
    }
}