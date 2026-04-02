package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.entity.MoodEntryEntity
import com.karamay.app.data.local.entity.SleepSegmentEntity

@Database(
    entities = [
        MoodEntryEntity::class,
        SleepSegmentEntity::class // Added the new Sleep Entity
    ],
    version  = 2, // Bumped version to 2
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {

    abstract fun moodEntryDao(): MoodEntryDao

    // The missing reference! This exposes the DAO to the rest of the app.
    abstract fun sleepSegmentDao(): SleepSegmentDao

    companion object {
        const val DATABASE_NAME = "karamay_db"
    }
}