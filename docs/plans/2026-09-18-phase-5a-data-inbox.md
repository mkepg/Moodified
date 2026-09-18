# Phase 5a — Notifications Inbox Data + UI + Profile Row Implementation Plan

**Goal:** Ship the notifications inbox end-to-end — Room v14→v15 migration + entity/DAO/repository, persistence hook in the two production notify sites, 90-day retention purge, full-screen `notifications/inbox` route + ViewModel, `moodified://inbox` deep link, Notifications row on Profile with a Flow-driven unread badge, and (bundled) fix the parked Phase 4 FeedbackSheet "No email app installed" Snackbar via a Profile-scoped `SnackbarHostState`.

**Architecture:**
- **Data layer** follows the existing Room + Hilt shape verbatim: entity under `data/local/entity/notification/`, DAO under `data/local/dao/notification/`, migration object in `MoodifiedDatabase.companion`, DAO provider in `DatabaseModule`, repository interface in `domain/repository/`, impl in `data/repository/`, `@Binds` in `RepositoryModule`.
- **Persistence hook** is a shared suspending helper on `NotificationHistoryRepository` — `suspend fun record(record: NotificationRecord)`. Both production notify sites (`MicroPromptReceiver.handleValenceSelection` and `TrackingService.showMicroPromptNotification`) call it via `runBlocking(Dispatchers.IO)` before `NotificationManagerCompat.notify(...)`. `ProfileViewModel.triggerTestMicroPrompt` is explicitly **excluded** — dev affordance, not a "the app said this" event; Phase 5d will move it into the Debug Drawer anyway.
- **Purge** adds a `PurgeOldNotificationRecordsUseCase` under `domain/usecase/common/`, invoked from `PurgeWorker.doWork()` after the existing `PurgeOldTelemetryUseCase()` call. 90-day cutoff, matching spec §3.2.2.
- **UI** — `NotificationsInboxScreen` is a full-screen route (bottom bar hidden). Chronological list grouped by day header. Tap opens `deepLink` via the shared nav controller; swipe-to-dismiss marks `dismissedAt`; top-bar "Clear all" empties the list (calls `dismissAll()`).
- **Profile Notifications row** is a new `NotificationsRow` composable rendered under a new **Notifications** section header on `ProfileScreen`, above the existing **Support** section. Unread count comes from `observeUnreadCount(): Flow<Int>` collected in `ProfileViewModel`.
- **Snackbar host** — `ProfileScreen` wraps its `LazyColumn` in a `Scaffold(snackbarHost = ...)`. A `showSnackbar: suspend (String) -> Unit` lambda is passed to `FeedbackSheet`; when `intent.resolveActivity(...)` returns `null`, it calls `showSnackbar("No email app installed")`. This is the parked Phase 4 minor.

**Tech Stack:** Kotlin, Room 2.6.1 (existing), Jetpack Compose (Material 3), Hilt (existing), Coroutines/Flow (existing), Compose Foundation (`SwipeToDismissBox`), `runBlocking` for the receiver hook. No new Gradle dependencies. Schema change: v14 → v15.

