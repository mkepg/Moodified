package com.karamay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.Arousal
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.model.Valence
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val valence: String,
    val arousal: String,
    val note: String?,
    val timestampMillis: Long,
    val isManual: Boolean = true
) {
    fun toDomain(): MoodEntry {
        val parsedTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

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
            id              = entry.id,
            valence         = entry.valence.name,
            arousal         = entry.arousal.name,
            note            = entry.note,
            timestampMillis = entry.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            isManual        = entry.isManual
        )
    }
}