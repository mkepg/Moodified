package com.karamay.app.domain.repository

interface InterventionRepository {
    suspend fun recordInterventionShown(id: String)
    suspend fun getLastShownTime(id: String): Long?
    suspend fun isOnCooldown(id: String, cooldownMillis: Long): Boolean
}