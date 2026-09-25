# Phase 2 — Insight Consolidation Implementation Plan

**Goal:** Collapse the 1469 LOC `InsightScreen` and three ~200 LOC domain drill-down screens into a single tabbed Insight surface backed by one `InsightViewModel`, extract the shared UI atoms out of `presentation/devtools/MonitorSharedComponents.kt` (removing the `"DEV TOOLS"` eyebrow default), and add legacy-route redirects for one release cycle.

**Architecture:**
- One tab-hosting Insight screen (`InsightScreen.kt` shell < 200 LOC) with four `@Composable` tab bodies (`OverviewTab`, `ActivityTab`, `SleepTab`, `ScreenUseTab`) rendered inside a `SecondaryTabRow`.
- Single `InsightViewModel` continues to expose one `StateFlow<InsightUiState>` unchanged. Tab UI state lives in the `InsightScreen` shell via `rememberSaveable(initialTab) { mutableStateOf(initialTab) }` (spec §3.3). Domain state (charts, trends, timelines) stays flat because the existing `combine(...)` already produces all the data — restructuring into nested sub-states adds churn without value here.
- User-facing UI atoms move to `presentation/insight/components/` under new (non-`Monitor`-prefixed) names. Charts specific to a domain stay inline in their tab file. `presentation/devtools/MonitorSharedComponents.kt` is deleted at the end of Phase 2; Phase 3 will rebuild any diagnostic atoms it needs from scratch.
- Legacy routes `insight/activity`, `insight/sleep`, `insight/screen_use` become tiny redirect composables that immediately navigate to `insight?tab=<domain>` and pop themselves off the back stack. Deleted after one release cycle (tracked in Phase 3+ follow-up).

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Navigation-Compose, kotlinx.coroutines Flow, JUnit4.

