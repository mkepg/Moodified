package com.moodified.app.data.local.dao.mood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.mood.MoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodEntryDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(entity: MoodEntryEntity): Long

    @Query("SELECT * FROM mood_entries ORDER BY timestampMillis ASC")
    fun getAllEntries(): Flow<List<MoodEntryEntity>>

    @Query(
        """
        SELECT * FROM mood_entries
        WHERE timestampMillis >= :startOfDayMillis AND timestampMillis < :endOfDayMillis
        ORDER BY timestampMillis ASC
    """,
    )
    fun getEntriesBetween(
        startOfDayMillis: Long,
        endOfDayMillis: Long,
    ): Flow<List<MoodEntryEntity>>

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM mood_entries")
    fun observeCount(): Flow<Long>
}
