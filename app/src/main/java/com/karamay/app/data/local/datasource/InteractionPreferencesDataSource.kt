// app/src/main/java/com/karamay/app/data/local/datasource/InteractionPreferencesDataSource.kt
package com.karamay.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("interaction_tracker_prefs", Context.MODE_PRIVATE)

    var isTracking: Boolean
        get() = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    var isScreenOn: Boolean
        get() = prefs.getBoolean("is_screen_on", true)
        set(value) = prefs.edit().putBoolean("is_screen_on", value).apply()

    var sessionStartMillis: Long
        get() = prefs.getLong("session_start_ms", -1L)
        set(value) = prefs.edit().putLong("session_start_ms", value).apply()

    // NEW: Separates session tracking from incremental duration counting
    var lastCalcMillis: Long
        get() = prefs.getLong("last_calc_ms", -1L)
        set(value) = prefs.edit().putLong("last_calc_ms", value).apply()

    var totalScreenTimeTodayMs: Long
        get() = prefs.getLong("screen_time_today_ms", 0L)
        set(value) = prefs.edit().putLong("screen_time_today_ms", value).apply()

    var unlocksToday: Int
        get() = prefs.getInt("unlocks_today", 0)
        set(value) = prefs.edit().putInt("unlocks_today", value).apply()

    var dayKey: String
        get() = prefs.getString("day_key", "") ?: ""
        set(value) = prefs.edit().putString("day_key", value).apply()

    fun rolloverToNewDay(newDayKey: String) {
        prefs.edit()
            .putString("day_key", newDayKey)
            .putLong("screen_time_today_ms", 0L)
            .putInt("unlocks_today", 0)
            // Note: We do NOT reset sessionStartMillis or lastCalcMillis here.
            // This ensures a session crossing midnight remains completely intact.
            .apply()
    }

    fun resetSession() {
        val today = LocalDate.now().toString()
        prefs.edit()
            .putString("day_key", today)
            .putBoolean("is_tracking", false)
            .putBoolean("is_screen_on", true)
            .putLong("session_start_ms", -1L)
            .putLong("last_calc_ms", -1L)
            .putLong("screen_time_today_ms", 0L)
            .putInt("unlocks_today", 0)
            .apply()
    }
}