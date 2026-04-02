package com.karamay.app.domain.repository

import com.karamay.app.domain.model.ActivitySignal
import kotlinx.coroutines.flow.Flow

/**
 * Contract for physical activity data.
 * Implementations own sensor lifecycle; callers never touch SensorManager directly.
 */
interface ActivityRepository {

    /**
     * Cold flow that emits [ActivitySignal] snapshots at most once per second
     * while tracking is active. Emits the last known snapshot immediately on
     * collection so the UI never shows stale zeros.
     */
    fun observeSignal(): Flow<ActivitySignal>

    /** Whether sensors are currently registered and accumulating data. */
    val isTracking: Boolean

    /**
     * Begin sensor registration. Safe to call multiple times — no-op if already tracking.
     * Returns false if required sensors are unavailable on this device.
     */
    fun startTracking(): Boolean

    /**
     * Unregister all sensors. Safe to call when not tracking.
     * Always call this when the consumer is destroyed to prevent battery drain.
     */
    fun stopTracking()
}
