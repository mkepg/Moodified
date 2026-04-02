package com.karamay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.Arousal
import com.karamay.app.domain.model.MoodEntry
import com.karamay.app.domain.model.Valence
import java.time.LocalDateTime

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val valence: String,      // stored as enum name string
    val arousal: String,      // stored as enum name string
    val note: String?,
    val timestamp: String,    // ISO-8601 via LocalDateTime.toString()
    val isManual: Boolean = true
) {
    fun toDomain(): MoodEntry = MoodEntry(
        id        = id,
        valence   = Valence.valueOf(valence),
        arousal   = Arousal.valueOf(arousal),
        note      = note,
        timestamp = LocalDateTime.parse(timestamp),
        isManual  = isManual
    )

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
