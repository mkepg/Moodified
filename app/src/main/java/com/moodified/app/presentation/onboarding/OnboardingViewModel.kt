package com.moodified.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.coordination.TrackingCoordinator
import com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val prefs: OnboardingPreferencesDataSource,
        private val trackingCoordinator: TrackingCoordinator,
    ) : ViewModel() {
        val slides: List<OnboardingSlide> = defaultOnboardingSlides

        fun completeOnboarding(onFinished: () -> Unit) {
            viewModelScope.launch {
                prefs.markCompleted()
                onFinished()
            }
        }

        fun onActivityRecognitionGranted() {
            trackingCoordinator.startActivity()
        }

        fun onUsageAccessGranted() {
            trackingCoordinator.startSleep()
            trackingCoordinator.startInteraction()
        }
    }
