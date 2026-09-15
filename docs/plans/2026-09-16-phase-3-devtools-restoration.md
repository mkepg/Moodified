# Phase 3 — Devtools Restoration Implementation Plan

**Goal:** Move the devtools UI surface (`DiagnosticAtoms.kt`, `DevToolsState.kt`) out of `src/main/` into `src/debug/`, build a `DebugDrawerScreen` with diagnostics (DB row counts, WorkManager last-run timestamps, permission grants, tracker states), and wire it behind the existing `DebugNavRegistrar` pattern. Endstate: `find app/src/main/java/com/moodified/app/presentation/devtools -type f` returns empty; release APK contains zero `presentation/devtools/*.class` files (spec §7 DoD).

**Architecture:**
- Reuse the existing `DebugNavRegistrar` interface (`src/main/core/devtools/`) + `DebugNavRegistrarImpl` (`src/debug/`) + release NoOp binding (`src/release/`) pattern. Extend the interface with `drawerRoute: String?`. This is a departure from spec §3.4 which shows raw `if (BuildConfig.DEBUG) { composable(...) }` inline in `MoodifiedNavHost` — the interface pattern already in place is stronger (zero devtools symbols in the release compilation unit, not just skipped at runtime) and is what the codebase actually uses.
- All Phase 3 files that hold UI or ViewModel logic live under `app/src/debug/java/com/moodified/app/...`. Only the *interface* extension lives in `src/main/`. Release builds cannot link against any debug UI symbol.
- `DebugDrawerScreen` consumes a `DebugDrawerViewModel` that combines four `Flow`s from a small `DiagnosticsRepository` (defined in `src/debug/`). The repository queries the `MoodifiedDatabase` directly for row counts, calls `WorkManager.getWorkInfosByTag(...)` for worker statuses, checks permission grants via `ContextCompat.checkSelfPermission`, and reads tracker on/off state from `SettingsPreferences` (or equivalent — check codebase during implementation). The VM is unit-testable with a fake repository; the repository impl itself is not unit-tested this round (would need Robolectric or androidTest).
- Temporary entry point until Phase 4's About long-press ships: extend the existing `MoreScreen`'s `on*Monitor` nullable-callback pattern with `onNavigateToDebugDrawer: (() -> Unit)?`. Same pattern, same debug-only visibility. Deleted in Phase 4 when replaced by the About long-press.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Navigation-Compose, Room, WorkManager, kotlinx.coroutines Flow, JUnit4, MockK (if already in use — verify at Task 6).

