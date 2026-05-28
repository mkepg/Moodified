package com.moodified.app.presentation.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.BuildConfig
import com.moodified.app.domain.usecase.privacy.DeleteAllUserDataUseCase
import com.moodified.app.domain.usecase.privacy.ExportUserDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PrivacyOpStatus {
    data object Idle : PrivacyOpStatus
    data object InProgress : PrivacyOpStatus
    data class Success(val message: String) : PrivacyOpStatus
    data class Error(val message: String) : PrivacyOpStatus
}

data class PrivacyUiState(
    val exportStatus: PrivacyOpStatus = PrivacyOpStatus.Idle,
    val deleteStatus: PrivacyOpStatus = PrivacyOpStatus.Idle,
    val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL,
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val exportUserData: ExportUserDataUseCase,
    private val deleteAllUserData: DeleteAllUserDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    fun exportData() {
        if (_uiState.value.exportStatus is PrivacyOpStatus.InProgress) return
        _uiState.update { it.copy(exportStatus = PrivacyOpStatus.InProgress) }
        viewModelScope.launch {
            runCatching { exportUserData() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(exportStatus = PrivacyOpStatus.Success("Saved to ${result.location}"))
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(exportStatus = PrivacyOpStatus.Error(e.message ?: "Export failed"))
                    }
                }
        }
    }

    fun deleteAllData() {
        if (_uiState.value.deleteStatus is PrivacyOpStatus.InProgress) return
        _uiState.update { it.copy(deleteStatus = PrivacyOpStatus.InProgress) }
        viewModelScope.launch {
            runCatching { deleteAllUserData() }
                .onSuccess {
                    _uiState.update {
                        it.copy(deleteStatus = PrivacyOpStatus.Success("All data deleted."))
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(deleteStatus = PrivacyOpStatus.Error(e.message ?: "Delete failed"))
                    }
                }
        }
    }

    fun dismissExportStatus() {
        _uiState.update { it.copy(exportStatus = PrivacyOpStatus.Idle) }
    }

    fun dismissDeleteStatus() {
        _uiState.update { it.copy(deleteStatus = PrivacyOpStatus.Idle) }
    }
}
