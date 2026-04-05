package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.ActivityIntensity
import javax.inject.Inject

class CalculateActivityIntensityUseCase @Inject constructor() {

    companion object {
        private const val MAX_CADENCE_SPM             = 160
        private const val MODERATE_CADENCE_THRESHOLD  = 100
        private const val SEDENTARY_CADENCE_THRESHOLD = 10
    }

    operator fun invoke(
        cadenceWindow: List<Pair<Long, Int>>,
        currentIntensity: ActivityIntensity
    ): ActivityIntensity {
        val spm = if (cadenceWindow.size >= 2) {
            val oldest     = cadenceWindow.first()
            val newest     = cadenceWindow.last()
            val elapsedMin = (newest.first - oldest.first) / 60_000.0
            val delta      = newest.second - oldest.second

            if (elapsedMin > 0) {
                (delta / elapsedMin).toInt().coerceAtMost(MAX_CADENCE_SPM)
            } else 0
        } else 0

        val cadenceIntensity = when {
            spm >= MODERATE_CADENCE_THRESHOLD  -> ActivityIntensity.MODERATE
            spm >= SEDENTARY_CADENCE_THRESHOLD -> ActivityIntensity.LIGHT
            else                               -> ActivityIntensity.SEDENTARY
        }

        // VIGOROUS overrides SEDENTARY drop-offs temporarily
        return when (currentIntensity) {
            ActivityIntensity.VIGOROUS ->
                if (cadenceIntensity == ActivityIntensity.SEDENTARY) ActivityIntensity.SEDENTARY
                else currentIntensity
            else -> cadenceIntensity
        }
    }
}