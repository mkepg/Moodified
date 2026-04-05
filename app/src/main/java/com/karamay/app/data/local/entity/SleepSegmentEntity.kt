package com.karamay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "sleep_segments")
data class SleepSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val status: String
) {
    fun toDomain(): SleepSegment = SleepSegment(
        id = id,
        startTime = Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        endTime   = Instant.ofEpochMilli(endTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
        status    = SleepStatus.valueOf(status)
    )

    companion object {
        fun fromDomain(segment: SleepSegment): SleepSegmentEntity = SleepSegmentEntity(
            id              = segment.id,
            startTimeMillis = segment.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endTimeMillis   = segment.endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            status          = segment.status.name
        )
    }
}