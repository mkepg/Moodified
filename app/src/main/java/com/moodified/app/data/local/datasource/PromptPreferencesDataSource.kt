package com.moodified.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptPreferencesDataSource
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        companion object {
            const val PREFS_NAME = "micro_prompt_prefs"
        }

        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        var lastUnlockCount: Int
            get() = prefs.getInt("last_unlock_count", -1)
            set(value) = prefs.edit().putInt("last_unlock_count", value).apply()

        var isCoolingDown: Boolean
            get() = prefs.getBoolean("is_cooling_down", false)
            set(value) = prefs.edit().putBoolean("is_cooling_down", value).apply()

        var lastActiveIntensity: String
            get() = prefs.getString("last_active_intensity", "") ?: ""
            set(value) = prefs.edit().putString("last_active_intensity", value).apply()

        var lastPromptTimestampMs: Long
            get() = prefs.getLong("last_prompt_timestamp_ms", 0L)
            set(value) = prefs.edit().putLong("last_prompt_timestamp_ms", value).apply()

        var promptsTodayCount: Int
            get() = prefs.getInt("prompts_today_count", 0)
            set(value) = prefs.edit().putInt("prompts_today_count", value).apply()

        var lastRolloverDate: String
            get() = prefs.getString("last_rollover_date", "") ?: ""
            set(value) = prefs.edit().putString("last_rollover_date", value).apply()
    }
