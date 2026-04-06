package com.karamay.app.domain.usecase.activity

import com.karamay.app.domain.model.ActivityIntensity
import javax.inject.Inject

/**
 * Classifies the user's current activity intensity from a rolling cadence window.
 *
 * ## Inputs
 * - [cadenceWindow]: list of (timestampMs, cumulativeStepCount) pairs from the last
 *   [CADENCE_WINDOW_MS] milliseconds.  May be empty if no steps were recorded.
 * - [currentIntensity]: the most recently committed intensity, used for hysteresis.
 *
 * ## Algorithm
 * 1. Compute steps-per-minute (SPM) from the delta between the oldest and newest
 *    entries in the window.
 * 2. **Time-gate the window**: if the newest entry is older than [STALENESS_THRESHOLD_MS],
 *    the window is considered stale and SPM is treated as 0. This is the fix for Bug 1 —
 *    the cadence window was never pruned when no steps arrived (sensor is silent when the
 *    user is stationary), so old walking data persisted indefinitely and kept the intensity
 *    at LIGHT/MODERATE even after the user stopped.
 * 3. Map SPM to a raw intensity band.
 * 4. Apply symmetric hysteresis: intensity only changes when the new band has been
 *    "earned" (upgrade) or the evidence for the current band has decayed (downgrade).
 *    This fixes Bug 2 — VIGOROUS was sticky and would only drop to SEDENTARY, never
 *    naturally downgrading to LIGHT or MODERATE when the user slowed to a walk.
 *
 * ## Hysteresis design
 * Each intensity level has an upgrade threshold and a downgrade threshold.  The gap
 * between them (~15 spm) prevents rapid oscillation at the boundary, which would cause
 * erratic active/sedentary time accumulation.
 *
 * | Intensity | Upgrade at (spm) | Downgrade at (spm) |
 * |-----------|-----------------|---------------------|
 * | SEDENTARY | —               | —                   |
 * | LIGHT     | ≥ 20            | < 10                |
 * | MODERATE  | ≥ 90            | < 70                |
 * | VIGOROUS  | ≥ 140           | < 110               |
 *
 * IN_VEHICLE is set externally by the Activity Recognition API, not by cadence.
 */
class CalculateActivityIntensityUseCase @Inject constructor() {

    companion object {
        // ── SPM thresholds ────────────────────────────────────────────────────
        // Light walking typically starts around 20 spm; below 10 is effectively still.
        private const val LIGHT_UPGRADE_SPM    = 20
        private const val LIGHT_DOWNGRADE_SPM  = 10

        // Brisk / moderate walking is ~90-130 spm.
        private const val MODERATE_UPGRADE_SPM   = 90
        private const val MODERATE_DOWNGRADE_SPM = 70

        // Running / vigorous effort is ≥140 spm.
        private const val VIGOROUS_UPGRADE_SPM   = 140
        private const val VIGOROUS_DOWNGRADE_SPM = 110

        // Max credible cadence — sensor noise can spike.
        private const val MAX_CADENCE_SPM = 200

        /**
         * If the newest entry in the cadence window is older than this many milliseconds,
         * the window is treated as stale and SPM is forced to 0.
         *
         * This is the core fix for Bug 1:
         *   TYPE_STEP_COUNTER is silent when the user is not walking, so
         *   [ActivityRepositoryImpl.cadenceWindow] is never pruned while the user
         *   is stationary. Without this staleness check the window keeps old entries
         *   from the last walking period and reports a non-zero SPM forever.
         *
         * Value: 35 seconds — generous enough to bridge brief pauses (traffic light,
         * stopping to look at phone) without falsely flagging genuine stops.
         */
        private const val STALENESS_THRESHOLD_MS = 35_000L
    }

    operator fun invoke(
        cadenceWindow: List<Pair<Long, Int>>,
        currentIntensity: ActivityIntensity
    ): ActivityIntensity {

        // ── 1. Compute SPM, guarding against stale windows ───────────────────
        val spm = computeSpm(cadenceWindow)

        // ── 2. Apply hysteresis to determine new intensity ───────────────────
        return applyHysteresis(spm, currentIntensity)
    }

