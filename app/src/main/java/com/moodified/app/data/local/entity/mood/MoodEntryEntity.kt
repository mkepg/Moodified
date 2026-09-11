package com.moodified.app.data.local.entity.mood

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import java.time.Instant
import java.time.ZoneId

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val valence: String,
    val arousal: String,
    val note: String?,
    val timestampMillis: Long,
    val isManual: Boolean = true,
    // Sprint 1 Additions
    @ColumnInfo(defaultValue = "NULL")
    val contextActivityIntensity: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val contextSleepMinutes: Int? = null,
) {
    fun toDomain(): MoodEntry {
        val parsedTime =
            Instant.ofEpochMilli(timestampMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        return MoodEntry(
            id = id,
            valence = runCatching { Valence.valueOf(valence) }.getOrDefault(Valence.NEUTRAL),
            arousal = runCatching { Arousal.valueOf(arousal) }.getOrDefault(Arousal.MID),
            note = note,
            timestamp = parsedTime,
            isManual = isManual,
            contextActivityIntensity = contextActivityIntensity,
            contextSleepMinutes = contextSleepMinutes,
        )
    }

    companion object {
        fun fromDomain(entry: MoodEntry): MoodEntryEntity =
            MoodEntryEntity(
                id = entry.id,
                valence = entry.valence.name,
                arousal = entry.arousal.name,
                note = entry.note,
                timestampMillis = entry.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                isManual = entry.isManual,
                contextActivityIntensity = entry.contextActivityIntensity,
                contextSleepMinutes = entry.contextSleepMinutes,
            )
    }
}
