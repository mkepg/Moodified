package com.karamay.app.data.local.dao.interaction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.interaction.InteractionSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InteractionSessionEntity)

    @Query("SELECT * FROM interaction_sessions WHERE startTimeMillis >= :startMillis AND startTimeMillis < :endMillis ORDER BY startTimeMillis ASC")
    fun getSessionsBetween(startMillis: Long, endMillis: Long): Flow<List<InteractionSessionEntity>>

    // Sprint 2 addition: One-shot query for flush aggregation
    @Query("SELECT * FROM interaction_sessions WHERE startTimeMillis >= :startMillis AND startTimeMillis < :endMillis ORDER BY startTimeMillis ASC")
    suspend fun getSessionsListBetween(startMillis: Long, endMillis: Long): List<InteractionSessionEntity>

    @Query("DELETE FROM interaction_sessions WHERE startTimeMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM interaction_sessions WHERE startTimeMillis >= :startMillis AND startTimeMillis < :endMillis")
    suspend fun deleteSessionsBetween(startMillis: Long, endMillis: Long)
}