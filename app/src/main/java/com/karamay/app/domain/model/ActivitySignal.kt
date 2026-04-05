package com.karamay.app.domain.model

import java.time.LocalDateTime

enum class ActivityIntensity {
    SEDENTARY,
    IN_VEHICLE,
    LIGHT,
    MODERATE,
    VIGOROUS;

    fun displayLabel(): String = when (this) {
        SEDENTARY  -> "Sedentary"
        IN_VEHICLE -> "In vehicle"
        LIGHT      -> "Light"
        MODERATE   -> "Moderate"
        VIGOROUS   -> "Vigorous"
    }
    // REMOVED icon() function entirely from the domain model
}

data class ActivitySignal(
    val steps: Int                    = 0,
    val intensity: ActivityIntensity  = ActivityIntensity.SEDENTARY,
    val activeMinutes: Int            = 0,
    val sedentaryMinutes: Int         = 0,
    val stepSensorAvailable: Boolean  = true,
    val accelAvailable: Boolean       = true,
    val timestamp: LocalDateTime      = LocalDateTime.now(),
    val isTracking: Boolean           = false,
) {
    fun toArousalEstimate(): Arousal {
        val totalMinutes = (activeMinutes + sedentaryMinutes).coerceAtLeast(1)
        val activeRatio  = activeMinutes.toFloat() / totalMinutes
        val stepCadence  = if (activeMinutes > 0 && stepSensorAvailable)
            steps.toFloat() / activeMinutes else 0f

        return when {
            intensity == ActivityIntensity.VIGOROUS
                    && activeMinutes >= 10                             -> Arousal.HIGH
            intensity == ActivityIntensity.MODERATE
                    && (activeRatio >= 0.40f || stepCadence >= 100f)  -> Arousal.HIGH
            steps >= 6_000 && activeRatio >= 0.35f                    -> Arousal.HIGH
            intensity == ActivityIntensity.MODERATE                   -> Arousal.MID
            intensity == ActivityIntensity.LIGHT
                    && activeRatio >= 0.25f                           -> Arousal.MID
            steps >= 2_500 && activeRatio >= 0.15f                    -> Arousal.MID
            else                                                       -> Arousal.LOW
        }
    }
}