package com.moodified.app.data.local.entity.intervention

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intervention_history")
data class InterventionHistoryEntity(
    @PrimaryKey
    val interventionId: String,
    val lastShownAtMillis: Long,
    @ColumnInfo(defaultValue = "NULL")
    val userFeedback: String? = null,
    @ColumnInfo(defaultValue = "")
    val domain: String = "",
    @ColumnInfo(defaultValue = "0")
    val wasCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val dismissalCount: Int = 0
)