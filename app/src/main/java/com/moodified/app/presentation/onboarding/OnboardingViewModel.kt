package com.moodified.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource
import com.moodified.app.domain.repository.ActivityRepository
import com.moodified.app.domain.repository.InteractionRepository
import com.moodified.app.domain.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val prefs: OnboardingPreferencesDataSource,
        private val activityRepository: ActivityRepository,
        private val sleepRepository: SleepRepository,
        private val interactionRepository: InteractionRepository,
    ) : ViewModel() {
        val slides: List<OnboardingSlide> = defaultOnboardingSlides

        fun completeOnboarding(onFinished: () -> Unit) {
            viewModelScope.launch {
                prefs.markCompleted()
                onFinished()
            }
        }

        fun onActivityRecognitionGranted() {
            activityRepository.startTracking()
        }

        fun onUsageAccessGranted() {
            sleepRepository.startTracking()
            interactionRepository.startTracking()
        }
    }