**Spec:** [docs/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.3 (Insight consolidation), §3.4 Move 1 (extract shared components), §6 Phase 2.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug` passes after every commit.
- **No Compose UI tests, no snapshot tests** — non-goal in spec §1. Composable correctness relies on compile + manual verification.
- `ktlint` and `detekt` (added in Phase 1, baselined) must remain green — no *new* violations.
- `presentation/insight/InsightScreen.kt` final LOC target: **< 200 LOC** (spec §7 Definition of Done).
- Rename convention when moving atoms out of `presentation/devtools/`: drop the `Monitor` prefix (`MonitorCard` → `InsightCard`, `MonitorStatTile` → `StatTile`, `MonitorWeeklyBars` → `WeeklyBarChart`, `MonitorHeader` → `InsightHeader`). `SectionLabel`, `BreakdownRow`, `StatusBadge`, `ConsistencyScoreSection`, `IdleBanner`, `PermissionDeniedCard`, `WeeklyBarEntry`, `consistencyColor` keep their names.
- New `InsightHeader` has **no default** for `eyebrow` — signature is `eyebrow: String? = null`. The old `"DEV TOOLS"` string is deleted, not moved.
- New enum: `com.moodified.app.presentation.insight.InsightTab { OVERVIEW, ACTIVITY, SLEEP, SCREEN_USE }`.
- Route query param: `insight?tab=overview|activity|sleep|screen_use`. Unknown or missing value → `OVERVIEW`.
- Deep-link URI scheme unchanged (`moodified://quicklog` etc. are out of scope for this phase; the `moodified://inbox` link is Phase 5).
- Feature branch: `phase-2-insight-consolidation` (already checked out).

---

### Task 1: Extract user-facing atoms into `presentation/insight/components/`

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightAtoms.kt` — all extracted atoms in one file for now; can split later if it grows past ~400 LOC.
- Test: `app/src/test/java/com/moodified/app/presentation/insight/components/ConsistencyColorTest.kt`
- Read (source of truth): `app/src/main/java/com/moodified/app/presentation/devtools/MonitorSharedComponents.kt` — copy from here, don't move yet (domain screens still import it).
- No edits to existing files yet — old `MonitorSharedComponents.kt` stays intact until Task 9.

**Interfaces:**
- Consumes: nothing from other Phase 2 tasks.
- Produces (all in package `com.moodified.app.presentation.insight.components`):
  - `data class WeeklyBarEntry(val label: String, val value: Int, val isToday: Boolean)`
  - `fun consistencyColor(score: Int): androidx.compose.ui.graphics.Color`
  - `@Composable fun InsightHeader(title: String, subtitle: String, isTracking: Boolean, liveIndicatorColor: Color = ValenceNeutral, eyebrow: String? = null, onBack: (() -> Unit)? = null)` — the back-button icon-button is rendered only when `onBack != null`.
  - `@Composable fun InsightCard(modifier: Modifier = Modifier, verticalSpacing: Int = 16, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun InsightCardEmpty(message: String, modifier: Modifier = Modifier)`
  - `@Composable fun SectionLabel(title: String)`
  - `@Composable fun StatTile(modifier: Modifier = Modifier, label: String, value: Any, subLabel: String, accentColor: Color)`
  - `@Composable fun BreakdownRow(label: String, value: String, note: String)`
  - `@Composable fun StatusBadge(text: String, color: Color)`
  - `@Composable fun WeeklyBarChart(entries: List<WeeklyBarEntry>, maxBarHeight: Int = 64)`
  - `@Composable fun ConsistencyScoreSection(score: Int, sublabel: String = "Duration variance score")`
  - `@Composable fun IdleBanner(text: String = "Tap Start Tracking to begin monitoring signals.")`
  - `@Composable fun PermissionDeniedCard(title: String = "Permission Required", body: String, canAskAgain: Boolean, onOpenSettings: () -> Unit)`

- [ ] **Step 1: Write the failing test for `consistencyColor` boundary values**

Create `app/src/test/java/com/moodified/app/presentation/insight/components/ConsistencyColorTest.kt`:

```kotlin
package com.moodified.app.presentation.insight.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConsistencyColorTest {
    @Test
    fun `high score returns high-consistency color`() {
        val high = consistencyColor(85)
        val low = consistencyColor(20)
        assertNotEquals("High and low scores must map to different colors", high.value, low.value)
    }

    @Test
    fun `score is clamped for out-of-range values`() {
        // Consistency scoring is defensive; over/under bounds should not crash and should
        // map to the same bucket as the nearest in-range value.
        assertEquals(consistencyColor(100).value, consistencyColor(120).value)
        assertEquals(consistencyColor(0).value, consistencyColor(-5).value)
    }

    @Test
    fun `three buckets exist across the 0-100 range`() {
        val colors = (0..100 step 10).map { consistencyColor(it).value }.toSet()
        // Existing implementation splits into low / mid / high — at least 2 distinct outputs.
        assert(colors.size >= 2) { "Expected at least two distinct buckets, got ${colors.size}" }
    }
}
```

- [ ] **Step 2: Run the test — expect COMPILATION FAILURE (unresolved reference)**

Run:
```
./gradlew testDebugUnitTest --tests "com.moodified.app.presentation.insight.components.ConsistencyColorTest"
```
Expected: `Unresolved reference: consistencyColor` — because the new package doesn't exist yet.

- [ ] **Step 3: Create the new atoms file**

Create `app/src/main/java/com/moodified/app/presentation/insight/components/InsightAtoms.kt`. **Copy** (do not move) the following from `presentation/devtools/MonitorSharedComponents.kt`, changing package to `com.moodified.app.presentation.insight.components` and applying the renames above:

- `WeeklyBarEntry` data class (was on line 26 of the source)
- `consistencyColor` helper (was on line 32) — copy body **verbatim**
- `private consistencyNote` helper (line 39) — copy verbatim, still `private`
- `InsightHeader` (renamed from `MonitorHeader`, was on line 47). **Two signature changes:**
  1. `eyebrow: String = "DEV TOOLS"` → `eyebrow: String? = null`; wrap the eyebrow row in `if (eyebrow != null) { ... }` so a null eyebrow renders no row at all.
  2. `onBack: () -> Unit` → `onBack: (() -> Unit)? = null`; wrap the back-button `IconButton(...)` in `if (onBack != null) { ... }` so tabs (which don't need back nav) can pass `null`.
- `StatTile` (renamed from `MonitorStatTile`, was on line 139)
- `InsightCard` (renamed from `MonitorCard`, was on line 205)
- `InsightCardEmpty` (renamed from `MonitorCardEmpty`, was on line 226)
- `SectionLabel` (was on line 251) — no changes
- `BreakdownRow` (was on line 266) — no changes
- `StatusBadge` (was on line 300) — no changes
- `WeeklyBarChart` (renamed from `MonitorWeeklyBars`, was on line 320)
- `ConsistencyScoreSection` (was on line 364) — no changes
- `PermissionDeniedCard` (was on line 422) — no changes
- `IdleBanner` (was on line 464) — no changes

**Do NOT copy**: `DebugRow` (line 289) — stays in `presentation/devtools/` as it is dev-only.

Any `import` statements inside copied composables that reference other atoms in the same file need no path change (they're all in the same new file). External imports (`androidx.compose.*`, theme colors from `com.moodified.app.core.theme.*`, etc.) stay identical.

- [ ] **Step 4: Run the test — expect PASS**

Run:
```
./gradlew testDebugUnitTest --tests "com.moodified.app.presentation.insight.components.ConsistencyColorTest"
```
Expected: 3 tests PASS.

- [ ] **Step 5: Confirm the whole build still compiles**

Run:
```
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. The old `MonitorSharedComponents.kt` still exists and is still used by the three domain screens — nothing else depends on the new file yet.

- [ ] **Step 6: Run ktlint + detekt**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: no *new* violations. If the copy triggered any, fix the copy — do not baseline.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/insight/components/InsightAtoms.kt \
        app/src/test/java/com/moodified/app/presentation/insight/components/ConsistencyColorTest.kt
git commit -m "feat(insight): extract user-facing atoms to insight/components/

Copies MonitorHeader → InsightHeader (no DEV TOOLS default), MonitorCard/StatTile/WeeklyBars
and shared atoms into presentation/insight/components/InsightAtoms.kt. Old
MonitorSharedComponents remains in place until domain screens are removed (Task 8).

Refs: Phase 2, spec §3.3 & §3.4 Move 1."
```

---

### Task 2: Build `InsightDomainTemplate` scaffold

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightDomainTemplate.kt`
- No tests (composable — spec non-goal excludes UI tests).

**Interfaces:**
- Consumes (from Task 1): `InsightHeader`, `SectionLabel`, `InsightCard`.
- Produces:
  ```kotlin
  @Composable
  fun InsightDomainTemplate(
      title: String,
      subtitle: String,
      isTracking: Boolean,
      onBack: (() -> Unit)? = null,
      status: (@Composable ColumnScope.() -> Unit)? = null,
      stats: (@Composable ColumnScope.() -> Unit)? = null,
      breakdown: (@Composable ColumnScope.() -> Unit)? = null,
      extras: (@Composable ColumnScope.() -> Unit)? = null,
  )
  ```
  Renders as a `LazyColumn` with `InsightHeader` at top followed by each non-null slot in its own `item {}` separated by 16.dp vertical spacing. Slot bodies are `ColumnScope`-scoped so callers can stack cards / labels without extra ceremony. The template does not inject `SectionLabel` — callers pass their own labels inside each slot.

- [ ] **Step 1: Create `InsightDomainTemplate.kt`**

Create the file with this exact body:

```kotlin
package com.moodified.app.presentation.insight.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moodified.app.core.theme.MilkWhite

@Composable
fun InsightDomainTemplate(
    title: String,
    subtitle: String,
    isTracking: Boolean,
    onBack: (() -> Unit)? = null,
    status: (@Composable ColumnScope.() -> Unit)? = null,
    stats: (@Composable ColumnScope.() -> Unit)? = null,
    breakdown: (@Composable ColumnScope.() -> Unit)? = null,
    extras: (@Composable ColumnScope.() -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MilkWhite),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            InsightHeader(
                title = title,
                subtitle = subtitle,
                isTracking = isTracking,
                eyebrow = null,
                onBack = onBack,
            )
        }
        slotItem(status)
        slotItem(stats)
        slotItem(breakdown)
        slotItem(extras)
    }
}


private fun LazyListScope.slotItem(slot: (@Composable ColumnScope.() -> Unit)?) {
    if (slot != null) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { slot() }
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/insight/components/InsightDomainTemplate.kt
git commit -m "feat(insight): add InsightDomainTemplate scaffold for per-tab layout

Provides { header, status, stats, breakdown, extras } slot-based layout used by the
four Insight tabs (Task 4). Reuses InsightHeader from Task 1.

Refs: Phase 2, spec §3.3."
```

---

### Task 3: Add `InsightTab` enum

Per spec §3.3 "Tab state persisted via `rememberSaveable`" — tab selection lives in the shell composable (Task 5), not in `InsightViewModel`. `InsightViewModel` stays untouched in this task; the enum is all we need.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/InsightTab.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class InsightTab { OVERVIEW, ACTIVITY, SLEEP, SCREEN_USE }` with companion helper `fun fromQueryParam(value: String?): InsightTab` mapping `"activity" → ACTIVITY`, `"sleep" → SLEEP`, `"screen_use" → SCREEN_USE`, everything else (including null) → `OVERVIEW`.

- [ ] **Step 1: Write the failing test for `InsightTab.fromQueryParam`**

Create `app/src/test/java/com/moodified/app/presentation/insight/InsightTabTest.kt`:

```kotlin
package com.moodified.app.presentation.insight

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightTabTest {
    @Test
    fun `known query values map to their tab`() {
        assertEquals(InsightTab.ACTIVITY, InsightTab.fromQueryParam("activity"))
        assertEquals(InsightTab.SLEEP, InsightTab.fromQueryParam("sleep"))
        assertEquals(InsightTab.SCREEN_USE, InsightTab.fromQueryParam("screen_use"))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam("overview"))
    }

    @Test
    fun `null and unknown values fall back to OVERVIEW`() {
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam(null))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam(""))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam("garbage"))
        assertEquals(InsightTab.OVERVIEW, InsightTab.fromQueryParam("SLEEP")) // case-sensitive, per contract
    }
}
```

- [ ] **Step 2: Run the test — expect COMPILATION FAILURE**

Run: `./gradlew testDebugUnitTest --tests "com.moodified.app.presentation.insight.InsightTabTest"`
Expected: `Unresolved reference: InsightTab`.

- [ ] **Step 3: Create `InsightTab.kt`**

```kotlin
package com.moodified.app.presentation.insight

