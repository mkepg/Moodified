package com.moodified.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the v13 → v14 Room migration preserves existing mood_entries rows.
 *
 * MIGRATION WHAT: v14 adds the `intervention_history` table. The `mood_entries` table is
 * untouched, so a seed row inserted at v13 must survive intact.
 *
 * TEMPLATE USAGE: Phase 5 should copy this file and adjust:
 *   - [dbName] → "migration-test-14-to-15.db"
 *   - [helper] versions 13→14 → 14→15
 *   - [MoodifiedDatabase.MIGRATION_13_14] → appropriate migration object
 *   - seed INSERT to match the v14 schema
 *   - SELECT predicate in the verification step
 *
 * ON-DEVICE VERIFICATION: Requires API 26+ device or emulator. Run:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     --tests "com.moodified.app.data.local.database.Migration13To14Test"
 */
@RunWith(AndroidJUnit4::class)
class Migration13To14Test {
    private val dbName = "migration-test-13-to-14.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoodifiedDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate13To14_preservesMoodEntries() {
        // 1. Create v13 DB and insert one row with valid v13 column names and types.
        //    v13 mood_entries schema (from exported schema JSON):
        //      id INTEGER PK AUTOINCREMENT, valence TEXT NOT NULL, arousal TEXT NOT NULL,
        //      note TEXT, timestampMillis INTEGER NOT NULL, isManual INTEGER NOT NULL,
        //      contextActivityIntensity TEXT DEFAULT NULL, contextSleepMinutes INTEGER DEFAULT NULL
        //    Valence stored as enum name: NEGATIVE | NEUTRAL | POSITIVE
        //    Arousal stored as enum name: LOW | MID | HIGH
        helper.createDatabase(dbName, 13).use { db ->
            db.execSQL(
                """
                INSERT INTO mood_entries (valence, arousal, timestampMillis, note, isManual)
                VALUES ('POSITIVE', 'MID', 1704067200000, 'seed row', 1)
                """.trimIndent(),
            )
        }

        // 2. Run the migration (adds intervention_history table; mood_entries is untouched).
        // validateDroppedTables = true ensures Room validates the full v14 schema.
        val migratedDb =
            helper.runMigrationsAndValidate(
                dbName,
                14,
                true,
                MoodifiedDatabase.MIGRATION_13_14,
            )

        // 3. Verify the seed row survived the migration.
        migratedDb.query("SELECT COUNT(*) FROM mood_entries WHERE note = 'seed row'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val count = cursor.getInt(0)
            assertTrue("Seed row lost during migration (count=$count)", count >= 1)
        }
    }
}
