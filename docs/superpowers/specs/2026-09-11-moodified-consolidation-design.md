# Moodified Consolidation & Missing-Sections Design

> Status: Proposal — approved through brainstorming, pending user spec review.
> Scope: Moodified Android app (Kotlin / Compose / Hilt / Room). On-device only.
> Distribution target: portfolio-quality demo (not Play Store submission this round).
> Author: engineering.
> Last updated: 2026-09-11.

---

## 1. Goals and non-goals

### Goals
- Add three standard app sections that are currently missing: **Onboarding & first-run**, **Notifications inbox**, and **Help / About / Feedback**.
- Reorganize the top-level navigation so the app reads like a shipped product rather than a scaffold: promote Calendar, collapse the four Insight surfaces into one, rename `More` → `Profile` with grouped sections.
- Complete the half-finished migration of ex-monitor / dev-facing tracker screens: strip debug lineage from user-facing components, restore a clean debug-only surface behind a hidden gesture, gate by `BuildConfig.DEBUG`.
- Land ship-ready polish concurrently: remove the destructive-migration fallback, add unit tests for the two engines, add a Room migration test, add ktlint + detekt, purge Android imports from `domain/`.
- Every phase merges independently. The app builds and runs after every phase.

### Non-goals
- Firebase, Firestore sync, Firebase Auth. Deferred to the cloud plan in `docs/firebase-firestore-and-stripe-integration-plan.md`.
- Stripe, subscriptions, Care Packs, monetization. Same deferral.
- Play Store submission, store listing artifacts, privacy manifest work.
- Full localization / mass string extraction to `strings.xml`. Called out as follow-up.
- Repository-scope injection refactor. Called out as follow-up.
- Compose UI / snapshot tests. Cost/benefit doesn't justify for this round.

---

## 2. Current state summary

Verified against the repository on 2026-09-11.

