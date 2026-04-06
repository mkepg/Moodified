// app/src/main/java/com/karamay/app/data/local/entity/interaction/InteractionDailySummaryEntity.kt
package com.karamay.app.data.local.entity.interaction

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karamay.app.domain.model.interaction.InteractionDailySummary

@Entity(tableName = "interaction_daily_summaries")
data class InteractionDailySummaryEntity(
    @PrimaryKey
    val date: String,
    val totalScreenTimeMinutes: Int,
    val totalUnlocks: Int,
    val isPartialDay: Boolean
) {
    fun toDomain(): InteractionDailySummary = InteractionDailySummary(
        date = date,
        totalScreenTimeMinutes = totalScreenTimeMinutes,
        unlocks = totalUnlocks,
        isPartialDay = isPartialDay
    )

    companion object {
        fun fromDomain(summary: InteractionDailySummary): InteractionDailySummaryEntity =
            InteractionDailySummaryEntity(
                date = summary.date,
                totalScreenTimeMinutes = summary.totalScreenTimeMinutes,
                totalUnlocks = summary.unlocks,
                isPartialDay = summary.isPartialDay
            )
    }
}