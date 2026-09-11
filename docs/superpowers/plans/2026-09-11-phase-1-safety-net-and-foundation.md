# Phase 1 — Safety Net & Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the ship-ready safety fixes and test scaffolding that the rest of the consolidation depends on: remove the destructive-migration fallback, purge Android imports from the domain layer, add static analysis, and give the two rule-based engines their first characterization tests.

**Architecture:** Purely additive/subtractive to existing Kotlin/Compose/Hilt/Room code. No new user-facing surface. Introduces a Room migration test template that Phase 5 will reuse for v14→v15. Extracts one Android-touching use case behind a domain interface with a data-layer implementation.

**Tech Stack:** Kotlin 2.1.0, JVM 17, JUnit 4, Room 2.6.1 (`room-testing`), ktlint (Gradle plugin `org.jlleitschuh.gradle.ktlint`), detekt (Gradle plugin `io.gitlab.arturbosch.detekt`), Gradle version catalog (`libs.versions.toml`), Hilt 2.54.

**Spec:** `docs/superpowers/specs/2026-09-11-moodified-consolidation-design.md` (Phase 1 in §6, requirements in §3.5 and §5).

## Global Constraints

- `minSdk = 26`, `targetSdk / compileSdk = 36`, Kotlin `2.1.0`, JVM target `17`.
- Never re-introduce `fallbackToDestructiveMigration()` on the Room builder.
- `domain/` package MUST NOT contain any `import android.*` statements after this phase. Verify with `grep -r "^import android\." app/src/main/java/com/moodified/app/domain` — output must be empty.
- All new dependencies go through `gradle/libs.versions.toml`. No inline `group:name:version` strings in `app/build.gradle.kts`.
- All new test files live under `app/src/test/java/com/moodified/app/` (JVM unit tests) or `app/src/androidTest/java/com/moodified/app/` (instrumented). Package structure mirrors `main/`.
- ktlint and detekt must both exit with code 0 at the end of this phase. Detekt baseline is allowed to swallow existing violations; new code introduced in this phase must not add to the baseline.
- Every task ends with a commit. Commit messages follow the repo's existing style (see `git log --oneline -20`): `type: Short subject`, present-tense verbs.
- Do **not** touch UI code, navigation, or repositories outside the explicit files listed in each task. Consolidation of UI and nav ships in Phases 2–5.

---

## File Structure

**Files created:**
- `config/detekt/detekt.yml` — detekt configuration
- `config/detekt/detekt-baseline.xml` — baseline (generated during Task 2)
- `app/src/androidTest/java/com/moodified/app/data/local/database/Migration13To14Test.kt` — migration test template
- `app/src/test/java/com/moodified/app/domain/usecase/inference/RuleBasedMoodInferenceEngineTest.kt` — engine unit tests
- `app/src/test/java/com/moodified/app/domain/usecase/intervention/CareEvaluationEngineTest.kt` — engine unit tests
- `app/src/main/java/com/moodified/app/domain/exporter/UserDataExporter.kt` — new domain interface
- `app/src/main/java/com/moodified/app/data/exporter/UserDataExporterImpl.kt` — data-layer implementation (contents mostly moved from the old use case)
- `app/src/main/java/com/moodified/app/di/ExporterModule.kt` — Hilt binding for `UserDataExporter`

**Files modified:**
- `gradle/libs.versions.toml` — add `workRuntime`, `hiltWork`, `roomTesting`, `coroutinesTest`, `ktlint`, `detekt` versions and library/plugin entries
- `build.gradle.kts` (root) — apply ktlint + detekt plugins in `plugins {}`, configure detekt
- `app/build.gradle.kts` — apply plugins, replace hardcoded WorkManager/Hilt-Work deps with catalog references, add test dependencies
- `app/src/main/java/com/moodified/app/di/DatabaseModule.kt:53` — remove `.fallbackToDestructiveMigration()`
- `app/src/main/java/com/moodified/app/domain/usecase/privacy/ExportUserDataUseCase.kt` — reduce to a thin delegate that calls `UserDataExporter`

**Files deleted:**
- `app/src/test/java/com/moodified/app/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/moodified/app/ExampleInstrumentedTest.kt`

**Dependency additions (via catalog):**
- `androidx.work:work-runtime-ktx` (was hardcoded)
- `androidx.hilt:hilt-work` (was hardcoded)
- `androidx.hilt:hilt-compiler` (was hardcoded)
- `androidx.room:room-testing` (androidTest)
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` (test)
- `org.jlleitschuh.gradle.ktlint` Gradle plugin `12.1.1`
- `io.gitlab.arturbosch.detekt` Gradle plugin `1.23.7`

---

## Task Ordering & Rationale

1. **Task 1** — Housekeeping (delete stubs, catalog WorkManager). Small, mechanical, unblocks everything else because subsequent tasks will fail lint or leave orphan files otherwise.
2. **Task 2** — Add ktlint + detekt. Do before adding new code so new code lands lint-clean, and existing violations get baselined once.
3. **Task 3** — Remove `fallbackToDestructiveMigration()`. Trivial safety fix; needs no test infrastructure yet.
4. **Task 4** — Migration test template (v13→v14). Establishes the testing pattern that Phase 5 reuses; also proves the eight registered migrations actually work end-to-end.
5. **Task 5** — Refactor `ExportUserDataUseCase` to remove Android imports from `domain/`. Pure move-and-rename; no behavior change and no characterization test.
6. **Task 6** — Unit tests for `RuleBasedMoodInferenceEngine` — core-shape tests only (manual-outrank, fallback, confidence bounds, explainability non-empty). Deeper per-rule tests are called out as follow-ups; they require constructing full domain summary fixtures which exceeds Phase 1 scope.
7. **Task 7** — Unit tests for `CareEvaluationEngine` **orchestration** — null-guard, priority sort, trend-alert cooldown gate. Uses fakes for the three collaborator use cases and the repository.
8. **Task 8** — Unit tests for `EvaluateBaseGuidanceUseCase` — this is where the "category selection per inferred state" logic from spec §3.5 actually lives (the engine only orchestrates). Covers the micro-confirmation, guidance-disconnect, guidance-move, motivation-momentum, and motivation-rest branches.

Tasks 4 through 8 are independent of one another and could be reordered without breaking anything.

---

### Task 1: Housekeeping — remove stub tests, catalog WorkManager & Hilt-Work

**Files:**
- Delete: `app/src/test/java/com/moodified/app/ExampleUnitTest.kt`
- Delete: `app/src/androidTest/java/com/moodified/app/ExampleInstrumentedTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:163-166`

**Interfaces:**
- Produces: `libs.androidx.work.runtime.ktx`, `libs.androidx.hilt.work`, `libs.androidx.hilt.compiler` catalog accessors used by all later tasks and phases.

- [ ] **Step 1: Delete the two example test files**

```bash
rm app/src/test/java/com/moodified/app/ExampleUnitTest.kt
rm app/src/androidTest/java/com/moodified/app/ExampleInstrumentedTest.kt
```

- [ ] **Step 2: Add WorkManager, Hilt-Work, and Hilt-compiler versions + libraries to the catalog**

Edit `gradle/libs.versions.toml`. In the `[versions]` block add:

```toml
workRuntime = "2.9.0"
hiltWork = "1.2.0"
```

In the `[libraries]` block add (place near the existing `androidx-hilt-common` entry so DI-adjacent deps stay together):

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntime" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
```

