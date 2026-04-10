package com.karamay.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        InteractionSessionEntity::class,
        InteractionDailySummaryEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class KaramayDatabase : RoomDatabase() {

    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun sleepSegmentDao(): SleepSegmentDao
    abstract fun sleepTelemetryDao(): SleepTelemetryDao
    abstract fun activityTelemetryDao(): ActivityTelemetryDao
    abstract fun activityDailySummaryDao(): ActivityDailySummaryDao
    abstract fun interactionSessionDao(): InteractionSessionDao
    abstract fun interactionDailySummaryDao(): InteractionDailySummaryDao

    companion object {
        const val DATABASE_NAME = "karamay_db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interaction_sessions_new` (
                        `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startTimeMillis` INTEGER NOT NULL,
                        `endTimeMillis`   INTEGER NOT NULL,
                        `durationMinutes` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `interaction_sessions_new`
                        (id, startTimeMillis, endTimeMillis, durationMinutes)
                    SELECT id, startTimeMillis, endTimeMillis, durationMinutes
                    FROM `interaction_sessions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `interaction_sessions`")
                db.execSQL("ALTER TABLE `interaction_sessions_new` RENAME TO `interaction_sessions`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interaction_daily_summaries_new` (
                        `date`                   TEXT    PRIMARY KEY NOT NULL,
                        `totalScreenTimeMinutes` INTEGER NOT NULL,
                        `lateNightUsageMinutes`  INTEGER NOT NULL DEFAULT 0,
                        `isPartialDay`           INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `interaction_daily_summaries_new`
                        (date, totalScreenTimeMinutes, lateNightUsageMinutes, isPartialDay)
                    SELECT date, totalScreenTimeMinutes, 0, isPartialDay
                    FROM `interaction_daily_summaries`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `interaction_daily_summaries`")
                db.execSQL(
                    "ALTER TABLE `interaction_daily_summaries_new` " +
                            "RENAME TO `interaction_daily_summaries`"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `interaction_daily_summaries` ADD COLUMN `unlockCount` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interaction_daily_summaries_new` (
                        `date`                   TEXT    PRIMARY KEY NOT NULL,
                        `totalScreenTimeMinutes` INTEGER NOT NULL,
                        `lateNightUsageMinutes`  INTEGER NOT NULL DEFAULT 0,
                        `unlockCount`            INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `interaction_daily_summaries_new`
                        (date, totalScreenTimeMinutes, lateNightUsageMinutes, unlockCount)
                    SELECT date, totalScreenTimeMinutes, lateNightUsageMinutes, unlockCount
                    FROM `interaction_daily_summaries`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `interaction_daily_summaries`")
                db.execSQL("ALTER TABLE `interaction_daily_summaries_new` RENAME TO `interaction_daily_summaries`")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `sleep_segments` ADD COLUMN `isBackfilled` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}