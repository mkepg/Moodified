package com.karamay.app.data.local.entity.sleep

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_telemetry")
data class SleepTelemetryEntity(
    @PrimaryKey
    val timestampMillis: Long,
    val confidence: Int,
    val deviceMotion: Int
)