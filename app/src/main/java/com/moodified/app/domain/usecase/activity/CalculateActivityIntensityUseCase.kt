package com.moodified.app.domain.usecase.activity

import com.moodified.app.domain.model.activity.ActivityIntensity
import javax.inject.Inject

class CalculateActivityIntensityUseCase @Inject constructor() {

    companion object {
        private const val LIGHT_UPGRADE_SPM      = 20
        private const val LIGHT_DOWNGRADE_SPM    = 10
        private const val MODERATE_UPGRADE_SPM   = 80
        private const val MODERATE_DOWNGRADE_SPM = 65
        private const val VIGOROUS_UPGRADE_SPM   = 110
        private const val VIGOROUS_DOWNGRADE_SPM = 95
        private const val MAX_CADENCE_SPM        = 200
        private const val PHYSIOLOGICAL_MAX_SPM  = 220 // Absolute max human sprint cadence
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

        var validDeltaSteps = 0
        var validElapsedMs = 0L

        // Evaluate tick-by-tick to isolate and discard mechanical micro-spikes
        for (i in 1 until window.size) {
            val prev = window[i - 1]
            val curr = window[i]

            val tickMs = curr.first - prev.first
            val tickSteps = curr.second - prev.second
            val tickMins = tickMs / 60_000.0

            if (tickMins > 0) {
                val tickSpm = tickSteps / tickMins
                // Only accumulate steps if the movement is physically possible for a human
                if (tickSpm <= PHYSIOLOGICAL_MAX_SPM) {
                    validDeltaSteps += tickSteps
                    validElapsedMs += tickMs
                }
            }
        }

        val validElapsedMins = validElapsedMs / 60_000.0
        if (validElapsedMins <= 0.0) return 0

        return (validDeltaSteps / validElapsedMins).toInt().coerceAtMost(MAX_CADENCE_SPM)
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