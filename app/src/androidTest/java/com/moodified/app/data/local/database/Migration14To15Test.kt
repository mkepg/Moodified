package com.moodified.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the v14 → v15 Room migration adds the `notification_records` table with
 * the expected columns + index, and that pre-existing tables (mood_entries) survive.
 *
 * MIGRATION WHAT: v15 adds the `notification_records` table + one index on `deliveredAt`.
 * No existing tables are altered.
 *
 * ON-DEVICE VERIFICATION: Requires API 26+ device or emulator. Run:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     --tests "com.moodified.app.data.local.database.Migration14To15Test"
 */
@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    private val dbName = "migration-test-14-to-15.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoodifiedDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate14To15_addsNotificationRecordsTable_andPreservesMoodEntries() {
        // 1. Create v14 DB and seed one mood_entries row to verify unrelated data survives.
        helper.createDatabase(dbName, 14).use { db ->
            db.execSQL(
                """
                INSERT INTO mood_entries (valence, arousal, timestampMillis, note, isManual)
                VALUES ('POSITIVE', 'MID', 1704067200000, 'seed row', 1)
                """.trimIndent(),
            )
        }

        // 2. Run the migration (creates notification_records + index).
        val migratedDb =
            helper.runMigrationsAndValidate(
                dbName,
                15,
                true,
                MoodifiedDatabase.MIGRATION_14_15,
            )

        // 3. Verify the seed row survived.
        migratedDb.query("SELECT COUNT(*) FROM mood_entries WHERE note = 'seed row'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("Seed row lost during migration", cursor.getInt(0) >= 1)
        }

        // 4. Verify notification_records is insertable and readable at v15 shape.
        migratedDb.execSQL(
            """
            INSERT INTO notification_records (type, title, body, deepLink, deliveredAt, readAt, dismissedAt)
            VALUES ('MICRO_PROMPT', 'How are you feeling?', 'Micro-prompt body', 'moodified://quicklog', 1704067200000, NULL, NULL)
            """.trimIndent(),
        )
        migratedDb.query("SELECT type, title, deliveredAt FROM notification_records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("MICRO_PROMPT", cursor.getString(0))
            assertEquals("How are you feeling?", cursor.getString(1))
            assertEquals(1704067200000L, cursor.getLong(2))
        }
    }
}
