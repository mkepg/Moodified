package com.karamay.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists activity-tracking state across process death and device reboots.
 *
 * ## Reboot-safety for step baseline
 * Android's TYPE_STEP_COUNTER is a cumulative hardware counter that **resets to zero
 * on every device reboot**.  We must therefore persist the reboot epoch — the
 * System.currentTimeMillis() at which we last observed the counter reset — and
 * invalidate the stored baseline whenever a new boot is detected.
 *
 * Detection strategy: on every call to [baselineSteps] we compare the persisted
 * [bootEpochMillis] against the current boot time derived from
 * `System.currentTimeMillis() - SystemClock.elapsedRealtime()`.  If they differ
 * by more than [BOOT_EPOCH_TOLERANCE_MS] we know the device has rebooted and the
 * old baseline is stale.
 */
@Singleton
class ActivityPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("activity_monitor_prefs", Context.MODE_PRIVATE)

    // ── Tracking state ────────────────────────────────────────────────────────

    var isTracking: Boolean
        get() = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    // ── Step counter ──────────────────────────────────────────────────────────

    /**
     * The raw sensor baseline captured at session start.
     * -1 means "not yet captured this session".
     * This value must be treated as **invalid** after a device reboot because
     * TYPE_STEP_COUNTER resets to 0 on boot.
     */
    var baselineSteps: Int
        get() = prefs.getInt("baseline_steps", -1)
        set(value) = prefs.edit().putInt("baseline_steps", value).apply()

    var sessionSteps: Int
        get() = prefs.getInt("session_steps", 0)
        set(value) = prefs.edit().putInt("session_steps", value).apply()

    /**
     * Wall-clock time (ms) corresponding to the most recent device boot,
     * estimated as System.currentTimeMillis() − SystemClock.elapsedRealtime()
     * at the moment we last wrote a baseline.
     *
     * Persisting this lets us detect reboots: if the stored epoch differs
     * from the current boot epoch by more than [BOOT_EPOCH_TOLERANCE_MS],
     * the sensor counter has reset and the old baseline is invalid.
     */
    var bootEpochMillis: Long
        get() = prefs.getLong("boot_epoch_millis", -1L)
        set(value) = prefs.edit().putLong("boot_epoch_millis", value).apply()

    // ── Active / sedentary time ───────────────────────────────────────────────

    var activeMs: Long
        get() = prefs.getLong("active_ms", 0L)
        set(value) = prefs.edit().putLong("active_ms", value).apply()

    var sedentaryMs: Long
        get() = prefs.getLong("sedentary_ms", 0L)
        set(value) = prefs.edit().putLong("sedentary_ms", value).apply()

    var intensity: String
        get() = prefs.getString("intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("intensity", value).apply()

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true when the stored [baselineSteps] was captured during a
     * previous boot cycle and must be discarded.
     *
     * We compute the current boot epoch as
     *   currentTimeMillis − elapsedRealtime
     * and compare it to the persisted value.  Reboots typically differ by hours
     * or days, so a 60-second tolerance is more than sufficient to ignore normal
     * clock drift.
     */
    fun isBaselineStale(currentBootEpochMillis: Long): Boolean {
        val stored = bootEpochMillis
        if (stored == -1L) return true                          // never written
        return Math.abs(currentBootEpochMillis - stored) > BOOT_EPOCH_TOLERANCE_MS
    }

    fun resetSession() {
        prefs.edit()
            .putInt("baseline_steps", -1)
            .putInt("session_steps", 0)
            .putLong("active_ms", 0L)
            .putLong("sedentary_ms", 0L)
            .putString("intensity", "SEDENTARY")
            .putLong("boot_epoch_millis", -1L)
            .apply()
    }

    companion object {
        /** Tolerate up to 60 s of wall-clock drift between two boot epochs. */
        private const val BOOT_EPOCH_TOLERANCE_MS = 60_000L
    }
}