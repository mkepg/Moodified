package com.karamay.app.data.local.dao.intervention

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karamay.app.data.local.entity.intervention.InterventionHistoryEntity

@Dao
interface InterventionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordIntervention(entity: InterventionHistoryEntity)

    @Query("SELECT lastShownAtMillis FROM intervention_history WHERE interventionId = :interventionId LIMIT 1")
    suspend fun getLastShownTime(interventionId: String): Long?
}