package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.activity.ActivityIntensity
import javax.inject.Inject

class CalculateActivityIntensityUseCase @Inject constructor() {
    companion object {
        // Real-World Biomechanical Evaluation Thresholds:
        // Sedentary: 0-19 SPM
        // Light:     20-79 SPM
        // Moderate:  80-109 SPM
        // Vigorous:  110+ SPM

        // Hysteresis constants (Upgrade triggers the new state, Downgrade drops it back)
        private const val LIGHT_UPGRADE_SPM      = 20
        private const val LIGHT_DOWNGRADE_SPM    = 10

        private const val MODERATE_UPGRADE_SPM   = 80
        private const val MODERATE_DOWNGRADE_SPM = 65

        private const val VIGOROUS_UPGRADE_SPM   = 110
        private const val VIGOROUS_DOWNGRADE_SPM = 95

        private const val MAX_CADENCE_SPM        = 200
    }

    operator fun invoke(
        cadenceWindow: List<Pair<Long, Int>>,
        currentIntensity: ActivityIntensity
    ): ActivityIntensity {
        val spm = computeSpm(cadenceWindow)
        return applyHysteresis(spm, currentIntensity)
    }

    private fun computeSpm(window: List<Pair<Long, Int>>): Int {
        if (window.size < 2) return 0

        val oldest = window.first()
        val newest = window.last()
        val elapsedMin = (newest.first - oldest.first) / 60_000.0

        if (elapsedMin <= 0) return 0

        val deltaSteps = newest.second - oldest.second
        if (deltaSteps < 0) return 0

        return (deltaSteps / elapsedMin).toInt().coerceAtMost(MAX_CADENCE_SPM)
    }

    private fun applyHysteresis(spm: Int, current: ActivityIntensity): ActivityIntensity {
        return when (current) {
            ActivityIntensity.SEDENTARY -> when {
                spm >= VIGOROUS_UPGRADE_SPM  -> ActivityIntensity.VIGOROUS
                spm >= MODERATE_UPGRADE_SPM  -> ActivityIntensity.MODERATE
                spm >= LIGHT_UPGRADE_SPM     -> ActivityIntensity.LIGHT
                else                         -> ActivityIntensity.SEDENTARY
            }
            ActivityIntensity.LIGHT -> when {
                spm >= VIGOROUS_UPGRADE_SPM  -> ActivityIntensity.VIGOROUS
                spm >= MODERATE_UPGRADE_SPM  -> ActivityIntensity.MODERATE
                spm >= LIGHT_DOWNGRADE_SPM   -> ActivityIntensity.LIGHT
                else                         -> ActivityIntensity.SEDENTARY
            }
            ActivityIntensity.MODERATE -> when {
                spm >= VIGOROUS_UPGRADE_SPM   -> ActivityIntensity.VIGOROUS
                spm >= MODERATE_DOWNGRADE_SPM -> ActivityIntensity.MODERATE
                spm >= LIGHT_DOWNGRADE_SPM    -> ActivityIntensity.LIGHT
                else                          -> ActivityIntensity.SEDENTARY
            }
            ActivityIntensity.VIGOROUS -> when {
                spm >= VIGOROUS_DOWNGRADE_SPM -> ActivityIntensity.VIGOROUS
                spm >= MODERATE_DOWNGRADE_SPM -> ActivityIntensity.MODERATE
                spm >= LIGHT_DOWNGRADE_SPM    -> ActivityIntensity.LIGHT
                else                          -> ActivityIntensity.SEDENTARY
            }
            ActivityIntensity.IN_VEHICLE -> ActivityIntensity.IN_VEHICLE
        }
    }
}