**Spec:** [docs/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.1 (Notifications row grouping), §3.2.2 (inbox surface), §4 (schema), §5 (Migration14To15Test in `Testing strategy`), §6 Phase 5. Fixes the parked FeedbackSheet Snackbar minor noted in [Phase 4 plan](2026-09-16-phase-4-support-surface.md)'s Follow-ups.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- Green gate per task: `./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt` — all green before commit.
- Test suite: existing Phase 1 tests plus **one new** `Migration14To15Test` added in Task 1. No unit tests for the ViewModel/UI this phase (consolidation phases carry no new test scaffolding beyond migration tests per spec §5).
- Detekt baseline additions allowed **only** in `FunctionNaming` / `LongParameterList` / `LongMethod` / `MatchingDeclarationName` (Compose false positives). Any other new category is a real signal — fix, don't baseline. Baseline path: `config/detekt/detekt-baseline.xml`. Regenerate via `./gradlew detektBaseline`. Wrap long constructor calls at authoring time so `MaxLineLength` never lands in the baseline.
- Feature branch: `phase-5a-data-inbox` (created in Task 0).- **Persistence-hook exclusion:** `ProfileViewModel.triggerTestMicroPrompt` (line 123, `Debug Data` section) is a dev affordance and MUST NOT call the repository. Phase 5d handles its relocation to the Debug Drawer.
- **Retention window:** 90 days (`90 * 24 * 60 * 60 * 1000L` milliseconds). Do not parameterize — hard-code.
- **Deep-link URI:** `moodified://inbox` — no path segment; parsed identically to the existing `moodified://quicklog` in `MainActivity.handleIntent`.
- **Migration test:** copy `app/src/androidTest/java/com/moodified/app/data/local/database/Migration13To14Test.kt` verbatim as `Migration14To15Test.kt` and adjust (the source file already contains a `TEMPLATE USAGE` block naming the exact fields to change).
- **No push to origin:** the phase merges locally to `main` after review clears. Do not `git push` from any task in this plan.
- **Scope fences:**
  - No touching `presentation/onboarding/` (Phase 5b).
  - No touching `BottomNavItem.kt` / bottom-nav wiring (Phase 5c).
  - No touching `presentation/devtools/` (Phase 5d and 3 territory).
  - No renaming "Tracking Preferences" → "Tracking" — that's Phase 5c.
  - No changes to `ProfileViewModel.triggerTestMicroPrompt`.

---

### Task 0: Create the feature branch and verify pre-phase greens

**Files:** none (git-only).

**Interfaces:** none.

- [ ] **Step 1: Ensure working tree is clean, main is up to date**

Run:
```
git status
git checkout main
git pull --ff-only
```
Expected: `main` at Phase 4's final commit (`0743f7f fix(profile): rename MoreUiState to ProfileUiState and use rememberSaveable for HelpScreen expandedId`) or later. Untracked docs under `docs/` (e.g., `docs/application-overview.md`, `docs/firebase-firestore-and-stripe-integration-plan.md`) — leave untracked, out of scope. If `.idea/` files are dirty, stash them: `git stash push -m "phase-5a-scratch" .idea/`.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-5a-data-inbox
```
Expected: branch created and checked out. Verify with `git status`.

- [ ] **Step 3: Sanity-check the pre-phase greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If any failure, stop and diagnose — Phase 5a needs a known-green baseline.

No commit for this task — Task 1 makes the first commit.

---

### Task 1: `NotificationRecordEntity` + `MIGRATION_14_15` + schema export + `Migration14To15Test`

Introduce the entity, register it on `MoodifiedDatabase`, add the migration to the companion + Hilt module, export the schema, and copy the Migration13To14Test template into a Migration14To15Test.

**Files:**
- Create: `app/src/main/java/com/moodified/app/data/local/entity/notification/NotificationRecordEntity.kt`
- Create: `app/src/main/java/com/moodified/app/data/local/entity/notification/NotificationRecordType.kt`
- Modify: `app/src/main/java/com/moodified/app/data/local/database/MoodifiedDatabase.kt` (add entity to `@Database`, bump `version` to 15, add `MIGRATION_14_15`)
- Modify: `app/src/main/java/com/moodified/app/di/DatabaseModule.kt` (add `.addMigrations(MoodifiedDatabase.MIGRATION_14_15,)` in the builder chain)
- Create: `app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/15.json` (Room emits this automatically on the first assembleDebug after the version bump — verify it appears)
- Create: `app/src/androidTest/java/com/moodified/app/data/local/database/Migration14To15Test.kt`

**Interfaces:**
- Consumes: nothing outside the file itself.
- Produces:
  - `com.moodified.app.data.local.entity.notification.NotificationRecordEntity` — Room entity, table `notification_records`, PK autogenerated `id: Long`.
  - `com.moodified.app.data.local.entity.notification.NotificationRecordType` — Kotlin enum: `MICRO_PROMPT`, `CARE_REMINDER`, `TREND_ALERT`, `TRACKING_STATUS`. String column, `.name` at insertion.
  - `MoodifiedDatabase.MIGRATION_14_15` — public `val`.
  - `MoodifiedDatabase.version = 15`.

- [ ] **Step 1: Create `NotificationRecordType.kt`**

Create `app/src/main/java/com/moodified/app/data/local/entity/notification/NotificationRecordType.kt`:
```kotlin
package com.moodified.app.data.local.entity.notification

/**
 * Category of a notification stored in the inbox. Persisted as the enum name (String).
 */
enum class NotificationRecordType {
    MICRO_PROMPT,
    CARE_REMINDER,
    TREND_ALERT,
    TRACKING_STATUS,
}
```

- [ ] **Step 2: Create `NotificationRecordEntity.kt`**

Create `app/src/main/java/com/moodified/app/data/local/entity/notification/NotificationRecordEntity.kt`:
```kotlin
package com.moodified.app.data.local.entity.notification

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_records",
    indices = [Index(value = ["deliveredAt"], name = "index_notification_records_deliveredAt")],
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "deepLink")
    val deepLink: String?,
    @ColumnInfo(name = "deliveredAt")
    val deliveredAt: Long,
    @ColumnInfo(name = "readAt")
    val readAt: Long?,
    @ColumnInfo(name = "dismissedAt")
    val dismissedAt: Long?,
)
```

- [ ] **Step 3: Register the entity + bump the version + add the migration in `MoodifiedDatabase.kt`**

In `app/src/main/java/com/moodified/app/data/local/database/MoodifiedDatabase.kt`:

3a. Add import at the top of the file (alphabetically among the other entity imports, after the `InterventionHistoryEntity` import on line 18):
```kotlin
import com.moodified.app.data.local.entity.notification.NotificationRecordEntity
```

3b. In the `@Database(entities = [...])` list (currently lines 23-31), add `NotificationRecordEntity::class,` as the last entry — the new entities list is:
```kotlin
entities = [
    MoodEntryEntity::class,
    SleepSegmentEntity::class,
    ActivityTelemetryEntity::class,
    ActivityDailySummaryEntity::class,
    InteractionSessionEntity::class,
    InteractionDailySummaryEntity::class,
    InterventionHistoryEntity::class,
    NotificationRecordEntity::class,
],
```

3c. Change `version = 14` (line 32) to `version = 15`.

3d. In the `companion object`, immediately after `MIGRATION_13_14` (currently ends at line 194 with the closing `}` of the migration object), add:
```kotlin

        val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `notification_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `type` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `body` TEXT NOT NULL,
                            `deepLink` TEXT,
                            `deliveredAt` INTEGER NOT NULL,
                            `readAt` INTEGER,
                            `dismissedAt` INTEGER
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS `index_notification_records_deliveredAt`
                          ON `notification_records`(`deliveredAt`)
                        """.trimIndent(),
                    )
                }
            }
```
Note the SQL uses back-ticked identifiers matching the existing style in this file (see `MIGRATION_13_14`).

- [ ] **Step 4: Register the migration in `DatabaseModule.kt`**

In `app/src/main/java/com/moodified/app/di/DatabaseModule.kt`, in the `.addMigrations(...)` call (currently lines 44-53), add `MoodifiedDatabase.MIGRATION_14_15,` as the last entry immediately after `MoodifiedDatabase.MIGRATION_13_14,`:
```kotlin
.addMigrations(
    MoodifiedDatabase.MIGRATION_6_7,
    MoodifiedDatabase.MIGRATION_7_8,
    MoodifiedDatabase.MIGRATION_8_9,
    MoodifiedDatabase.MIGRATION_9_10,
    MoodifiedDatabase.MIGRATION_10_11,
    MoodifiedDatabase.MIGRATION_11_12,
    MoodifiedDatabase.MIGRATION_12_13,
    MoodifiedDatabase.MIGRATION_13_14,
    MoodifiedDatabase.MIGRATION_14_15,
)
```

- [ ] **Step 5: Trigger schema export**

Run:
```
./gradlew :app:assembleDebug
```
Expected: build passes; a new file appears at `app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/15.json`. Open it and verify it contains a `"notification_records"` table with the eight columns above and one index on `deliveredAt`.

- [ ] **Step 6: Create `Migration14To15Test.kt` from the template**

Copy `app/src/androidTest/java/com/moodified/app/data/local/database/Migration13To14Test.kt` to `app/src/androidTest/java/com/moodified/app/data/local/database/Migration14To15Test.kt`.

Replace the file body with:
```kotlin
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
```

