package com.karamay.app.data.local.entity.intervention

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intervention_history")
data class InterventionHistoryEntity(
    @PrimaryKey
    val interventionId: String,
    val lastShownAtMillis: Long
)