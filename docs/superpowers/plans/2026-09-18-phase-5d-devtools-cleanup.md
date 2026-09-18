# Phase 5d — Dev-Tools Leak Cleanup + Docs Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase 3's dev-tools restoration by removing every dev-only affordance still living on release-shipping code paths, moving each into the existing debug-only `DebugDrawerScreen`, and stripping the release APK of the moved code. Then refresh `docs/application-overview.md` to reflect the post-consolidation state.

**Architecture:**
- **Sweep first** — Task 1 grep-catalogs every function on a release-shipping ViewModel/Composable that is dev-only (currently known: `ProfileViewModel.triggerTestMicroPrompt`, `ProfileViewModel.injectMockMoodData`, `ProfileViewModel.injectMockActivityData`). If the sweep surfaces only these three, this plan runs as designed. If it surfaces more, each additional leak gets its own Task-1a step and the plan expands.
- **Migration strategy** — `MockDataSeeder` interface already exists (`app/src/main/java/com/moodified/app/core/devtools/MockDataSeeder.kt`) with `seedMoodData()` and `seedActivityData()`. Extend it with `suspend fun fireTestMicroPrompt(context: Context)`. Debug variant (`DebugMockDataSeeder`) implements it by moving the current `ProfileViewModel.triggerTestMicroPrompt` body verbatim. Release variant is no-op.
- **UI relocation** — the three actions render in `DebugDrawerScreen` (Phase 3's surface). ProfileScreen's "Debug Data" section is deleted entirely. `ProfileViewModel` loses `mockDataSeeder` and `isMockDataAvailable` (no longer needed — the drawer already exists only in debug builds via `DebugNavRegistrar`).
- **APK verification** — after the removal, run `./gradlew :app:assembleRelease` + `bundletool dump` (or a `javap` on the extracted release DEX) and grep for `triggerTestMicroPrompt` / `injectMockMoodData` / `injectMockActivityData` in the release class list. All three must be absent, matching spec §7's "no `presentation/devtools/*` files in release APK" DoD but extended to cover leaked dev-methods on user-facing ViewModels.
- **Docs refresh** — final Task rewrites `docs/application-overview.md` to reflect: post-consolidation Insight tabs, Support surface, Notifications inbox, Onboarding, bottom-nav shape (Check-in / Calendar / QuickLog / Insight / Profile), and the "Care is a full-screen route" change. Small delta, one bounded commit.

**Tech Stack:** Kotlin, Hilt (existing variant-source-set pattern), Compose (existing). No new dependencies. No schema change.

**Spec:** [docs/superpowers/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.4 (dev-tools restructure), §7 (Definition of done — "zero references to `presentation/devtools/` from user-facing packages in release builds", "no `presentation/devtools/*` files in the release APK"). Phase 5d extends the second DoD line to cover leaked methods, not just leaked packages. Docs refresh satisfies §7's `application-overview.md` clause.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- Green gate per task: `./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt` — all green before commit.
- Detekt baseline additions allowed **only** in the four Compose false-positive categories from prior phases.
- Feature branch: `phase-5d-devtools-cleanup` (created in Task 0), cut from `main` **after** Phase 5c has merged.
- **Trailer policy — HARD RULE:** No `Co-Authored-By: Claude` trailers on any commit.
- **APK verification is required.** Task 4 verifies the release APK does not contain the moved method names.
- **`docs/application-overview.md` is currently untracked** (per Phase 4 kickoff notes). Task 5 is the first commit that adds it to git. The file may already exist on disk under `docs/` — use whatever is there as a starting point and refresh; if it's absent, create it fresh.
- **No push to origin.**
- **Scope fences:**
  - No touching `presentation/onboarding/`, `presentation/inbox/`, `data/local/entity/notification/`, `BottomNavItem.kt`, `CheckInScreen.kt`, `AppRoutes.kt` (previous sub-phases).
  - No renaming existing `MockDataSeeder` methods — only adding `fireTestMicroPrompt`.
  - No new domain logic — this is a mechanical relocation + verification pass.
  - The docs-refresh in Task 5 stays factual: what the app looks like today. No forward-looking language, no roadmap.

---

### Task 0: Create the feature branch and verify pre-phase greens

**Files:** none.

- [ ] **Step 1: Ensure Phase 5c is on main**

Run:
```
git status
git checkout main
git pull --ff-only
git log --oneline -6
```
Expected: Phase 5c's 4 commits sit at the tip of `main`, ending with `chore(profile): rename Tracking Preferences → Tracking per spec §3.1`.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-5d-devtools-cleanup
```

- [ ] **Step 3: Sanity-check the pre-phase greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

No commit for this task.

---

### Task 1: Sweep — catalog dev-only functions on release-shipping code

Grep-sweep for anything else beyond the three known targets. Record findings; do not fix anything yet.

**Files:** none (read-only investigation).

- [ ] **Step 1: Grep the main source set for known dev-only markers**

Run each of these greps and record hits:
```
grep -rn "MockDataSeeder\|isMockDataAvailable\|triggerTestMicroPrompt\|injectMockMoodData\|injectMockActivityData\|ENABLE_MOCK_DATA\|BuildConfig.DEBUG" app/src/main/java/com/moodified/app/presentation
grep -rn "MockDataSeeder\|isMockDataAvailable\|triggerTestMicroPrompt\|injectMockMoodData\|injectMockActivityData" app/src/main/java/com/moodified/app
```
Expected hits (baseline):
- `ProfileScreen.kt` — three `MenuRow` calls that consume `viewModel::triggerTestMicroPrompt`, `viewModel::injectMockMoodData`, `viewModel::injectMockActivityData` (lines 297-330 area, gated by `viewModel.isMockDataAvailable`).
- `ProfileViewModel.kt` — the three `fun` declarations, the `mockDataSeeder` constructor parameter, the `isMockDataAvailable` getter.
- `DebugDevToolsModule.kt` and `ReleaseDevToolsModule.kt` — variant `@Binds` (do not touch these; the interface pattern stays).
- `MockDataSeeder.kt`, `DebugMockDataSeeder.kt` — the interface + debug impl (keep).

If the sweep surfaces any *additional* dev-only method living on a release-shipping composable/ViewModel (i.e., not gated behind `BuildConfig.DEBUG` in a source-set-only way), record it in the task list. For each additional finding, add a Step 1a-N: "move `<method>` from `<file>` to `<debug-only-surface>`" before Task 2 executes. If the sweep finds only the three baseline targets, proceed directly to Task 2 without adding steps.

- [ ] **Step 2: Verify the current release APK still contains the leaked methods (baseline confirmation)**

Run:
```
./gradlew :app:assembleRelease
```
Then extract the DEX and grep for the leaked symbols. Options depend on tooling available:

Option A (`bundletool` if the user has it):
```
bundletool dump manifest --bundle app/build/outputs/bundle/release/app-release.aab || true
```
Option B (simpler — `javap` after unzipping the APK and running `dexdump` or `d8 --dump`):
```
unzip -o app/build/outputs/apk/release/app-release.apk -d /tmp/apk-release
# Then use whatever DEX-decompile tool is on the machine — Android Studio's APK Analyzer is easiest.
```
Option C (fallback — just grep the debug ProGuard mapping if minify is on):
```
find app/build/outputs -name "mapping.txt" | xargs grep -l "triggerTestMicroPrompt" || echo "no mapping match (may be release minified — that's fine)"
```

The purpose of Step 2 is only to confirm the pre-Task-2 state (dev methods exist in the release APK). If the release build already minifies away unused methods (R8/ProGuard with `-keepnames` off), the release APK may already lack the symbols under their original names — that's fine, note it in the commit message and move on. The real "post" verification in Task 4 uses the same tool and expects the same or better result.

**No commit in Task 1.** The plan below assumes the sweep found only the three baseline targets. If it found more, this plan expands to cover them; document each extra target with the same task shape as Task 2.

---

### Task 2: Extend `MockDataSeeder` + move `triggerTestMicroPrompt` implementation

Extend the interface with `fireTestMicroPrompt`. Copy the body from `ProfileViewModel.triggerTestMicroPrompt` (lines 123-185) into `DebugMockDataSeeder`. Release variant no-ops.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/core/devtools/MockDataSeeder.kt` (add method to interface)
- Modify: `app/src/debug/java/com/moodified/app/core/devtools/DebugMockDataSeeder.kt` (add impl — copy from ProfileViewModel body)
- Locate & modify: the release-variant no-op `MockDataSeeder` impl (path unknown until grepped; may be inline in `ReleaseDevToolsModule.kt` or a separate file — grep to find it).
- (No changes to ProfileViewModel yet — Task 3 does the deletion.)

**Interfaces:**
- Produces: `MockDataSeeder.fireTestMicroPrompt(context: Context)` — `suspend fun`.

- [ ] **Step 1: Add the method to the interface**

In `app/src/main/java/com/moodified/app/core/devtools/MockDataSeeder.kt`, extend:
```kotlin
package com.moodified.app.core.devtools

import android.content.Context

interface MockDataSeeder {
    val isAvailable: Boolean

    suspend fun seedMoodData()

    suspend fun seedActivityData()

    suspend fun fireTestMicroPrompt(context: Context)
}
```

- [ ] **Step 2: Read the current `DebugMockDataSeeder`**

Run:
```
cat app/src/debug/java/com/moodified/app/core/devtools/DebugMockDataSeeder.kt
```
Note its class shape, its constructor injection pattern (likely `@Inject`), and how it satisfies `seedMoodData()` / `seedActivityData()` — mirror that shape when adding `fireTestMicroPrompt`.

- [ ] **Step 3: Add `fireTestMicroPrompt` to `DebugMockDataSeeder`**

Copy the body of `ProfileViewModel.triggerTestMicroPrompt` (currently `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt` lines 123-185). Adapt as a suspending method — the current body is not `suspend` but the interface method is; the body doesn't await anything but the enclosing `suspend` is fine. Adjust `context` reference: the source uses an injected `@ApplicationContext context`; if `DebugMockDataSeeder` already has one, use it. Otherwise accept `context` as the method parameter (as the interface declares).

Skeleton (adapt the imports to the file's package):
```kotlin
override suspend fun fireTestMicroPrompt(context: Context) {
    val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel =
            android.app.NotificationChannel(
                "MicroPromptChannel",
                "Gentle Check-ins",
                android.app.NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Context-aware prompts asking how you are feeling."
                setShowBadge(true)
            }
        nm.createNotificationChannel(channel)
    }

    val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE

    val actions =
        com.moodified.app.domain.model.mood.Valence.entries.mapIndexed { index, valence ->
            val intent =
                android.content.Intent(context, com.moodified.app.data.receiver.MicroPromptReceiver::class.java).apply {
                    action = com.moodified.app.data.receiver.MicroPromptReceiver.ACTION_SELECT_VALENCE
                    putExtra(com.moodified.app.data.receiver.MicroPromptReceiver.EXTRA_VALENCE, valence.name)
                }
            val pending = android.app.PendingIntent.getBroadcast(context, index, intent, flags)
            androidx.core.app.NotificationCompat.Action.Builder(
                valence.iconRes(),
                valence.displayLabel(),
                pending,
            ).build()
        }

    val tapIntent =
        android.content.Intent(context, com.moodified.app.MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_VIEW
            data = android.net.Uri.parse("moodified://quicklog")
        }

    val pendingTapIntent =
        android.app.PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    val notification =
        androidx.core.app.NotificationCompat.Builder(context, "MicroPromptChannel")
            .setContentTitle("Moodified is with you")
            .setContentText("You've been resting for a bit. How are you feeling?")
            .setSmallIcon(com.moodified.app.R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingTapIntent)
            .apply { actions.forEach { addAction(it) } }
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .build()

    nm.notify(com.moodified.app.data.receiver.MicroPromptReceiver.PROMPT_NOTIFICATION_ID, notification)
}
```
Clean up the fully-qualified names using proper imports at the top of the file — the above is legible enough for the executor to translate.

**Persistence-hook note:** Phase 5a explicitly excluded the ProfileViewModel dev-fire from writing an inbox record. That exclusion carries over — `DebugMockDataSeeder.fireTestMicroPrompt` MUST NOT call `NotificationHistoryRepository.record(...)`. The comment justifying this (dev affordance, not "the app said this") should be preserved in the moved code as a one-line comment.

- [ ] **Step 4: Add a no-op release impl**

Find the release-variant `MockDataSeeder` implementation. Grep:
```
grep -rn "MockDataSeeder" app/src/release
```
The result is either a class file (e.g., `app/src/release/java/com/moodified/app/core/devtools/NoOpMockDataSeeder.kt`) or an inline anonymous object in `ReleaseDevToolsModule.kt`. Whichever it is, add `override suspend fun fireTestMicroPrompt(context: Context) = Unit` (an empty no-op).

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. The build should fail with a "class must override" error if the release variant is missing the new method — Step 4 must be complete before this step passes.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/core/devtools/MockDataSeeder.kt \
        app/src/debug/java/com/moodified/app/core/devtools/DebugMockDataSeeder.kt \
        app/src/release  # only the file(s) actually modified — narrow if possible

git commit -m "feat(devtools): move fireTestMicroPrompt into MockDataSeeder (debug/release variants)"
```

---

### Task 3: Wire the three actions into `DebugDrawerScreen` + delete Profile "Debug Data" section

Add three action rows to the existing `DebugDrawerScreen` action list. Delete the "Debug Data" section on `ProfileScreen`. Remove `mockDataSeeder` / `isMockDataAvailable` / the three methods from `ProfileViewModel`.

**Files:**
- Modify: `app/src/debug/java/com/moodified/app/presentation/devtools/DebugDrawerScreen.kt` (add three action rows — path may differ, grep to locate)
- Modify: `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt` (delete the "Debug Data" section — currently lines 297-330)
- Modify: `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt` (remove `MockDataSeeder` injection, `isMockDataAvailable` getter, `injectMockMoodData()`, `injectMockActivityData()`, `triggerTestMicroPrompt()`)

**Interfaces:**
- Consumes: `MockDataSeeder` (already available via Hilt injection in the debug source set).
- Produces: `DebugDrawerScreen` gains three actions that consume `MockDataSeeder.seedMoodData()`, `seedActivityData()`, `fireTestMicroPrompt(context)`.

- [ ] **Step 1: Locate `DebugDrawerScreen`**

Run:
```
grep -rn "DebugDrawerScreen" app/src
```
The file lives under `app/src/debug/java/com/moodified/app/presentation/devtools/` (Phase 3). Read the current file to understand its action-list structure. It should already have some diagnostic rows or monitor-navigation links — the three new dev-data actions add to that list.

- [ ] **Step 2: Add the three action rows to `DebugDrawerScreen`**

Inject `MockDataSeeder` into the drawer's ViewModel (if it has one — grep for `DebugDrawerViewModel`; if the screen consumes state directly, use `EntryPointAccessors` or add a ViewModel). Preferred: extend the existing ViewModel; if it doesn't have one, follow the pattern established by the drawer's existing diagnostics injection.

Add three action rows. Example (adapt to the drawer's existing row-composable):
```kotlin
DrawerActionRow(
    title = "Seed mock mood data",
    subtitle = "Insert 14 days of synthetic mood entries",
    onClick = { viewModel.seedMoodData() },
)
DrawerActionRow(
    title = "Seed mock activity data",
    subtitle = "Insert 14 days of synthetic activity summaries",
    onClick = { viewModel.seedActivityData() },
)
DrawerActionRow(
    title = "Fire test micro-prompt",
    subtitle = "Manually trigger a check-in notification",
    onClick = { viewModel.fireTestMicroPrompt() },
)
```
The corresponding ViewModel methods each delegate to `mockDataSeeder.<method>()` within `viewModelScope.launch { ... }`. `fireTestMicroPrompt` needs a `Context` — pass it via `@ApplicationContext` injection into the ViewModel (matches `ProfileViewModel`'s existing pattern).

- [ ] **Step 3: Delete Profile "Debug Data" section**

In `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt`, delete lines 297-330 in their entirety (the whole `if (viewModel.isMockDataAvailable) { item { ... } }` block).

- [ ] **Step 4: Delete methods + injection from `ProfileViewModel.kt`**

In `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt`:

4a. Delete lines 115-185 (the three functions: `injectMockMoodData`, `injectMockActivityData`, `triggerTestMicroPrompt`).

4b. Delete the `isMockDataAvailable` getter (line 56):
```kotlin
val isMockDataAvailable: Boolean get() = mockDataSeeder.isAvailable
```

4c. Delete the `mockDataSeeder` constructor parameter (line 53):
```kotlin
        private val mockDataSeeder: MockDataSeeder,
```

4d. Delete the `MockDataSeeder` import (currently line 15).

4e. Verify no other file references `ProfileViewModel.isMockDataAvailable` or the three deleted methods:
```
grep -rn "isMockDataAvailable\|triggerTestMicroPrompt\|injectMockMoodData\|injectMockActivityData" app/src/main
```
Expected: no hits.

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/debug/java/com/moodified/app/presentation/devtools/ \
        app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt \
        app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt

git commit -m "refactor(devtools): move Debug Data actions from Profile to DebugDrawer"
```

---

### Task 4: Verify release APK is clean

Build a release APK and confirm the moved method names are absent from its DEX.

**Files:** none.

- [ ] **Step 1: Build release**

Run:
```
./gradlew clean :app:assembleRelease
```
Expected: build succeeds.

- [ ] **Step 2: Extract and grep the release DEX**

The exact grep tool depends on the environment. Try in order until one works:

Option A (`javap` on the DEX after decompression):
```
mkdir -p /tmp/moodified-release-apk
unzip -o app/build/outputs/apk/release/app-release.apk -d /tmp/moodified-release-apk
# Then use Android Studio's APK Analyzer or `dexdump` on classes.dex:
dexdump -f /tmp/moodified-release-apk/classes*.dex 2>/dev/null | grep -E "triggerTestMicroPrompt|injectMockMoodData|injectMockActivityData" || echo "CLEAN"
```

Option B (`strings` — coarse but fast):
```
strings /tmp/moodified-release-apk/classes*.dex | grep -E "triggerTestMicroPrompt|injectMockMoodData|injectMockActivityData" || echo "CLEAN"
```
Expected: "CLEAN" printed. If minification (R8) is enabled in release, the symbols may be renamed — grep will not find them under their original names, and that's the correct outcome.

If minification is NOT enabled and the symbols DO appear in the release DEX, that means the removal didn't take effect (or a residual caller exists on the main source set). Grep the main source set again:
```
grep -rn "triggerTestMicroPrompt\|injectMockMoodData\|injectMockActivityData" app/src/main
```
Any hit here is a bug — fix by finding the caller and removing it.

- [ ] **Step 3: Record findings in a task-note commit (optional)**

If minification is off and the DEX is clean, this task doesn't produce a commit — the verification is diagnostic. If minification is on and the symbols would be renamed anyway, still no commit needed; the removal is proven by the compile-succeeds gate in Task 3.

No commit in Task 4.

---

### Task 5: Refresh `docs/application-overview.md`

Update the overview doc to reflect the post-consolidation state. Small, factual, bounded.

**Files:**
- Create-or-modify: `docs/application-overview.md` (currently exists as untracked file per Phase 4 kickoff — use it as a starting point).

- [ ] **Step 1: Read the current state**

Run:
```
ls docs/application-overview.md 2>/dev/null && cat docs/application-overview.md || echo "file absent — create fresh"
```
Working from whatever is there. If the file is absent, create a fresh short overview (~150-300 words).

- [ ] **Step 2: Update / write the overview**

The refreshed content covers, in this order:
1. **One-paragraph purpose** — Moodified helps users notice their mood, its context, and long-term patterns. Portfolio-quality Android app, local-only, no cloud.
2. **Bottom nav** — Check-in / Calendar / Quick Log (FAB) / Insight / Profile. Care is a full-screen route reachable from the Today's Care card on Check-in.
3. **Insight** — one screen, four tabs (Overview, Activity, Sleep, Screen Use) backed by a single ViewModel, per Phase 2.
4. **Notifications inbox** — accessible from Profile → Notifications, from the `moodified://inbox` deep link, and via notification taps.
5. **Onboarding** — first-run only, 4 slides, DataStore-flagged.
6. **Support** — Help / About / Feedback / Licenses under Profile → Support.
7. **Debug drawer** — debug builds only, reachable via long-press on the About-screen version label.
8. **Data model** — Room, current schema version 15. Migration tests cover v13→v14 and v14→v15.
9. **Tech stack line** — Kotlin, Compose, Hilt, Room, Coroutines/Flow. AGP / Kotlin / Compose BOM versions per `libs.versions.toml`.

Skip any language about "planned", "future", "cloud sync", "Firebase", "Stripe" — those are out of scope.

- [ ] **Step 3: Verify all greens (docs-only change; smoke-check the greens anyway)**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. A docs-only edit shouldn't touch these, but confirming avoids surprise regressions.

- [ ] **Step 4: Commit**

Run:
```
git add docs/application-overview.md

git commit -m "docs: refresh application-overview for post-consolidation state"
```

---

### Task 6: Final full-greens gate + phase-shape verification

- [ ] **Step 1: Clean and re-run all gates**

Run:
```
./gradlew clean
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 2: Verify branch shape**

Run:
```
git log main..phase-5d-devtools-cleanup --oneline
git log main..phase-5d-devtools-cleanup --format="%an <%ae>" | sort -u
```
Expected: 3-4 commits (Tasks 2, 3, 5 produce commits; Tasks 1 and 4 don't). No `Co-Authored-By: Claude` trailers. Only Mikhael Edman Gomez as author.

- [ ] **Step 3: End-of-consolidation summary**

At this point, Phases 5a + 5b + 5c + 5d have all landed. The only remaining consolidation item is the manual smoke test per spec §5, executed on a physical device by the user (deferred per `feedback_smoke_test_timing.md`). Print a summary line:
```
echo "Phase 5d complete. Consolidation phases 1-5 done. Awaiting user's end-of-consolidation smoke test."
```

No commit in Task 6.

---

## Follow-ups (out of scope for Phase 5d)

- **Manual smoke test** — user-executed, deferred to after 5d merges (see memory `feedback_smoke_test_timing.md`).
- **`CareViewModel` refactor to consume `ObserveTopCareInterventionUseCase`** — carried forward from Phase 5c's follow-up list.
- **Additional dev-only leaks discovered in Task 1's sweep** — if any surface, they were addressed by expanded Task 2/3 steps; if any were deferred, list them here explicitly.

## Merge steps (executed by the human, not by the plan)

After all reviews clear for 5d AND the full 5a→5b→5c→5d chain is ready for a single push:
```
git checkout main
git merge --ff-only phase-5d-devtools-cleanup
git push origin main
```
The `git push` is the ONLY push in Phase 5; it goes at the end after every sub-phase has merged into local main.