- [ ] **Step 3: Replace the hardcoded dependency strings in `app/build.gradle.kts`**

Replace lines 163–166 (the "WorkManager & Hilt Work (ADDED FOR PHASE 3)" block) with:

```kotlin
    // WorkManager + Hilt Work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 4: Verify the build still assembles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. No warnings about missing WorkManager or Hilt.

- [ ] **Step 5: Verify no `ExampleUnitTest` or `ExampleInstrumentedTest` references remain**

Run: `grep -r "ExampleUnitTest\|ExampleInstrumentedTest" app/`
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git rm app/src/test/java/com/moodified/app/ExampleUnitTest.kt
git rm app/src/androidTest/java/com/moodified/app/ExampleInstrumentedTest.kt
git commit -m "chore: Remove stub tests and move WorkManager to version catalog"
```

---

### Task 2: Add ktlint + detekt with baseline

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Create: `config/detekt/detekt.yml`
- Create: `config/detekt/detekt-baseline.xml` (generated)

**Interfaces:**
- Produces: Gradle tasks `ktlintCheck`, `ktlintFormat`, `detekt`, `detektBaseline` are runnable from any module context. All later tasks assume these exist.

- [ ] **Step 1: Add plugin versions to the catalog**

Edit `gradle/libs.versions.toml`. In `[versions]` add:

```toml
ktlint = "12.1.1"
detekt = "1.23.7"
```

In `[plugins]` add:

```toml
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 2: Apply the plugins in the root `build.gradle.kts`**

Add to the root `plugins {}` block (create the block if it doesn't exist at the top of the file):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/detekt-baseline.xml")
        buildUponDefaultConfig = true
    }
}
```

If the root file already declares plugins, merge — do not overwrite.

- [ ] **Step 3: Create `config/detekt/detekt.yml` with a minimal starter config**

```yaml
build:
  maxIssues: 0
  weights:
    complexity: 2
    LongParameterList: 1
    style: 1
    comments: 1

complexity:
  active: true
  LongMethod:
    active: true
    threshold: 80
  LargeClass:
    active: true
    threshold: 600
  TooManyFunctions:
    active: true
    thresholdInFiles: 30
    thresholdInClasses: 20

style:
  active: true
  MagicNumber:
    active: false
  ReturnCount:
    active: true
    max: 4
  MaxLineLength:
    active: true
    maxLineLength: 140

naming:
  active: true
  FunctionNaming:
    active: true
  VariableNaming:
    active: true

formatting:
  active: false   # ktlint owns formatting
```

- [ ] **Step 4: Generate the detekt baseline so existing violations don't block the build**

Run: `./gradlew detektBaseline`
Expected: creates `config/detekt/detekt-baseline.xml`. Warnings about existing violations are expected — they get captured in the baseline.

- [ ] **Step 5: Run ktlint format on existing code, then check**

Run: `./gradlew ktlintFormat` (auto-fixes trivial style issues in existing code)
Then: `./gradlew ktlintCheck`
Expected: both succeed. If `ktlintCheck` still reports violations, fix them by hand or add a `.editorconfig` disabling the specific rule; do not baseline ktlint (its violations are usually one-line fixes).

- [ ] **Step 6: Confirm detekt is green against the baseline**

Run: `./gradlew detekt`
Expected: `BUILD SUCCESSFUL`. The baseline swallows existing issues.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts config/detekt/
git add -u   # picks up any ktlint auto-formatting
git commit -m "chore: Add ktlint and detekt with baseline"
```

---

### Task 3: Remove `fallbackToDestructiveMigration()`

**Files:**
- Modify: `app/src/main/java/com/moodified/app/di/DatabaseModule.kt:53`

**Interfaces:** none — pure code deletion.

- [ ] **Step 1: Open `di/DatabaseModule.kt` and delete the fallback line**

Remove line 53: `.fallbackToDestructiveMigration()`

The `Room.databaseBuilder(...)` chain should now go directly from the eight `.addMigrations(...)` calls to `.build()`.

- [ ] **Step 2: Verify the app still builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify no other call site references `fallbackToDestructive`**

Run: `grep -r "fallbackToDestructive" app/src/main`
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/moodified/app/di/DatabaseModule.kt
git commit -m "fix: Remove fallbackToDestructiveMigration from Room builder"
```