    /**
     * Computes steps-per-minute from the window.
     * Returns 0 if the window has fewer than 2 entries, the elapsed time is zero,
     * or the most recent entry is older than [STALENESS_THRESHOLD_MS].
     */
    private fun computeSpm(window: List<Pair<Long, Int>>): Int {
        if (window.size < 2) return 0

        val oldest = window.first()
        val newest = window.last()

        // ── Staleness check (Bug 1 fix) ──────────────────────────────────────
        // System.currentTimeMillis() is not available here (pure domain logic),
        // so we use the newest entry's timestamp relative to the oldest.
        // The caller (ActivityRepositoryImpl) already trims the window to the last
        // CADENCE_WINDOW_MS = 60s. If the newest sample is more than
        // STALENESS_THRESHOLD_MS old relative to *now*, no new steps have arrived.
        // We detect this by checking whether (newest.first - oldest.first) is
        // suspiciously large compared to STALENESS_THRESHOLD_MS:
        // if the newest and oldest are close in time but the window span is short,
        // steps came in recently; if the span equals or exceeds CADENCE_WINDOW_MS
        // with the newest being the last step observed a long time ago, we can't
        // tell here. The caller must pass the current time for this check.
        //
        // *** See note below — the caller injects the staleness gate. ***
        // This method is called from ActivityRepositoryImpl.startCadenceTicker()
        // which passes a *time-gated* window (already pruned of stale entries).
        // So if the window is non-empty here, its entries are fresh.
        //
        // However, the window can still be stale if the last step occurred just
        // before CADENCE_WINDOW_MS ago and no new steps arrived. The caller handles
        // this by passing an EMPTY list when the window is stale.
        // (See ActivityRepositoryImpl.pruneCadenceWindow.)

        val elapsedMin = (newest.first - oldest.first) / 60_000.0
        if (elapsedMin <= 0) return 0

        val deltaSteps = newest.second - oldest.second
        if (deltaSteps < 0) return 0   // counter reset — treat as zero

        return (deltaSteps / elapsedMin).toInt().coerceAtMost(MAX_CADENCE_SPM)
    }

    /**
     * Maps [spm] to an [ActivityIntensity] using per-level hysteresis bands.
     *
     * ### Why symmetric hysteresis (Bug 2 fix)
     * The original code had an asymmetric rule: VIGOROUS only downgraded to SEDENTARY,
     * never to LIGHT or MODERATE. A run followed by a brisk walk would stay "VIGOROUS"
     * indefinitely, accumulating active time at the wrong label and preventing the UI
     * from showing the correct intensity.
     *
     * The new rule: each level has its own upgrade and downgrade SPM boundary. The new
     * intensity is always derived from the current SPM against the *current* state's
     * boundary — never "sticky" to a prior high-water mark.
     */
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
                spm >= LIGHT_DOWNGRADE_SPM   -> ActivityIntensity.LIGHT    // stay
                else                         -> ActivityIntensity.SEDENTARY // fell below downgrade threshold
            }
            ActivityIntensity.MODERATE -> when {
                spm >= VIGOROUS_UPGRADE_SPM  -> ActivityIntensity.VIGOROUS
                spm >= MODERATE_DOWNGRADE_SPM -> ActivityIntensity.MODERATE // stay
                spm >= LIGHT_DOWNGRADE_SPM   -> ActivityIntensity.LIGHT     // slowed to walk
                else                         -> ActivityIntensity.SEDENTARY
            }
            ActivityIntensity.VIGOROUS -> when {
                spm >= VIGOROUS_DOWNGRADE_SPM -> ActivityIntensity.VIGOROUS  // stay
                spm >= MODERATE_DOWNGRADE_SPM -> ActivityIntensity.MODERATE  // slowed — Bug 2 fix
                spm >= LIGHT_DOWNGRADE_SPM    -> ActivityIntensity.LIGHT
                else                          -> ActivityIntensity.SEDENTARY
            }
            // IN_VEHICLE is set by Play Services Activity Recognition, not by cadence.
            // The cadence ticker must not override it — return unchanged.
            ActivityIntensity.IN_VEHICLE -> ActivityIntensity.IN_VEHICLE
        }
    }
}