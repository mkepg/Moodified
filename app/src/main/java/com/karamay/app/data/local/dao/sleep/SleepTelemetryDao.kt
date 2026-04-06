package com.karamay.app.data.local.dao.sleep

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.sleep.SleepTelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(telemetry: List<SleepTelemetryEntity>)

    @Query("SELECT * FROM sleep_telemetry WHERE timestampMillis >= :startMillis AND timestampMillis <= :endMillis ORDER BY timestampMillis ASC")
    fun getTelemetryBetween(startMillis: Long, endMillis: Long): Flow<List<SleepTelemetryEntity>>

    @Query("DELETE FROM sleep_telemetry WHERE timestampMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}