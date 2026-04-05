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

    @Query("SELECT * FROM mood_entries ORDER BY timestampMillis ASC")
    fun getAllEntries(): Flow<List<MoodEntryEntity>>

    @Query("""
        SELECT * FROM mood_entries
        WHERE timestampMillis >= :startOfDayMillis AND timestampMillis < :endOfDayMillis
        ORDER BY timestampMillis ASC
    """)
    fun getEntriesBetween(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<MoodEntryEntity>>

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}