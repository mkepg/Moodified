package com.karamay.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.SleepSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SleepSegmentEntity>)

    @Query("SELECT * FROM sleep_segments WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime ASC")
    fun getSegmentsForDate(startOfDay: String, endOfDay: String): Flow<List<SleepSegmentEntity>>

    @Query("DELETE FROM sleep_segments")
    suspend fun clearAll()

    @Query("SELECT * FROM sleep_segments WHERE startTime >= :start AND startTime < :end ORDER BY startTime ASC")
    fun getSegmentsBetween(start: String, end: String): Flow<List<SleepSegmentEntity>>
}