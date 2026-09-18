package com.moodified.app.data.repository

import com.moodified.app.data.local.dao.notification.NotificationRecordDao
import com.moodified.app.data.local.entity.notification.NotificationRecordEntity
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHistoryRepositoryImpl
    @Inject
    constructor(
        private val dao: NotificationRecordDao,
    ) : NotificationHistoryRepository {
        override suspend fun record(record: NotificationRecord): Long = dao.insert(record.toEntity())

        override fun observeAll(): Flow<List<NotificationRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

        override suspend fun markRead(id: Long) = dao.markRead(id, System.currentTimeMillis())

        override suspend fun markAllRead() = dao.markAllRead(System.currentTimeMillis())

        override suspend fun dismiss(id: Long) = dao.dismiss(id, System.currentTimeMillis())

        override suspend fun dismissAll() = dao.dismissAll(System.currentTimeMillis())

        override suspend fun deleteOlderThan(cutoffMillis: Long): Int = dao.deleteOlderThan(cutoffMillis)
    }

private fun NotificationRecord.toEntity(): NotificationRecordEntity =
    NotificationRecordEntity(
        id = id,
        type = type.name,
        title = title,
        body = body,
        deepLink = deepLink,
        deliveredAt = deliveredAt,
        readAt = readAt,
        dismissedAt = dismissedAt,
    )

private fun NotificationRecordEntity.toDomain(): NotificationRecord =
    NotificationRecord(
        id = id,
        type = com.moodified.app.data.local.entity.notification.NotificationRecordType.valueOf(type),
        title = title,
        body = body,
        deepLink = deepLink,
        deliveredAt = deliveredAt,
        readAt = readAt,
        dismissedAt = dismissedAt,
    )
