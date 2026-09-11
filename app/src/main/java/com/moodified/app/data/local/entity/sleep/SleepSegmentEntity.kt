package com.moodified.app.data.local.entity.sleep

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodified.app.domain.model.sleep.SleepSegment
import com.moodified.app.domain.model.sleep.SleepStatus
import java.time.Instant
import java.time.ZoneId

@Entity(tableName = "sleep_segments")
data class SleepSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val status: String,
    @ColumnInfo(name = "isBackfilled", defaultValue = "0")
    val isBackfilled: Boolean = false,
    @ColumnInfo(name = "awakenings", defaultValue = "0")
    val awakenings: Int = 0,
    @ColumnInfo(name = "timeInBedMinutes", defaultValue = "0")
    val timeInBedMinutes: Int = 0,
    @ColumnInfo(name = "totalSleepMinutes", defaultValue = "0")
    val totalSleepMinutes: Int = 0,
    @ColumnInfo(name = "confidence", defaultValue = "0")
    val confidence: Int = 0,
) {
    fun toDomain(): SleepSegment =
        SleepSegment(
            id = id,
            startTime = Instant.ofEpochMilli(startTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            endTime = Instant.ofEpochMilli(endTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            status = SleepStatus.valueOf(status),
            awakenings = awakenings,
            timeInBedMinutes = timeInBedMinutes,
            totalSleepMinutes = totalSleepMinutes,
            confidence = confidence,
        )

    companion object {
        fun fromDomain(
            segment: SleepSegment,
            isBackfilled: Boolean = false,
        ): SleepSegmentEntity =
            SleepSegmentEntity(
                id = segment.id,
                startTimeMillis = segment.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endTimeMillis = segment.endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                status = segment.status.name,
                isBackfilled = isBackfilled,
                awakenings = segment.awakenings,
                timeInBedMinutes = segment.timeInBedMinutes,
                totalSleepMinutes = segment.totalSleepMinutes,
                confidence = segment.confidence,
            )
    }
}
