package com.moodified.app.data.local.entity.activity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivityIntensity

@Entity(tableName = "activity_daily_summaries")
data class ActivityDailySummaryEntity(
    @PrimaryKey
    val date: String,
    val totalSteps: Int,
    val activeMinutes: Int,
    val sedentaryMinutes: Int,
    val peakIntensity: String,
    val isPartialDay: Boolean,
    // Sprint 1 Addition: Stored as a simple comma-separated String (e.g., "SEDENTARY:100,LIGHT:50")
    @ColumnInfo(defaultValue = "")
    val minutesPerIntensityBandRaw: String = "",
) {
    fun toDomain(): ActivityDailySummary {
        val distribution =
            minutesPerIntensityBandRaw
                .split(",")
                .filter { it.contains(":") }
                .associate { part ->
                    val (key, value) = part.split(":")
                    val intensity = runCatching { ActivityIntensity.valueOf(key) }.getOrDefault(ActivityIntensity.SEDENTARY)
                    intensity to (value.toIntOrNull() ?: 0)
                }

        return ActivityDailySummary(
            date = date,
            totalSteps = totalSteps,
            activeMinutes = activeMinutes,
            sedentaryMinutes = sedentaryMinutes,
            peakIntensity =
                runCatching { ActivityIntensity.valueOf(peakIntensity) }
                    .getOrDefault(ActivityIntensity.SEDENTARY),
            isPartialDay = isPartialDay,
            minutesPerIntensityBand = distribution,
        )
    }

    companion object {
        fun fromDomain(summary: ActivityDailySummary): ActivityDailySummaryEntity {
            val rawDistribution =
                summary.minutesPerIntensityBand.entries
                    .joinToString(",") { "${it.key.name}:${it.value}" }

            return ActivityDailySummaryEntity(
                date = summary.date,
                totalSteps = summary.totalSteps,
                activeMinutes = summary.activeMinutes,
                sedentaryMinutes = summary.sedentaryMinutes,
                peakIntensity = summary.peakIntensity.name,
                isPartialDay = summary.isPartialDay,
                minutesPerIntensityBandRaw = rawDistribution,
            )
        }
    }
}
