package com.moodified.app.domain.usecase.common

import com.moodified.app.domain.repository.NotificationHistoryRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PurgeOldNotificationRecordsUseCase
    @Inject
    constructor(
        private val notificationHistoryRepository: NotificationHistoryRepository,
    ) {
        suspend operator fun invoke(): Int {
            val cutoffMillis = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS).toEpochMilli()
            return notificationHistoryRepository.deleteOlderThan(cutoffMillis)
        }

        companion object {
            private const val RETENTION_DAYS = 90L
        }
    }
