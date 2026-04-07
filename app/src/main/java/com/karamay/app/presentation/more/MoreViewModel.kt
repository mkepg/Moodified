package com.karamay.app.presentation.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karamay.app.domain.usecase.devtools.SeedMockMoodDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val seedMockMoodDataUseCase: SeedMockMoodDataUseCase
) : ViewModel() {

    fun injectMockData() {
        viewModelScope.launch {
            seedMockMoodDataUseCase()
        }
    }
}