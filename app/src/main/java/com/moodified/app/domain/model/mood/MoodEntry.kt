package com.moodified.app.domain.model.mood

import com.moodified.app.R
import java.time.LocalDateTime

enum class Valence {
    NEGATIVE, NEUTRAL, POSITIVE;

    fun displayLabel(): String = when (this) {
        NEGATIVE -> "Not great"
        NEUTRAL  -> "So-so"
        POSITIVE -> "Good vibes"
    }

    // Matches the Image resources in QuickLogSheet
    fun iconRes(): Int = when (this) {
        NEGATIVE -> R.drawable.ic_sad
        NEUTRAL  -> R.drawable.ic_meh
        POSITIVE -> R.drawable.ic_happy
    }
}

enum class Arousal {
    LOW, MID, HIGH;

    fun displayLabel(): String = when (this) {
        LOW  -> "Calm"
        MID  -> "Balanced"
        HIGH -> "Elevated"
    }

    // Matches the Energy resources in QuickLogSheet
    fun iconRes(): Int = when (this) {
        LOW  -> R.drawable.ic_no_energy
        MID  -> R.drawable.ic_mid_energy
        HIGH -> R.drawable.ic_high_energy
    }
}

data class MoodEntry(
    val id: Long = 0,
    val valence: Valence,
    val arousal: Arousal,
    val note: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isManual: Boolean = true,
    val contextActivityIntensity: String? = null,
    val contextSleepMinutes: Int? = null
)