package com.karamay.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sleep_tracker_prefs", Context.MODE_PRIVATE)

    var isTracking: Boolean
        get() = prefs.getBoolean("is_tracking", false)
        set(value) = prefs.edit().putBoolean("is_tracking", value).apply()

    var hasActiveSession: Boolean
        get() = prefs.getBoolean("has_active_session", false)
        set(value) = prefs.edit().putBoolean("has_active_session", value).apply()

    var lastAsleepTimestamp: LocalDateTime?
        get() {
            val saved = prefs.getString("last_asleep_time", null)
            return if (saved != null) runCatching { LocalDateTime.parse(saved) }.getOrNull() else null
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("last_asleep_time").apply()
            } else {
                prefs.edit().putString("last_asleep_time", value.toString()).apply()
            }
        }

    fun resetSession() {
        prefs.edit()
            .remove("last_asleep_time")
            .remove("has_active_session")
            // FIX BUG-09: The original resetSession() left is_tracking = true in prefs.
            // This caused BootReceiver to restart sleep tracking after a user-initiated reset,
            // because BootReceiver reads is_tracking directly from SharedPreferences.
            .putBoolean("is_tracking", false)
            .apply()
    }
}
