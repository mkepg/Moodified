package com.karamay.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.DirectionsCarFilled
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDateTime

enum class ActivityIntensity {
    SEDENTARY,
    IN_VEHICLE,   // Fix #9 (prior pass): distinct from SEDENTARY
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

    fun icon(): ImageVector = when (this) {
        SEDENTARY  -> Icons.Rounded.AirlineSeatReclineNormal
        IN_VEHICLE -> Icons.Rounded.DirectionsCarFilled
        LIGHT      -> Icons.AutoMirrored.Rounded.DirectionsWalk
        MODERATE   -> Icons.AutoMirrored.Rounded.DirectionsRun
        VIGOROUS   -> Icons.Rounded.LocalFireDepartment
    }
}

/**
 * Fix A: Added isTracking field, mirroring SleepSignal.isTracking.
 *
 * SleepSignal carried isTracking so SleepMonitorViewModel could derive its
 * tracking indicator purely from the live signal Flow. ActivitySignal was missing
 * this field, forcing ActivityMonitorViewModel to manage isTracking as separate
 * optimistic state that could diverge from the repository. The field is now
 * persisted through ActivitySignalBus alongside the other snapshot values, so
 * after a process kill the ViewModel receives the correct tracking state on the
 * very first Flow emission.
 */
data class ActivitySignal(
    val steps: Int                    = 0,
    val intensity: ActivityIntensity  = ActivityIntensity.SEDENTARY,
    val activeMinutes: Int            = 0,
    val sedentaryMinutes: Int         = 0,
    val stepSensorAvailable: Boolean  = true,
    val accelAvailable: Boolean       = true,
    val timestamp: LocalDateTime      = LocalDateTime.now(),
    val isTracking: Boolean           = false,    // Fix A: symmetric with SleepSignal
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
