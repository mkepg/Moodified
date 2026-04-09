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
    // FIX P3: SharedPreferences name and key constants are declared here as the
    // single source of truth. BootReceiver imports these instead of duplicating
    // raw strings, so a rename here propagates automatically to all consumers.
    companion object {
        const val PREFS_NAME      = "activity_monitor_prefs"
        const val KEY_IS_TRACKING = "is_tracking"

        private const val BOOT_EPOCH_TOLERANCE_MS = 60_000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isTracking: Boolean
        get()      = prefs.getBoolean(KEY_IS_TRACKING, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_TRACKING, value).apply()

    var baselineSteps: Int
        get()      = prefs.getInt("baseline_steps", -1)
        set(value) = prefs.edit().putInt("baseline_steps", value).apply()

    var sessionSteps: Int
        get()      = prefs.getInt("session_steps", 0)
        set(value) = prefs.edit().putInt("session_steps", value).apply()

    var bootEpochMillis: Long
        get()      = prefs.getLong("boot_epoch_millis", -1L)
        set(value) = prefs.edit().putLong("boot_epoch_millis", value).apply()

    var activeMs: Long
        get()      = prefs.getLong("active_ms", 0L)
        set(value) = prefs.edit().putLong("active_ms", value).apply()

    var sedentaryMs: Long
        get()      = prefs.getLong("sedentary_ms", 0L)
        set(value) = prefs.edit().putLong("sedentary_ms", value).apply()

    var intensity: String
        get()      = prefs.getString("intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("intensity", value).apply()

    var segmentStartMillis: Long
        get()      = prefs.getLong("segment_start_ms", -1L)
        set(value) = prefs.edit().putLong("segment_start_ms", value).apply()

    var dayKey: String
        get()      = prefs.getString("day_key", "") ?: ""
        set(value) = prefs.edit().putString("day_key", value).apply()

    var peakIntensity: String
        get()      = prefs.getString("peak_intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("peak_intensity", value).apply()

    fun isBaselineStale(currentBootEpochMillis: Long): Boolean {
        val stored = bootEpochMillis
        if (stored == -1L) return true
        return Math.abs(currentBootEpochMillis - stored) > BOOT_EPOCH_TOLERANCE_MS
    }

    fun rolloverToNewDay(newDayKey: String) {
        prefs.edit()
            .putString("day_key",          newDayKey)
            .putInt("session_steps",        0)
            .putInt("baseline_steps",       -1)
            .putLong("active_ms",           0L)
            .putLong("sedentary_ms",        0L)
            .putString("intensity",         "SEDENTARY")
            .putString("peak_intensity",    "SEDENTARY")
            .putLong("segment_start_ms",    -1L)
            .putLong("boot_epoch_millis",   -1L)
            .apply()
    }

    fun resetSession() {
        val today = LocalDate.now().toString()
        prefs.edit()
            .putString("day_key",          today)
            .putInt("baseline_steps",       -1)
            .putInt("session_steps",        0)
            .putLong("active_ms",           0L)
            .putLong("sedentary_ms",        0L)
            .putString("intensity",         "SEDENTARY")
            .putString("peak_intensity",    "SEDENTARY")
            .putLong("boot_epoch_millis",   -1L)
            .putLong("segment_start_ms",    -1L)
            .apply()
    }
}
