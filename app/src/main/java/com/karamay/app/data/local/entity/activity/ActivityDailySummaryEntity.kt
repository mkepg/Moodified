package com.karamay.app.data.local.entity.activity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.activity.ActivityIntensity
import com.karamay.app.domain.model.activity.ActivityDailySummary

/**
 * One row per calendar day. The primary key is an ISO date string ("YYYY-MM-DD").
 * Rows are upserted on every TelemetryWorker tick and on stopTracking(), so the
 * record for "today" is progressively updated throughout the day rather than
 * written only once at midnight.
 */
@Entity(tableName = "activity_daily_summaries")
data class ActivityDailySummaryEntity(
    @PrimaryKey
    val date: String,                   // "YYYY-MM-DD"
    val totalSteps: Int,
    val activeMinutes: Int,
    val sedentaryMinutes: Int,
    val peakIntensity: String,          // ActivityIntensity.name
    val isPartialDay: Boolean,
) {
    fun toDomain(): ActivityDailySummary = ActivityDailySummary(
        date             = date,
        totalSteps       = totalSteps,
        activeMinutes    = activeMinutes,
        sedentaryMinutes = sedentaryMinutes,
        peakIntensity    = runCatching { ActivityIntensity.valueOf(peakIntensity) }
            .getOrDefault(ActivityIntensity.SEDENTARY),
        isPartialDay     = isPartialDay,
    )

    companion object {
        fun fromDomain(summary: ActivityDailySummary): ActivityDailySummaryEntity =
            ActivityDailySummaryEntity(
                date             = summary.date,
                totalSteps       = summary.totalSteps,
                activeMinutes    = summary.activeMinutes,
                sedentaryMinutes = summary.sedentaryMinutes,
                peakIntensity    = summary.peakIntensity.name,
                isPartialDay     = summary.isPartialDay,
            )
    }
}
