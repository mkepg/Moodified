package com.karamay.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.ActivityTelemetryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Fix #6: New DAO for activity_telemetry, mirroring SleepTelemetryDao.
 * PurgeOldTelemetryUseCase calls deleteOlderThan() to prevent unbounded table growth.
 *
 * Note on clearAll(): deliberately NOT added here. SleepSegmentDao.clearAll() was
 * flagged as dangerous (issue #25) because it nukes all rows with no filter.
 * Only a bounded delete is exposed on this DAO.
 */
@Dao
interface ActivityTelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityTelemetryEntity)

    @Query("""
        SELECT * FROM activity_telemetry
        WHERE timestampMillis >= :startMillis AND timestampMillis <= :endMillis
        ORDER BY timestampMillis ASC
    """)
    fun getTelemetryBetween(startMillis: Long, endMillis: Long): Flow<List<ActivityTelemetryEntity>>

    /** Fix #6: Bounded delete — safe for scheduled purges via PurgeOldTelemetryUseCase. */
    @Query("DELETE FROM activity_telemetry WHERE timestampMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
