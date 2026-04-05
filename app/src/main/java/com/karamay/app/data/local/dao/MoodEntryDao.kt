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

    @Query("SELECT * FROM mood_entries ORDER BY timestamp ASC")
    fun getAllEntries(): Flow<List<MoodEntryEntity>>

    @Query("""
        SELECT * FROM mood_entries
        WHERE timestamp LIKE :datePrefix || '%'
        ORDER BY timestamp ASC
    """)
    fun getEntriesByDate(datePrefix: String): Flow<List<MoodEntryEntity>>

    // Fix #34: Reactive LIMIT 1 query. Room emits a new value whenever the table changes,
    // so CheckInViewModel.observeLatestEntry() always stays current without a manual reload.
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestEntry(): Flow<MoodEntryEntity?>

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