- 134 Kotlin source files, ~15.4k LOC in `app/src/main`.
- `presentation/insight/InsightScreen.kt` is 1265 LOC; the three domain drill-downs (`activity/`, `sleep/`, `screenuse/`) are ~200 LOC each and largely duplicate each other's layout.
- `presentation/devtools/MonitorSharedComponents.kt` (447 LOC) is imported by three **production** insight screens (`ActivityInsightScreen`, `SleepInsightScreen`, `ScreenUseInsightScreen`), the `MoodifiedNavHost`, `MoreViewModel`, and `MidnightTickerFlow` utility. Its `MonitorHeader` has `eyebrow: String = "DEV TOOLS"` as a default parameter.
- `DebugEntryPoints` is gone (removed in commit `3ae3878`). There is no `BuildConfig.DEBUG` gating anywhere in `main` — whatever was dev-only is currently shipping in release.
- `di/DatabaseModule.kt:53` calls `fallbackToDestructiveMigration()` despite eight explicit migrations from v6 → v14 being registered.
- `domain/usecase/privacy/ExportUserDataUseCase.kt` imports `android.content.Context`, `android.provider`, `android.os` — violates clean-architecture layering.
- Test coverage: two stub files (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`). Zero coverage on `RuleBasedMoodInferenceEngine`, `CareEvaluationEngine`, repositories, migrations.
- No static analysis: no ktlint, no detekt.
- `androidx.work:work-runtime-ktx:2.9.0` is hardcoded in `app/build.gradle.kts:164` outside the version catalog.

The docs (`docs/application-overview.md`) reference `DebugEntryPoints` and describe the four insight screens as separate destinations — they are stale relative to the recent monitor→insight promotion.

---

## 3. Target design

### 3.1 Navigation

**Bottom navigation after reorg:**

```
┌──────────┬──────────┬──────────────┬──────────┬──────────┐
│ Check In │ Calendar │ [Quick Log]  │ Insight  │ Profile  │
└──────────┴──────────┴──────────────┴──────────┴──────────┘
```

- **Check In** — unchanged as home. Adds a single "Today's Care" card that surfaces one top-ranked Care intervention inline.
- **Calendar** — promoted from behind Check In to a peer tab. Serves as the history surface. Default view remains the month grid; adds a list-view toggle for chronological browsing.
- **Quick Log** — unchanged. Center FAB, deep-linkable via `moodified://quicklog`.
- **Insight** — one screen, four tabs (see §3.3).
- **Profile** — renamed from `More`. Sections grouped as **You / Tracking / Notifications / Support / Data & Privacy**.

**Care as a destination is retired.** The swipeable stack moves to a full-screen `care/all` route reachable from the "Today's Care" card on Check In. No functionality is lost; a bottom-nav slot is freed for Calendar.

**Full-screen routes (bottom bar hidden):**
- `onboarding/` (first-run only)
- `care/all`
- `notifications/inbox`
- `support/help`, `support/help/{articleId}`, `support/about`, `support/licenses`
- `privacy/`, `privacy/export`

**Debug-only routes** (registered only when `BuildConfig.DEBUG == true`):
- `debug/drawer`
- `debug/monitors/activity`, `debug/monitors/sleep`, `debug/monitors/interaction`

**Deep links:**
- `moodified://quicklog` — preserved
- `moodified://inbox` — new; opens `notifications/inbox`

**Route redirects (for one release cycle):**
- Old `insight/activity`, `insight/sleep`, `insight/screenuse` redirect to `insight?tab=<domain>`. Delete after the migration lands.

**Files touched:**
- `presentation/navigation/MoodifiedNavHost.kt`
- `core/navigation/BottomNavItem.kt`
- `core/navigation/AppRoutes.kt`
- New: `core/navigation/DebugRoutes.kt`

---

### 3.2 New sections

#### 3.2.1 Onboarding & first-run

4 lightweight slides:

1. **Welcome** — one-sentence positioning + Lottie.
2. **Two-tap logging** — animated preview of the QuickLog sheet.
3. **Passive context** — explains why we ask for Activity Recognition, Notifications, Usage Access; primes each ahead of the OS dialog.
4. **You're set** — CTA into Check In.

**Data:** one `Boolean` DataStore key, `hasCompletedOnboarding`. Set true on the "You're set" tap.

**Routing:** `MainActivity` reads the flag at startup. If false, `MoodifiedNavHost.startDestination` is `onboarding/`; otherwise `checkin/` as today. Bottom bar hidden throughout.

**Permission priming:** each priming card explains the benefit → user taps "Enable" → we then launch the real system permission request. "Skip" is always available on every card. Denial never blocks the app.

**Files:**
- `presentation/onboarding/OnboardingScreen.kt`
- `presentation/onboarding/OnboardingViewModel.kt`
- `presentation/onboarding/OnboardingSlide.kt`
- New DataStore key alongside existing preferences in `data/local/datasource/`

#### 3.2.2 Notifications inbox

**Purpose:** in-app history of micro-prompts, care reminders, trend alerts, and tracking-status notifications so users can review what the app said and why.

**Room schema — migration v14 → v15:**

```
NotificationRecordEntity(
  id: Long PK autoGenerate,
  type: String,                 // enum-backed
  title: String,
  body: String,
  deepLink: String?,
  deliveredAt: Long,
  readAt: Long?,
  dismissedAt: Long?
)
```

Type is a Kotlin enum backed by a String column: `MICRO_PROMPT | CARE_REMINDER | TREND_ALERT | TRACKING_STATUS`.

**DAO:** `NotificationRecordDao`
- `fun observeAll(): Flow<List<NotificationRecordEntity>>`
- `fun observeUnreadCount(): Flow<Int>`
- `suspend fun markRead(id: Long)`
- `suspend fun markAllRead()`
- `suspend fun dismiss(id: Long)`
- `suspend fun deleteOlderThan(cutoffMillis: Long): Int`

**Repository:** `NotificationHistoryRepository` interface in `domain/repository/`, implementation in `data/repository/`. Bound in `RepositoryModule`.

**Persistence hook:** every code path that currently calls `NotificationManagerCompat.notify(...)` writes a record via the repository *first*, then posts. Two current sites: `MicroPromptReceiver`, care/tracking notification code in `TrackingService` (and any future Trend-alert path).

**UI:** full-screen route `notifications/inbox`. Chronological list grouped by day. Unread count badge on the Profile → Notifications row (Flow-driven). Tap opens `deepLink` via nav controller. Swipe-to-dismiss. Top-bar action "Clear all".

**Retention:** `PurgeWorker` gains a step to delete inbox records older than 90 days (matching the telemetry purge window).

**Files:**
- `data/local/entity/NotificationRecordEntity.kt`
- `data/local/dao/notification/NotificationRecordDao.kt`
- `data/repository/NotificationHistoryRepositoryImpl.kt`
- `domain/repository/NotificationHistoryRepository.kt`
- `presentation/inbox/NotificationsInboxScreen.kt`
- `presentation/inbox/NotificationsInboxViewModel.kt`
- New migration `MoodifiedDatabase.MIGRATION_14_15`
- `data/receiver/MicroPromptReceiver.kt` — add DAO write
- `core/service/TrackingService.kt` — add DAO write around care/status notifications
- `core/worker/PurgeWorker.kt` — add inbox purge step

#### 3.2.3 Help / About / Feedback

**Help.** Local FAQ, no live help center. ~8 canned entries as a `List<HelpArticle>` under `presentation/support/data/HelpArticles.kt`. Each article: `id`, `title`, `body` (backed by `stringResource` for localization later). Rendered as expandable list with a search field.

**About.** Static screen showing:
- App name + `versionName` + `buildType` from `BuildConfig`
- One-paragraph mission (mirrors the `application-overview.md` intro)
- "Made with" credits (Compose, Room, Hilt, Lottie, Material 3)
- Open-source licenses via `AboutLibraries` or a hand-rolled list (decide during implementation based on dependency footprint)
- Version label carries the hidden gesture from §3.4

**Feedback.** `ACTION_SENDTO` email intent, pre-filled:
- `to` = value from `BuildConfig.FEEDBACK_EMAIL` (new `buildConfigField`)
- `subject` = `"Moodified v${versionName} feedback"`
- `body` = optional device-info block (Android version, device model, build type), gated by a checkbox in the feedback sheet; disabled by default
- Fallback: Snackbar "No email app installed" if `resolveActivity(...)` returns null

No backend, no analytics, no PII collected server-side (device info stays inside the email the user chooses to send).

**Files:**
- `presentation/support/HelpScreen.kt`
- `presentation/support/HelpViewModel.kt`
- `presentation/support/AboutScreen.kt`
- `presentation/support/FeedbackSheet.kt`
- `presentation/support/LicensesScreen.kt`
- `presentation/support/data/HelpArticles.kt`
- `app/build.gradle.kts` — add `buildConfigField "String", "FEEDBACK_EMAIL", "\"...\""`

---

### 3.3 Insight consolidation

**Target structure:**

```
presentation/insight/
  InsightScreen.kt              — shell: TabRow + content switcher (< 200 LOC target)
  InsightViewModel.kt           — one UiState containing sub-states per tab
  tabs/
    OverviewTab.kt              — weekly distribution + completeness
    ActivityTab.kt              — steps / intensity / active minutes
    SleepTab.kt                 — duration / efficiency / consistency
    ScreenUseTab.kt             — screen time / unlocks / late-night
  components/
    InsightDomainTemplate.kt    — shared { header, status, stats, breakdown } scaffold
    WeeklyBarChart.kt           — pulled from MonitorSharedComponents
    StatusBadge.kt              — pulled from MonitorSharedComponents
    StatTile.kt
    BreakdownRow.kt
```

`SecondaryTabRow` at the top. Tab state persisted via `rememberSaveable`. Optional `tab` query param on the route so notifications / deep-links can open a specific tab.

**ViewModel:** single `InsightViewModel` exposing `data class InsightUiState(overview, activity, sleep, screenUse, selectedTab)`. Each sub-state is its own `Flow` from the relevant repository, combined via `combine(...)`. Tabs are pure `@Composable`s consuming slices; they never own their own ViewModels.

**Redirects:** for one release, `insight/activity`, `insight/sleep`, `insight/screenuse` register a redirect composable that immediately `navController.navigate("insight?tab=<domain>") { popUpTo(...) { inclusive = true } }`. Delete after this consolidation ships.

**Byproducts / deletions:**
- `presentation/insight/activity/ActivityInsightScreen.kt` — deleted
- `presentation/insight/sleep/SleepInsightScreen.kt` — deleted
- `presentation/insight/screenuse/ScreenUseInsightScreen.kt` — deleted
- `presentation/insight/InsightScreen.kt` — rewritten to shell (~150 LOC target)

---

### 3.4 Dev-tools restructure

Two independent moves.

**Move 1 — extract shared components out of `devtools/`.**

`presentation/devtools/MonitorSharedComponents.kt` is split by concern:

- **User-facing atoms** → `presentation/insight/components/` (per §3.3): `WeeklyBarChart`, `StatusBadge`, `StatTile`, `BreakdownRow`, `consistencyColor(...)` helper, and a header component without the `eyebrow` default parameter.
- **Diagnostic atoms** (live pulse indicator, raw counter tile) → `presentation/devtools/components/`. Debug-only, only referenced from debug-only screens.

The current `eyebrow: String = "DEV TOOLS"` default is deleted. User-facing headers no longer have a debug-y fallback.

**Move 2 — restore `devtools/` as a debug-only surface.**

- Raw monitor screens rebuilt under `presentation/devtools/monitors/{Activity,Sleep,Interaction}MonitorScreen.kt`. Content: the pre-migration raw views, using only diagnostic atoms.
- `presentation/devtools/DebugDrawerScreen.kt` — a single drawer with:
  - Links to each monitor
  - Diagnostics block: DB row counts per entity, `WorkManager` last-run timestamps for each worker, current permission grants, current tracker states
- `core/navigation/DebugRoutes.kt` — route constants.
- `MoodifiedNavHost.kt` conditionally registers routes:

```kotlin
if (BuildConfig.DEBUG) {
    composable(DebugRoutes.DRAWER) { DebugDrawerScreen(...) }
    composable(DebugRoutes.MONITOR_ACTIVITY) { ActivityMonitorScreen(...) }
    composable(DebugRoutes.MONITOR_SLEEP)    { SleepMonitorScreen(...) }
    composable(DebugRoutes.MONITOR_INTERACTION) { InteractionMonitorScreen(...) }
}
```

**Hidden entry point:** on `AboutScreen`, the version label uses `combinedClickable` with an `onLongClick` that navigates to `DebugRoutes.DRAWER` **only when `BuildConfig.DEBUG` is true**. In release the callback is a no-op and the routes don't exist in the graph. Zero release surface.

**Files:**
- `presentation/navigation/MoodifiedNavHost.kt`
- `core/navigation/DebugRoutes.kt` (new)
- `presentation/devtools/monitors/*.kt` (new — 3 screens)
- `presentation/devtools/DebugDrawerScreen.kt` (new)
- `presentation/devtools/components/*.kt` (new — diagnostic atoms)
- `presentation/devtools/MonitorSharedComponents.kt` — deleted
- Three insight domain screens deleted (per §3.3)

---

### 3.5 Polish workstream

Runs alongside sections 3.1–3.4. Every item is small and independently mergeable.

**Safety fixes (Phase 1, trivial):**
- `di/DatabaseModule.kt:53` — remove `fallbackToDestructiveMigration()`. Keep the eight explicit migrations. If a schema mismatch ever occurs the app should crash loudly rather than silently wipe user data.
- `domain/usecase/privacy/ExportUserDataUseCase.kt` — Android imports removed. Introduce `UserDataExporter` interface in `domain/`; implementation moves to `data/exporter/UserDataExporterImpl.kt` and is bound in a new `ExporterModule` (or extends `RepositoryModule`).

**Test scaffolding:**
- Delete `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.
- Unit tests for `RuleBasedMoodInferenceEngine`:
  - Manual entries always outrank inference.
  - Sleep-rule branches (good / poor / missing).
  - Activity-rule branches (high / sedentary / vigorous-exercise decay).
  - Digital-fatigue rule.
  - Completeness < 30% → fallback state with `isFallback = true`.
  - Confidence scoring within `[0, 100]` bounds.
  - Explainability string is non-empty for every emitted state.
- Unit tests for `CareEvaluationEngine`:
  - Ranking order per state.
  - Dedup against `InterventionHistory`.
  - Category selection per inferred state.
- Room migration test: v13 → v14 round-trip on a seeded DB. Serves as the template for future migration tests (including the new v14 → v15).

**Tooling:**
- Add `ktlint` Gradle plugin.
- Add `detekt` with `config/detekt/detekt.yml` and a `detekt-baseline.xml` so existing violations don't block the build; new violations do.
- Move `androidx.work:work-runtime-ktx:2.9.0` from `app/build.gradle.kts:164` into `libs.versions.toml`.
- Add `buildConfigField "String", "FEEDBACK_EMAIL", "\"...\""` (feeds §3.2.3).

**Consolidations (small):**
- `core/utils/DateTimeUtils.kt` gains `dayFormatter(locale)` / `monthDayFormatter(locale)`. Replace the ~3 duplicate `DateTimeFormatter.ofPattern("EEE")` sites in `presentation/`.
- Extract `RequirePermission(permission, rationale) { granted -> ... }` composable in `presentation/components/permission/`. Replace the duplicated `rememberLauncherForActivityResult + checkSelfPermission` blocks in Check In / Insight, and use it in Onboarding.

**Documented follow-ups (not this round):**
- Repository CoroutineScope injection (three repos duplicate `CoroutineScope(SupervisorJob() + Dispatchers.IO)`).
- Mass string extraction to `strings.xml` for localization.
- Additional Room migration tests beyond v13 → v14.
- Compose UI / snapshot tests.
- Refactor documentation (`application-overview.md`) to reflect the post-consolidation state.

---

## 4. Data & schema changes

Only one schema change this round.

**Migration v14 → v15 — add `notification_records` table:**

```sql
CREATE TABLE IF NOT EXISTS notification_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  deepLink TEXT,
  deliveredAt INTEGER NOT NULL,
  readAt INTEGER,
  dismissedAt INTEGER
);
CREATE INDEX IF NOT EXISTS index_notification_records_deliveredAt
  ON notification_records(deliveredAt);
