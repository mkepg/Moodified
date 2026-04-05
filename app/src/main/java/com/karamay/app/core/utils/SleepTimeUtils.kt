package com.karamay.app.core.utils

import java.time.Duration
import java.time.LocalDateTime

/**
 * Fix #26: minutesSince6PM() was privately duplicated in both
 * GetDailySleepSummaryUseCase and SleepRepositoryImpl.
 *
 * A single authoritative copy here ensures both call-sites stay in sync
 * if the anchor time ever needs to change (e.g., personalisable bedtime).
 */
object SleepTimeUtils {

    /**
     * Returns the number of minutes between the previous 6 PM anchor and [dateTime].
     *
     * If [dateTime] is at or after 18:00, the anchor is the same calendar day at 18:00.
     * Otherwise (i.e. early morning), the anchor is the *previous* calendar day at 18:00,
     * correctly spanning overnight sessions.
     *
     * Example: 23:30 → 210 min after 18:00 same day.
     *          02:00 → 480 min after 18:00 the day before.
     */
    fun minutesSince6PM(dateTime: LocalDateTime): Int {
        val anchor = if (dateTime.hour >= 18) {
            dateTime.toLocalDate().atTime(18, 0)
        } else {
            dateTime.toLocalDate().minusDays(1).atTime(18, 0)
        }
        return Duration.between(anchor, dateTime).toMinutes().toInt()
    }
}
