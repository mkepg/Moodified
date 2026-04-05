package com.karamay.app.core.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    fun getGreeting(): String {
        return when (LocalDateTime.now().hour) {
            in 0..11  -> "Good morning"
            in 12..17 -> "Good afternoon"
            else      -> "Good evening"
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
}
