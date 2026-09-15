package com.moodified.app.data.local.dao.activity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.activity.ActivityTelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityTelemetryEntity)

    @Query(
        """
        SELECT * FROM activity_telemetry
        WHERE timestampMillis >= :startMillis AND timestampMillis <= :endMillis
        ORDER BY timestampMillis ASC
    """,
    )
    fun getTelemetryBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<ActivityTelemetryEntity>>

    @Query(
        """
        SELECT * FROM activity_telemetry
        WHERE timestampMillis >= :startMillis AND timestampMillis <= :endMillis
        ORDER BY timestampMillis ASC
    """,
    )
    suspend fun getTelemetryListBetween(
        startMillis: Long,
        endMillis: Long,
    ): List<ActivityTelemetryEntity>

    @Query("DELETE FROM activity_telemetry WHERE timestampMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("SELECT COUNT(*) FROM activity_telemetry")
    fun observeCount(): Flow<Long>
}