- [ ] **Step 7: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. The instrumented `Migration14To15Test` does NOT run under `testDebugUnitTest` (it's under `androidTest`), so no test run is verified here — that's fine; instrumented tests run on-device only. The migration test is committed and will run under `:app:connectedDebugAndroidTest` when the user next runs an emulator.

- [ ] **Step 8: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/data/local/entity/notification/ \
        app/src/main/java/com/moodified/app/data/local/database/MoodifiedDatabase.kt \
        app/src/main/java/com/moodified/app/di/DatabaseModule.kt \
        app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/15.json \
        app/src/androidTest/java/com/moodified/app/data/local/database/Migration14To15Test.kt

git commit -m "feat(inbox): add NotificationRecordEntity + v14→v15 migration"
```
No `Co-Authored-By` trailer.

---

### Task 2: `NotificationRecordDao` + DAO provider in `DatabaseModule`

Add the DAO abstraction (spec §3.2.2 exact method list), an accessor on `MoodifiedDatabase`, and a Hilt provider.

**Files:**
- Create: `app/src/main/java/com/moodified/app/data/local/dao/notification/NotificationRecordDao.kt`
- Modify: `app/src/main/java/com/moodified/app/data/local/database/MoodifiedDatabase.kt` (add `abstract fun notificationRecordDao(): NotificationRecordDao`)
- Modify: `app/src/main/java/com/moodified/app/di/DatabaseModule.kt` (add `@Provides fun provideNotificationRecordDao(...)`)

**Interfaces:**
- Consumes: `NotificationRecordEntity` from Task 1.
- Produces: `NotificationRecordDao` — public methods listed in Step 1. Hilt-provided singleton.

- [ ] **Step 1: Create `NotificationRecordDao.kt`**

Create `app/src/main/java/com/moodified/app/data/local/dao/notification/NotificationRecordDao.kt`:
```kotlin
package com.moodified.app.data.local.dao.notification

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moodified.app.data.local.entity.notification.NotificationRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: NotificationRecordEntity): Long

    @Query(
        "SELECT * FROM notification_records " +
            "WHERE dismissedAt IS NULL " +
            "ORDER BY deliveredAt DESC",
    )
    fun observeAll(): Flow<List<NotificationRecordEntity>>

    @Query(
        "SELECT COUNT(*) FROM notification_records " +
            "WHERE readAt IS NULL AND dismissedAt IS NULL",
    )
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE notification_records SET readAt = :nowMillis WHERE id = :id AND readAt IS NULL")
    suspend fun markRead(id: Long, nowMillis: Long)

    @Query("UPDATE notification_records SET readAt = :nowMillis WHERE readAt IS NULL")
    suspend fun markAllRead(nowMillis: Long)

    @Query("UPDATE notification_records SET dismissedAt = :nowMillis WHERE id = :id AND dismissedAt IS NULL")
    suspend fun dismiss(id: Long, nowMillis: Long)

    @Query("UPDATE notification_records SET dismissedAt = :nowMillis WHERE dismissedAt IS NULL")
    suspend fun dismissAll(nowMillis: Long)

    @Query("DELETE FROM notification_records WHERE deliveredAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
```

Note: `observeAll()` filters out dismissed entries so swipe-to-dismiss + "Clear all" hide records immediately without a physical delete (physical delete happens via `deleteOlderThan` in the purge worker after 90 days).

- [ ] **Step 2: Add DAO accessor on `MoodifiedDatabase`**

In `app/src/main/java/com/moodified/app/data/local/database/MoodifiedDatabase.kt`, add the import near the other DAO imports (after `SleepSegmentDao` on line 13):
```kotlin
import com.moodified.app.data.local.dao.notification.NotificationRecordDao
```

Add the abstract method inside the `MoodifiedDatabase` class body, immediately after `abstract fun interventionHistoryDao(): InterventionHistoryDao` (currently line 48):
```kotlin

    abstract fun notificationRecordDao(): NotificationRecordDao
```

- [ ] **Step 3: Add Hilt provider in `DatabaseModule`**

In `app/src/main/java/com/moodified/app/di/DatabaseModule.kt`, add the import (after the existing DAO imports at line 11):
```kotlin
import com.moodified.app.data.local.dao.notification.NotificationRecordDao
```

Add the provider method inside `DatabaseModule` (after `provideInterventionHistoryDao` on line 75):
```kotlin

    @Provides @Singleton
    fun provideNotificationRecordDao(db: MoodifiedDatabase): NotificationRecordDao = db.notificationRecordDao()
```

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Room's KSP processor picks up the new `@Dao` interface and generates its `_Impl` — build should still succeed.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/data/local/dao/notification/ \
        app/src/main/java/com/moodified/app/data/local/database/MoodifiedDatabase.kt \
        app/src/main/java/com/moodified/app/di/DatabaseModule.kt

git commit -m "feat(inbox): add NotificationRecordDao + Hilt provider"
```

---

### Task 3: `NotificationHistoryRepository` (domain + data) + `@Binds` in `RepositoryModule`

Wrap the DAO in a domain-level repository so the receiver and service (which do not depend on Room directly) can persist without importing DAO types. Also introduces the `NotificationRecord` domain model — the entity is a data-layer concern, the domain sees a POKO.

**Files:**
- Create: `app/src/main/java/com/moodified/app/domain/model/notification/NotificationRecord.kt`
- Create: `app/src/main/java/com/moodified/app/domain/repository/NotificationHistoryRepository.kt`
- Create: `app/src/main/java/com/moodified/app/data/repository/NotificationHistoryRepositoryImpl.kt`
- Modify: `app/src/main/java/com/moodified/app/di/DatabaseModule.kt` (add `@Binds` inside the existing `RepositoryModule`)

**Interfaces:**
- Consumes: `NotificationRecordDao`, `NotificationRecordEntity`, `NotificationRecordType` from Tasks 1–2.
- Produces:
  - `com.moodified.app.domain.model.notification.NotificationRecord` — domain POKO. Fields: `id: Long`, `type: NotificationRecordType`, `title: String`, `body: String`, `deepLink: String?`, `deliveredAt: Long`, `readAt: Long?`, `dismissedAt: Long?`.
  - `NotificationHistoryRepository` — the surface Tasks 4, 5, 6, 8 all consume. Methods:
    - `suspend fun record(record: NotificationRecord): Long`
    - `fun observeAll(): Flow<List<NotificationRecord>>`
    - `fun observeUnreadCount(): Flow<Int>`
    - `suspend fun markRead(id: Long)`
    - `suspend fun markAllRead()`
    - `suspend fun dismiss(id: Long)`
    - `suspend fun dismissAll()`
    - `suspend fun deleteOlderThan(cutoffMillis: Long): Int`
  - `NotificationHistoryRepositoryImpl` — `@Inject`-constructor implementation delegating to `NotificationRecordDao`.

- [ ] **Step 1: Create the domain model `NotificationRecord.kt`**

Create `app/src/main/java/com/moodified/app/domain/model/notification/NotificationRecord.kt`:
```kotlin
package com.moodified.app.domain.model.notification

import com.moodified.app.data.local.entity.notification.NotificationRecordType

data class NotificationRecord(
    val id: Long = 0L,
    val type: NotificationRecordType,
    val title: String,
    val body: String,
    val deepLink: String?,
    val deliveredAt: Long,
    val readAt: Long? = null,
    val dismissedAt: Long? = null,
)
```

Note: `NotificationRecordType` intentionally lives under `data.local.entity.notification` because it's the raw column vocabulary; the domain re-uses it rather than duplicating. This is consistent with how `Valence`/`Arousal` are used from domain in this codebase (grep `import com.moodified.app.domain.model.mood.Valence` — used from both data and domain).

- [ ] **Step 2: Create the domain interface `NotificationHistoryRepository.kt`**

Create `app/src/main/java/com/moodified/app/domain/repository/NotificationHistoryRepository.kt`:
```kotlin
package com.moodified.app.domain.repository

import com.moodified.app.domain.model.notification.NotificationRecord
import kotlinx.coroutines.flow.Flow

interface NotificationHistoryRepository {
    suspend fun record(record: NotificationRecord): Long

    fun observeAll(): Flow<List<NotificationRecord>>

    fun observeUnreadCount(): Flow<Int>

    suspend fun markRead(id: Long)

    suspend fun markAllRead()

    suspend fun dismiss(id: Long)

    suspend fun dismissAll()

    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
```

- [ ] **Step 3: Create the data-layer impl `NotificationHistoryRepositoryImpl.kt`**

Create `app/src/main/java/com/moodified/app/data/repository/NotificationHistoryRepositoryImpl.kt`:
```kotlin
package com.moodified.app.data.repository

import com.moodified.app.data.local.dao.notification.NotificationRecordDao
import com.moodified.app.data.local.entity.notification.NotificationRecordEntity
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHistoryRepositoryImpl
    @Inject
    constructor(
        private val dao: NotificationRecordDao,
    ) : NotificationHistoryRepository {
        override suspend fun record(record: NotificationRecord): Long =
            dao.insert(record.toEntity())

        override fun observeAll(): Flow<List<NotificationRecord>> =
            dao.observeAll().map { list -> list.map { it.toDomain() } }

        override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

        override suspend fun markRead(id: Long) = dao.markRead(id, System.currentTimeMillis())

        override suspend fun markAllRead() = dao.markAllRead(System.currentTimeMillis())

        override suspend fun dismiss(id: Long) = dao.dismiss(id, System.currentTimeMillis())

        override suspend fun dismissAll() = dao.dismissAll(System.currentTimeMillis())

        override suspend fun deleteOlderThan(cutoffMillis: Long): Int = dao.deleteOlderThan(cutoffMillis)
    }

private fun NotificationRecord.toEntity(): NotificationRecordEntity =
    NotificationRecordEntity(
        id = id,
        type = type.name,
        title = title,
        body = body,
        deepLink = deepLink,
        deliveredAt = deliveredAt,
        readAt = readAt,
        dismissedAt = dismissedAt,
    )

private fun NotificationRecordEntity.toDomain(): NotificationRecord =
    NotificationRecord(
        id = id,
        type = com.moodified.app.data.local.entity.notification.NotificationRecordType.valueOf(type),
        title = title,
        body = body,
        deepLink = deepLink,
        deliveredAt = deliveredAt,
        readAt = readAt,
        dismissedAt = dismissedAt,
    )
```

- [ ] **Step 4: Bind the impl in `RepositoryModule`**

In `app/src/main/java/com/moodified/app/di/DatabaseModule.kt`, add imports near the other repository imports (after `InterventionRepositoryImpl` on line 15 and after `InterventionRepository` on line 20):
```kotlin
import com.moodified.app.data.repository.NotificationHistoryRepositoryImpl
import com.moodified.app.domain.repository.NotificationHistoryRepository
```

Add the binding inside the `RepositoryModule` abstract class (after `bindInterventionRepository` on line 94):
```kotlin

    @Binds @Singleton
    abstract fun bindNotificationHistoryRepository(
        impl: NotificationHistoryRepositoryImpl,
    ): NotificationHistoryRepository
```

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Hilt validation passes because the repository is bound but not yet consumed — that's fine, unused bindings are allowed.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/domain/model/notification/ \
        app/src/main/java/com/moodified/app/domain/repository/NotificationHistoryRepository.kt \
        app/src/main/java/com/moodified/app/data/repository/NotificationHistoryRepositoryImpl.kt \
        app/src/main/java/com/moodified/app/di/DatabaseModule.kt

git commit -m "feat(inbox): add NotificationHistoryRepository interface + impl"
```

---

### Task 4: Persistence hook in `MicroPromptReceiver` and `TrackingService`

Wire the repository into the two production notify sites. `MicroPromptReceiver` gets an `@Inject` field (already `@AndroidEntryPoint` at line 22); `TrackingService` gets one too. Each writes a `NotificationRecord` via `runBlocking(Dispatchers.IO)` immediately before its `nm?.notify(...)` call.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/data/receiver/MicroPromptReceiver.kt` (add repository injection + record write at line 84)
- Modify: `app/src/main/java/com/moodified/app/core/service/TrackingService.kt` (add repository injection + record write at line 319)

**Interfaces:**
- Consumes: `NotificationHistoryRepository` from Task 3, `NotificationRecordType` from Task 1.
- Produces: two records per notification-send event. No new symbols.

**Persistence-hook exclusion (critical):** `ProfileViewModel.triggerTestMicroPrompt` (line 123 of `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt`) MUST NOT be modified. It is a dev affordance; Phase 5d will move it into the Debug Drawer.

- [ ] **Step 1: Inject the repository into `MicroPromptReceiver`**

In `app/src/main/java/com/moodified/app/data/receiver/MicroPromptReceiver.kt`:

1a. Add the import (alphabetically among the existing `com.moodified.app.domain.*` imports around lines 11-14):
```kotlin
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.data.local.entity.notification.NotificationRecordType
import com.moodified.app.domain.repository.NotificationHistoryRepository
```

1b. Add the injected field next to `moodRepository` (currently line 24):
```kotlin
    @Inject lateinit var notificationHistoryRepository: NotificationHistoryRepository
```

- [ ] **Step 2: Write the record before `nm?.notify(...)` in `handleValenceSelection`**

Currently line 83-84 reads:
```kotlin
                .build()

        nm?.notify(PROMPT_NOTIFICATION_ID, secondPrompt)
```

Replace with (adds a `kotlinx.coroutines.runBlocking` block before the `notify` call):
```kotlin
                .build()

        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            notificationHistoryRepository.record(
                NotificationRecord(
                    type = NotificationRecordType.MICRO_PROMPT,
                    title = "And your energy?",
                    body = "How does your body feel right now?",
                    deepLink = null,
                    deliveredAt = System.currentTimeMillis(),
                ),
            )
        }
        nm?.notify(PROMPT_NOTIFICATION_ID, secondPrompt)
```
Rationale for `runBlocking`: `BroadcastReceiver.onReceive` runs on the main thread with a 10-second deadline; the record write is a single indexed insert (single-digit milliseconds), fits inside the deadline, and avoids leaking the record write past `goAsync()` boundaries. This receiver already uses `goAsync()` only in `handleFinalLogging` (line 93) — `handleValenceSelection` runs entirely inline.

- [ ] **Step 3: Inject the repository into `TrackingService`**

In `app/src/main/java/com/moodified/app/core/service/TrackingService.kt`:

3a. Add the same three imports near the existing `com.moodified.app.domain.*` imports (find the alphabetized block and add):
```kotlin
import com.moodified.app.data.local.entity.notification.NotificationRecordType
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.NotificationHistoryRepository
```

3b. Add the injected field in the class body. `TrackingService` is already `@AndroidEntryPoint`; find its other `@Inject` fields (grep for `@Inject`) and add:
```kotlin
    @Inject lateinit var notificationHistoryRepository: NotificationHistoryRepository
```

- [ ] **Step 4: Write the record before `nm?.notify(...)` at line 319**

Currently lines 317-320 read:
```kotlin
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
    }
```

Replace with:
```kotlin
        val nm = getSystemService(NotificationManager::class.java)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            notificationHistoryRepository.record(
                NotificationRecord(
                    type = NotificationRecordType.MICRO_PROMPT,
                    title = "Moodified is with you",
                    body = contextMessage,
                    deepLink = "moodified://quicklog",
                    deliveredAt = System.currentTimeMillis(),
                ),
            )
        }
        nm?.notify(MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
    }
```

`contextMessage` is the same String already used in the `NotificationCompat.Builder(...).setContentText(contextMessage)` call at line 310 — reuse it verbatim.

The `TrackingService` foreground-service notification (`startServiceForeground` at line 322) does NOT write a record — it's the persistent "we're tracking" notification, not a discrete event, and it doesn't show up in the user's inbox concept. Verify no record write is added there.

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Hilt validation passes — `NotificationHistoryRepository` is now consumed from two `@AndroidEntryPoint` sites.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/data/receiver/MicroPromptReceiver.kt \
        app/src/main/java/com/moodified/app/core/service/TrackingService.kt

git commit -m "feat(inbox): persist inbox record on micro-prompt + tracking notifications"
```

---

### Task 5: `PurgeOldNotificationRecordsUseCase` + `PurgeWorker` retention step

Extend the periodic purge to cover the new table. Follows the exact shape of `PurgeOldTelemetryUseCase`.

**Files:**
- Create: `app/src/main/java/com/moodified/app/domain/usecase/common/PurgeOldNotificationRecordsUseCase.kt`
- Modify: `app/src/main/java/com/moodified/app/core/worker/PurgeWorker.kt` (inject + invoke after telemetry purge)

**Interfaces:**
- Consumes: `NotificationHistoryRepository` from Task 3.
- Produces: `PurgeOldNotificationRecordsUseCase` — `suspend operator fun invoke()`. `PurgeWorker` gains one new injected dependency.

- [ ] **Step 1: Create `PurgeOldNotificationRecordsUseCase.kt`**

Create `app/src/main/java/com/moodified/app/domain/usecase/common/PurgeOldNotificationRecordsUseCase.kt`:
```kotlin
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
```

- [ ] **Step 2: Extend `PurgeWorker` to call the new use case**

In `app/src/main/java/com/moodified/app/core/worker/PurgeWorker.kt`:

2a. Add the import (after `PurgeOldTelemetryUseCase` on line 11):
```kotlin
import com.moodified.app.domain.usecase.common.PurgeOldNotificationRecordsUseCase
```

2b. Add the new constructor parameter (after `purgeOldTelemetryUseCase` on line 22):
```kotlin
        private val purgeOldTelemetryUseCase: PurgeOldTelemetryUseCase,
        private val purgeOldNotificationRecordsUseCase: PurgeOldNotificationRecordsUseCase,
```

2c. In `doWork()` (currently lines 42-51), invoke the new use case after the telemetry purge and log the deleted count:
```kotlin
        override suspend fun doWork(): Result {
            return try {
                purgeOldTelemetryUseCase()
                val deletedInboxCount = purgeOldNotificationRecordsUseCase()
                Log.d(TAG, "Purged old telemetry data + $deletedInboxCount inbox records older than 90 days.")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge data: ${e.message}", e)
                Result.retry()
            }
        }
```

- [ ] **Step 3: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Hilt validates the new `@AssistedInject` constructor parameter.

- [ ] **Step 4: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/domain/usecase/common/PurgeOldNotificationRecordsUseCase.kt \
        app/src/main/java/com/moodified/app/core/worker/PurgeWorker.kt

git commit -m "feat(inbox): purge inbox records older than 90 days via PurgeWorker"
```

---

### Task 6: `NotificationsInboxScreen` + `NotificationsInboxViewModel` + `AppRoutes.Inbox`

Full-screen route, chronological list grouped by day header, tap → deepLink navigate, swipe-to-dismiss, top-bar "Clear all". No nav registration in this task — Task 7 registers the route.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt` (add `AppRoutes.Inbox`)
- Create: `app/src/main/java/com/moodified/app/presentation/inbox/NotificationsInboxViewModel.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/inbox/NotificationsInboxScreen.kt`

**Interfaces:**
- Consumes: `NotificationHistoryRepository`, `NotificationRecord`, `NotificationRecordType` from Tasks 1/3.
- Produces:
  - `com.moodified.app.core.navigation.AppRoutes.Inbox` — `data object Inbox : AppRoutes("notifications/inbox")`.
  - `NotificationsInboxViewModel` — `@HiltViewModel` exposing `uiState: StateFlow<NotificationsInboxUiState>` and mutators `markRead(id)`, `markAllRead()`, `dismiss(id)`, `dismissAll()`.
  - `NotificationsInboxScreen(onBack: () -> Unit, onDeepLink: (String) -> Unit)` — public composable.

- [ ] **Step 1: Add `AppRoutes.Inbox`**

In `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`, after `data object Calendar : AppRoutes("calendar")` (line 27), add:
```kotlin

    // Notifications inbox (Phase 5a)
    data object Inbox : AppRoutes("notifications/inbox")
```

- [ ] **Step 2: Create the ViewModel**

Create `app/src/main/java/com/moodified/app/presentation/inbox/NotificationsInboxViewModel.kt`:
```kotlin
package com.moodified.app.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.domain.model.notification.NotificationRecord
import com.moodified.app.domain.repository.NotificationHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class NotificationsInboxUiState(
    val groups: List<DayGroup> = emptyList(),
    val isEmpty: Boolean = true,
) {
    data class DayGroup(
        val date: LocalDate,
        val records: List<NotificationRecord>,
    )
}

@HiltViewModel
class NotificationsInboxViewModel
    @Inject
    constructor(
        private val repository: NotificationHistoryRepository,
    ) : ViewModel() {
        val uiState: StateFlow<NotificationsInboxUiState> =
            repository.observeAll()
                .map { records -> records.toUiState() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = NotificationsInboxUiState(),
                )

        fun markRead(id: Long) {
            viewModelScope.launch { repository.markRead(id) }
        }

        fun markAllRead() {
            viewModelScope.launch { repository.markAllRead() }
        }

        fun dismiss(id: Long) {
            viewModelScope.launch { repository.dismiss(id) }
        }

        fun dismissAll() {
            viewModelScope.launch { repository.dismissAll() }
        }
    }

private fun List<NotificationRecord>.toUiState(): NotificationsInboxUiState {
    if (isEmpty()) return NotificationsInboxUiState(isEmpty = true)
    val zone = ZoneId.systemDefault()
    val grouped =
        groupBy { record ->
            Instant.ofEpochMilli(record.deliveredAt).atZone(zone).toLocalDate()
        }
            .toSortedMap(compareByDescending { it })
            .map { (date, records) -> NotificationsInboxUiState.DayGroup(date, records) }
    return NotificationsInboxUiState(groups = grouped, isEmpty = false)
}
```

- [ ] **Step 3: Create the Screen**

Create `app/src/main/java/com/moodified/app/presentation/inbox/NotificationsInboxScreen.kt`:
```kotlin
package com.moodified.app.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.domain.model.notification.NotificationRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsInboxScreen(
    onBack: () -> Unit,
    onDeepLink: (String) -> Unit,
    viewModel: NotificationsInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().background(MilkWhite).statusBarsPadding(),
    ) {
        InboxTopBar(
            onBack = onBack,
            onClearAll = viewModel::dismissAll,
            clearAllEnabled = !state.isEmpty,
        )
        if (state.isEmpty) {
            InboxEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header_${group.date}") {
                        DayHeader(date = group.date)
                    }
                    items(count = group.records.size, key = { i -> "record_${group.records[i].id}" }) { i ->
                        val record = group.records[i]
                        DismissibleInboxRow(
                            record = record,
                            onTap = {
                                viewModel.markRead(record.id)
                                record.deepLink?.let(onDeepLink)
                            },
                            onDismissed = { viewModel.dismiss(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxTopBar(
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    clearAllEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
            )
        }
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (clearAllEnabled) {
            TextButton(onClick = onClearAll) {
                Text(text = "Clear all", color = DeepSage, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InboxEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.NotificationsNone,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No notifications yet",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "When Moodified sends you a check-in, it will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val label = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontSize = 10.sp,
        ),
        color = TextTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleInboxRow(
    record: NotificationRecord,
    onTap: () -> Unit,
    onDismissed: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    onDismissed()
                    true
                } else {
                    false
                }
            },
        )
    LaunchedEffect(record.id) {
        // Reset state on recomposition to a new record — SwipeToDismissBoxState is not keyed to record id.
        dismissState.reset()
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(SageSurface),
            )
        },
    ) {
        InboxRowContent(record = record, onTap = onTap)
    }
}

@Composable
private fun InboxRowContent(
    record: NotificationRecord,
    onTap: () -> Unit,
) {
    val isUnread = record.readAt == null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onTap)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UnreadDot(visible = isUnread)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = TextPrimary,
            )
            Text(text = record.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun UnreadDot(visible: Boolean) {
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (visible) DeepSage else androidx.compose.ui.graphics.Color.Transparent),
    )
}
```

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. `NotificationsInboxScreen` is orphan (no nav entry yet) — that's fine, Task 7 wires it. If detekt flags `LongMethod` on `NotificationsInboxScreen` or `LongParameterList` on `DismissibleInboxRow`, baseline them (these are the allowed Compose false-positive categories).

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt \
        app/src/main/java/com/moodified/app/presentation/inbox/

# Only if detekt baseline was regenerated:
git add config/detekt/detekt-baseline.xml

git commit -m "feat(inbox): NotificationsInboxScreen + ViewModel + Inbox route constant"
```

