package com.karamay.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("activity_monitor_prefs", Context.MODE_PRIVATE)

    var isTracking: Boolean
        get() = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    var baselineSteps: Int
        get() = prefs.getInt("baseline_steps", -1)
        set(value) = prefs.edit().putInt("baseline_steps", value).apply()

    var sessionSteps: Int
        get() = prefs.getInt("session_steps", 0)
        set(value) = prefs.edit().putInt("session_steps", value).apply()

    var bootEpochMillis: Long
        get() = prefs.getLong("boot_epoch_millis", -1L)
        set(value) = prefs.edit().putLong("boot_epoch_millis", value).apply()

    var activeMs: Long
        get() = prefs.getLong("active_ms", 0L)
        set(value) = prefs.edit().putLong("active_ms", value).apply()

    var sedentaryMs: Long
        get() = prefs.getLong("sedentary_ms", 0L)
        set(value) = prefs.edit().putLong("sedentary_ms", value).apply()

    var intensity: String
        get() = prefs.getString("intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("intensity", value).apply()

    // FIX BUG-01/02: Persisted wall-clock timestamp of when the current intensity
    // segment started. Survives process death and is used to restore stateEnteredAt
    // on resume instead of resetting it to System.currentTimeMillis().
    // -1L means no active segment (i.e. tracking is stopped or was just reset).
    var segmentStartMillis: Long
        get() = prefs.getLong("segment_start_ms", -1L)
        set(value) = prefs.edit().putLong("segment_start_ms", value).apply()

    fun isBaselineStale(currentBootEpochMillis: Long): Boolean {
        val stored = bootEpochMillis
        if (stored == -1L) return true
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
            .putLong("segment_start_ms", -1L)   // FIX BUG-01/02: clear anchor on reset
            .apply()
    }

    companion object {
        private const val BOOT_EPOCH_TOLERANCE_MS = 60_000L
    }
}
