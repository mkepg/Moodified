package com.karamay.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("activity_monitor_prefs", Context.MODE_PRIVATE)

    // ── Tracking state ───────────────────────────────────────────────────────

    var isTracking: Boolean
        get() = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    // ── Step-counter state ───────────────────────────────────────────────────

    var baselineSteps: Int
        get() = prefs.getInt("baseline_steps", -1)
        set(value) = prefs.edit().putInt("baseline_steps", value).apply()

    var sessionSteps: Int
        get() = prefs.getInt("session_steps", 0)
        set(value) = prefs.edit().putInt("session_steps", value).apply()

    var bootEpochMillis: Long
        get() = prefs.getLong("boot_epoch_millis", -1L)
        set(value) = prefs.edit().putLong("boot_epoch_millis", value).apply()

    // ── Time accumulators ────────────────────────────────────────────────────

    var activeMs: Long
        get() = prefs.getLong("active_ms", 0L)
        set(value) = prefs.edit().putLong("active_ms", value).apply()

    var sedentaryMs: Long
        get() = prefs.getLong("sedentary_ms", 0L)
        set(value) = prefs.edit().putLong("sedentary_ms", value).apply()

    // ── Intensity / segment anchor ───────────────────────────────────────────

    var intensity: String
        get() = prefs.getString("intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("intensity", value).apply()

    var segmentStartMillis: Long
        get() = prefs.getLong("segment_start_ms", -1L)
        set(value) = prefs.edit().putLong("segment_start_ms", value).apply()

    // ── Day key (midnight rollover) ──────────────────────────────────────────

    /**
     * The ISO date string ("YYYY-MM-DD") for which the current in-memory
     * accumulators (activeMs, sedentaryMs, sessionSteps) are accumulating.
     * When this differs from [LocalDate.now().toString()], the repository
     * must flush the stale accumulators to Room and reset them before
     * continuing to accumulate for the new day.
     */
    var dayKey: String
        get() = prefs.getString("day_key", "") ?: ""
        set(value) = prefs.edit().putString("day_key", value).apply()

    // ── Peak intensity for the current day ──────────────────────────────────

    /**
     * The highest-energy intensity observed so far today. Persisted so a
     * process restart during the day doesn't reset it to SEDENTARY.
     */
    var peakIntensity: String
        get() = prefs.getString("peak_intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("peak_intensity", value).apply()

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun isBaselineStale(currentBootEpochMillis: Long): Boolean {
        val stored = bootEpochMillis
        if (stored == -1L) return true
        return Math.abs(currentBootEpochMillis - stored) > BOOT_EPOCH_TOLERANCE_MS
    }

    /**
     * Resets all in-day accumulators and the day key.
     * Called by the repository after flushing the previous day's data to Room,
     * and also from resetSession() in dev tools.
     */
    fun rolloverToNewDay(newDayKey: String) {
        prefs.edit()
            .putString("day_key", newDayKey)
            .putInt("session_steps", 0)
            .putInt("baseline_steps", -1)
            .putLong("active_ms", 0L)
            .putLong("sedentary_ms", 0L)
            .putString("intensity", "SEDENTARY")
            .putString("peak_intensity", "SEDENTARY")
            .putLong("segment_start_ms", -1L)
            .putLong("boot_epoch_millis", -1L)
            .apply()
    }

    /**
     * Dev-tools reset. Clears the live display accumulators without touching
     * any already-flushed Room records — those are preserved so historical
     * data is not lost. The day key is also reset so the next flush creates
     * a fresh record for today.
     */
    fun resetSession() {
        val today = LocalDate.now().toString()
        prefs.edit()
            .putString("day_key", today)
            .putInt("baseline_steps", -1)
            .putInt("session_steps", 0)
            .putLong("active_ms", 0L)
            .putLong("sedentary_ms", 0L)
            .putString("intensity", "SEDENTARY")
            .putString("peak_intensity", "SEDENTARY")
            .putLong("boot_epoch_millis", -1L)
            .putLong("segment_start_ms", -1L)
            .apply()
    }

    companion object {
        private const val BOOT_EPOCH_TOLERANCE_MS = 60_000L
    }
}
