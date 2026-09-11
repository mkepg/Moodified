package com.moodified.app.domain.repository

import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity

interface InterventionRepository {
    suspend fun recordInterventionShown(id: String)

    suspend fun getLastShownTime(id: String): Long?

    suspend fun isOnCooldown(
        id: String,
        cooldownMillis: Long,
    ): Boolean

    suspend fun getInterventionHistory(id: String): InterventionHistoryEntity?

    suspend fun recordFeedback(
        id: String,
        feedback: String,
        wasCompleted: Boolean,
    )
}