enum class InsightTab {
    OVERVIEW,
    ACTIVITY,
    SLEEP,
    SCREEN_USE,
    ;

    companion object {
        fun fromQueryParam(value: String?): InsightTab =
            when (value) {
                "activity" -> ACTIVITY
                "sleep" -> SLEEP
                "screen_use" -> SCREEN_USE
                "overview" -> OVERVIEW
                else -> OVERVIEW
            }
    }
}
```

- [ ] **Step 4: Run the test — expect PASS**

Run: `./gradlew testDebugUnitTest --tests "com.moodified.app.presentation.insight.InsightTabTest"`
Expected: PASS.

- [ ] **Step 5: Confirm build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Nothing consumes `InsightTab` yet — purely additive.

- [ ] **Step 6: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/insight/InsightTab.kt \
        app/src/test/java/com/moodified/app/presentation/insight/InsightTabTest.kt
git commit -m "feat(insight): add InsightTab enum

Enum with { OVERVIEW, ACTIVITY, SLEEP, SCREEN_USE } and fromQueryParam() helper
for parsing the optional 'tab' route argument. Tab selection state itself lives
in the InsightScreen shell via rememberSaveable (Task 5), per spec §3.3.

Refs: Phase 2, spec §3.3."
```

---

### Task 4: Build the four tab composables

**Files:**
- Create:
  - `app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt`
  - `app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt`
  - `app/src/main/java/com/moodified/app/presentation/insight/tabs/SleepTab.kt`
  - `app/src/main/java/com/moodified/app/presentation/insight/tabs/ScreenUseTab.kt`
