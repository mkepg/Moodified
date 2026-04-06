package com.karamay.app.data.local.entity.activity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fix #6: New entity backing the activity_telemetry table.
 *
 * Previously, activity telemetry had no persistence layer at all — the repository
 * kept snapshots in-memory only. This table gives PurgeOldTelemetryUseCase a target
 * to housekeep, mirroring the existing sleep_telemetry table so both tracking systems
 * have symmetrical data lifecycle management.
 */
@Entity(tableName = "activity_telemetry")
data class ActivityTelemetryEntity(
    @PrimaryKey
    val timestampMillis: Long,
    val steps: Int,
    val activeMinutes: Int,
    val sedentaryMinutes: Int,
    val intensity: String,
)
