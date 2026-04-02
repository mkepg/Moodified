package com.karamay.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.SleepSegment
import com.karamay.app.domain.model.SleepStatus
import java.time.LocalDateTime

@Entity(tableName = "sleep_segments")
data class SleepSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: String,
    val endTime: String,
    val status: String // AWAKE or ASLEEP
) {
    fun toDomain(): SleepSegment = SleepSegment(
        id = id,
        startTime = LocalDateTime.parse(startTime),
        endTime = LocalDateTime.parse(endTime),
        status = SleepStatus.valueOf(status)
    )

    companion object {
        fun fromDomain(segment: SleepSegment): SleepSegmentEntity = SleepSegmentEntity(
            id = segment.id,
            startTime = segment.startTime.toString(),
            endTime = segment.endTime.toString(),
            status = segment.status.name
        )
    }
}