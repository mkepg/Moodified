package com.moodified.app.data.local.dao.activity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.activity.ActivityDailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDailySummaryDao {
    /**
     * Upsert a daily summary. Called on every TelemetryWorker flush and on
     * stopTracking(), so "today" row is progressively updated throughout the day.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActivityDailySummaryEntity)

    /** Returns the summary for a specific date, or null if no data exists yet. */
    @Query("SELECT * FROM activity_daily_summaries WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<ActivityDailySummaryEntity?>

    /**
     * Returns summaries for a list of dates, ordered oldest-first.
     * Room does not support passing a List<String> to IN clauses directly
     * in all versions, so we use a bounded range query instead and filter
     * in the repository.
     */
    @Query(
        """
        SELECT * FROM activity_daily_summaries
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date ASC
    """,
    )
    fun getBetweenDates(
        startDate: String,
        endDate: String,
    ): Flow<List<ActivityDailySummaryEntity>>

    /** Purge records older than [cutoffDate] to honour the 14-day retention policy. */
    @Query("DELETE FROM activity_daily_summaries WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
