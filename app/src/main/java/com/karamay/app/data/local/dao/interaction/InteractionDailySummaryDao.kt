package com.karamay.app.data.local.dao.interaction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.interaction.InteractionDailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionDailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InteractionDailySummaryEntity)

    @Query("SELECT * FROM interaction_daily_summaries WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<InteractionDailySummaryEntity?>

    @Query("SELECT * FROM interaction_daily_summaries WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getBetweenDates(startDate: String, endDate: String): Flow<List<InteractionDailySummaryEntity>>

    @Query("DELETE FROM interaction_daily_summaries WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}