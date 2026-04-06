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

/**
 * Live snapshot of the user's activity state.
 *
 * ## [instantCadenceSpm] — new field (Bug 5 fix)
 *
 * The original code computed "step cadence" in the UI as:
 *   `steps.toFloat() / activeMinutes`
 * which is the *session-average* steps-per-active-minute — not a cadence at all.
 * A 2-hour session with 5,000 steps and 40 active minutes would report
 * 125 "spm", which happens to look plausible but is meaningless and unstable
 * (it grows as the session progresses even if the user is stationary).
 *
 * [instantCadenceSpm] carries the live steps-per-minute derived from the 60-second
 * cadence window in [ActivityRepositoryImpl]. The UI should display this value for
 * "current cadence" and use it in arousal estimation. The session-average step rate
 * can still be computed as `steps / (activeMinutes.coerceAtLeast(1))` when needed
 * for summary purposes, but is not used for real-time classification.
 *
 * ## [toArousalEstimate] fix
 *
 * The arousal estimate formerly used the session-step-rate as a cadence proxy.
 * It now uses [instantCadenceSpm] for the cadence comparison, which reflects the
 * user's current pace rather than their accumulated average.
 */
data class ActivitySignal(
    val steps: Int                    = 0,
    val intensity: ActivityIntensity  = ActivityIntensity.SEDENTARY,
    val activeMinutes: Int            = 0,
    val sedentaryMinutes: Int         = 0,
    /** Live steps-per-minute from the 60-second cadence window. 0 when stationary. */
    val instantCadenceSpm: Int        = 0,
    val stepSensorAvailable: Boolean  = true,
    val accelAvailable: Boolean       = true,
    val timestamp: LocalDateTime      = LocalDateTime.now(),
    val isTracking: Boolean           = false,
    val hasActiveSession: Boolean     = false,
) {
    /**
     * Estimates the user's physiological arousal from activity signals.
     *
     * ### Changes from original
     * - Uses [instantCadenceSpm] instead of `steps / activeMinutes` for cadence comparisons.
     *   This reflects the current pace rather than a session-long average, making the
     *   arousal estimate responsive to the user's actual activity in the last minute.
     * - The MODERATE + cadence condition now correctly evaluates whether the *current*
     *   pace is brisk, not whether the user took many steps hours ago.
     */
    fun toArousalEstimate(): Arousal {
        val totalMinutes = (activeMinutes + sedentaryMinutes).coerceAtLeast(1)
        val activeRatio  = activeMinutes.toFloat() / totalMinutes

        // instantCadenceSpm = 0 when the user is stationary (window is empty/stale).
        // This correctly prevents a high-cadence reading from a prior walk from
        // keeping arousal elevated after the user has been sitting for 30+ minutes.
        val currentCadence = instantCadenceSpm

        return when {
            intensity == ActivityIntensity.VIGOROUS
                    && activeMinutes >= 10                                    -> Arousal.HIGH
            intensity == ActivityIntensity.MODERATE
                    && (activeRatio >= 0.40f || currentCadence >= 100)        -> Arousal.HIGH
            steps >= 6_000 && activeRatio >= 0.35f                            -> Arousal.HIGH
            intensity == ActivityIntensity.MODERATE                           -> Arousal.MID
            intensity == ActivityIntensity.LIGHT && activeRatio >= 0.25f      -> Arousal.MID
            steps >= 2_500 && activeRatio >= 0.15f                            -> Arousal.MID
            else                                                              -> Arousal.LOW
        }
    }
}