- Read (source of content, do not modify yet):
  - `app/src/main/java/com/moodified/app/presentation/insight/InsightScreen.kt` — contains chart composables to reuse.
  - `app/src/main/java/com/moodified/app/presentation/insight/activity/ActivityInsightScreen.kt`
  - `app/src/main/java/com/moodified/app/presentation/insight/sleep/SleepInsightScreen.kt`
  - `app/src/main/java/com/moodified/app/presentation/insight/screenuse/ScreenUseInsightScreen.kt`

**Interfaces:**
- Consumes: `InsightUiState` (existing type in `InsightViewModel.kt` — unchanged in Phase 2), `InsightDomainTemplate` (Task 2), all atoms from Task 1.
- Produces (each in package `com.moodified.app.presentation.insight.tabs`):
  - `@Composable fun OverviewTab(state: InsightUiState)` — mood distribution (`MoodLineChart`), completeness card, `TodayMoodCard`, `MoodStabilityCard`. Content sourced from `InsightScreen.kt` lines 93–136, 628–690 (TodayMoodCard), 420–511 (MoodStabilityCard), 764–835 (MoodLineChart). These private composables get copied inline into `OverviewTab.kt` as `private @Composable` helpers for now — Task 5 will delete them from `InsightScreen.kt`.
  - `@Composable fun ActivityTab(state: InsightUiState)` — steps / intensity / active minutes. Uses `InsightDomainTemplate`. Content sourced from `InsightScreen.kt` lines 140–160, 721–730 (`ActivityTrendRow`), 977–1140 (`ActivityStackedBarChart`).
  - `@Composable fun SleepTab(state: InsightUiState)` — duration / efficiency / consistency. Uses `InsightDomainTemplate`. Content sourced from `InsightScreen.kt` lines 164–184, 703–718 (`SleepTrendRow`), 880–970 (`SleepBarChart`).
  - `@Composable fun ScreenUseTab(state: InsightUiState)` — screen time / unlocks / late-night. Uses `InsightDomainTemplate`. Content sourced from `InsightScreen.kt` lines 188–208, 733–745 (`ScreenTimeTrendRow`), 1150–1296 (`ScreenTimeBarChart`).

Each domain tab renders as:
```kotlin
InsightDomainTemplate(
    title = "Activity",
    subtitle = /* dynamic e.g. "Last 7 days" */,
    isTracking = state.domainReadiness.activity.isTracking,
    // onBack omitted — tabs live inside the Insight screen; template hides the back button
    stats = { ActivityTrendRow(...) },
    breakdown = { /* stacked bar chart card */ },
)
```

`onBack` is already nullable in `InsightDomainTemplate` / `InsightHeader` (Tasks 1–2), so tabs simply omit it.

- [ ] **Step 1: Create `OverviewTab.kt`**

Package `com.moodified.app.presentation.insight.tabs`. Copy — **into private helpers inside this file** — the following from `InsightScreen.kt`:
- Lines 93–107 (InsightHeader + TodayMoodCard section wrapping)
- Lines 111–136 ("Your mood this week" section, chart + stability)
- Lines 420–511 (`MoodStabilityCard` private composable)
- Lines 545–557 (`drawGridLines` DrawScope helper — needed by MoodLineChart)
- Lines 628–690 (`TodayMoodCard`)
- Lines 764–835 (`MoodLineChart`)
- Lines 837–877 (`drawMoodLine` DrawScope helper)
- Lines 1360–1379 (`ChartSurface`)
- Lines 1382–1403 (`LegendDot`)
- Lines 1430–1469 (`getMoodDescription` helper)

Wrap the visible layout in an outer `LazyColumn` with the same padding as the current `InsightContentScreen`. Public signature:
```kotlin
@Composable
fun OverviewTab(state: InsightUiState) { ... }
```

Anything referenced from `com.moodified.app.core.theme.*` and `com.moodified.app.domain.model.*` needs the same imports as the source file.

- [ ] **Step 2: Create `ActivityTab.kt`**

```kotlin
@Composable
fun ActivityTab(state: InsightUiState) {
    InsightDomainTemplate(
        title = "Activity",
        subtitle = "Last 7 days",
        isTracking = state.domainReadiness.activity.isTracking,
        stats = { ActivityTrendRow(state.activityTrends) },
        breakdown = { ActivityStackedBarChart(state.activityBarPoints) },
    )
}
```
Copy `ActivityTrendRow` (InsightScreen.kt lines 721–730), `ActivityStackedBarChart` (lines 977–1140), `formatSteps` (lines 1142–1147), and any DrawScope helpers those depend on into this file as `private @Composable` / `private fun`. Wrap chart in a `ChartSurface` if the source did.

The exact `subtitle` string can be derived from `state.daysWithData` or `state.activityTrends?.periodLabel` — check the existing `ActivityInsightScreen.kt` for the string it uses. If unclear at implementation time, use `"Last 7 days"` verbatim as a safe fallback.

- [ ] **Step 3: Create `SleepTab.kt`**

Mirror ActivityTab. Copy `SleepTrendRow` (InsightScreen.kt lines 703–718), `SleepBarChart` (lines 880–970). Subtitle from `SleepInsightScreen.kt` or fallback to `"Last 7 days"`.

- [ ] **Step 4: Create `ScreenUseTab.kt`**

Mirror ActivityTab. Copy `ScreenTimeTrendRow` (lines 733–745), `ScreenTimeBarChart` (lines 1150–1296).