**Spec:** [docs/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.4 Move 2 (Rebuild devtools surface), §6 Phase 3, §7 Definition of Done.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- **No Compose UI tests, no snapshot tests** — non-goal in spec §1.
- `ktlint` and `detekt` (added Phase 1, baselined) must remain green. New violations only allowed in baselined categories (`FunctionNaming`, `LongParameterList`, `LongMethod`, `MatchingDeclarationName` — Compose false positives). Anything else is a real signal and must be fixed, not baselined.
- Feature branch: `phase-3-devtools-restoration` (created in Task 0).
- Existing debug source-set files stay put — no renaming `activitymonitor/` → `monitors/` per spec §3.4 letter. The DoD is about release-APK cleanliness, not folder shape.
- The `MonitorHeader` `eyebrow: String = "DEV TOOLS"` default parameter stays as-is once the file is in `src/debug/` — it correctly labels debug UI and never ships in release. No cleanup needed there.- **DebugDrawer diagnostics content (spec §3.4 Move 2):** DB row counts *per entity*, `WorkManager` last-run timestamps for each worker, current permission grants, current tracker states. Exact entity list + worker list + permission list are looked up during Task 6 implementation from `MoodifiedDatabase.kt` / `core/worker/` / manifest.
- **Departure from spec §3.4 code snippet (documented, deliberate):** we do NOT add raw `if (BuildConfig.DEBUG) { composable(...) }` blocks to `MoodifiedNavHost`. The existing `DebugNavRegistrar` interface + `NoOp` release binding pattern already satisfies §7 DoD and does so more strongly (zero release compilation unit surface). We extend the existing pattern for the drawer route.

---

### Task 0: Create the feature branch

**Files:** none (git-only).

**Interfaces:** none.

- [ ] **Step 1: Ensure working tree is clean, main is up to date**

Run:
```
git status
git checkout main
git pull --ff-only
```
Expected: `main` at `6b5dd12` (Phase 2 merge) or later. If there are uncommitted changes to `.idea/claudeCodeTabState.xml` from the IDE, stash them: `git stash push -m "phase-3-scratch" .idea/`.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-3-devtools-restoration
```
Expected: branch created and checked out. Verify with `git status`.

- [ ] **Step 3: Sanity check the pre-phase state**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all green. If any failure, stop and diagnose before proceeding — Phase 3 needs a known-green baseline.

No commit for this task — Task 1 makes the first commit.

---

### Task 1: Extend `DebugNavRegistrar` with `drawerRoute`

The drawer route needs a hook on the interface so the release NoOp can expose `null` and the debug impl can expose a real string. Does not yet wire a `register(...)` composable for it — that lands in Task 7 once the drawer screen exists.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/core/devtools/DebugNavRegistrar.kt`
- Modify: `app/src/release/java/com/moodified/app/di/ReleaseDevToolsModule.kt`
- Modify: `app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt`
- Modify: `app/src/debug/java/com/moodified/app/core/devtools/DebugRoutes.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `DebugNavRegistrar.drawerRoute: String?` — nullable; null in release, non-null in debug.
  - `DebugRoutes.DRAWER = "dev/drawer"` (debug-only constant).

- [ ] **Step 1: Add `DRAWER` to `DebugRoutes`**

Edit `app/src/debug/java/com/moodified/app/core/devtools/DebugRoutes.kt`:

```kotlin
package com.moodified.app.core.devtools

/**
 * Debug-only route strings. These are duplicated here (not added to the shared
 * `AppRoutes`) so release builds don't even know they exist.
 */
object DebugRoutes {
    const val DRAWER = "dev/drawer"
    const val ACTIVITY_MONITOR = "dev/activity_monitor"
    const val SLEEP_MONITOR = "dev/sleep_monitor"
    const val INTERACTION_MONITOR = "dev/interaction_monitor"
}
```

- [ ] **Step 2: Add `drawerRoute` to the interface**

Edit `app/src/main/java/com/moodified/app/core/devtools/DebugNavRegistrar.kt` — add the property alongside the existing monitor-route properties:

```kotlin
package com.moodified.app.core.devtools

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Registers debug-only routes (Debug Drawer + Activity / Sleep / Interaction
 * monitor screens) into the app's NavHost. Real implementation lives in
 * `src/debug/`; release builds receive a no-op stub so the screens never ship.
 */
interface DebugNavRegistrar {
    val isAvailable: Boolean
    val routes: Set<String>
    val drawerRoute: String?
    val activityMonitorRoute: String?
    val sleepMonitorRoute: String?
    val interactionMonitorRoute: String?

    fun register(
        graph: NavGraphBuilder,
        navController: NavController,
    )
}
```

- [ ] **Step 3: Update the release NoOp binding**

Edit `app/src/release/java/com/moodified/app/di/ReleaseDevToolsModule.kt` — add `override val drawerRoute: String? = null` to the anonymous `DebugNavRegistrar` object:

```kotlin
override val isAvailable: Boolean = false
override val routes: Set<String> = emptySet()
override val drawerRoute: String? = null
override val activityMonitorRoute: String? = null
override val sleepMonitorRoute: String? = null
override val interactionMonitorRoute: String? = null
```

- [ ] **Step 4: Update `DebugNavRegistrarImpl`**

Edit `app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt`:
- Add `override val drawerRoute: String = DebugRoutes.DRAWER` alongside the other route properties.
- Add `DebugRoutes.DRAWER` to the `routes` set.
- Do NOT register a composable for the drawer route in `register(...)` yet — the `DebugDrawerScreen` doesn't exist. Task 7 wires it. The route is present in the `routes` set (used by `MoodifiedNavHost` for the `fullScreenRoutes` computation) — that's fine; an unregistered route in `routes` doesn't cause runtime issues on its own because nothing navigates to it yet.

Body:

```kotlin
@Singleton
class DebugNavRegistrarImpl
    @Inject
    constructor() : DebugNavRegistrar {
        override val isAvailable: Boolean = true
        override val drawerRoute: String = DebugRoutes.DRAWER
        override val activityMonitorRoute: String = DebugRoutes.ACTIVITY_MONITOR
        override val sleepMonitorRoute: String = DebugRoutes.SLEEP_MONITOR
        override val interactionMonitorRoute: String = DebugRoutes.INTERACTION_MONITOR
        override val routes: Set<String> =
            setOf(
                DebugRoutes.DRAWER,
                DebugRoutes.ACTIVITY_MONITOR,
                DebugRoutes.SLEEP_MONITOR,
                DebugRoutes.INTERACTION_MONITOR,
            )

        override fun register(
            graph: NavGraphBuilder,
            navController: NavController,
        ) {
            graph.composable(DebugRoutes.ACTIVITY_MONITOR) {
                ActivityMonitorScreen(onBack = { navController.popBackStack() })
            }
            graph.composable(DebugRoutes.SLEEP_MONITOR) {
                SleepMonitorScreen(onBack = { navController.popBackStack() })
            }
            graph.composable(DebugRoutes.INTERACTION_MONITOR) {
                InteractionMonitorScreen(onBack = { navController.popBackStack() })
            }
            // DRAWER composable registered in Task 7 once DebugDrawerScreen exists.
        }
    }
```

- [ ] **Step 5: Verify both variants build**

Run:
```
./gradlew assembleDebug assembleRelease
```
Expected: BUILD SUCCESSFUL for both. Nothing consumes `drawerRoute` yet — this is purely additive.

- [ ] **Step 6: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/core/devtools/DebugNavRegistrar.kt \
        app/src/release/java/com/moodified/app/di/ReleaseDevToolsModule.kt \
        app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt \
        app/src/debug/java/com/moodified/app/core/devtools/DebugRoutes.kt
git commit -m "feat(devtools): add drawerRoute to DebugNavRegistrar

Extends the DebugNavRegistrar interface with drawerRoute: String?. Debug binding
returns 'dev/drawer'; release NoOp binding returns null. DebugRoutes gains a
DRAWER constant. Route composable not yet registered — Task 7 wires it once
DebugDrawerScreen exists.

Refs: Phase 3, spec §3.4 Move 2."
```

---

### Task 2: Move `DiagnosticAtoms.kt` from `src/main/` to `src/debug/`

The file is only consumed by the three monitor screens in `src/debug/`. Moving it makes release builds unable to link against those symbols — the whole point of Phase 3.

**Files:**
- Delete: `app/src/main/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt`
- Create: `app/src/debug/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt`

**Interfaces:**
- Consumes: nothing.
- Produces (all in package `com.moodified.app.presentation.devtools`, now debug-only):
  `WeeklyBarEntry`, `consistencyColor`, `MonitorHeader`, `MonitorStatTile`, `MonitorCard`, `MonitorCardEmpty`, `SectionLabel`, `BreakdownRow`, `DebugRow`, `StatusBadge`, `MonitorWeeklyBars`, `ConsistencyScoreSection`, `IdleBanner`.

`PermissionDeniedCard` (currently in the source-of-truth `DiagnosticAtoms.kt`) is **deleted** — grep confirms zero references from `src/debug/` (or anywhere else after Phase 2). If Task 5's verification finds any external reference, restore the composable to this file.

- [ ] **Step 1: Sanity check — no main-source consumers**

Run:
```
grep -r "com.moodified.app.presentation.devtools" app/src/main/java --include="*.kt"
```
Expected: only two hits — the `package` declarations of `DiagnosticAtoms.kt` and `DevToolsState.kt` themselves. If anything in `src/main/java/` **imports** the package, stop and investigate — those imports need to be resolved before the move.

- [ ] **Step 2: Create the file under `src/debug/` with `PermissionDeniedCard` removed**

Copy the entire body of `app/src/main/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt` verbatim to `app/src/debug/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt`, then delete the `PermissionDeniedCard` composable (currently lines 421–461) and the `androidx.compose.foundation.border` import if that was its only user (verify by grepping `.border(` inside the file after deletion).

Package declaration stays `package com.moodified.app.presentation.devtools`.

- [ ] **Step 3: Delete the source-of-truth in `src/main/`**

Run:
```
rm app/src/main/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt
```

- [ ] **Step 4: Verify debug build compiles**

Run:
```
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. The three debug monitor screens still resolve `MonitorHeader`, `SectionLabel`, etc. — they now come from the debug-source-set copy.

- [ ] **Step 5: Verify release build compiles**

Run:
```
./gradlew assembleRelease
```
Expected: BUILD SUCCESSFUL. Release does not include the `DiagnosticAtoms.kt` file at all. If release fails with `Unresolved reference: MonitorHeader` etc., it means some `src/main/` code still consumed the atoms — go back and reconcile.

- [ ] **Step 6: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt \
        app/src/debug/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt
git commit -m "refactor(devtools): move DiagnosticAtoms to src/debug/

Only src/debug/ monitor screens consume these atoms after Phase 2. Moving the
file to the debug source set means release builds cannot link against
MonitorHeader / MonitorCard / etc. — a strict enforcement of spec §7 DoD.
Drops PermissionDeniedCard (unreferenced).

Refs: Phase 3, spec §3.4 Move 2, §7 DoD."
```

---

### Task 3: Move `DevToolsState.kt` from `src/main/` to `src/debug/`

Same rationale as Task 2. `MonitorUiState<>` is only used by the debug monitor VMs.

**Files:**
- Delete: `app/src/main/java/com/moodified/app/presentation/devtools/DevToolsState.kt`
- Create: `app/src/debug/java/com/moodified/app/presentation/devtools/DevToolsState.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `MonitorUiState<Signal, DailySummary, WeeklyData>` in package `com.moodified.app.presentation.devtools`.

- [ ] **Step 1: Sanity check — no main-source consumers**

Run:
```
grep -rn "MonitorUiState" app/src/main/java --include="*.kt"
```
Expected: zero hits.

- [ ] **Step 2: Create the file under `src/debug/`**

Copy `app/src/main/java/com/moodified/app/presentation/devtools/DevToolsState.kt` verbatim to `app/src/debug/java/com/moodified/app/presentation/devtools/DevToolsState.kt`. Body is short:

```kotlin
package com.moodified.app.presentation.devtools

// midnightTickerFlow has been moved to com.moodified.app.core.utils.MidnightTickerFlow.kt
// Import it from there: import com.moodified.app.core.utils.midnightTickerFlow

data class MonitorUiState<Signal, DailySummary, WeeklyData>(
    val isLoading: Boolean = false,
    val isTracking: Boolean = false,
    val liveSignal: Signal,
    val todaySummary: DailySummary? = null,
    val weeklyData: WeeklyData? = null,
)
```

- [ ] **Step 3: Delete the source-of-truth in `src/main/`**

Run:
```
rm app/src/main/java/com/moodified/app/presentation/devtools/DevToolsState.kt
```

- [ ] **Step 4: Verify both variants build**

Run:
```
./gradlew assembleDebug assembleRelease
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 5: Verify main is now devtools-free**

Run:
```
find app/src/main/java/com/moodified/app/presentation/devtools -type f 2>/dev/null || echo "empty"
```
Expected: `empty` (or an error indicating the directory does not exist). If the directory still exists empty, remove it:
```
rmdir app/src/main/java/com/moodified/app/presentation/devtools
```
(`rmdir` fails harmlessly on some Windows shells if git tracks the folder — the intent is the directory is empty of tracked files.)

- [ ] **Step 6: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/devtools/DevToolsState.kt \
        app/src/debug/java/com/moodified/app/presentation/devtools/DevToolsState.kt
git commit -m "refactor(devtools): move DevToolsState to src/debug/

MonitorUiState<> is a generic used only by the src/debug/ monitor VMs. Move
completes the src/main/presentation/devtools/ evacuation started in Task 2.
src/main/ now contains zero devtools UI surface — verified via find.

Refs: Phase 3, spec §3.4 Move 2, §7 DoD."
```

---

### Task 4: Verify release APK has zero `presentation/devtools/*.class` files

Cross-check that the source-set migration in Tasks 2–3 actually delivered the DoD's binary-level guarantee.

**Files:** none (verification only).

- [ ] **Step 1: Build the release APK**

Run:
```
./gradlew assembleRelease
```
Expected: BUILD SUCCESSFUL. Locate the APK — typically `app/build/outputs/apk/release/app-release-unsigned.apk`.

- [ ] **Step 2: Inspect the APK for devtools classes**

Run (from repo root, adjust path if the APK name differs):
```
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep -i "presentation/devtools" || echo "OK: no devtools classes in release APK"
```

Expected output: `OK: no devtools classes in release APK`.

If `unzip` isn't available on Windows, use `jar tf app/build/outputs/apk/release/app-release-unsigned.apk | grep -i "presentation/devtools" || echo "OK"` or open the APK in Android Studio's APK Analyzer and confirm no `com/moodified/app/presentation/devtools/**` entries. If ANY match is found, stop and investigate — the migration is incomplete.

- [ ] **Step 3: Verify debug APK still contains the classes**

Run:
```
./gradlew assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -i "presentation/devtools"
```

Expected: several `com/moodified/app/presentation/devtools/**.class` entries (the atoms, the state, the monitor screens/VMs). Confirms the debug variant still ships them.

- [ ] **Step 4: No commit for this task**

Verification only. Nothing to commit. Proceed to Task 5.

---

### Task 5: Build the `DiagnosticsRepository` (interface + impl in `src/debug/`)

A small aggregator that the `DebugDrawerViewModel` (Task 6) consumes. Kept as a distinct type so the VM is unit-testable with a fake.

**Files:**
- Create: `app/src/debug/java/com/moodified/app/core/devtools/DiagnosticsRepository.kt` (interface + impl in one file — small, colocated).

**Interfaces:**
- Consumes: `MoodifiedDatabase`, `androidx.work.WorkManager`, `android.content.Context`, whichever preferences source exposes tracker states (grep during implementation — likely `SettingsPreferences` or `TrackingPreferences`).
- Produces:
  ```kotlin
  data class DiagnosticsSnapshot(
      val dbRowCounts: Map<String, Long>,
      val workerStatuses: List<WorkerStatus>,
      val permissionGrants: List<PermissionStatus>,
      val trackerStates: Map<String, Boolean>,
  )

  data class WorkerStatus(
      val tag: String,
      val state: String,          // e.g. "SUCCEEDED", "ENQUEUED", "RUNNING", "FAILED", "NEVER RUN"
      val lastRunMillis: Long?,   // null if never enqueued
  )

  data class PermissionStatus(
      val permission: String,     // e.g. "android.permission.ACTIVITY_RECOGNITION"
      val granted: Boolean,
  )

  interface DiagnosticsRepository {
      fun snapshot(): Flow<DiagnosticsSnapshot>
  }

  @Singleton
  class DiagnosticsRepositoryImpl @Inject constructor(
      @ApplicationContext private val context: Context,
      private val database: MoodifiedDatabase,
      private val workManager: WorkManager,
      // + whichever preferences source is discovered
  ) : DiagnosticsRepository { ... }
  ```

- [ ] **Step 1: Discover the exact ingredients**

Before writing code, do a quick codebase read (5 min) to establish:

- Entity list: run `grep -l "@Entity" app/src/main/java/com/moodified/app/data/local/entity/` and list the `tableName = "..."` values. Aim for the ~8 entities the app tracks.
- Worker list: `grep -rn ": CoroutineWorker\|: Worker" app/src/main/java/com/moodified/app/core/worker/`. Note each class name and any WorkManager tags used.
- Permission list: read `app/src/main/AndroidManifest.xml`, collect every `<uses-permission>` that is runtime-requestable (skip `INTERNET`, `POST_NOTIFICATIONS` gates by API 33+, etc.). Typical set: `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS` (special access — use `AppOpsManager.checkOpNoThrow(...)` instead of `checkSelfPermission`).
- Tracker preferences: `grep -rn "tracker\|isTracking\|trackingEnabled" app/src/main/java/com/moodified/app/data/local/datasource/` and identify the flow-based preferences interface. If unclear, inject the preferences interface used by the three monitor VMs.

Write findings inline into a scratch section of this task in the plan file if unsure — commit those findings so the executor after you has them.

- [ ] **Step 2: Write the interface + data classes**

Create `app/src/debug/java/com/moodified/app/core/devtools/DiagnosticsRepository.kt` with the interface and data-class declarations from the Interfaces block above.

- [ ] **Step 3: Implement `DiagnosticsRepositoryImpl.snapshot()`**

Implementation notes:

- **DB row counts:** `combine(...)` `Flow`s from each DAO's `observeCount()` method (add these to DAOs if missing — one-line addition per DAO: `@Query("SELECT COUNT(*) FROM <table>") fun observeCount(): Flow<Long>`). If adding DAO methods is out-of-scope-feeling, alternative: use `flow { emit(database.query("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) }) }` in a background dispatcher with a 5-second polling `flow`. Prefer the DAO approach — reactive is nicer, no polling. Adding an `observeCount()` method to each DAO is a small mechanical change; do it as part of this task.
- **Worker statuses:** for each known worker tag, call `workManager.getWorkInfosByTagLiveData(tag).asFlow()` (add `androidx.lifecycle:lifecycle-livedata-ktx` if not on classpath — check `libs.versions.toml` / `app/build.gradle.kts` first). The `WorkInfo` object has `state` and no direct "last run millis"; approximate with `WorkInfo.outputData` timestamp if the workers write one, or fall back to `null` and label "unknown" in the UI. Acceptable to ship worker state without last-run timestamp if extracting it is non-trivial — mark the field `lastRunMillis: Long? = null` and address in a follow-up.
- **Permission grants:** wrap `ContextCompat.checkSelfPermission(context, name)` in a `flow { emit(...) }.stateIn(...)` — permissions don't change while the app is foreground, so re-emit on `WhileSubscribed` restart. `PACKAGE_USAGE_STATS` uses `AppOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)`.
- **Tracker states:** consume the preferences flow from the discovered source.
- Combine via `kotlinx.coroutines.flow.combine(...)` into a single `Flow<DiagnosticsSnapshot>`.

- [ ] **Step 4: Verify debug build compiles**

Run:
```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. `DiagnosticsRepositoryImpl` will be automatically injectable via Hilt because of the `@Singleton` + `@Inject constructor` combination — no `@Binds` needed if we let Hilt bind the impl directly, or add a `@Binds` in `DebugDevToolsModule` if we want to inject the interface. Prefer the `@Binds` approach for testability:

Add to `app/src/debug/java/com/moodified/app/di/DebugDevToolsModule.kt`:
```kotlin
@Binds @Singleton
abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository
```

- [ ] **Step 5: Verify release still builds**

Run:
```
./gradlew assembleRelease
```
Expected: BUILD SUCCESSFUL. `DiagnosticsRepository` lives in `src/debug/`, so release does not see it — no release-side binding needed.

- [ ] **Step 6: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations outside baselined categories.

- [ ] **Step 7: Commit**

```
git add app/src/debug/java/com/moodified/app/core/devtools/DiagnosticsRepository.kt \
        app/src/debug/java/com/moodified/app/di/DebugDevToolsModule.kt \
        app/src/main/java/com/moodified/app/data/local/dao/  # if observeCount() added to DAOs
git commit -m "feat(devtools): add DiagnosticsRepository

Aggregates DB row counts, WorkManager statuses, permission grants, and tracker
states into a single Flow<DiagnosticsSnapshot>. Lives in src/debug/, bound in
DebugDevToolsModule. DAOs gain observeCount() where needed.

Refs: Phase 3, spec §3.4 Move 2."
```

Note: if DAOs were modified, they live in `src/main/` — the commit spans both source sets, which is expected.

---

### Task 6: Build the `DebugDrawerViewModel` (test-first)

**Files:**
- Create: `app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModel.kt`
- Test: `app/src/test/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModelTest.kt`

Note: the test lives under `src/test/` (unit test source set), NOT `src/testDebug/`. Kotlin/Android unit tests default to `src/test/` and the debug variant merges `src/debug/` main-source into the compilation, so tests in `src/test/` can reference debug-only types when running against the `testDebugUnitTest` task. Verify this works in Step 4 — if the test fails to resolve `DebugDrawerViewModel`, move the test to `src/testDebug/java/...`.

**Interfaces:**
- Consumes: `DiagnosticsRepository` (Task 5).
- Produces:
  ```kotlin
  data class DebugDrawerUiState(
      val snapshot: DiagnosticsSnapshot? = null,
      val isLoading: Boolean = true,
  )

  @HiltViewModel
  class DebugDrawerViewModel @Inject constructor(
      private val repository: DiagnosticsRepository,
  ) : ViewModel() {
      val uiState: StateFlow<DebugDrawerUiState>
  }
  ```

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModelTest.kt`:

```kotlin
package com.moodified.app.presentation.devtools.drawer

import app.cash.turbine.test
import com.moodified.app.core.devtools.DiagnosticsRepository
import com.moodified.app.core.devtools.DiagnosticsSnapshot
import com.moodified.app.core.devtools.PermissionStatus
import com.moodified.app.core.devtools.WorkerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugDrawerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeDiagnosticsRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeDiagnosticsRepository()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading with null snapshot`() = runTest(dispatcher) {
        val vm = DebugDrawerViewModel(repo)
        val initial = vm.uiState.value
        assertTrue("Expected initial loading=true", initial.isLoading)
        assertNull(initial.snapshot)
    }

    @Test
    fun `emits snapshot when repository publishes`() = runTest(dispatcher) {
        val vm = DebugDrawerViewModel(repo)
        vm.uiState.test {
            skipItems(1) // initial loading
            repo.emit(SAMPLE_SNAPSHOT)
            val loaded = awaitItem()
            assertEquals(SAMPLE_SNAPSHOT, loaded.snapshot)
            assertEquals(false, loaded.isLoading)
        }
    }

    private companion object {
        val SAMPLE_SNAPSHOT = DiagnosticsSnapshot(
            dbRowCounts = mapOf("mood_entries" to 12L, "activity_signals" to 340L),
            workerStatuses = listOf(WorkerStatus("PurgeWorker", "ENQUEUED", 0L)),
            permissionGrants = listOf(PermissionStatus("android.permission.ACTIVITY_RECOGNITION", true)),
            trackerStates = mapOf("activity" to true, "sleep" to false),
        )
    }
}

private class FakeDiagnosticsRepository : DiagnosticsRepository {
    private val flow = MutableStateFlow<DiagnosticsSnapshot?>(null)
    override fun snapshot(): kotlinx.coroutines.flow.Flow<DiagnosticsSnapshot> =
        kotlinx.coroutines.flow.flow {
            flow.collect { snap -> if (snap != null) emit(snap) }
        }
    suspend fun emit(snap: DiagnosticsSnapshot) { flow.value = snap }
}
```

If `app.cash.turbine.test` isn't on the classpath, check `libs.versions.toml` — if turbine isn't available, replace `.test { ... }` with a direct `val values = mutableListOf<DebugDrawerUiState>(); val job = launch { vm.uiState.toList(values) }; dispatcher.scheduler.advanceUntilIdle(); repo.emit(...); dispatcher.scheduler.advanceUntilIdle(); assertEquals(SAMPLE_SNAPSHOT, values.last().snapshot); job.cancel()`. Prefer turbine if available.

- [ ] **Step 2: Run the test — expect COMPILATION FAILURE**

Run:
```
./gradlew testDebugUnitTest --tests "com.moodified.app.presentation.devtools.drawer.DebugDrawerViewModelTest"
```
Expected: `Unresolved reference: DebugDrawerViewModel`. If instead the test fails to resolve `DiagnosticsRepository`, the test source set isn't merging debug main-source — move the test file to `app/src/testDebug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModelTest.kt` (create the directory) and re-run.

- [ ] **Step 3: Create `DebugDrawerViewModel`**

Create `app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModel.kt`:

```kotlin
package com.moodified.app.presentation.devtools.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.core.devtools.DiagnosticsRepository
import com.moodified.app.core.devtools.DiagnosticsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DebugDrawerUiState(
    val snapshot: DiagnosticsSnapshot? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class DebugDrawerViewModel @Inject constructor(
    repository: DiagnosticsRepository,
) : ViewModel() {
    val uiState: StateFlow<DebugDrawerUiState> =
        repository.snapshot()
            .map { DebugDrawerUiState(snapshot = it, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DebugDrawerUiState(),
            )
}
```

- [ ] **Step 4: Run the test — expect PASS**

Run:
```
./gradlew testDebugUnitTest --tests "com.moodified.app.presentation.devtools.drawer.DebugDrawerViewModelTest"
```
Expected: 2 tests PASS.

- [ ] **Step 5: Full test suite still green**

Run:
```
./gradlew testDebugUnitTest
```
Expected: all pre-existing Phase 1 + Phase 2 tests still pass, plus the 2 new ones.

- [ ] **Step 6: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations outside baselined categories.

- [ ] **Step 7: Commit**

```
git add app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModel.kt \
        app/src/test/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModelTest.kt
# or app/src/testDebug/... if the test moved in Step 2
git commit -m "feat(devtools): add DebugDrawerViewModel

Consumes DiagnosticsRepository and maps snapshot Flow to a StateFlow<UiState>
with a loading indicator. Two unit tests cover initial-loading and post-emit
states with a fake repository.

Refs: Phase 3, spec §3.4 Move 2."
```

---

### Task 7: Build `DebugDrawerScreen` and register its route

**Files:**
- Create: `app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerScreen.kt`
- Modify: `app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt` — register drawer composable in `register(...)`.

**Interfaces:**
- Consumes: `DebugDrawerViewModel` (Task 6), diagnostic atoms from `DiagnosticAtoms.kt` (`MonitorHeader`, `MonitorCard`, `SectionLabel`, `DebugRow`, `StatusBadge`, `IdleBanner`), `DebugRoutes.{ACTIVITY,SLEEP,INTERACTION}_MONITOR` for the monitor links.
- Produces:
  ```kotlin
  @Composable
  fun DebugDrawerScreen(
      onBack: () -> Unit,
      onNavigateToMonitor: (route: String) -> Unit,
      viewModel: DebugDrawerViewModel = hiltViewModel(),
  )
  ```

- [ ] **Step 1: Create `DebugDrawerScreen.kt`**

The layout is intentionally dry — this is a diagnostics surface, not a designed screen. Use existing atoms only.

```kotlin
package com.moodified.app.presentation.devtools.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.devtools.DebugRoutes
import com.moodified.app.core.devtools.DiagnosticsSnapshot
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.presentation.devtools.DebugRow
import com.moodified.app.presentation.devtools.IdleBanner
import com.moodified.app.presentation.devtools.MonitorCard
import com.moodified.app.presentation.devtools.MonitorHeader
import com.moodified.app.presentation.devtools.SectionLabel

@Composable
fun DebugDrawerScreen(
    onBack: () -> Unit,
    onNavigateToMonitor: (route: String) -> Unit,
    viewModel: DebugDrawerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            MonitorHeader(
                title = "Debug Drawer",
                subtitle = "Internal diagnostics",
                isTracking = false,
                onBack = onBack,
            )
        }

        item { SectionLabel("Monitors") }
        item { MonitorLinksCard(onNavigateToMonitor) }

        val snap = state.snapshot
        if (snap == null) {
            item { Spacer(Modifier.height(16.dp)) }
            item { IdleBanner("Loading diagnostics...") }
        } else {
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("DB Row Counts") }
            item { DbCountsCard(snap) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("Workers") }
            item { WorkersCard(snap) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("Permissions") }
            item { PermissionsCard(snap) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SectionLabel("Trackers") }
            item { TrackersCard(snap) }
        }
    }
}

@Composable
private fun MonitorLinksCard(onNavigate: (String) -> Unit) {
    MonitorCard {
        OutlinedButton(
            onClick = { onNavigate(DebugRoutes.ACTIVITY_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Activity Monitor") }
        OutlinedButton(
            onClick = { onNavigate(DebugRoutes.SLEEP_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sleep Monitor") }
        OutlinedButton(
            onClick = { onNavigate(DebugRoutes.INTERACTION_MONITOR) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Interaction Monitor") }
    }
}

@Composable
private fun DbCountsCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.dbRowCounts.entries.sortedBy { it.key }.forEach { (table, count) ->
            DebugRow(table, count.toString())
        }
    }
}

@Composable
private fun WorkersCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.workerStatuses.forEach { w ->
            val value = if (w.lastRunMillis != null) "${w.state} @ ${w.lastRunMillis}" else w.state
            DebugRow(w.tag, value)
        }
    }
}

@Composable
private fun PermissionsCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.permissionGrants.forEach { p ->
            DebugRow(p.permission.substringAfterLast('.'), if (p.granted) "granted" else "denied")
        }
    }
}

@Composable
private fun TrackersCard(snap: DiagnosticsSnapshot) {
    MonitorCard {
        snap.trackerStates.entries.sortedBy { it.key }.forEach { (name, enabled) ->
            DebugRow(name, if (enabled) "on" else "off")
        }
    }
}
```

- [ ] **Step 2: Register the drawer composable in `DebugNavRegistrarImpl`**

Edit `app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt` — add the drawer composable at the top of `register(...)`:

```kotlin
override fun register(
    graph: NavGraphBuilder,
    navController: NavController,
) {
    graph.composable(DebugRoutes.DRAWER) {
        DebugDrawerScreen(
            onBack = { navController.popBackStack() },
            onNavigateToMonitor = { route -> navController.navigate(route) },
        )
    }
    graph.composable(DebugRoutes.ACTIVITY_MONITOR) {
        ActivityMonitorScreen(onBack = { navController.popBackStack() })
    }
    graph.composable(DebugRoutes.SLEEP_MONITOR) {
        SleepMonitorScreen(onBack = { navController.popBackStack() })
    }
    graph.composable(DebugRoutes.INTERACTION_MONITOR) {
        InteractionMonitorScreen(onBack = { navController.popBackStack() })
    }
}
```

Add import: `import com.moodified.app.presentation.devtools.drawer.DebugDrawerScreen`.

- [ ] **Step 3: Verify both variants build**

Run:
```
./gradlew assembleDebug assembleRelease
```
Expected: BUILD SUCCESSFUL for both. Release still contains zero devtools symbols (Task 4's guarantee).

- [ ] **Step 4: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations outside baselined categories.

- [ ] **Step 5: Commit**

```
git add app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerScreen.kt \
        app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt
git commit -m "feat(devtools): add DebugDrawerScreen with diagnostics

New screen at DebugRoutes.DRAWER (dev/drawer). Links to the three monitor
screens, renders DB row counts / worker statuses / permission grants /
tracker states from DebugDrawerViewModel. Uses diagnostic atoms only.

Refs: Phase 3, spec §3.4 Move 2."
```

---

### Task 8: Wire temporary drawer entry point via `MoreScreen`

Until Phase 4's About-screen long-press ships, the drawer needs an in-app entry point so it can be exercised. Follows the existing pattern in `MoreScreen`: nullable `on*` callback, gated by whether `debugNavRegistrar.drawerRoute != null`.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/more/MoreScreen.kt` — add `onNavigateToDebugDrawer: (() -> Unit)? = null` parameter, render a button when non-null. Place it visually adjacent to the existing monitor-navigation buttons.
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` — pass the callback through, gated on `debugNavRegistrar.drawerRoute`.

**Interfaces:**
- Consumes: `DebugNavRegistrar.drawerRoute` (Task 1).
- Produces: nothing new; extends existing `MoreScreen` signature.

- [ ] **Step 1: Read `MoreScreen.kt` to find the monitor-buttons placement**

Read `app/src/main/java/com/moodified/app/presentation/more/MoreScreen.kt`. Locate the block that renders the three `on*Monitor` buttons (they're conditional on non-null callbacks). The new "Debug Drawer" button goes above them so the drawer is the *primary* debug entry and the individual monitor buttons remain accessible as a fallback.

- [ ] **Step 2: Add `onNavigateToDebugDrawer` parameter**

Edit `MoreScreen.kt`'s composable signature — add `onNavigateToDebugDrawer: (() -> Unit)? = null` alongside the existing three `on*Monitor` nullable callbacks (default: `null`, matching the existing pattern).

In the render body, above the existing monitor buttons block, add:

```kotlin
if (onNavigateToDebugDrawer != null) {
    OutlinedButton(
        onClick = onNavigateToDebugDrawer,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Debug Drawer") }
}
```

Match the exact modifier and shape used by the neighboring monitor buttons — do not introduce styling drift. If those buttons live inside a `Column` with `verticalArrangement = Arrangement.spacedBy(...)`, the new button just goes in as another child.

- [ ] **Step 3: Pass the callback from `MoodifiedNavHost`**

Edit `MoodifiedNavHost.kt` — in the `composable(AppRoutes.More.route) { MoreScreen(...) }` block, add:

```kotlin
onNavigateToDebugDrawer =
    debugNavRegistrar.drawerRoute?.let { route ->
        { navController.navigate(route) }
    },
```

Mirrors the existing `onNavigateToActivityMonitor`/`onNavigateToSleepMonitor`/`onNavigateToInteractionMonitor` pattern precisely.

- [ ] **Step 4: Verify both variants build**

Run:
```
./gradlew assembleDebug assembleRelease
```
Expected: BUILD SUCCESSFUL. Release build: `drawerRoute` is `null`, so the callback is `null`, so the button doesn't render. Debug build: button renders and taps navigate to the drawer.

- [ ] **Step 5: Run lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no new violations outside baselined categories.

- [ ] **Step 6: Manual smoke — drawer reachable in debug, invisible in release**

Debug reachability:
```
./gradlew installDebug
```
Launch → Bottom nav → More → confirm "Debug Drawer" button appears → tap → drawer renders with diagnostics.

Release invisibility (build only — no need to install):
```
./gradlew assembleRelease
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk classes*.dex | strings | grep -i "Debug Drawer" || echo "OK: no Debug Drawer string in release APK"
```

Note: the string search may false-positive on generic "debug" strings from Compose/Hilt internals. The stronger check remains Task 4's `presentation/devtools` class-file inspection. If a false positive appears, verify manually that no `DebugDrawerScreen` class is in the release APK using APK Analyzer.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/more/MoreScreen.kt \
        app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
git commit -m "feat(devtools): temporary MoreScreen entry point for debug drawer

Extends MoreScreen with onNavigateToDebugDrawer: (() -> Unit)? = null following
the existing on*Monitor nullable-callback pattern. NavHost wires it through
debugNavRegistrar.drawerRoute — null in release (button hidden), non-null in
debug (button rendered). Removed in Phase 4 when the About-screen long-press
gesture replaces this affordance.

Refs: Phase 3, spec §3.4 Move 2 (temporary entry point; Phase 4 spec §6 note)."
```

---

### Task 9: Final verification pass

**Files:** none (verification + optional doc mark-off).

- [ ] **Step 1: Full clean build**

Run:
```
./gradlew clean assembleDebug assembleRelease
```
Expected: BOTH BUILD SUCCESSFUL.

- [ ] **Step 2: Full test suite**

Run:
```
./gradlew testDebugUnitTest
```
Expected: all pre-existing tests (Phase 1 + Phase 2) plus new Phase 3 tests (`DebugDrawerViewModelTest`) pass. Zero failures.

- [ ] **Step 3: Lint**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL. No new baseline entries outside the accepted Compose-false-positive categories.

- [ ] **Step 4: DoD verification — src/main is devtools-free**

Run:
```
find app/src/main/java/com/moodified/app/presentation/devtools -type f 2>/dev/null | head
```
Expected: no output.

- [ ] **Step 5: DoD verification — release APK has no devtools classes**

Run (adjust APK path as needed):
```
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep -i "presentation/devtools" || echo "OK"
```
Expected: `OK`.

- [ ] **Step 6: Manual on-device smoke test (covers Phase 2 + Phase 3)**

`./gradlew installDebug`. Walk through:

1. Launch → land on Check-In. No crash.
2. Bottom-nav Insight → land on Overview → tab through Activity / Sleep / Screen Use. Rotate device on each; state persists.
3. Bottom-nav to More/Profile → confirm "Debug Drawer" button visible.
4. Tap Debug Drawer → screen loads → DB counts + workers + permissions + trackers render.
5. From Debug Drawer, tap "Activity Monitor" → activity monitor screen renders. Back → returns to drawer.
6. Repeat for Sleep Monitor and Interaction Monitor.
7. Back from drawer → returns to More.
8. Bottom-nav Check-In → back to home; no state leak.
9. From More, tap the legacy "Activity insight" link → confirm redirect to Insight?tab=activity still works (Phase 2 regression check).

If any step fails, stop and investigate before Task 10.

- [ ] **Step 7: Mark plan complete**

Update this file's checkboxes to `- [x]` throughout, commit as a doc update:

```
git add docs/plans/2026-09-16-phase-3-devtools-restoration.md
git commit -m "docs(phase-3): mark Phase 3 plan complete"
```

---

### Task 10: Strip Co-Authored-By trailers and prepare for merge

**Files:** git history rewrite on the feature branch only.

- [ ] **Step 1: Ensure working tree is clean**

Run:
```
git status
```
Expected: `nothing to commit, working tree clean`. If `.idea/claudeCodeTabState.xml` is dirty, stash it: `git stash push -m "pre-filter" .idea/`.

- [ ] **Step 2: Identify base commit**

Run:
```
git merge-base main HEAD
```
Note the resulting SHA; call it `<base>`.

- [ ] **Step 3: Strip trailers from every commit in this branch's range**

Run:
```
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f --msg-filter \
  'sed "/^Co-Authored-By:.*[Cc]laude/d; /^Co-Authored-By:.*noreply@anthropic/d"' \
  <base>..HEAD
```

Substitute the SHA from Step 2 for `<base>`. Confirm afterward:
```
git log <base>..HEAD --format='%B' | grep -i "co-authored-by" || echo "OK: no trailers"
```
Expected: `OK: no trailers`.

- [ ] **Step 4: Restore stashed .idea/ (if applicable)**

```
git stash pop
```
(or `git stash drop` if the tabstate change isn't worth keeping).

- [ ] **Step 5: Announce ready for merge**

Print a summary of the branch: `git log --oneline main..HEAD`. Announce to the user that Phase 3 is ready. Do NOT merge or push without explicit user approval — user drives the merge decision via `superpowers:finishing-a-development-branch`.

No commit for this task — filter-branch already rewrote history in-place.

---

## Summary of Deliverables

- **New files:**
  - `app/src/debug/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt` (moved from `src/main/`, minus `PermissionDeniedCard`)
  - `app/src/debug/java/com/moodified/app/presentation/devtools/DevToolsState.kt` (moved from `src/main/`)
  - `app/src/debug/java/com/moodified/app/core/devtools/DiagnosticsRepository.kt`
  - `app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModel.kt`
  - `app/src/debug/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerScreen.kt`
  - `app/src/test/java/com/moodified/app/presentation/devtools/drawer/DebugDrawerViewModelTest.kt` (or `src/testDebug/` per Task 6 Step 2)
- **Modified files:**
  - `app/src/main/java/com/moodified/app/core/devtools/DebugNavRegistrar.kt` (add `drawerRoute`)
  - `app/src/release/java/com/moodified/app/di/ReleaseDevToolsModule.kt` (NoOp `drawerRoute`)
  - `app/src/debug/java/com/moodified/app/core/devtools/DebugNavRegistrarImpl.kt` (drawer route + composable registration)
  - `app/src/debug/java/com/moodified/app/core/devtools/DebugRoutes.kt` (add `DRAWER`)
  - `app/src/debug/java/com/moodified/app/di/DebugDevToolsModule.kt` (bind `DiagnosticsRepository`)
  - `app/src/main/java/com/moodified/app/presentation/more/MoreScreen.kt` (temporary drawer entry point)
  - `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` (pass drawer callback)
  - Possibly: DAO files in `app/src/main/java/com/moodified/app/data/local/dao/**` (add `observeCount()` per Task 5)
- **Deleted files:**
  - `app/src/main/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt`
  - `app/src/main/java/com/moodified/app/presentation/devtools/DevToolsState.kt`

## Follow-ups (out of Phase 3 scope; tracked here)

- **Phase 3.5 — Insight chart-helper consolidation:** carve `overviewDrawGridLines` / `activityTabDrawGridLines` / `sleepTabDrawGridLines` / `screenUseTabDrawGridLines` + per-tab `ChartSurface` / `LegendDot` / `formatSteps` / `getMoodDescription` out of the four tab files into `presentation/insight/components/InsightChartAtoms.kt`. Same phase, split `InsightAtoms.kt` (~515 LOC) into logically-scoped subfiles. Own branch, own review.
- **Phase 4** — About-screen long-press replaces the temporary MoreScreen "Debug Drawer" button. When Phase 4 lands, delete `onNavigateToDebugDrawer` from `MoreScreen.kt` and the corresponding wire-up in `MoodifiedNavHost.kt`.
- **Delete legacy `insight/{activity,sleep,screen_use}` redirects** — one release cycle after Phase 2 shipped. Phase 4 or later.
- **`DiagnosticsRepositoryImpl` under test** — currently untested (would need Robolectric or `androidTest`). Fine as a follow-up; the VM has coverage via a fake.
- **Workers' `lastRunMillis` — actual timestamp extraction** — Task 5 falls back to `null` if `WorkInfo.outputData` doesn't already contain a timestamp. Follow-up: have workers write `outputData` on completion so the drawer can display a real value.