---

### Task 7: Register `notifications/inbox` route + `moodified://inbox` deep link

Wire the screen into `MoodifiedNavHost`, add the deep link to the manifest, and route the URI through `MainActivity`.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` (register composable, mark route full-screen)
- Modify: `app/src/main/AndroidManifest.xml` (add `<data android:scheme="moodified" android:host="inbox" />` to `MainActivity`'s intent-filter)
- Modify: `app/src/main/java/com/moodified/app/MainActivity.kt` (route `moodified://inbox` intents to a new `openInboxTrigger` shared flow)

**Interfaces:**
- Consumes: `AppRoutes.Inbox`, `NotificationsInboxScreen` from Task 6.
- Produces:
  - `MainActivity` now exposes an `openInboxTrigger: MutableSharedFlow<Unit>` (passed to `MoodifiedNavHost` as a parameter alongside `quickLogTrigger`).
  - `MoodifiedNavHost` accepts `openInboxTrigger` and navigates to `AppRoutes.Inbox.route` when it emits.

- [ ] **Step 1: Add the deep-link intent-filter data element**

In `app/src/main/AndroidManifest.xml`, in the existing `<activity android:name=".MainActivity">` block, find the intent-filter that already declares `moodified://quicklog` (lines 58-63):
```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="moodified" android:host="quicklog" />
            </intent-filter>
```