- [ ] **Step 5: Verify all four tabs compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. There are now duplicate composables (charts still exist in InsightScreen.kt and now in tab files) — that duplication is fine for one task and will be resolved in Task 5.

- [ ] **Step 6: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations. Duplicated composables aren't a lint failure.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/ \
        app/src/main/java/com/moodified/app/presentation/insight/components/
git commit -m "feat(insight): add OverviewTab, ActivityTab, SleepTab, ScreenUseTab

Each tab is a pure @Composable consuming a slice of InsightUiState. Domain tabs
render via InsightDomainTemplate (with slot-based header/status/stats/breakdown);
OverviewTab keeps the multi-section layout of the current InsightScreen.

Chart composables are duplicated between InsightScreen.kt and tab files
intentionally — Task 5 removes them from InsightScreen.kt. Header/template now
accept a nullable onBack.

Refs: Phase 2, spec §3.3."
```

---

### Task 5: Rewrite `InsightScreen.kt` as a shell

**Files:**
- Modify (full rewrite): `app/src/main/java/com/moodified/app/presentation/insight/InsightScreen.kt`

**Interfaces:**
- Consumes: all tab composables (Task 4), `InsightTab` enum (Task 3).
- Produces:
  ```kotlin
  @Composable
  fun InsightScreen(
      initialTab: InsightTab = InsightTab.OVERVIEW,
      viewModel: InsightViewModel = hiltViewModel(),
  )
  ```

Tab-selection state lives in the shell via `rememberSaveable`, seeded by `initialTab`. `InsightViewModel` is not modified here — the shell owns tab UI state; the ViewModel owns domain data.

The new file body (< 200 LOC target — should land around 100 LOC):

```kotlin
package com.moodified.app.presentation.insight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.presentation.insight.tabs.ActivityTab
import com.moodified.app.presentation.insight.tabs.OverviewTab
import com.moodified.app.presentation.insight.tabs.ScreenUseTab
import com.moodified.app.presentation.insight.tabs.SleepTab

@Composable
fun InsightScreen(
    initialTab: InsightTab = InsightTab.OVERVIEW,
    viewModel: InsightViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    AnimatedContent(
        targetState = state.isLoading,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "insightRoot",
    ) { loading ->
        if (loading) {
            InsightLoading()
        } else {
            InsightContent(
                state = state,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        }
    }
}

@Composable
private fun InsightLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(MilkWhite).statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = DeepSage, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun InsightContent(
    state: InsightUiState,
    selectedTab: InsightTab,
    onTabSelected: (InsightTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MilkWhite)) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            InsightTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label()) },
                )
            }
        }
        when (selectedTab) {
            InsightTab.OVERVIEW -> OverviewTab(state)
            InsightTab.ACTIVITY -> ActivityTab(state)
            InsightTab.SLEEP -> SleepTab(state)
            InsightTab.SCREEN_USE -> ScreenUseTab(state)
        }
    }
}

private fun InsightTab.label(): String =
    when (this) {
        InsightTab.OVERVIEW -> "Overview"
        InsightTab.ACTIVITY -> "Activity"
        InsightTab.SLEEP -> "Sleep"
        InsightTab.SCREEN_USE -> "Screen Use"
    }
```

**Note on `rememberSaveable(initialTab)`:** the `initialTab` key means when the composition is entered with a *different* initialTab (e.g. via a redirect from `insight/sleep` after previously landing on Activity), the saveable resets to the new value. Under normal navigation this is exactly what we want; a rotation preserves the saved value because `initialTab` is stable for that composition.

- [ ] **Step 1: Rewrite `InsightScreen.kt` completely**

Replace the entire file contents with the code above. Delete all 1469 lines of the old implementation — every private composable in the old file was moved into tab files during Task 4.

- [ ] **Step 2: Verify the file is < 200 LOC**

Run: `wc -l app/src/main/java/com/moodified/app/presentation/insight/InsightScreen.kt`
Expected: < 200. Current target is ~90 LOC.

- [ ] **Step 3: Confirm build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. The three old domain screens still exist and still route separately (their routes untouched), so nothing else breaks.

- [ ] **Step 4: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 5: Manual smoke test**

Install debug on a device / emulator: `./gradlew installDebug`
Launch app → tap Insight bottom-nav tab → confirm:
- TabRow shows four labels: Overview, Activity, Sleep, Screen Use
- Overview loads with the mood chart + today card
- Tapping each tab renders the correct content
- Tab selection persists across a configuration change (rotate device) — via `rememberSaveable`

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/insight/InsightScreen.kt
git commit -m "refactor(insight): rewrite InsightScreen as tabbed shell

Replaces the 1469 LOC single-scroll InsightScreen with a < 100 LOC shell that
hosts a SecondaryTabRow of four tab composables. Tab content lives in
presentation/insight/tabs/. Route query param (Task 6) will drive initialTab.

Refs: Phase 2, spec §3.3, §7 (InsightScreen.kt < 200 LOC)."
```

---

### Task 6: Wire the `tab` query parameter on the `insight` route

