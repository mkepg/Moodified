package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karamay.app.data.local.dao.ActivityDailySummaryDao
import com.karamay.app.data.local.dao.ActivityTelemetryDao
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.local.entity.ActivityDailySummaryEntity
import com.karamay.app.data.local.entity.ActivityTelemetryEntity
import com.karamay.app.data.local.entity.MoodEntryEntity
import com.karamay.app.data.local.entity.SleepSegmentEntity
import com.karamay.app.data.local.entity.SleepTelemetryEntity

@Database(
    entities = [
        MoodEntryEntity::class,
        SleepSegmentEntity::class,
        SleepTelemetryEntity::class,
        ActivityTelemetryEntity::class,
        ActivityDailySummaryEntity::class,   // NEW — version 5
    ],
    version      = 5,
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun sleepSegmentDao(): SleepSegmentDao
    abstract fun sleepTelemetryDao(): SleepTelemetryDao
    abstract fun activityTelemetryDao(): ActivityTelemetryDao
    abstract fun activityDailySummaryDao(): ActivityDailySummaryDao   // NEW

    companion object {
        const val DATABASE_NAME = "karamay_db"
    }
}
