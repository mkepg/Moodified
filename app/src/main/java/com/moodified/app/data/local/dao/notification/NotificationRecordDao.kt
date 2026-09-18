package com.moodified.app.data.local.dao.notification

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.notification.NotificationRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: NotificationRecordEntity): Long

    @Query(
        "SELECT * FROM notification_records " +
            "WHERE dismissedAt IS NULL " +
            "ORDER BY deliveredAt DESC",
    )
    fun observeAll(): Flow<List<NotificationRecordEntity>>

    @Query(
        "SELECT COUNT(*) FROM notification_records " +
            "WHERE readAt IS NULL AND dismissedAt IS NULL",
    )
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE notification_records SET readAt = :nowMillis WHERE id = :id AND readAt IS NULL")
    suspend fun markRead(
        id: Long,
        nowMillis: Long,
    )

    @Query("UPDATE notification_records SET readAt = :nowMillis WHERE readAt IS NULL")
    suspend fun markAllRead(nowMillis: Long)

    @Query("UPDATE notification_records SET dismissedAt = :nowMillis WHERE id = :id AND dismissedAt IS NULL")
    suspend fun dismiss(
        id: Long,
        nowMillis: Long,
    )

    @Query("UPDATE notification_records SET dismissedAt = :nowMillis WHERE dismissedAt IS NULL")
    suspend fun dismissAll(nowMillis: Long)

    @Query("DELETE FROM notification_records WHERE deliveredAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
