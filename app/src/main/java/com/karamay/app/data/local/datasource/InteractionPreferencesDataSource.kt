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
        get()      = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    var dayKey: String
        get()      = prefs.getString("day_key", "") ?: ""
        set(value) = prefs.edit().putString("day_key", value).apply()

    var totalScreenTimeTodayMs: Long
        get()      = prefs.getLong("screen_time_today_ms", 0L)
        set(value) = prefs.edit().putLong("screen_time_today_ms", value).apply()

    var lateNightScreenTimeTodayMs: Long
        get()      = prefs.getLong("late_night_screen_time_today_ms", 0L)
        set(value) = prefs.edit().putLong("late_night_screen_time_today_ms", value).apply()

    var unlockCount: Int
        get()      = prefs.getInt("unlock_count", 0)
        set(value) = prefs.edit().putInt("unlock_count", value).apply()

    fun rolloverToNewDay(newDayKey: String) {
        prefs.edit()
            .putString("day_key", newDayKey)
            .putLong("screen_time_today_ms", 0L)
            .putLong("late_night_screen_time_today_ms", 0L)
            .putInt("unlock_count", 0)
            .apply()
    }

    fun resetSession() {
        val today = LocalDate.now().toString()
        prefs.edit()
            .putString("day_key", today)
            .putBoolean("is_tracking", false)
            .putLong("screen_time_today_ms", 0L)
            .putLong("late_night_screen_time_today_ms", 0L)
            .putInt("unlock_count", 0)
            .apply()
    }
}