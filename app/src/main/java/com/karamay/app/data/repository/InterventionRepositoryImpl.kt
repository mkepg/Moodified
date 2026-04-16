package com.karamay.app.data.repository

import com.karamay.app.data.local.dao.intervention.InterventionHistoryDao
import com.karamay.app.data.local.entity.intervention.InterventionHistoryEntity
import com.karamay.app.domain.repository.InterventionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterventionRepositoryImpl @Inject constructor(
    private val dao: InterventionHistoryDao
) : InterventionRepository {

    override suspend fun recordInterventionShown(id: String) {
        val existing = dao.getInterventionHistory(id)
        val entity = InterventionHistoryEntity(
            interventionId = id,
            lastShownAtMillis = System.currentTimeMillis(),
            userFeedback = existing?.userFeedback,
            domain = existing?.domain ?: "",
            wasCompleted = existing?.wasCompleted ?: false,
            dismissalCount = existing?.dismissalCount ?: 0
        )
        dao.recordIntervention(entity)
    }

    override suspend fun getLastShownTime(id: String): Long? {
        return dao.getLastShownTime(id)
    }

    override suspend fun getInterventionHistory(id: String): InterventionHistoryEntity? {
        return dao.getInterventionHistory(id)
    }

    override suspend fun isOnCooldown(id: String, cooldownMillis: Long): Boolean {
        val history = getInterventionHistory(id) ?: return false

        // Adaptive suppression logic: Double cooldown if dismissed 3+ times
        val dismissalMultiplier = if (history.dismissalCount >= 3) 2L else 1L
        val effectiveCooldown = cooldownMillis * dismissalMultiplier

        return (System.currentTimeMillis() - history.lastShownAtMillis) < effectiveCooldown
    }

    override suspend fun recordFeedback(id: String, feedback: String, wasCompleted: Boolean) {
        if (feedback == "dismissed") {
            dao.incrementDismissal(id)
        } else if (feedback == "helpful") {
            dao.resetDismissalCount(id)
        }
        dao.updateFeedback(id, feedback, wasCompleted)
    }
}