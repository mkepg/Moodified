package com.karamay.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDateTime

/**
 * Physical activity intensity classified from accelerometer StdDev.
 * Thresholds derived from peer-reviewed HAR literature (e.g. Bao & Intille 2004).
 */
enum class ActivityIntensity {
    SEDENTARY,
    LIGHT,
    MODERATE,
    VIGOROUS;

    fun displayLabel(): String = when (this) {
        SEDENTARY -> "Sedentary"
        LIGHT     -> "Light"
        MODERATE  -> "Moderate"
        VIGOROUS  -> "Vigorous"
    }

    fun icon(): ImageVector = when (this) {
        SEDENTARY -> Icons.Rounded.AirlineSeatReclineNormal
        LIGHT     -> Icons.Rounded.DirectionsWalk
        MODERATE  -> Icons.Rounded.DirectionsRun
        VIGOROUS  -> Icons.Rounded.Bolt
    }

    /**
     * Maps raw activity intensity to an Arousal estimate for the mood
     * inference engine. Kept here so the inference layer stays thin.
     */
    fun toArousalEstimate(): Arousal = when (this) {
        SEDENTARY -> Arousal.LOW
        LIGHT     -> Arousal.LOW
        MODERATE  -> Arousal.MID
        VIGOROUS  -> Arousal.HIGH
    }

}

/**
 * Immutable snapshot of the current physical activity state.
 * Published at most once per second from [ActivityRepository].
 *
 * @param steps          Steps counted in the current tracking session.
 * @param intensity      Current movement intensity derived from accelerometer StdDev window.
 * @param activeMinutes  Minutes in which intensity > SEDENTARY since session start.
 * @param sedentaryMinutes Minutes in which intensity == SEDENTARY since session start.
 * @param stepSensorAvailable  Whether the device has TYPE_STEP_COUNTER hardware.
 * @param accelAvailable       Whether the device has TYPE_ACCELEROMETER hardware.
 * @param timestamp      Wall-clock time of this snapshot.
 */
data class ActivitySignal(
    val steps: Int                    = 0,
    val intensity: ActivityIntensity  = ActivityIntensity.SEDENTARY,
    val activeMinutes: Int            = 0,
    val sedentaryMinutes: Int         = 0,
    val stepSensorAvailable: Boolean  = true,
    val accelAvailable: Boolean       = true,
    val timestamp: LocalDateTime      = LocalDateTime.now()
)