Replace with two intent-filters (an intent-filter can only have one host; add a sibling filter):
```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="moodified" android:host="quicklog" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="moodified" android:host="inbox" />
            </intent-filter>
```

- [ ] **Step 2: Route the inbox deep link through `MainActivity`**

In `app/src/main/java/com/moodified/app/MainActivity.kt`, replace the file body with:
```kotlin
package com.moodified.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moodified.app.core.theme.MoodifiedTheme
import com.moodified.app.presentation.navigation.MoodifiedNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Emits events when the app is opened via a deep link
    private val quickLogTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val openInboxTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            MoodifiedTheme {
                MoodifiedNavHost(
                    quickLogTrigger = quickLogTrigger,
                    openInboxTrigger = openInboxTrigger,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.data?.toString()) {
            "moodified://quicklog" -> quickLogTrigger.tryEmit(Unit)
            "moodified://inbox" -> openInboxTrigger.tryEmit(Unit)
        }
    }
}
```

- [ ] **Step 3: Wire the inbox trigger + register the route in `MoodifiedNavHost.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`:

3a. Add the imports (with the other `presentation` imports):
```kotlin
import com.moodified.app.presentation.inbox.NotificationsInboxScreen
```

3b. Change the function signature (line 76) to accept `openInboxTrigger`:
```kotlin
@Composable
fun MoodifiedNavHost(
    quickLogTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    openInboxTrigger: SharedFlow<Unit> = MutableSharedFlow(),
) {
```