```

Exported schema written under `app/schemas/com.moodified.app.data.local.database.MoodifiedDatabase/15.json`. Migration registered in `di/DatabaseModule.kt` alongside the existing eight.

New DataStore key:
- `hasCompletedOnboarding: Boolean` (default `false`).

---

## 5. Testing strategy

**Unit (JVM, no Android):**
- `RuleBasedMoodInferenceEngineTest` — see §3.5 for cases.
- `CareEvaluationEngineTest` — see §3.5.
- `NotificationHistoryRepositoryImplTest` — insertion, unread counting, purge (uses in-memory Room via `Robolectric` or a lightweight fake DAO).

**Instrumented (Android):**
- `Migration13To14Test` — seed v13 DB, run migration, assert schema + data survives. Ships in Phase 1 as the template.
- `Migration14To15Test` — seed v14 DB, run migration, insert a `NotificationRecordEntity`, assert index and column shape. Ships in Phase 5 alongside the migration itself.

**Manual test plan for portfolio demo:**
1. Fresh install → onboarding flow → grant / skip each permission → land on Check In.
2. Quick Log → save → confirm animation → back to Check In.
3. Check In → tap Calendar tab → back to grid → tap a past date → return.
4. Insight tab → switch between all four tabs → back.
5. Trigger a micro-prompt (debug drawer action) → open inbox → mark read → deep-link into Quick Log.
6. Profile → Support → Help (search) → About → long-press version label (debug build only) → debug drawer opens → back to About. In release build, verify long-press does nothing.
7. Profile → Data & Privacy → Delete data → confirm → fresh state.
8. Force-close mid-tracking → reopen → confirm tracking resumes and no data loss.

---

## 6. Rollout phases

Each phase merges independently. The app builds and runs after every phase.

### Phase 1 — Safety net & foundation (2-3 days)
1. Remove `fallbackToDestructiveMigration()` in `DatabaseModule.kt`.
2. Move `ExportUserDataUseCase` Android imports to a `data/` implementation behind a `UserDataExporter` domain interface.
3. Add ktlint + detekt with baseline.
4. Unit tests for `RuleBasedMoodInferenceEngine` + `CareEvaluationEngine`.
5. Migration test for v13 → v14.
6. Delete `ExampleUnitTest.kt` + `ExampleInstrumentedTest.kt`.
7. Move WorkManager version into `libs.versions.toml`.

### Phase 2 — Insight consolidation (3-4 days)
1. Extract shared components out of `presentation/devtools/MonitorSharedComponents.kt` into `presentation/insight/components/`. Delete the `"DEV TOOLS"` eyebrow default in the process.
2. Build `InsightDomainTemplate`.
3. Collapse `InsightScreen` + three domain screens into single screen with `SecondaryTabRow`; four tabs backed by a single `InsightViewModel`.
4. Register redirect routes from old paths.
5. Delete duplicated code.

### Phase 3 — Dev-tools restoration (1-2 days)
1. Rebuild raw monitor screens under `presentation/devtools/monitors/`, using diagnostic atoms only.
2. `DebugDrawerScreen` with diagnostics block.
3. `BuildConfig.DEBUG`-gated route registration in `MoodifiedNavHost`.
4. `DebugRoutes.kt` route constants.

*Note: the hidden About-screen entry point ships with Phase 4 when About is built.*

### Phase 4 — Support surface + Profile rename (2-3 days)
1. `HelpScreen` + local FAQ dataset.
2. `AboutScreen` + long-press debug gesture (`BuildConfig.DEBUG` guarded).
3. `FeedbackSheet` + `FEEDBACK_EMAIL` `buildConfigField`.
4. `LicensesScreen`.
5. `More` → `Profile` rename + section grouping (You / Tracking / Notifications / Support / Data & Privacy).

### Phase 5 — Onboarding + Inbox + bottom-nav reorg (4-5 days)
1. Room v14 → v15 migration + `NotificationRecordEntity` + DAO + repository.
2. Persistence hook in `MicroPromptReceiver` and `TrackingService` notification code.
3. `NotificationsInboxScreen` + ViewModel + deep-link `moodified://inbox`.
4. `PurgeWorker` addition for inbox retention (90 days).
5. `OnboardingScreen` + 4 slides + permission priming.
6. `hasCompletedOnboarding` DataStore key + `MainActivity` startDestination routing.
7. Bottom-nav reorg: swap Care ↔ Calendar, add "Today's Care" card on Check In, move all-Care to `care/all` full-screen route.