**Files:**
- Modify: `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `InsightScreen(initialTab, viewModel)` (Task 5), `InsightTab.fromQueryParam` (Task 3).
- Produces:
  - New helper `AppRoutes.Insight.withTab(tab: InsightTab): String` returning `"insight?tab=<value>"` or `"insight"` for `OVERVIEW`.
  - The `composable(AppRoutes.Insight.route)` entry in NavHost reads `backStackEntry.arguments?.getString("tab")` and passes it as `initialTab`.

- [ ] **Step 1: Modify `AppRoutes.kt`**

In `com.moodified.app.core.navigation.AppRoutes`, change the `Insight` data object:

```kotlin
data object Insight : AppRoutes("insight") {
    const val ROUTE_WITH_ARGS = "insight?tab={tab}"
    const val TAB_ARG = "tab"
    fun withTab(tab: InsightTab): String =
        if (tab == InsightTab.OVERVIEW) route else "insight?tab=${tab.queryValue()}"
}

private fun InsightTab.queryValue(): String =
    when (this) {
        InsightTab.OVERVIEW -> "overview"
        InsightTab.ACTIVITY -> "activity"
        InsightTab.SLEEP -> "sleep"
        InsightTab.SCREEN_USE -> "screen_use"
    }
```

Add the import for `InsightTab` at the top of `AppRoutes.kt`.

Bottom nav (which uses `AppRoutes.Insight.route` — the plain `"insight"` string) is unaffected because that string equals the base route, and Navigation-Compose matches optional query args against the same base route.

- [ ] **Step 2: Modify `MoodifiedNavHost.kt`**

Replace the current `composable(AppRoutes.Insight.route) { InsightScreen() }` (line 146) with:

```kotlin
composable(
    route = AppRoutes.Insight.ROUTE_WITH_ARGS,
    arguments = listOf(
        navArgument(AppRoutes.Insight.TAB_ARG) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    ),
) { entry ->
    val tabArg = entry.arguments?.getString(AppRoutes.Insight.TAB_ARG)
    InsightScreen(initialTab = InsightTab.fromQueryParam(tabArg))
}
```

Add imports: `androidx.navigation.NavType`, `androidx.navigation.compose.navArgument`, `com.moodified.app.presentation.insight.InsightTab`.

**Bottom nav interaction:** `BottomNavItem.Insight.route = "insight"`. Navigation-Compose treats `"insight"` and `"insight?tab=..."` as the same base destination — tapping the tab from the bottom bar navigates to `"insight"`, which matches `ROUTE_WITH_ARGS` with a null `tab` arg → `InsightTab.OVERVIEW`. No bottom-nav change needed.

- [ ] **Step 3: Confirm build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 5: Manual smoke test**

Reinstall: `./gradlew installDebug`
- Bottom-nav Insight → lands on Overview (initial tab = OVERVIEW).
- No regression versus Task 5's smoke test.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt \
        app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
git commit -m "feat(nav): accept optional tab query param on insight route

Adds insight?tab={tab} route form. Bottom-nav Insight still lands on Overview
(null tab arg → OVERVIEW via InsightTab.fromQueryParam). Sets up redirect
composables from legacy insight/{domain} routes (Task 7).

Refs: Phase 2, spec §3.3."
```

---

### Task 7: Redirect legacy domain routes to tabbed Insight

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `AppRoutes.Insight.withTab(...)` (Task 6).
- Produces: three redirect `composable(...)` entries that immediately `navController.navigate(AppRoutes.Insight.withTab(tab)) { popUpTo(<legacyRoute>) { inclusive = true } }` on entry.

- [ ] **Step 1: Replace the three legacy `composable(...)` blocks with redirect composables**

In `MoodifiedNavHost.kt`, replace lines 175–183 (the three `composable(AppRoutes.{Activity,Sleep,ScreenUse}Insight.route) { ... }` blocks) with:

```kotlin
composable(AppRoutes.ActivityInsight.route) {
    LegacyInsightRedirect(navController, AppRoutes.ActivityInsight.route, InsightTab.ACTIVITY)
}
composable(AppRoutes.SleepInsight.route) {
    LegacyInsightRedirect(navController, AppRoutes.SleepInsight.route, InsightTab.SLEEP)
}
composable(AppRoutes.ScreenUseInsight.route) {
    LegacyInsightRedirect(navController, AppRoutes.ScreenUseInsight.route, InsightTab.SCREEN_USE)
}
```

Add a private helper at the bottom of the file:

```kotlin
@Composable
private fun LegacyInsightRedirect(
    navController: NavHostController,
    legacyRoute: String,
    tab: InsightTab,
) {
    LaunchedEffect(legacyRoute, tab) {
        navController.navigate(AppRoutes.Insight.withTab(tab)) {
            popUpTo(legacyRoute) { inclusive = true }
        }
    }
}
```

Add imports: `androidx.navigation.NavHostController`, `com.moodified.app.presentation.insight.InsightTab`.

- [ ] **Step 2: Remove legacy routes from `fullScreenRoutes` set**

In `MoodifiedNavHost.kt` lines 87–97, delete the three lines:

```
AppRoutes.ActivityInsight.route,
AppRoutes.SleepInsight.route,
AppRoutes.ScreenUseInsight.route,
```

The tabbed Insight surface shares the main bottom-nav shell — no full-screen hide.

- [ ] **Step 3: Delete unused imports for domain screens (if the compiler complains)**

After Task 7, `MoodifiedNavHost.kt` no longer directly references `ActivityInsightScreen`, `SleepInsightScreen`, `ScreenUseInsightScreen`. Delete their import lines (44–46). If the file still compiles with them present, that means Task 8 will handle deletion — either way, they must be gone by the end of Task 8.

- [ ] **Step 4: Confirm build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke test — legacy route redirect**

