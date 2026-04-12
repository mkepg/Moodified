package com.karamay.app.core.utils

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {

    fun getGreeting(): String {
        return when (LocalDateTime.now().hour) {
            in 5..11 -> listOf(
                "Good morning.",
                "A fresh start.",
                "Morning. Take it at your own pace today."
            ).random()

            in 12..16 -> listOf(
                "Good afternoon.",
                "Taking a midday pause?",
                "Hope your day is flowing well."
            ).random()

            in 17..21 -> listOf(
                "Good evening.",
                "Winding down?",
                "Time to rest and reflect."
            ).random()

            else -> listOf( // 22:00 to 04:59
                "Up late?",
                "Still awake? Be gentle with yourself.",
                "It's quiet hours. Hope you can find some rest soon."
            ).random()
        }
    }

    fun formatDisplayTime(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        return dateTime.format(formatter)
    }

    fun formatDisplayDate(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        return dateTime.format(formatter)
    }

    fun formatFullDateTime(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.getDefault())
        return dateTime.format(formatter)
    }

    fun formatMs(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMinutes = ms / 60_000L
        val hours        = totalMinutes / 60
        val minutes      = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun formatMinutes(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "0m"
        val hrs  = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }

    /**
     * Converts a sleep-onset offset (minutes elapsed since 6 PM on the prior evening)
     * into a human-readable approximate clock time, prefixed with "~" to signal that
     * this is an estimate derived from backfilled screen-inactivity data.
     *
     * Examples:
     *   236  → "~9:56 PM"    (phone screen went off ~3h 56m after 6 PM)
     *   596  → "~3:56 AM"    (phone screen went off ~9h 56m after 6 PM, next morning)
     *
     * @param minutesSince6PM  Non-negative offset in minutes from 18:00.
     */
    fun offsetMinutesToClockTime(minutesSince6PM: Int): String {
        if (minutesSince6PM < 0) return "—"
        val clockTime = LocalTime.of(18, 0).plusMinutes(minutesSince6PM.toLong())
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        return "${clockTime.format(formatter)}"
    }
}
