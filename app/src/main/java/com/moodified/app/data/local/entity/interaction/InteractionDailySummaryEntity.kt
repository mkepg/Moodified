package com.moodified.app.data.local.entity.interaction

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodified.app.domain.model.interaction.InteractionDailySummary

@Entity(tableName = "interaction_daily_summaries")
data class InteractionDailySummaryEntity(
    @PrimaryKey
    val date: String,
    val totalScreenTimeMinutes: Int,
    val lateNightUsageMinutes: Int = 0,
    @ColumnInfo(name = "unlockCount", defaultValue = "0")
    val unlockCount: Int = 0,
    // Sprint 1 Additions
    @ColumnInfo(defaultValue = "0")
    val sessionCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val averageSessionDurationMinutes: Int = 0,
) {
    fun toDomain(): InteractionDailySummary =
        InteractionDailySummary(
            date = date,
            totalScreenTimeMinutes = totalScreenTimeMinutes,
            lateNightUsageMinutes = lateNightUsageMinutes,
            unlockCount = unlockCount,
            sessionCount = sessionCount,
            averageSessionDurationMinutes = averageSessionDurationMinutes,
        )

    companion object {
        fun fromDomain(summary: InteractionDailySummary): InteractionDailySummaryEntity =
            InteractionDailySummaryEntity(
                date = summary.date,
                totalScreenTimeMinutes = summary.totalScreenTimeMinutes,
                lateNightUsageMinutes = summary.lateNightUsageMinutes,
                unlockCount = summary.unlockCount,
                sessionCount = summary.sessionCount,
                averageSessionDurationMinutes = summary.averageSessionDurationMinutes,
            )
    }
}
