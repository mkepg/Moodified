package com.moodified.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepPreferencesDataSource
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        companion object {
            const val PREFS_NAME = "sleep_tracker_prefs"
            const val KEY_IS_TRACKING = "is_tracking"
            private const val KEY_INSTALL_TIME_MS = "install_time_ms"
        }

        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // ── Install-time sentinel ─────────────────────────────────────────────────

        /**
         * The epoch-millis timestamp recorded the very first time this data source
         * is initialised after a fresh install. Used by [SleepRepositoryImpl] to
         * prevent inference from running before the app has observed at least one
         * full night of data, which would otherwise let it fabricate sleep summaries
         * from pre-install UsageStats history.
         *
         * Written once; never overwritten.
         */
        val installTimeMillis: Long
            get() = prefs.getLong(KEY_INSTALL_TIME_MS, System.currentTimeMillis())

        /**
         * Call once during first initialisation (e.g. from [SleepRepositoryImpl]'s
         * init block). Safe to call repeatedly — writes only when the key is absent.
         */
        fun ensureInstallTimeRecorded() {
            if (prefs.getLong(KEY_INSTALL_TIME_MS, -1L) == -1L) {
                prefs.edit().putLong(KEY_INSTALL_TIME_MS, System.currentTimeMillis()).apply()
            }
        }

        // ── Tracking state ────────────────────────────────────────────────────────

        var isTracking: Boolean
            get() = prefs.getBoolean(KEY_IS_TRACKING, false)
            set(value) = prefs.edit().putBoolean(KEY_IS_TRACKING, value).apply()

        var hasActiveSession: Boolean
            get() = prefs.getBoolean("has_active_session", false)
            set(value) = prefs.edit().putBoolean("has_active_session", value).apply()

        // ── Inference cache ───────────────────────────────────────────────────────

        var lastInferredDate: String
            get() = prefs.getString("last_inferred_date", "") ?: ""
            set(value) = prefs.edit().putString("last_inferred_date", value).apply()

        var lastInferredSleepStart: LocalDateTime?
            get() {
                val saved = prefs.getString("last_sleep_start", null) ?: return null
                return runCatching { LocalDateTime.parse(saved) }.getOrNull()
            }
            set(value) {
                if (value == null) {
                    prefs.edit().remove("last_sleep_start").apply()
                } else {
                    prefs.edit().putString("last_sleep_start", value.toString()).apply()
                }
            }

        var lastInferredSleepEnd: LocalDateTime?
            get() {
                val saved = prefs.getString("last_sleep_end", null) ?: return null
                return runCatching { LocalDateTime.parse(saved) }.getOrNull()
            }
            set(value) {
                if (value == null) {
                    prefs.edit().remove("last_sleep_end").apply()
                } else {
                    prefs.edit().putString("last_sleep_end", value.toString()).apply()
                }
            }

        var inferredSleepMinutes: Int
            get() = prefs.getInt("inferred_sleep_minutes", 0)
            set(value) = prefs.edit().putInt("inferred_sleep_minutes", value).apply()

        var inferredConfidence: Int
            get() = prefs.getInt("inferred_confidence", 0)
            set(value) = prefs.edit().putInt("inferred_confidence", value).apply()

        // ── Live signal state ─────────────────────────────────────────────────────

        var lastScreenOffMillis: Long
            get() = prefs.getLong("last_screen_off_ms", -1L)
            set(value) = prefs.edit().putLong("last_screen_off_ms", value).apply()

        // ── Helpers ───────────────────────────────────────────────────────────────

        fun cacheInferenceResult(
            date: LocalDate,
            sleepStart: LocalDateTime,
            sleepEnd: LocalDateTime,
            sleepMinutes: Int,
            confidence: Int,
        ) {
            prefs.edit()
                .putString("last_inferred_date", date.toString())
                .putString("last_sleep_start", sleepStart.toString())
                .putString("last_sleep_end", sleepEnd.toString())
                .putInt("inferred_sleep_minutes", sleepMinutes)
                .putInt("inferred_confidence", confidence)
                .apply()
        }
    }
