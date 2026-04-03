package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.local.entity.MoodEntryEntity
import com.karamay.app.data.local.entity.SleepSegmentEntity
import com.karamay.app.data.local.entity.SleepTelemetryEntity

@Database(
    entities = [
        MoodEntryEntity::class,
        SleepSegmentEntity::class,
        SleepTelemetryEntity::class // Added new entity
    ],
    version  = 3, // Incremented version
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun sleepSegmentDao(): SleepSegmentDao
    abstract fun sleepTelemetryDao(): SleepTelemetryDao // Added new DAO

    companion object {
        const val DATABASE_NAME = "karamay_db"
    }
}