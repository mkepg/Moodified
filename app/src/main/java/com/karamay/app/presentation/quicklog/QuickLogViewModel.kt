package com.karamay.app.presentation.quicklog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.Arousal
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.model.Valence
import com.karamay.app.domain.usecase.mood.SaveMoodEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuickLogStep { VALENCE, AROUSAL, SUCCESS }

data class QuickLogUiState(
    val step: QuickLogStep        = QuickLogStep.VALENCE,
    val selectedValence: Valence? = null,
    val selectedArousal: Arousal? = null,
    val isSaving: Boolean         = false,
    val error: String?            = null
)

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val saveMoodEntry: SaveMoodEntryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickLogUiState())
    val uiState: StateFlow<QuickLogUiState> = _uiState.asStateFlow()

    fun selectValence(valence: Valence) {
        _uiState.update { it.copy(selectedValence = valence) }
    }

    fun goToArousal() {
        if (_uiState.value.selectedValence == null) return
        _uiState.update { it.copy(step = QuickLogStep.AROUSAL) }
    }

    fun goBackToValence() {
        _uiState.update { it.copy(step = QuickLogStep.VALENCE) }
    }

    fun selectArousal(arousal: Arousal) {
        _uiState.update { it.copy(selectedArousal = arousal) }
    }

    fun save() {
        val state = _uiState.value
        val valence = state.selectedValence ?: return
        val arousal = state.selectedArousal ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            saveMoodEntry(MoodEntry(valence = valence, arousal = arousal))
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, step = QuickLogStep.SUCCESS) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun reset() {
        _uiState.value = QuickLogUiState()
    }
}
