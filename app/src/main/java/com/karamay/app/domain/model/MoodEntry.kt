package com.karamay.app.domain.model

import java.time.LocalDateTime

enum class Valence {
    NEGATIVE, NEUTRAL, POSITIVE;

    fun displayLabel(): String = when (this) {
        NEGATIVE -> "Not great"
        NEUTRAL  -> "So-so"
        POSITIVE -> "Good vibes"
    }
}

enum class Arousal {
    LOW, MID, HIGH;

    fun displayLabel(): String = when (this) {
        LOW  -> "Calm"
        MID  -> "Balanced"
        HIGH -> "Elevated"
    }
}

data class MoodEntry(
    val id: Long = 0,
    val valence: Valence,
    val arousal: Arousal,
    val note: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isManual: Boolean = true
)
