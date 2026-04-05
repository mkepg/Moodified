package com.karamay.app.presentation.quicklog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.model.Arousal
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.model.Valence
import com.karamay.app.domain.repository.MoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuickLogStep { VALENCE, AROUSAL, SUCCESS }

data class QuickLogUiState(
    val step: QuickLogStep        = QuickLogStep.VALENCE,
    val selectedValence: Valence? = null,
    val selectedArousal: Arousal? = null,
    val isSaving: Boolean         = false,
)

// Fix #36: Error is a one-shot event rather than persistent state.
// Keeping it in UiState causes the same error toast to re-appear on every recomposition.
// A Channel fires exactly once and is consumed by the collector in QuickLogSheet.
sealed interface QuickLogEvent {
    data class SaveError(val message: String) : QuickLogEvent
}

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    // REPLACED: Injected the Repository directly instead of the deleted SaveMoodEntryUseCase
    private val repository: MoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickLogUiState())
    val uiState: StateFlow<QuickLogUiState> = _uiState.asStateFlow()

    // Fix #36: Events channel — capacity = 1 so a burst of rapid taps doesn't queue duplicate errors.
    private val _events = Channel<QuickLogEvent>(Channel.CONFLATED)
    val events = _events.receiveAsFlow()

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
        // Fix #35: Atomic CAS — if isSaving is already true the update returns the old state
        // unchanged and the subsequent check short-circuits, preventing duplicate inserts
        // from rapid double-taps before the first coroutine propagates the state update.
        var alreadySaving = false
        _uiState.update { current ->
            if (current.isSaving) {
                alreadySaving = true
                current
            } else {
                current.copy(isSaving = true)
            }
        }
        if (alreadySaving) return

        val state   = _uiState.value
        val valence = state.selectedValence ?: run {
            _uiState.update { it.copy(isSaving = false) }
            return
        }
        val arousal = state.selectedArousal ?: run {
            _uiState.update { it.copy(isSaving = false) }
            return
        }

        viewModelScope.launch {
            // UPDATED: Wrapped the raw repository call in runCatching to emulate the old use case behavior
            runCatching {
                repository.insertEntry(MoodEntry(valence = valence, arousal = arousal))
            }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, step = QuickLogStep.SUCCESS) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false) }
                    // Fix #36: Send the error as a one-shot event so QuickLogSheet can
                    // show a Snackbar without the toast re-triggering on recomposition.
                    _events.send(QuickLogEvent.SaveError(e.message ?: "Failed to save entry"))
                }
        }
    }

    fun reset() {
        _uiState.value = QuickLogUiState()
    }
}