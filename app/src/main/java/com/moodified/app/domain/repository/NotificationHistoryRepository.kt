package com.moodified.app.domain.repository

import com.moodified.app.domain.model.notification.NotificationRecord
import kotlinx.coroutines.flow.Flow

interface NotificationHistoryRepository {
    suspend fun record(record: NotificationRecord): Long

    fun observeAll(): Flow<List<NotificationRecord>>

    fun observeUnreadCount(): Flow<Int>

    suspend fun markRead(id: Long)

    suspend fun markAllRead()

    suspend fun dismiss(id: Long)

    suspend fun dismissAll()

    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
