package com.moodified.app.presentation.devtools.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.devtools.DiagnosticsRepository
import com.moodified.app.core.devtools.DiagnosticsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DebugDrawerUiState(
    val snapshot: DiagnosticsSnapshot? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class DebugDrawerViewModel
    @Inject
    constructor(
        repository: DiagnosticsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<DebugDrawerUiState> =
            repository.snapshot()
                .map { DebugDrawerUiState(snapshot = it, isLoading = false) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DebugDrawerUiState(),
                )
    }