3c. Add `AppRoutes.Inbox.route` to the `fullScreenRoutes` set (currently lines 90-100):
```kotlin
    val fullScreenRoutes =
        remember(debugNavRegistrar) {
            debugNavRegistrar.routes +
                setOf(
                    AppRoutes.Calendar.route,
                    AppRoutes.Privacy.route,
                    AppRoutes.Support.HELP,
                    AppRoutes.Support.ABOUT,
                    AppRoutes.Support.LICENSES,
                    AppRoutes.Inbox.route,
                )
        }
```

3d. Register the composable inside `NavHost { ... }`, immediately after the `AppRoutes.Support.ABOUT` block (currently ends line 195):
```kotlin
                composable(AppRoutes.Inbox.route) {
                    NotificationsInboxScreen(
                        onBack = { navController.popBackStack() },
                        onDeepLink = { uri ->
                            // Best-effort: only quicklog is currently reachable via deep link URI
                            if (uri == "moodified://quicklog") {
                                showQuickLog = true
                            }
                        },
                    )
                }
```

3e. Add a `LaunchedEffect` that navigates when `openInboxTrigger` emits. Immediately after the existing `LaunchedEffect(quickLogTrigger) { ... }` block (currently lines 103-107):
```kotlin
    LaunchedEffect(openInboxTrigger) {
        openInboxTrigger.collect {
            navController.navigate(AppRoutes.Inbox.route)
        }
    }
```

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/moodified/app/MainActivity.kt