**Total:** 12–17 working days end-to-end.

---

## 7. Definition of done

- Zero references to `presentation/devtools/` from user-facing packages in release builds.
- No `presentation/devtools/*` files present in the release APK (verified via `bundletool dump` or manual APK inspection).
- `presentation/insight/InsightScreen.kt` < 200 LOC.
- ktlint + detekt both green (with baseline; no *new* violations).
- `RuleBasedMoodInferenceEngine` and `CareEvaluationEngine` have unit tests covering every branch listed in §3.5.
- `di/DatabaseModule.kt` no longer calls `fallbackToDestructiveMigration()`.
- `domain/` contains zero `android.*` imports (`grep -r "^import android\." app/src/main/java/com/moodified/app/domain` returns empty).
- Onboarding runs on first install; Notifications inbox, Help, About, and Feedback are all reachable through Profile (Notifications inbox additionally reachable via `moodified://inbox` deep link and notification taps).
- Profile section grouped as spec'd, `More` name gone.
- Manual test plan in §5 passes end-to-end on a fresh install.
- Docs `application-overview.md` updated to reflect the post-consolidation state (or explicitly flagged as follow-up in `refactor-and-production-readiness-plan.md`).

---

## 8. Open questions / follow-ups

Answered during brainstorming:
- **Cloud in scope?** No — deferred to the Firebase/Stripe plan.
- **Play Store submission?** No — portfolio-quality target.
- **Auth?** Out of scope this round.

Deferred but flagged:
- Confirm `BuildConfig.FEEDBACK_EMAIL` value at implementation time (or leave a placeholder that the user fills before merging Phase 4).
- Decide between `AboutLibraries` dependency vs. hand-rolled licenses list during Phase 4 implementation, based on transitive dep footprint.
- Decide onboarding permission priming order at implementation time (Notifications is API 33+ runtime; Activity Recognition is API 29+; Usage Access is a special access screen).

Follow-ups explicitly out of scope this round:
- Repository CoroutineScope injection.
- Mass string extraction to `strings.xml`.
- Additional Room migration tests beyond v13 → v14 (v14 → v15 test lands with the migration).
- Compose UI / snapshot tests.
- Refactor `application-overview.md` and `refactor-and-production-readiness-plan.md` for post-consolidation state.

---

*This spec supersedes any Insight / dev-tools organization described in `docs/application-overview.md`. The overview will be updated after the consolidation ships.*
