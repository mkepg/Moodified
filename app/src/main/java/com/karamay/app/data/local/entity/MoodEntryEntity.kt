package com.karamay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.Arousal
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.model.Valence
import java.time.LocalDateTime
import java.util.logging.Logger

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val valence: String,
    val arousal: String,
    val note: String?,
    val timestamp: String,
    val isManual: Boolean = true
) {
    /**
     * Fix #18: Wrapped LocalDateTime.parse() in a try-catch.
     *
     * A single malformed or legacy timestamp string previously caused DateTimeParseException
     * to propagate uncaught through getAllEntries() / getTodayEntries() Flows, crashing the
     * entire mood history display. Now a bad row returns a sentinel entry with the epoch
     * start time and logs the error for crash analytics.
     */
    fun toDomain(): MoodEntry {
        val parsedTime = runCatching { LocalDateTime.parse(timestamp) }
            .onFailure { e ->
                Logger.getLogger("MoodEntryEntity")
                    .warning("Failed to parse timestamp '$timestamp' for entry id=$id: ${e.message}")
            }
            .getOrDefault(LocalDateTime.of(1970, 1, 1, 0, 0))

        return MoodEntry(
            id        = id,
            valence   = runCatching { Valence.valueOf(valence) }.getOrDefault(Valence.NEUTRAL),
            arousal   = runCatching { Arousal.valueOf(arousal) }.getOrDefault(Arousal.MID),
            note      = note,
            timestamp = parsedTime,
            isManual  = isManual
        )
    }

    companion object {
        fun fromDomain(entry: MoodEntry): MoodEntryEntity = MoodEntryEntity(
            id        = entry.id,
            valence   = entry.valence.name,
            arousal   = entry.arousal.name,
            note      = entry.note,
            timestamp = entry.timestamp.toString(),
            isManual  = entry.isManual
        )
    }
}