git commit -m "feat(inbox): register notifications/inbox route + moodified://inbox deep link"
```

---

### Task 8: Notifications row on Profile with unread badge + Profile-scoped SnackbarHost + FeedbackSheet Snackbar fix

Add a **Notifications** section header + `NotificationsRow` composable on `ProfileScreen`, sitting above the existing **Support** section. Unread badge count comes from `NotificationHistoryRepository.observeUnreadCount()` collected in `ProfileViewModel`. Wrap `ProfileScreen`'s `LazyColumn` in a `Scaffold` so a `SnackbarHostState` is scoped there, and pass a `showSnackbar` callback into `FeedbackSheet` so its `resolveActivity` null-branch surfaces "No email app installed" (parked Phase 4 minor).

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt` (inject repository, add `unreadCount` field on `ProfileUiState`)
- Modify: `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt` (accept `onNavigateToInbox`, add Notifications section, wrap in Scaffold with SnackbarHost, pass `showSnackbar` into FeedbackSheet)
- Modify: `app/src/main/java/com/moodified/app/presentation/support/FeedbackSheet.kt` (accept `showSnackbar`, call it in the null branch)
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` (pass `onNavigateToInbox = { navController.navigate(AppRoutes.Inbox.route) }` into `ProfileScreen`)

**Interfaces:**
- Consumes: `NotificationHistoryRepository` from Task 3, `AppRoutes.Inbox` from Task 6.
- Produces:
  - `ProfileUiState` gains `val unreadNotificationCount: Int = 0`.
  - `ProfileScreen` signature becomes `ProfileScreen(onNavigateToPrivacy, onNavigateToHelp, onNavigateToAbout, onNavigateToInbox, viewModel = hiltViewModel())`.
  - `FeedbackSheet` signature becomes `FeedbackSheet(onDismiss: () -> Unit, showSnackbar: suspend (String) -> Unit)`.

- [ ] **Step 1: Extend `ProfileUiState` and `ProfileViewModel`**

In `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt`:

1a. Add the import:
```kotlin
import com.moodified.app.domain.repository.NotificationHistoryRepository
```

1b. Add `unreadNotificationCount` to `ProfileUiState` (currently lines 31-37). Field goes at the end of the data class:
```kotlin
data class ProfileUiState(
    val isActivityTracking: Boolean = false,
    val isSleepTracking: Boolean = false,
    val isInteractionTracking: Boolean = false,
    val activityDenials: Int = 0,
    val notificationDenials: Int = 0,
    val unreadNotificationCount: Int = 0,
)
```

1c. Add the repository to the constructor (after `permissionDenialTracker` on line 54):
```kotlin
        private val permissionDenialTracker: PermissionDenialTracker,
        private val notificationHistoryRepository: NotificationHistoryRepository,