---

### Task 4: Migration test template — v13 → v14

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (add `room-testing` androidTest dep)
- Create: `app/src/androidTest/java/com/moodified/app/data/local/database/Migration13To14Test.kt`

**Interfaces:**
- Consumes: `MoodifiedDatabase.MIGRATION_13_14` (existing), exported schema files in `app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/{13,14}.json`.
- Produces: `Migration13To14Test` — serves as the template Phase 5's `Migration14To15Test` copies.

- [ ] **Step 1: Add `room-testing` to the catalog**

Edit `gradle/libs.versions.toml`. In `[libraries]` add:

```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

- [ ] **Step 2: Add the dependency in `app/build.gradle.kts`**

In the `dependencies { ... }` block, add near the other androidTest lines:

```kotlin
    androidTestImplementation(libs.androidx.room.testing)
```

- [ ] **Step 3: Verify the exported v13 and v14 schemas exist**

Run: `ls app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/`
Expected output includes at minimum `13.json` and `14.json`. If either is missing, stop — you cannot write a migration test without the exported schemas. Investigate whether `room.schemaLocation` is configured in `app/build.gradle.kts`.

- [ ] **Step 4: Write the failing migration test**

Create `app/src/androidTest/java/com/moodified/app/data/local/database/Migration13To14Test.kt`:

```kotlin
package com.moodified.app.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration13To14Test {

    private val dbName = "migration-test-13-to-14.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoodifiedDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate13To14_preservesMoodEntries() {
        // 1. Create v13 DB and insert one row into a table that survives to v14.
        helper.createDatabase(dbName, 13).use { db ->
            db.execSQL(
                """
                INSERT INTO mood_entries (valence, arousal, timestamp, note, isManual)
                VALUES (2, 1, 1704067200000, 'seed row', 1)
                """.trimIndent()
            )
        }

        // 2. Run the migration.
        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            14,
            /* validateDroppedTables = */ true,
            MoodifiedDatabase.MIGRATION_13_14,
        )

        // 3. Verify the seed row is still readable.
        migratedDb.query("SELECT COUNT(*) FROM mood_entries WHERE note = 'seed row'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val count = cursor.getInt(0)
            assertNotNull(count)
            assertTrue("Seed row lost during migration (count=$count)", count >= 1)
        }
    }
}
```

Note: the `INSERT` statement above assumes `mood_entries` at v13 has exactly the columns `(valence, arousal, timestamp, note, isManual)`. Before running, open `app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/13.json` and confirm the column list. If a column is missing or renamed at v13, adjust the `INSERT` to match — this is why we look at the exported schema first.

- [ ] **Step 5: Run the test**

Requires a connected device or emulator (API 26+). Run:
```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.moodified.app.data.local.database.Migration13To14Test"
```
Expected: PASS. If it fails because a v13 column doesn't exist, correct the seed `INSERT` and re-run.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/androidTest/java/com/moodified/app/data/local/database/Migration13To14Test.kt
git commit -m "test: Add v13→v14 Room migration test as template"
```

---

### Task 5: Refactor `ExportUserDataUseCase` — extract Android I/O into `data/`

**Goal:** After this task, `domain/` contains zero `android.*` imports. The current use case's raw-SQL dump + MediaStore/Downloads write becomes an implementation of a new `UserDataExporter` interface in `data/`. The use case shrinks to a thin delegator.

**Files:**
- Create: `app/src/main/java/com/moodified/app/domain/exporter/UserDataExporter.kt`
- Create: `app/src/main/java/com/moodified/app/data/exporter/UserDataExporterImpl.kt`
- Create: `app/src/main/java/com/moodified/app/di/ExporterModule.kt`
- Modify: `app/src/main/java/com/moodified/app/domain/usecase/privacy/ExportUserDataUseCase.kt`

**Interfaces:**
- Consumes: `MoodifiedDatabase` (existing), `@ApplicationContext Context` (existing, only used in `data/`).
- Produces:
  ```kotlin
  interface UserDataExporter {
      suspend fun export(): ExportResult
      data class ExportResult(val location: String)
  }
  ```

- [ ] **Step 1: Read the current `ExportUserDataUseCase` end-to-end**

File: `app/src/main/java/com/moodified/app/domain/usecase/privacy/ExportUserDataUseCase.kt`
Note that: (a) the use case both **builds** the JSON dump and **writes** it; (b) the returned type is `ExportResult(location: String)`; (c) `location` is either `"Downloads/<filename>"` on API 29+ or the absolute file path on older devices.

The refactor keeps that behavior exactly. Any change in output format is out of scope.

- [ ] **Step 2: Create the domain interface**

File: `app/src/main/java/com/moodified/app/domain/exporter/UserDataExporter.kt`

```kotlin
package com.moodified.app.domain.exporter

/**
 * Writes a snapshot of the user's on-device data to a location the user can share.
 * All Android I/O and MediaStore concerns live in the data-layer implementation.
 */
interface UserDataExporter {

    suspend fun export(): ExportResult

    data class ExportResult(val location: String)
}
```

Verify no Android imports: this file MUST NOT import anything from `android.*`.

- [ ] **Step 3: Create the data-layer implementation by moving the current logic**

File: `app/src/main/java/com/moodified/app/data/exporter/UserDataExporterImpl.kt`

Copy the entire body of the current `ExportUserDataUseCase` (constructor, `tablesToExport`, `invoke()`, `dumpTable`, `valueToJson`, `timestamp`, `writeViaMediaStore`, `writeToLegacyDownloads`) into this new class. Change:

- Class name: `class UserDataExporterImpl @Inject constructor(...)`
- Implements: `: UserDataExporter`
- Rename `operator fun invoke()` → `override suspend fun export()`
- Return type: `UserDataExporter.ExportResult` instead of the (deleted) inner class on the old use case

