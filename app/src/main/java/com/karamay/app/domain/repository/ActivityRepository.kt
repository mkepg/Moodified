package com.karamay.app.domain.repository

import com.karamay.app.domain.model.ActivityIntensity
import com.karamay.app.domain.model.ActivitySignal
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun observeSignal(): Flow<ActivitySignal>
    val isTracking: Boolean
    fun startTracking(): Boolean
    fun stopTracking()
    fun resetSession()
    suspend fun purgeActivityTelemetryOlderThan(cutoffMillis: Long)

    // UPDATED: Now takes pure domain types instead of Google's DetectedActivity
    suspend fun updateActivityIntensity(intensity: ActivityIntensity, confidence: Int)
}