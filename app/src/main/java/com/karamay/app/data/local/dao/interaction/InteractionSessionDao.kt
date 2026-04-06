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

    @Query("DELETE FROM interaction_sessions WHERE startTimeMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}