Complete file skeleton:

```kotlin
package com.moodified.app.data.exporter

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.moodified.app.data.local.database.MoodifiedDatabase
import com.moodified.app.domain.exporter.UserDataExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class UserDataExporterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MoodifiedDatabase,
) : UserDataExporter {

    private val tablesToExport = listOf(
        "mood_entries",
        "sleep_segments",
        "activity_daily_summaries",
        "interaction_daily_summaries",
        "intervention_history",
    )

    override suspend fun export(): UserDataExporter.ExportResult = withContext(Dispatchers.IO) {
        // ... paste the body from the old ExportUserDataUseCase.invoke() here, returning
        //     UserDataExporter.ExportResult(location = location)
    }

    // Paste dumpTable, valueToJson, timestamp, writeViaMediaStore, writeToLegacyDownloads
    // unchanged from the old use case.
}
```

- [ ] **Step 4: Shrink the domain use case to a thin delegate**

Replace the entire contents of `app/src/main/java/com/moodified/app/domain/usecase/privacy/ExportUserDataUseCase.kt` with:

```kotlin
package com.moodified.app.domain.usecase.privacy

import com.moodified.app.domain.exporter.UserDataExporter
import javax.inject.Inject

class ExportUserDataUseCase @Inject constructor(
    private val exporter: UserDataExporter,
) {
    suspend operator fun invoke(): UserDataExporter.ExportResult = exporter.export()
}
```

Verify no Android imports remain in this file.

- [ ] **Step 5: Add a Hilt module that binds the interface**

File: `app/src/main/java/com/moodified/app/di/ExporterModule.kt`

```kotlin
package com.moodified.app.di

import com.moodified.app.data.exporter.UserDataExporterImpl
import com.moodified.app.domain.exporter.UserDataExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExporterModule {

    @Binds
    @Singleton
    abstract fun bindUserDataExporter(impl: UserDataExporterImpl): UserDataExporter
}
```

- [ ] **Step 6: Verify the domain-purity invariant**

Run: `grep -rn "^import android\." app/src/main/java/com/moodified/app/domain`
Expected: **no output.**

If anything is found, address it before moving on. This is the acceptance gate for this task.

- [ ] **Step 7: Build the app and run all existing tests**

Run:
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
Expected: both succeed. If Hilt reports an unresolved binding for `UserDataExporter`, verify the module was created correctly and both classes carry the right annotations.

- [ ] **Step 8: Sanity-check that callers of `ExportUserDataUseCase` still compile**

Run: `grep -rn "ExportUserDataUseCase" app/src/main`
For each hit, open the file and confirm the caller still uses `.invoke()` (or the `()` operator) and reads `result.location`. The return type changed from `ExportUserDataUseCase.ExportResult` to `UserDataExporter.ExportResult`; if any caller imports the old inner-class type, update its import. `grep -rn "ExportUserDataUseCase.ExportResult" app/src/main` should yield **no output** after this step.

- [ ] **Step 9: Run ktlint + detekt on the touched files**

Run: `./gradlew ktlintCheck detekt`
Expected: green.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/moodified/app/domain/exporter/UserDataExporter.kt \
        app/src/main/java/com/moodified/app/data/exporter/UserDataExporterImpl.kt \
        app/src/main/java/com/moodified/app/di/ExporterModule.kt \
        app/src/main/java/com/moodified/app/domain/usecase/privacy/ExportUserDataUseCase.kt
git add -u   # picks up any caller import updates
git commit -m "refactor: Extract UserDataExporter to purge Android imports from domain"
```

---

### Task 6: Unit tests for `RuleBasedMoodInferenceEngine` (core-shape tests)

**Goal:** Cover the four rule shapes the engine must always satisfy: manual entries outrank inference, low completeness returns a fallback state, confidence stays in bounds, and the explainability string is never blank. Deeper per-rule tests (sleep / activity / screen-use / vigorous exercise scoring branches) require constructing full `DailySleepSummary` / `ActivityDailySummary` / `InteractionDailySummary` fixtures and are called out as follow-ups.

**Files:**
- Create: `app/src/test/java/com/moodified/app/domain/usecase/inference/RuleBasedMoodInferenceEngineTest.kt`

**Interfaces:**
- Consumes:
  - `RuleBasedMoodInferenceEngine(interpreter: MoodStateInterpreter, explainer: MoodExplainabilityGenerator)` — both collaborators are pure `@Inject constructor()` classes with no dependencies of their own, so unit tests instantiate real instances.
  - `operator fun invoke(snapshot: DailyBehaviorSnapshot): InferredMoodState`
  - `DailyBehaviorSnapshot(targetDate: LocalDate, sleepSummary: DailySleepSummary?, activitySummary: ActivityDailySummary?, interactionSummary: InteractionDailySummary?, moodEntries: List<MoodEntry>, dataCompletenessScore: Int, sleepTrends: SleepTrends? = null, activityTrends: ActivityTrends? = null)` — all summaries nullable, so a minimal snapshot passes only `targetDate`, an empty mood list, and a completeness score.
  - `InferredMoodState(valence: Valence, arousal: Arousal, interpretationLabel: String, confidenceScore: Int, explainabilityString: String, isFallback: Boolean)`
  - `MoodEntry(id, valence, arousal, note, timestamp, isManual, contextActivityIntensity, contextSleepMinutes)` — most fields have defaults; a test entry needs only `valence` and `arousal`.
  - `InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE` — read this in Step 1 to pick a "low" and "high" completeness score for tests.

- [ ] **Step 1: Read `InferenceConstants.kt` to pin the completeness threshold**

Open `app/src/main/java/com/moodified/app/domain/usecase/inference/InferenceConstants.kt` and note the value of `MIN_COMPLETENESS_FOR_INFERENCE`. Tests below use `belowThreshold = threshold - 1` and `aboveThreshold = 100` — you'll need the exact number.

- [ ] **Step 2: Create the test file**

File: `app/src/test/java/com/moodified/app/domain/usecase/inference/RuleBasedMoodInferenceEngineTest.kt`

```kotlin
package com.moodified.app.domain.usecase.inference