Reinstall: `./gradlew installDebug`
- Navigate through Profile / More → "Activity insight" link. Verify:
  - The old route triggers, redirect fires, lands on Insight with the Activity tab selected.
  - Bottom bar is visible (Insight surface is a normal tab, not full-screen).
  - Back stack: pressing back does NOT return to the redirect composable (it should return to More/Profile).
- Repeat for Sleep and Screen Use.

- [ ] **Step 6: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
git commit -m "feat(nav): redirect legacy insight domain routes to tabbed Insight

insight/activity, insight/sleep, insight/screen_use now redirect immediately to
insight?tab={domain} and pop themselves off the back stack. Removes those
routes from the full-screen set — the tabbed Insight lives in the normal
bottom-nav shell. Delete redirects after one release cycle (follow-up).

Refs: Phase 2, spec §3.3."
```

---

### Task 8: Delete the three domain screens + their ViewModels

**Files:**
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/activity/ActivityInsightScreen.kt`
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/activity/ActivityInsightViewModel.kt`
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/sleep/SleepInsightScreen.kt`
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/sleep/SleepInsightViewModel.kt`
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/screenuse/ScreenUseInsightScreen.kt`
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/screenuse/ScreenUseInsightViewModel.kt`
- Delete (empty parent dirs): the `activity/`, `sleep/`, `screenuse/` folders.
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` — clean imports (already targeted in Task 7 Step 3 as a sweep).
- Verify: no other file imports from those packages.

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new. Deletions only.

- [ ] **Step 1: Verify nothing else imports the domain screens or their ViewModels**

Run (Grep tool):
```
Pattern: com\.moodified\.app\.presentation\.insight\.(activity|sleep|screenuse)
```
Expected hits: only inside the files being deleted plus (already fixed) the `MoodifiedNavHost.kt` imports handled in Task 7 Step 3. If any other files hit, they need to be updated before deletion — most likely candidates are `MoreScreen.kt` or something in `di/`.

- [ ] **Step 2: Delete the six files**

```
rm app/src/main/java/com/moodified/app/presentation/insight/activity/ActivityInsightScreen.kt
rm app/src/main/java/com/moodified/app/presentation/insight/activity/ActivityInsightViewModel.kt
rm app/src/main/java/com/moodified/app/presentation/insight/sleep/SleepInsightScreen.kt
rm app/src/main/java/com/moodified/app/presentation/insight/sleep/SleepInsightViewModel.kt
rm app/src/main/java/com/moodified/app/presentation/insight/screenuse/ScreenUseInsightScreen.kt
rm app/src/main/java/com/moodified/app/presentation/insight/screenuse/ScreenUseInsightViewModel.kt
rmdir app/src/main/java/com/moodified/app/presentation/insight/activity
rmdir app/src/main/java/com/moodified/app/presentation/insight/sleep
rmdir app/src/main/java/com/moodified/app/presentation/insight/screenuse
```

`rmdir` fails harmlessly if the OS/git tracks the folder differently on Windows — the intent is: the directories should be empty after the files are removed.

- [ ] **Step 3: Confirm build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: all Phase 1 + new Phase 2 tests pass. No orphaned test references to the deleted ViewModels.

- [ ] **Step 5: Manual regression**

Reinstall: `./gradlew installDebug`
- Legacy Profile "Activity insight" / "Sleep insight" / "Screen use insight" navigation → still redirects to the tabbed Insight (Task 7 path unchanged).
- Overview / Activity / Sleep / Screen Use tabs still render.

- [ ] **Step 6: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 7: Commit**

```
git add -A app/src/main/java/com/moodified/app/presentation/insight/
git commit -m "refactor(insight): delete legacy domain drill-down screens

Removes ActivityInsightScreen/ViewModel, SleepInsightScreen/ViewModel, and
ScreenUseInsightScreen/ViewModel. Their content lives in
presentation/insight/tabs/ as of Task 4; their routes redirect to the tabbed
Insight surface (Task 7). Their ViewModels' Flow composition is subsumed by
InsightViewModel, which already ingests the same trends / summaries.

Refs: Phase 2, spec §3.3, §7."
```

---

### Task 9: Retire `presentation/devtools/MonitorSharedComponents.kt`

**Files:**
- Delete: `app/src/main/java/com/moodified/app/presentation/devtools/MonitorSharedComponents.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing. Deletion only.

- [ ] **Step 1: Verify zero remaining imports from `presentation.devtools.MonitorSharedComponents`**

Run (Grep tool):
```
Pattern: presentation\.devtools\.(MonitorSharedComponents|MonitorHeader|MonitorCard|MonitorStatTile|MonitorCardEmpty|MonitorWeeklyBars|WeeklyBarEntry|ConsistencyScoreSection|StatusBadge|BreakdownRow|SectionLabel|IdleBanner|PermissionDeniedCard)
```
Expected: zero hits. If any remain (most likely a stray dev-only usage of `DebugRow`), leave that specific symbol behind in a smaller `presentation/devtools/DiagnosticAtoms.kt` and delete the rest — Phase 3 will formalize the diagnostic split. If no `DebugRow` usage remains either, delete the whole file.

- [ ] **Step 2: Delete the file (or reduce it to just `DebugRow` per Step 1's finding)**

Case A — no lingering usage of `DebugRow` in `main` source:
```
rm app/src/main/java/com/moodified/app/presentation/devtools/MonitorSharedComponents.kt
```

Case B — `DebugRow` is still referenced by something (e.g. a `src/debug` monitor screen):
- Extract `DebugRow` into `app/src/main/java/com/moodified/app/presentation/devtools/DiagnosticAtoms.kt` verbatim (same package).
- Delete `MonitorSharedComponents.kt`.
- Update the caller's import path from `MonitorSharedComponents` → `DiagnosticAtoms` (a same-package import needs no change; a cross-package one does).

- [ ] **Step 3: Confirm build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: all pass.

- [ ] **Step 5: Run ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: no new violations.

- [ ] **Step 6: Commit**

```
git add -A app/src/main/java/com/moodified/app/presentation/devtools/
git commit -m "chore(devtools): retire MonitorSharedComponents

