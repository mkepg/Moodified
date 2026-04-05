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
    private val prefs: SharedPreferences = context.getSharedPreferences("activity_monitor_prefs", Context.MODE_PRIVATE)

    var isTracking: Boolean
        get() = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    var baselineSteps: Int
        get() = prefs.getInt("baseline_steps", -1)
        set(value) = prefs.edit().putInt("baseline_steps", value).apply()

    var sessionSteps: Int
        get() = prefs.getInt("session_steps", 0)
        set(value) = prefs.edit().putInt("session_steps", value).apply()

    var activeMs: Long
        get() = prefs.getLong("active_ms", 0L)
        set(value) = prefs.edit().putLong("active_ms", value).apply()

    var sedentaryMs: Long
        get() = prefs.getLong("sedentary_ms", 0L)
        set(value) = prefs.edit().putLong("sedentary_ms", value).apply()

    var intensity: String
        get() = prefs.getString("intensity", "SEDENTARY") ?: "SEDENTARY"
        set(value) = prefs.edit().putString("intensity", value).apply()

    fun resetSession() {
        prefs.edit()
            .putInt("baseline_steps", -1)
            .putInt("session_steps", 0)
            .putLong("active_ms", 0L)
            .putLong("sedentary_ms", 0L)
            .putString("intensity", "SEDENTARY")
            .apply()
    }
}