```

1d. Extend the `combine(...)` in `uiState` (currently lines 59-84) to also collect the unread count. Change from 5-arg `combine` to 6-arg:
```kotlin
        val uiState: StateFlow<ProfileUiState> =
            combine(
                activityRepository.observeSignal(),
                sleepRepository.observeLiveSignal(),
                interactionRepository.observeLiveSignal(),
                permissionDenialTracker.activityRecognitionDenials,
                permissionDenialTracker.postNotificationDenials,
                notificationHistoryRepository.observeUnreadCount(),
            ) { values ->
                ProfileUiState(
                    isActivityTracking = (values[0] as com.moodified.app.domain.model.activity.ActivitySignal).isTracking,
                    isSleepTracking = (values[1] as com.moodified.app.domain.model.sleep.SleepSignal).isTracking,
                    isInteractionTracking = (values[2] as com.moodified.app.domain.model.interaction.InteractionSignal).isTracking,
                    activityDenials = values[3] as Int,
                    notificationDenials = values[4] as Int,
                    unreadNotificationCount = values[5] as Int,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    ProfileUiState(
                        isActivityTracking = activityRepository.isTracking,
                        isSleepTracking = sleepRepository.isTracking,
                        isInteractionTracking = interactionRepository.isTracking,
                        activityDenials = permissionDenialTracker.activityRecognitionDenials.value,
                        notificationDenials = permissionDenialTracker.postNotificationDenials.value,
                        unreadNotificationCount = 0,
                    ),
            )
```
Rationale for switching to varargs `combine`: Kotlin's 5-arg `combine` is the largest typed overload; adding a 6th collapses to `combine(vararg flows): Flow<Array<*>>` which requires index-based unpacking. If the signals' concrete types differ from the imports above, correct them to match the actual return types (grep `observeSignal` / `observeLiveSignal` return types).

**Alternative if the vararg combine style clashes with codebase norms:** wrap two nested `combine` calls — a first `combine` producing an intermediate data holder, a second `combine` merging with `observeUnreadCount()`. Choose whichever compiles cleanly.

- [ ] **Step 2: Update `FeedbackSheet` signature to accept `showSnackbar`**

In `app/src/main/java/com/moodified/app/presentation/support/FeedbackSheet.kt`:

2a. Change the signature (line 51):
```kotlin
@Composable
fun FeedbackSheet(
    onDismiss: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
) {
```

2b. Modify the send-button onClick (lines 88-96) to invoke `showSnackbar` when no email app is available:
```kotlin
            SendButton(
                onClick = {
                    val intent = buildFeedbackIntent(includeDeviceInfo)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    } else {
                        scope.launch {
                            sheetState.hide()
                            showSnackbar("No email app installed")
                        }.invokeOnCompletion { onDismiss() }
                    }
                },
            )
```

- [ ] **Step 3: Wrap `ProfileScreen` in a Scaffold + add Notifications section + wire FeedbackSheet**

In `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt`:

3a. Add imports:
```kotlin
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

3b. Change the composable signature (line 57):
```kotlin
@Composable
fun ProfileScreen(
    onNavigateToPrivacy: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToInbox: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
```

3c. Add `SnackbarHostState` and `showSnackbar` lambda near the top of the function body (immediately after `val state by viewModel.uiState.collectAsStateWithLifecycle()` on line 66):
```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val showSnackbar: suspend (String) -> Unit = { message -> snackbarHostState.showSnackbar(message) }
```

3d. Wrap the outer `LazyColumn` (currently lines 195-331) in a `Scaffold`:
```kotlin
    Scaffold(
        containerColor = MilkWhite,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MilkWhite)
                    .statusBarsPadding()
                    .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            // ...existing item blocks unchanged...
        }
    }
```

3e. Add the **Notifications** section between the Tracking-Preferences block (ends line 251) and the Support block (starts line 253). Insert:
```kotlin
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Notifications")

            NotificationsRow(
                unreadCount = state.unreadNotificationCount,
                onClick = onNavigateToInbox,
            )
        }
```

3f. Add the `NotificationsRow` composable after the existing `MenuRow` at the bottom of the file (after line 536):
```kotlin
@Composable
private fun NotificationsRow(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepSage.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = DeepSage,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Notifications", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                text = "Review recent check-ins and reminders",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        if (unreadCount > 0) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DeepSage,
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MilkWhite,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
```

3g. Update the FeedbackSheet call site (currently line 334):
```kotlin
    if (showFeedback) {
        FeedbackSheet(
            onDismiss = { showFeedback = false },
            showSnackbar = showSnackbar,
        )
    }
```

- [ ] **Step 4: Pass `onNavigateToInbox` from `MoodifiedNavHost`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`, update the `composable(AppRoutes.Profile.route) { ... }` block (currently lines 167-173):
```kotlin
                composable(AppRoutes.Profile.route) {
                    ProfileScreen(
                        onNavigateToPrivacy = { navController.navigate(AppRoutes.Privacy.route) },
                        onNavigateToHelp = { navController.navigate(AppRoutes.Support.HELP) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.Support.ABOUT) },
                        onNavigateToInbox = { navController.navigate(AppRoutes.Inbox.route) },
                    )
                }
```

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If detekt flags `LongParameterList` on `ProfileScreen` (now 5 params including viewModel), baseline it — allowed category. If it flags `LongMethod` on ProfileScreen's function body, baseline it — allowed category.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt \
        app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt \
        app/src/main/java/com/moodified/app/presentation/support/FeedbackSheet.kt \
        app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt

# Only if detekt baseline was regenerated:
git add config/detekt/detekt-baseline.xml

git commit -m "feat(inbox): add Notifications row on Profile + FeedbackSheet snackbar fix"
```

---

### Task 9: Final full-greens gate

No file changes. Runs the full green-gate suite one more time from a clean state, since Task 8 was the last code-change task.

- [ ] **Step 1: Clean and re-run all gates**

Run:
```
./gradlew clean
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green from a clean state. This is the final gate before merging Phase 5a to main.

- [ ] **Step 2: Verify branch shape**

Run:
```
git log main..phase-5a-data-inbox --oneline
```
Expected: 8 commits (one per Task 1-8). No `Co-Authored-By: Claude` trailers on any of them (spot-check `git log main..phase-5a-data-inbox` full output).

Also run:
```
git log main..phase-5a-data-inbox --format="%an <%ae>" | sort -u
```
Expected: only Mikhael Edman Gomez as author. If anything else appears, roll the offending commits.

No commit in this task.

---

## Follow-ups (out of scope for Phase 5a — bundled into their proper phases)

- `ProfileViewModel.triggerTestMicroPrompt` + `injectMockMoodData` + `injectMockActivityData` — Phase 5d moves these into the Debug Drawer.
- "Tracking Preferences" → "Tracking" section header rename — Phase 5c.
- Physical-device smoke test — deferred to end of Phase 5 per user preference (see memory `feedback_smoke_test_timing.md`).
- `application-overview.md` refresh — Phase 5d final task.

## Merge steps (executed by the human, not by the plan)

After all reviews clear:
```
git checkout main
git merge --ff-only phase-5a-data-inbox
# Do NOT git push — bundled with 5b/5c/5d after full-phase review.
```
