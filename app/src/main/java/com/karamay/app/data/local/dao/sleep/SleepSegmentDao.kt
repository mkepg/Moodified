package com.karamay.app.data.local.dao.sleep

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.sleep.SleepSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SleepSegmentEntity>)

    @Query("""
        SELECT * FROM sleep_segments
        WHERE startTimeMillis >= :startMillis AND startTimeMillis < :endMillis
        ORDER BY startTimeMillis ASC
    """)
    fun getSegmentsBetween(startMillis: Long, endMillis: Long): Flow<List<SleepSegmentEntity>>

    @Query("""
        SELECT COUNT(*) FROM sleep_segments
        WHERE startTimeMillis >= :startMillis AND startTimeMillis < :endMillis
    """)
    suspend fun countSegmentsInWindow(startMillis: Long, endMillis: Long): Int

    @Query("DELETE FROM sleep_segments")
    suspend fun clearAll()
}