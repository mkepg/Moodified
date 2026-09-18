package com.moodified.app.domain.model.notification

import com.moodified.app.data.local.entity.notification.NotificationRecordType

data class NotificationRecord(
    val id: Long = 0L,
    val type: NotificationRecordType,
    val title: String,
    val body: String,
    val deepLink: String?,
    val deliveredAt: Long,
    val readAt: Long? = null,
    val dismissedAt: Long? = null,
)
