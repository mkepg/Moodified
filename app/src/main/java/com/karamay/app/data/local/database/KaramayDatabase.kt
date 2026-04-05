package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karamay.app.data.local.dao.ActivityTelemetryDao
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.local.entity.ActivityTelemetryEntity
import com.karamay.app.data.local.entity.MoodEntryEntity
import com.karamay.app.data.local.entity.SleepSegmentEntity
import com.karamay.app.data.local.entity.SleepTelemetryEntity

// Fix #6: Version bumped from 3 → 4 to introduce the activity_telemetry table.
// fallbackToDestructiveMigration() is still in place during development
// (see DatabaseModule). Before production, add Migration(3, 4) that executes:
//   CREATE TABLE IF NOT EXISTS `activity_telemetry` (
//     `timestampMillis` INTEGER NOT NULL,
//     `steps` INTEGER NOT NULL,
//     `activeMinutes` INTEGER NOT NULL,
//     `sedentaryMinutes` INTEGER NOT NULL,
//     `intensity` TEXT NOT NULL,
//     PRIMARY KEY(`timestampMillis`)
//   )
@Database(
    entities = [
        MoodEntryEntity::class,
        SleepSegmentEntity::class,
        SleepTelemetryEntity::class,
        ActivityTelemetryEntity::class,   // Fix #6: new table
    ],
    version      = 4,
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun sleepSegmentDao(): SleepSegmentDao
    abstract fun sleepTelemetryDao(): SleepTelemetryDao
    abstract fun activityTelemetryDao(): ActivityTelemetryDao   // Fix #6

    companion object {
        const val DATABASE_NAME = "karamay_db"
    }
}
