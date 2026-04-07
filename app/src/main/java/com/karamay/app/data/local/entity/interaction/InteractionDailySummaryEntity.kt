package com.karamay.app.data.local.entity.interaction

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.interaction.InteractionDailySummary

@Entity(tableName = "interaction_daily_summaries")
data class InteractionDailySummaryEntity(
    @PrimaryKey
    val date: String,
    val totalScreenTimeMinutes: Int,
    // totalUnlocks column removed
    val lateNightUsageMinutes: Int = 0,   // minutes of screen-on time between 00:00–05:00
    val isPartialDay: Boolean
) {
    fun toDomain(): InteractionDailySummary = InteractionDailySummary(
        date                   = date,
        totalScreenTimeMinutes = totalScreenTimeMinutes,
        lateNightUsageMinutes  = lateNightUsageMinutes,
        isPartialDay           = isPartialDay
    )

    companion object {
        fun fromDomain(summary: InteractionDailySummary): InteractionDailySummaryEntity =
            InteractionDailySummaryEntity(
                date                   = summary.date,
                totalScreenTimeMinutes = summary.totalScreenTimeMinutes,
                lateNightUsageMinutes  = summary.lateNightUsageMinutes,
                isPartialDay           = summary.isPartialDay
            )
    }
}
