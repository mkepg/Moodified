package com.moodified.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val prefs: OnboardingPreferencesDataSource,
    ) : ViewModel() {
        val slides: List<OnboardingSlide> = defaultOnboardingSlides

        fun completeOnboarding(onFinished: () -> Unit) {
            viewModelScope.launch {
                prefs.markCompleted()
                onFinished()
            }
        }
    }
