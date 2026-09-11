package com.moodified.app.data.local.dao.intervention

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity

@Dao
interface InterventionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordIntervention(entity: InterventionHistoryEntity)

    @Query("SELECT lastShownAtMillis FROM intervention_history WHERE interventionId = :interventionId LIMIT 1")
    suspend fun getLastShownTime(interventionId: String): Long?

    @Query("SELECT * FROM intervention_history WHERE interventionId = :interventionId LIMIT 1")
    suspend fun getInterventionHistory(interventionId: String): InterventionHistoryEntity?

    @Query("UPDATE intervention_history SET userFeedback = :feedback, wasCompleted = :wasCompleted WHERE interventionId = :interventionId")
    suspend fun updateFeedback(
        interventionId: String,
        feedback: String?,
        wasCompleted: Boolean,
    )

    @Query("UPDATE intervention_history SET dismissalCount = dismissalCount + 1 WHERE interventionId = :interventionId")
    suspend fun incrementDismissal(interventionId: String)

    @Query("UPDATE intervention_history SET dismissalCount = 0 WHERE interventionId = :interventionId")
    suspend fun resetDismissalCount(interventionId: String)
}
