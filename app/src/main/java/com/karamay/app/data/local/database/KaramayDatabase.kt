package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.entity.MoodEntryEntity

@Database(
    entities = [MoodEntryEntity::class],
    version  = 1,
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao

    companion object {
        const val DATABASE_NAME = "karamay_db"
    }
}