import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RuleBasedMoodInferenceEngineTest {

    private val engine = RuleBasedMoodInferenceEngine(
        interpreter = MoodStateInterpreter(),
        explainer   = MoodExplainabilityGenerator(),
    )

    private fun snapshot(
        moodEntries: List<MoodEntry> = emptyList(),
        completeness: Int = 100,
    ): DailyBehaviorSnapshot = DailyBehaviorSnapshot(
        targetDate            = LocalDate.of(2026, 1, 15),
        sleepSummary          = null,
        activitySummary       = null,
        interactionSummary    = null,
        moodEntries           = moodEntries,
        dataCompletenessScore = completeness,
    )

    // ---- Rule: manual entries always outrank inference ----

    @Test
    fun manualEntryDrivesReturnedValenceAndArousal() {
        val manual = MoodEntry(valence = Valence.POSITIVE, arousal = Arousal.HIGH)
        val state = engine(snapshot(moodEntries = listOf(manual)))
        assertEquals(Valence.POSITIVE, state.valence)
        assertEquals(Arousal.HIGH, state.arousal)
        assertFalse("Manual-derived state must not be marked fallback", state.isFallback)
    }

    // ---- Rule: low completeness returns a fallback state ----

    @Test
    fun lowCompletenessReturnsFallbackState() {
        val belowThreshold = InferenceConstants.MIN_COMPLETENESS_FOR_INFERENCE - 1
        val state = engine(snapshot(completeness = belowThreshold))
        assertTrue("Expected isFallback when completeness < threshold", state.isFallback)
    }

    // ---- Rule: sufficient completeness with no manual entries runs passive inference (not fallback) ----

    @Test
    fun sufficientCompletenessRunsPassiveInference() {
        val state = engine(snapshot(completeness = 100))
        assertFalse("Should not fall back when completeness is 100", state.isFallback)
    }

    // ---- Rule: confidence stays in [0, 100] regardless of input shape ----

    @Test
    fun confidenceScoreStaysWithinZeroToOneHundred() {
        listOf(0, 25, 50, 75, 100).forEach { completeness ->
            val state = engine(snapshot(completeness = completeness))
            assertTrue(
                "confidenceScore out of range for completeness=$completeness: ${state.confidenceScore}",
                state.confidenceScore in 0..100,
            )
        }
    }

    // ---- Rule: explainability string is never blank ----

    @Test
    fun explainabilityStringIsNeverBlank() {
        val state = engine(snapshot())
        assertFalse(
            "Explainability must be non-empty",
            state.explainabilityString.isBlank(),
        )
    }

    // ---- Rule: interpretation label is never blank ----

    @Test
    fun interpretationLabelIsNeverBlank() {
        val state = engine(snapshot())
        assertFalse(state.interpretationLabel.isBlank())
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.moodified.app.domain.usecase.inference.RuleBasedMoodInferenceEngineTest"`

If any test fails, treat it as a characterization discovery:
1. Read the corresponding branch in `RuleBasedMoodInferenceEngine.kt`.
2. Decide whether the *test* is wrong (adjust the assertion) or the *engine* has a real bug (do not fix in this task — add `@Ignore("Engine bug: <description>")` and note in the commit message).

Expected outcome: all six tests pass without `@Ignore`.

- [ ] **Step 4: Run ktlint + detekt on the new file**

Run: `./gradlew ktlintCheck detekt`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/moodified/app/domain/usecase/inference/RuleBasedMoodInferenceEngineTest.kt
git commit -m "test: Add core-shape unit tests for RuleBasedMoodInferenceEngine"
```

**Follow-up (out of Phase 1 scope, add to a follow-ups doc):** deeper scoring-branch tests that construct real `DailySleepSummary` / `ActivityDailySummary` / `InteractionDailySummary` fixtures and assert the effect on `valence` / `arousal` for each rule in `InferenceConstants` (sleep good/poor, activity high/sedentary, digital fatigue, vigorous-exercise decay).

---

### Task 7: Unit tests for `CareEvaluationEngine` (orchestration only)

**Goal:** Verify the engine's orchestration behavior — null-guard, priority-descending sort, aggregation of collaborator results, and the trend-alert cooldown gate. The actual category-selection logic lives in `EvaluateBaseGuidanceUseCase` and is covered in Task 8; here we use fakes for all four dependencies.

**Files:**
- Create: `app/src/test/java/com/moodified/app/domain/usecase/intervention/CareEvaluationEngineTest.kt`

**Interfaces:**
- Consumes:
  - `CareEvaluationEngine(evaluateBaseGuidanceUseCase, detectNegativeTrendsUseCase, selectGuidedRoutineUseCase, interventionRepository)` — all four are interfaces or classes injected by Hilt in production; tests substitute fakes.
  - `suspend operator fun invoke(moodState: InferredMoodState?, snapshot: DailyBehaviorSnapshot?, liveActivity: ActivitySignal, liveInteraction: InteractionSignal, historicalSleep: List<DailySleepSummary>, historicalActivity: List<ActivityDailySummary>, historicalInteraction: List<InteractionDailySummary>, historicalMoods: List<MoodEntry>): List<InterventionAction>`
  - `InterventionAction` sealed interface with `priority: Int` field on every variant. The engine's contract is: sort the final list by `.priority` descending.
  - `InterventionRepository.isOnCooldown(id: String, cooldownMillis: Long): Boolean`
- Notes:
  - `EvaluateBaseGuidanceUseCase` and `SelectGuidedRoutineUseCase` are concrete classes — fakes subclass them and override `invoke`. `DetectNegativeTrendsUseCase` is likewise a class; same pattern.
  - `InterventionRepository` is an interface — trivial fake `object` implementation.
  - Building live `ActivitySignal` and `InteractionSignal` requires reading the two files under `domain/model/activity/` and `domain/model/interaction/` in Step 1.

- [ ] **Step 1: Read model shapes needed for fixtures**

Open in order:
- `app/src/main/java/com/moodified/app/domain/model/activity/ActivitySignal.kt`
- `app/src/main/java/com/moodified/app/domain/model/interaction/InteractionModels.kt` (contains `InteractionSignal`)
- `app/src/main/java/com/moodified/app/domain/usecase/intervention/DetectNegativeTrendsUseCase.kt` (constructor signature only)
- `app/src/main/java/com/moodified/app/domain/usecase/intervention/SelectGuidedRoutineUseCase.kt` (constructor signature only)
- `app/src/main/java/com/moodified/app/domain/usecase/intervention/EvaluateBaseGuidanceUseCase.kt` (constructor signature only — you'll reuse it in Task 8)

You need the exact constructors and any required-not-nullable fields on `ActivitySignal` / `InteractionSignal` before writing the test.

- [ ] **Step 2: Create fakes and the test file**

File: `app/src/test/java/com/moodified/app/domain/usecase/intervention/CareEvaluationEngineTest.kt`

```kotlin
package com.moodified.app.domain.usecase.intervention

import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.intervention.TrendDirection
import com.moodified.app.domain.model.intervention.WellBeingDomain
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.repository.InterventionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CareEvaluationEngineTest {

    // --- Fakes ---

    private class FakeInterventionRepository(
        private val onCooldownIds: Set<String> = emptySet(),
    ) : InterventionRepository {
        val recorded = mutableListOf<String>()
        override suspend fun recordInterventionShown(id: String) { recorded += id }
        override suspend fun getLastShownTime(id: String): Long? = null
        override suspend fun isOnCooldown(id: String, cooldownMillis: Long): Boolean =
            id in onCooldownIds
        override suspend fun getInterventionHistory(id: String): InterventionHistoryEntity? = null
        override suspend fun recordFeedback(id: String, feedback: String, wasCompleted: Boolean) = Unit
    }

    private class FakeEvaluateBaseGuidance(
        private val result: List<InterventionAction>,
        repo: InterventionRepository,
    ) : EvaluateBaseGuidanceUseCase(repo) {
        override suspend fun invoke(
            moodState: InferredMoodState?,
            snapshot: DailyBehaviorSnapshot?,
            liveActivity: ActivitySignal,
            liveInteraction: InteractionSignal,
        ): List<InterventionAction> = result
    }

    private class FakeSelectGuidedRoutine(
        private val result: InterventionAction?,
        repo: InterventionRepository,
    ) : SelectGuidedRoutineUseCase(repo) {
        override suspend fun invoke(
            moodState: InferredMoodState,
            cooldownMillis: Long,
        ): InterventionAction? = result
    }

    private class FakeDetectNegativeTrends(
        private val result: List<InterventionAction.TrendAlert>,
    ) : DetectNegativeTrendsUseCase() {
        override suspend fun invoke(
            sleepSummaries: List<DailySleepSummary>,
            activitySummaries: List<ActivityDailySummary>,
            interactionSummaries: List<InteractionDailySummary>,
            moodEntries: List<MoodEntry>,
        ): List<InterventionAction.TrendAlert> = result
    }

    // --- Fixtures ---

    private val moodState = InferredMoodState(
        valence = Valence.NEUTRAL,
        arousal = Arousal.MID,
        interpretationLabel = "Steady",
        confidenceScore = 70,
        explainabilityString = "Fixture state",
    )

    private val snapshot = DailyBehaviorSnapshot(
        targetDate = LocalDate.of(2026, 1, 15),
        sleepSummary = null,
        activitySummary = null,
        interactionSummary = null,
        moodEntries = emptyList(),
        dataCompletenessScore = 80,
    )

    private val liveActivity: ActivitySignal = ActivitySignal()
    private val liveInteraction: InteractionSignal = InteractionSignal()

    private fun buildEngine(
        baseActions: List<InterventionAction> = emptyList(),
        routine: InterventionAction? = null,
        trends: List<InterventionAction.TrendAlert> = emptyList(),
        onCooldownIds: Set<String> = emptySet(),
    ): CareEvaluationEngine {
        val repo = FakeInterventionRepository(onCooldownIds)
        return CareEvaluationEngine(
            evaluateBaseGuidanceUseCase = FakeEvaluateBaseGuidance(baseActions, repo),
            detectNegativeTrendsUseCase = FakeDetectNegativeTrends(trends),
            selectGuidedRoutineUseCase = FakeSelectGuidedRoutine(routine, repo),
            interventionRepository = repo,
        )
    }

    // ---- Null-guard ----

    @Test
    fun returnsEmptyListWhenMoodStateIsNull() = runTest {
        val engine = buildEngine(baseActions = listOf(fakeGuidance("g1", 10)))
        val result = engine(
            moodState = null,
            snapshot = snapshot,
            liveActivity = liveActivity,
            liveInteraction = liveInteraction,
            historicalSleep = emptyList(),
            historicalActivity = emptyList(),
            historicalInteraction = emptyList(),
            historicalMoods = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyListWhenSnapshotIsNull() = runTest {
        val engine = buildEngine(baseActions = listOf(fakeGuidance("g1", 10)))
        val result = engine(
            moodState = moodState,
            snapshot = null,
            liveActivity = liveActivity,
            liveInteraction = liveInteraction,
            historicalSleep = emptyList(),
            historicalActivity = emptyList(),
            historicalInteraction = emptyList(),
            historicalMoods = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    // ---- Priority-descending sort across all sources ----

    @Test
    fun aggregatedActionsAreReturnedSortedByPriorityDescending() = runTest {
        val engine = buildEngine(
            baseActions = listOf(fakeGuidance("g1", priority = 5), fakeGuidance("g2", priority = 20)),
            routine = fakeRoutine("r1", priority = 15),
            trends = listOf(fakeTrend("t1", priority = 30)),
        )
        val result = engine(
            moodState = moodState,
            snapshot = snapshot,
            liveActivity = liveActivity,
            liveInteraction = liveInteraction,
            historicalSleep = emptyList(),
            historicalActivity = emptyList(),
            historicalInteraction = emptyList(),
            historicalMoods = emptyList(),
        )
        assertEquals(listOf(30, 20, 15, 5), result.map { it.priority })
    }

    // ---- Trend-alert cooldown filter ----

    @Test
    fun trendAlertOnCooldownIsExcluded() = runTest {
        val engine = buildEngine(
            trends = listOf(fakeTrend("t1", 30), fakeTrend("t2", 25)),
            onCooldownIds = setOf("t1"),
        )
        val result = engine(
            moodState = moodState,
            snapshot = snapshot,
            liveActivity = liveActivity,
            liveInteraction = liveInteraction,
            historicalSleep = emptyList(),
            historicalActivity = emptyList(),
            historicalInteraction = emptyList(),
            historicalMoods = emptyList(),
        )
        assertEquals(listOf("t2"), result.map { it.id })
    }

    // ---- Helper builders ----

    private fun fakeGuidance(id: String, priority: Int) =
        InterventionAction.Guidance(id = id, priority = priority, title = "t", description = "d")

    private fun fakeRoutine(id: String, priority: Int) =
        InterventionAction.GuidedRoutine(
            id = id,
            priority = priority,
            phases = emptyList(),
            estimatedMinutes = 5,
            routineType = RoutineType.WIND_DOWN,
        )

    private fun fakeTrend(id: String, priority: Int) =
        InterventionAction.TrendAlert(
            id = id,
            priority = priority,
            domain = WellBeingDomain.SLEEP,
            trendDirection = TrendDirection.DECLINING,
            severityLevel = 1,
            supportingDataPoints = emptyList(),
        )
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.moodified.app.domain.usecase.intervention.CareEvaluationEngineTest"`

If any test fails, apply the same characterization protocol as Task 6 Step 3.

- [ ] **Step 4: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/moodified/app/domain/usecase/intervention/CareEvaluationEngineTest.kt
git commit -m "test: Add orchestration unit tests for CareEvaluationEngine"
```

---

### Task 8: Unit tests for `EvaluateBaseGuidanceUseCase` (category selection per state)

**Goal:** Cover the "category selection per inferred state" behavior called out in spec §3.5 — this is where the branching lives that Task 7's orchestration tests cannot exercise. Tests use a fake `InterventionRepository` to control cooldowns and verify which `InterventionAction` variants are emitted for each mood state.

**Files:**
- Create: `app/src/test/java/com/moodified/app/domain/usecase/intervention/EvaluateBaseGuidanceUseCaseTest.kt`

**Interfaces:**
- Consumes:
  - `EvaluateBaseGuidanceUseCase(interventionRepository)` — single injected dep.
  - `suspend operator fun invoke(moodState: InferredMoodState?, snapshot: DailyBehaviorSnapshot?, liveActivity: ActivitySignal, liveInteraction: InteractionSignal): List<InterventionAction>`
  - Constant IDs from the companion object: `ID_MICRO_CONFIRM`, `ID_GUIDANCE_DISCONNECT`, `ID_GUIDANCE_MOVE`, `ID_MOTIVATION_MOMENTUM`, `ID_MOTIVATION_REST`. Tests use these IDs to assert which branch fired.

- [ ] **Step 1: Read the use case body top-to-bottom to inventory the branches**

Open `app/src/main/java/com/moodified/app/domain/usecase/intervention/EvaluateBaseGuidanceUseCase.kt`. There are three top-level branches:

1. **Micro-confirmation** — gated by `shouldTriggerMicroConfirmation(...)` and the `COOLDOWN_MICRO_CONFIRMATION` timer. Reads `moodState.valence` + live signals.
2. **Adaptive Guidance** — fires when `moodState.valence == NEGATIVE`. Two sub-branches:
   - `ID_GUIDANCE_DISCONNECT` — high late-night or total screen time.
   - `ID_GUIDANCE_MOVE` — negative + low arousal + sedentary + long sedentary minutes.
3. **Motivational Support** — fires when `moodState.valence == POSITIVE`. Two sub-branches:
   - `ID_MOTIVATION_MOMENTUM` — high arousal + steps > 5000.
   - `ID_MOTIVATION_REST` — (read the file for the exact condition).

Note the exact thresholds. The tests below use inputs comfortably above/below each threshold so small refactors of the numbers don't break them.

- [ ] **Step 2: Create the test file**

File: `app/src/test/java/com/moodified/app/domain/usecase/intervention/EvaluateBaseGuidanceUseCaseTest.kt`

```kotlin
package com.moodified.app.domain.usecase.intervention

import com.moodified.app.data.local.entity.intervention.InterventionHistoryEntity
import com.moodified.app.domain.model.activity.ActivityIntensity
import com.moodified.app.domain.model.activity.ActivitySignal
import com.moodified.app.domain.model.inference.DailyBehaviorSnapshot
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.interaction.InteractionSignal
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
import com.moodified.app.domain.repository.InterventionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EvaluateBaseGuidanceUseCaseTest {

    private class FakeInterventionRepository(
        private val onCooldownIds: Set<String> = emptySet(),
    ) : InterventionRepository {
        override suspend fun recordInterventionShown(id: String) = Unit
        override suspend fun getLastShownTime(id: String): Long? = null
        override suspend fun isOnCooldown(id: String, cooldownMillis: Long): Boolean =
            id in onCooldownIds
        override suspend fun getInterventionHistory(id: String): InterventionHistoryEntity? = null
        override suspend fun recordFeedback(id: String, feedback: String, wasCompleted: Boolean) = Unit
    }

    private val snapshot = DailyBehaviorSnapshot(
        targetDate = LocalDate.of(2026, 1, 15),
        sleepSummary = null,
        activitySummary = null,
        interactionSummary = null,
        moodEntries = emptyList(),
        dataCompletenessScore = 80,
    )

    private fun moodState(valence: Valence, arousal: Arousal) = InferredMoodState(
        valence = valence,
        arousal = arousal,
        interpretationLabel = "test",
        confidenceScore = 70,
        explainabilityString = "test",
    )

    private fun activity(
        steps: Int = 0,
        intensity: ActivityIntensity = ActivityIntensity.SEDENTARY,
        sedentaryMinutes: Int = 0,
    ): ActivitySignal = ActivitySignal(
        steps = steps,
        intensity = intensity,
        sedentaryMinutes = sedentaryMinutes,
    )

    private fun interaction(
        totalScreenTimeTodayMs: Long = 0L,
        lateNightScreenTimeTodayMs: Long = 0L,
    ): InteractionSignal = InteractionSignal(
        totalScreenTimeTodayMs = totalScreenTimeTodayMs,
        lateNightScreenTimeTodayMs = lateNightScreenTimeTodayMs,
    )

    private fun useCase(onCooldownIds: Set<String> = emptySet()) =
        EvaluateBaseGuidanceUseCase(FakeInterventionRepository(onCooldownIds))

    // ---- Guidance: disconnect fires for NEGATIVE + high screen time ----

    @Test
    fun negativeValenceWithHighScreenTimeEmitsDisconnectGuidance() = runTest {
        val result = useCase()(
            moodState(Valence.NEGATIVE, Arousal.MID),
            snapshot,
            activity(),
            interaction(totalScreenTimeTodayMs = 300 * 60_000L),
        )
        assertTrue(
            "Expected ID_GUIDANCE_DISCONNECT in result",
            result.any { it.id == EvaluateBaseGuidanceUseCase.ID_GUIDANCE_DISCONNECT },
        )
    }

    // ---- Guidance: move fires for NEGATIVE + LOW arousal + long sedentary ----

    @Test
    fun negativeLowArousalWhileSedentaryEmitsMoveGuidance() = runTest {
        val result = useCase()(
            moodState(Valence.NEGATIVE, Arousal.LOW),
            snapshot,
            activity(
                steps = 200,
                intensity = ActivityIntensity.SEDENTARY,
                sedentaryMinutes = 180,
            ),
            interaction(),
        )
        assertTrue(
            result.any { it.id == EvaluateBaseGuidanceUseCase.ID_GUIDANCE_MOVE },
        )
    }

    // ---- Motivation: momentum fires for POSITIVE + HIGH arousal + steps > 5000 ----

    @Test
    fun positiveHighArousalWithManyStepsEmitsMotivationMomentum() = runTest {
        val result = useCase()(
            moodState(Valence.POSITIVE, Arousal.HIGH),
            snapshot,
            activity(steps = 8_000),
            interaction(),
        )
        assertTrue(
            result.any { it.id == EvaluateBaseGuidanceUseCase.ID_MOTIVATION_MOMENTUM },
        )
    }

    // ---- Micro-confirmation is suppressed when its ID is on cooldown ----

    @Test
    fun microConfirmationOnCooldownDoesNotFire() = runTest {
        val onCooldown = setOf(EvaluateBaseGuidanceUseCase.ID_MICRO_CONFIRM)
        val result = useCase(onCooldownIds = onCooldown)(
            moodState(Valence.POSITIVE, Arousal.MID),
            snapshot,
            activity(),
            interaction(),
        )
        assertTrue(
            "Micro-confirmation must not be emitted while on cooldown",
            result.none { it is InterventionAction.MicroConfirmation },
        )
    }

    // ---- Null-guard: no state → no actions ----

    @Test
    fun nullMoodStateReturnsEmpty() = runTest {
        val result = useCase()(null, snapshot, activity(), interaction())
        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.moodified.app.domain.usecase.intervention.EvaluateBaseGuidanceUseCaseTest"`

Same characterization protocol on failures. Expected outcome: all five tests pass without `@Ignore`.

If the `ID_MOTIVATION_REST` branch has a straightforward trigger you can cover with one more test, add it — otherwise leave it for a follow-up.

- [ ] **Step 4: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/moodified/app/domain/usecase/intervention/EvaluateBaseGuidanceUseCaseTest.kt
git commit -m "test: Add category-selection unit tests for EvaluateBaseGuidanceUseCase"
```

---

## Phase 1 Acceptance Checklist

Run before declaring the phase complete:

- [ ] `./gradlew :app:assembleDebug` — succeeds
- [ ] `./gradlew :app:testDebugUnitTest` — all tests pass (no `@Ignore` without a linked follow-up note)
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "Migration13To14Test"` — passes on emulator or device
- [ ] `./gradlew ktlintCheck detekt` — both green
- [ ] `grep -rn "^import android\." app/src/main/java/com/moodified/app/domain` — no output
- [ ] `grep -rn "fallbackToDestructive" app/src/main` — no output
- [ ] `grep -rn "ExampleUnitTest\|ExampleInstrumentedTest" app/` — no output
- [ ] `grep -rn "androidx.work:work-runtime-ktx:" app/build.gradle.kts` — no output (only catalog references remain)
- [ ] Eight commits landed on `main` (one per task) — verify with `git log --oneline -8`

Once every box is ticked, this phase is done. Notify the plan author to generate the Phase 2 plan.
