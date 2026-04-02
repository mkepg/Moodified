package com.karamay.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.MoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MoodEntryEntity): Long

    // Changed to ASC so the oldest entries appear first in the list
    @Query("SELECT * FROM mood_entries ORDER BY timestamp ASC")
    fun getAllEntries(): Flow<List<MoodEntryEntity>>

    // Changed to ASC so today's entries flow from morning to night
    @Query("""
        SELECT * FROM mood_entries
        WHERE timestamp LIKE :datePrefix || '%'
        ORDER BY timestamp ASC
    """)
    fun getEntriesByDate(datePrefix: String): Flow<List<MoodEntryEntity>>

    // We keep this as DESC because we still want the single most recent entry
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): MoodEntryEntity?

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}