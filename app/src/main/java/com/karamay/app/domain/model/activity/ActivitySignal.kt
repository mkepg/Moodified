package com.karamay.app.domain.model.activity

import com.karamay.app.domain.model.mood.Arousal
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
}

data class ActivitySignal(
    val steps: Int                    = 0,
    val intensity: ActivityIntensity  = ActivityIntensity.SEDENTARY,
    val activeMinutes: Int            = 0,
    val sedentaryMinutes: Int         = 0,
    val instantCadenceSpm: Int        = 0,
    val stepSensorAvailable: Boolean  = true,
    val accelAvailable: Boolean       = true,
    val timestamp: LocalDateTime      = LocalDateTime.now(),
    val isTracking: Boolean           = false,
    val hasActiveSession: Boolean     = false,
)