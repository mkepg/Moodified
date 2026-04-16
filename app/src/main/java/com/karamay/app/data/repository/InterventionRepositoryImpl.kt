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
        dao.recordIntervention(InterventionHistoryEntity(id, System.currentTimeMillis()))
    }

    override suspend fun getLastShownTime(id: String): Long? {
        return dao.getLastShownTime(id)
    }

    override suspend fun isOnCooldown(id: String, cooldownMillis: Long): Boolean {
        val lastShown = getLastShownTime(id) ?: return false
        return (System.currentTimeMillis() - lastShown) < cooldownMillis
    }
}