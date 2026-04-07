package com.karamay.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.core.utils.DateTimeUtils
import com.karamay.app.domain.model.mood.Arousal
import com.karamay.app.domain.model.mood.MoodEntry
import com.karamay.app.domain.model.mood.Valence
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.domain.usecase.mood.GetMoodHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI models
// ---------------------------------------------------------------------------

data class MoodHistoryEntryUiModel(
    val id: Long,
    val valence: Valence,
    val arousal: Arousal,
    val displayTime: String,
    val note: String?,
)

data class MoodHistoryDayUiModel(
    val date: LocalDate,
    val dateLabel: String,       // e.g. "Today", "Yesterday", "Monday, Apr 7"
    val entryCount: Int,
    val entries: List<MoodHistoryEntryUiModel>,
)

// ---------------------------------------------------------------------------
// Sealed UI state
// ---------------------------------------------------------------------------

sealed interface MoodHistoryUiState {
    data object Loading : MoodHistoryUiState
    data object Empty   : MoodHistoryUiState
    data class  Success(val days: List<MoodHistoryDayUiModel>) : MoodHistoryUiState
    data class  Error(val message: String) : MoodHistoryUiState
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class MoodHistoryViewModel @Inject constructor(
    private val getMoodHistory: GetMoodHistoryUseCase,
    private val repository: MoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MoodHistoryUiState>(MoodHistoryUiState.Loading)
    val uiState: StateFlow<MoodHistoryUiState> = _uiState.asStateFlow()

    /** Tracks the id currently pending a delete-confirmation, null if none. */
    private val _pendingDeleteId = MutableStateFlow<Long?>(null)
    val pendingDeleteId: StateFlow<Long?> = _pendingDeleteId.asStateFlow()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        getMoodHistory()
            .onStart { _uiState.value = MoodHistoryUiState.Loading }
            .onEach { grouped ->
                if (grouped.isEmpty()) {
                    _uiState.value = MoodHistoryUiState.Empty
                } else {
                    val today     = LocalDate.now()
                    val yesterday = today.minusDays(1)
                    val days = grouped.map { (date, entries) ->
                        MoodHistoryDayUiModel(
                            date       = date,
                            dateLabel  = when (date) {
                                today     -> "Today"
                                yesterday -> "Yesterday"
                                else      -> formatHistoryDate(date)
                            },
                            entryCount = entries.size,
                            entries    = entries
                                .sortedByDescending { it.timestamp }
                                .map { it.toUiModel() },
                        )
                    }
                    _uiState.value = MoodHistoryUiState.Success(days)
                }
            }
            .catch { e ->
                _uiState.value = MoodHistoryUiState.Error(
                    e.message ?: "Failed to load mood history"
                )
            }
            .launchIn(viewModelScope)
    }

    fun requestDelete(id: Long) {
        _pendingDeleteId.value = id
    }

    fun cancelDelete() {
        _pendingDeleteId.value = null
    }

    fun confirmDelete() {
        val id = _pendingDeleteId.value ?: return
        _pendingDeleteId.value = null
        viewModelScope.launch {
            runCatching { repository.deleteEntry(id) }
        }
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private fun MoodEntry.toUiModel() = MoodHistoryEntryUiModel(
        id          = id,
        valence     = valence,
        arousal     = arousal,
        displayTime = DateTimeUtils.formatDisplayTime(timestamp),
        note        = note,
    )

    private fun formatHistoryDate(date: LocalDate): String {
        val now = LocalDate.now()
        return when {
            date.year == now.year ->
                date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
            else ->
                date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        }
    }
}
