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
}

data class ActivitySignal(
    val steps: Int                    = 0,
    val intensity: ActivityIntensity  = ActivityIntensity.SEDENTARY,
    val activeMinutes: Int            = 0,
    val sedentaryMinutes: Int         = 0,
    val stepSensorAvailable: Boolean  = true,
    val accelAvailable: Boolean       = true,
    val timestamp: LocalDateTime      = LocalDateTime.now()
) {
    /**
     * Estimates arousal from the full activity picture rather than just the
     * instantaneous intensity label.
     *
     * Three factors are combined:
     *  - [intensity]    – what the body is doing right now
     *  - [activeRatio]  – what fraction of the tracked window was active
     *                     (duration of effort, not just a snapshot)
     *  - [stepCadence]  – steps per active minute, a proxy for true exertion
     *                     that catches brisk walking classified as LIGHT
     */
    fun toArousalEstimate(): Arousal {
        val totalMinutes  = (activeMinutes + sedentaryMinutes).coerceAtLeast(1)
        val activeRatio   = activeMinutes.toFloat() / totalMinutes
        // cadence = 0 when step sensor unavailable or no active minutes logged
        val stepCadence   = if (activeMinutes > 0 && stepSensorAvailable)
            steps.toFloat() / activeMinutes else 0f

        return when {
            // ── HIGH ─────────────────────────────────────────────────────────
            // Vigorous burst of meaningful duration
            intensity == ActivityIntensity.VIGOROUS
                    && activeMinutes >= 10                              -> Arousal.HIGH

            // Moderate effort sustained for a large chunk of the window,
            // OR a high step cadence (≥100 spm ≈ brisk/running pace)
            intensity == ActivityIntensity.MODERATE
                    && (activeRatio >= 0.40f || stepCadence >= 100f)   -> Arousal.HIGH

            // High step volume even if the sensor classified it lower
            steps >= 6_000 && activeRatio >= 0.35f                     -> Arousal.HIGH

            // ── MID ──────────────────────────────────────────────────────────
            // Moderate effort present but not enough to reach HIGH
            intensity == ActivityIntensity.MODERATE                    -> Arousal.MID

            // Light activity covering a decent portion of the window
            intensity == ActivityIntensity.LIGHT
                    && activeRatio >= 0.25f                            -> Arousal.MID

            // Noticeable step volume with some sustained movement
            steps >= 2_500 && activeRatio >= 0.15f                     -> Arousal.MID

            // ── LOW ──────────────────────────────────────────────────────────
            else                                                        -> Arousal.LOW
        }
    }
}