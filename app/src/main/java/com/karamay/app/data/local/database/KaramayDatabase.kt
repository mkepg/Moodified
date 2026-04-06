package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karamay.app.data.local.dao.activity.ActivityDailySummaryDao
import com.karamay.app.data.local.dao.activity.ActivityTelemetryDao
import com.karamay.app.data.local.dao.interaction.InteractionDailySummaryDao
import com.karamay.app.data.local.dao.interaction.InteractionSessionDao
import com.karamay.app.data.local.dao.mood.MoodEntryDao
import com.karamay.app.data.local.dao.sleep.SleepSegmentDao
import com.karamay.app.data.local.dao.sleep.SleepTelemetryDao
import com.karamay.app.data.local.entity.activity.ActivityDailySummaryEntity
import com.karamay.app.data.local.entity.activity.ActivityTelemetryEntity
import com.karamay.app.data.local.entity.interaction.InteractionDailySummaryEntity
import com.karamay.app.data.local.entity.interaction.InteractionSessionEntity
import com.karamay.app.data.local.entity.mood.MoodEntryEntity
import com.karamay.app.data.local.entity.sleep.SleepSegmentEntity
import com.karamay.app.data.local.entity.sleep.SleepTelemetryEntity

@Database(
    entities = [
        MoodEntryEntity::class,
        SleepSegmentEntity::class,
        SleepTelemetryEntity::class,
        ActivityTelemetryEntity::class,
        ActivityDailySummaryEntity::class,
        InteractionSessionEntity::class,        // <-- NEW PHASE 3
        InteractionDailySummaryEntity::class    // <-- NEW PHASE 3
    ],
    version = 6, // <-- Bumped from 5 to 6
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {

    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun sleepSegmentDao(): SleepSegmentDao
    abstract fun sleepTelemetryDao(): SleepTelemetryDao
    abstract fun activityTelemetryDao(): ActivityTelemetryDao
    abstract fun activityDailySummaryDao(): ActivityDailySummaryDao

    // --- NEW PHASE 3 DAOs ---
    abstract fun interactionSessionDao(): InteractionSessionDao
    abstract fun interactionDailySummaryDao(): InteractionDailySummaryDao

    companion object {
        const val DATABASE_NAME = "karamay_db"
    }
}