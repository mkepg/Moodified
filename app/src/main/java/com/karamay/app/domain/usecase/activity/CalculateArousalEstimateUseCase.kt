package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.activity.ActivitySignal
import com.karamay.app.domain.model.mood.Arousal
import javax.inject.Inject

class CalculateArousalEstimateUseCase @Inject constructor() {

    operator fun invoke(signal: ActivitySignal): Arousal {
        val totalMinutes = (signal.activeMinutes + signal.sedentaryMinutes).coerceAtLeast(1)
        val activeRatio  = signal.activeMinutes.toFloat() / totalMinutes
        val currentCadence = signal.instantCadenceSpm

        return when {
            signal.intensity == ActivityIntensity.VIGOROUS && signal.activeMinutes >= 10 -> Arousal.HIGH
            signal.intensity == ActivityIntensity.MODERATE && (activeRatio >= 0.40f || currentCadence >= 100) -> Arousal.HIGH
            signal.steps >= 6_000 && activeRatio >= 0.35f -> Arousal.HIGH
            signal.intensity == ActivityIntensity.MODERATE -> Arousal.MID
            signal.intensity == ActivityIntensity.LIGHT && activeRatio >= 0.25f -> Arousal.MID
            signal.steps >= 2_500 && activeRatio >= 0.15f -> Arousal.MID
            else -> Arousal.LOW
        }
    }
}