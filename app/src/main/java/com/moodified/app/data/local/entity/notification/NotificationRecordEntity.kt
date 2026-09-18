package com.moodified.app.data.local.entity.notification

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_records",
    indices = [Index(value = ["deliveredAt"], name = "index_notification_records_deliveredAt")],
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "deepLink")
    val deepLink: String?,
    @ColumnInfo(name = "deliveredAt")
    val deliveredAt: Long,
    @ColumnInfo(name = "readAt")
    val readAt: Long?,
    @ColumnInfo(name = "dismissedAt")
    val dismissedAt: Long?,
)