User-facing atoms moved to presentation/insight/components/ (Task 1). No
production code imports from presentation.devtools.MonitorSharedComponents
anymore. Phase 3 will introduce a proper diagnostic-atoms module for
debug-only monitor screens.

Refs: Phase 2, spec §3.4 Move 1."
```

---

### Task 10: Final verification and manual pass

**Files:**
- No code changes.
- Optional: `docs/plans/2026-09-14-phase-2-insight-consolidation.md` — this plan — check off completed tasks (not required for merge; helps auditability).

- [ ] **Step 1: Full clean build**

Run:
```
./gradlew clean assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full test suite (unit only — no instrumented tests in Phase 2)**

Run:
```
./gradlew testDebugUnitTest
```
Expected: all pre-existing Phase 1 tests + `ConsistencyColorTest` + `InsightTabTest` pass. Zero failures.

- [ ] **Step 3: ktlint + detekt**

Run:
```
./gradlew ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL with zero new violations.

- [ ] **Step 4: Verify `InsightScreen.kt` LOC**

Run:
```
wc -l app/src/main/java/com/moodified/app/presentation/insight/InsightScreen.kt
```
Expected: < 200.

- [ ] **Step 5: Verify Definition of Done criteria (spec §7)**

Grep sweep — expected zero hits each:
```
Pattern: DEV TOOLS
Glob: app/src/main/**/*.kt
```
```
Pattern: presentation\.devtools\.Monitor
Glob: app/src/main/**/*.kt
```

- [ ] **Step 6: Manual test plan**

Fresh install: `./gradlew installDebug`

1. Launch app → land on Check-in.
2. Tap Insight bottom-nav → land on Overview tab.
3. Rotate device → Overview tab persists.
4. Tap Activity tab → Activity content renders.
5. Tap Sleep tab → Sleep content renders.
6. Tap Screen Use tab → Screen Use content renders.
7. Bottom-nav to More/Profile → tap "Activity insight" → redirect fires → land on Insight with Activity tab selected. Press back → return to Profile (not to redirect composable).
8. Repeat step 7 for Sleep and Screen Use link.
9. Force-close app → reopen → land on last screen; no crash.
10. Toggle system dark mode → visuals still legible (no regression from atom extraction).

- [ ] **Step 7: Update the plan file's checkboxes**

Mark all task steps `- [x]` in this plan file, commit as a doc update:

```
git add docs/plans/2026-09-14-phase-2-insight-consolidation.md
git commit -m "docs(phase-2): mark Phase 2 plan complete"
```

- [ ] **Step 8: Ready for merge**

Announce completion. Then decide on merge or PR.

---

## Summary of Deliverables

- **New files (7):**
  - `presentation/insight/components/InsightAtoms.kt`
  - `presentation/insight/components/InsightDomainTemplate.kt`
  - `presentation/insight/tabs/OverviewTab.kt`
  - `presentation/insight/tabs/ActivityTab.kt`
  - `presentation/insight/tabs/SleepTab.kt`
  - `presentation/insight/tabs/ScreenUseTab.kt`
  - `presentation/insight/InsightTab.kt`
- **Modified files (3):**
  - `presentation/insight/InsightScreen.kt` (rewritten: 1469 → < 200 LOC)
  - `presentation/navigation/MoodifiedNavHost.kt` (query-param route + redirects)
  - `core/navigation/AppRoutes.kt` (Insight.withTab helper)
  - `presentation/insight/InsightViewModel.kt` is **not** modified — tab UI state lives in the shell (`rememberSaveable`).
- **Deleted files (7):**
  - Six domain screen/ViewModel files under `presentation/insight/{activity,sleep,screenuse}/`
  - `presentation/devtools/MonitorSharedComponents.kt`
- **New tests (2):**
  - `ConsistencyColorTest.kt`
  - `InsightTabTest.kt`

## Follow-ups (out of Phase 2, tracked here for §8 of spec)

- Delete the three legacy-route redirect composables after one release cycle.
- Phase 3 will (re)introduce diagnostic atoms under `presentation/devtools/components/`. Phase 3 also owns moving debug-only atoms out of `src/main/presentation/devtools/DiagnosticAtoms.kt` and into the debug variant so they don't ship in the release APK (spec §7 DoD).
- **Chart-helper duplication** (surfaced in Phase 2 final review): four tab files each carry tab-prefixed copies of `drawGridLines`, `drawMoodLine`, `ChartSurface`, `LegendDot`, `formatSteps`, `getMoodDescription`. Consolidate into `presentation/insight/components/InsightChartAtoms.kt`. Fast-follow after Phase 3 to reduce chart-styling drift risk.
- **Split `InsightAtoms.kt`** (~515 LOC after Phase 2, over the 400 LOC "can-split" guideline). Do this alongside the chart-helper consolidation above.
- Compose UI / snapshot tests for the four tabs remain a non-goal; can be picked up in a later hardening pass.
