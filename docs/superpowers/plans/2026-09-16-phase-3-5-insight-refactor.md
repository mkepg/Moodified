# Phase 3.5 — Insight Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate byte-identical chart primitives (`*drawGridLines`, `*ChartSurface`, `*LegendDot`) duplicated across the four insight tab files into a single `InsightChartAtoms.kt`, relocate the `activityTabFormatSteps` pure-Kotlin helper into a Compose-free `util/InsightFormatters.kt`, and split the ~515 LOC `InsightAtoms.kt` into four concern-bucketed files. Behavior-preserving refactor inside `presentation/insight/` only.

**Architecture:**
- Empirical finding (verified before planning): all four `*drawGridLines` bodies are byte-for-byte identical (same `Color(0xFF465940).copy(alpha = 0.08f)`, same `strokeWidth = 1f`, same `dashPathEffect(floatArrayOf(6f, 6f))`, same `steps: Int = 3` default). Same for `*ChartSurface` (`RoundedCornerShape(20.dp)`, `MilkDeep`, 20.dp horizontal + 16.dp inner padding) and `*LegendDot` (8.dp dot, `labelSmall.copy(fontSize = 9.sp)`, `TextTertiary`). No parameterization needed — the shared signatures are exactly the current signatures with the tab prefix stripped.
- `InsightAtoms.kt` splits by concern (visual role) rather than by call-site or domain: headers/labels, cards/containers, stats/values, composed chart widgets. Chart *primitives* (grid/surface/legend) live in `InsightChartAtoms.kt` (workstream 1); composed chart *widgets* (`WeeklyBarChart`, `ConsistencyScoreSection`) live in `InsightCharts.kt` — different abstraction levels, different files.
- `formatSteps` is pure Kotlin (no `@Composable`, no `Modifier`, no theme colors), so it belongs in a `util/` subdirectory alongside future non-Compose helpers rather than in a Compose-only components file.
- **Two corrections to the original follow-up notes:** `formatSteps` is not actually duplicated — it exists only in `ActivityTab.kt` as `activityTabFormatSteps` (single call site at line 211). Reframed as relocation-not-consolidation. `getMoodDescription` does not exist in the codebase (verified with `grep` over `app/src/main/java`) — descoped entirely.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Compose Foundation (`DrawScope`, `PathEffect`, `Offset`). No new dependencies. No tests (non-goal per spec §1).

**Spec:** [docs/superpowers/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.3 (Insight consolidation) plus fast-follows from [Phase 2 plan](2026-09-14-phase-2-insight-consolidation.md) and [Phase 3 plan](2026-09-16-phase-3-devtools-restoration.md) "Follow-ups" sections.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- **No new tests.** Non-goal per spec §1. Acceptance is compile + lint + existing test suite green + manual visual parity (owed as follow-up).
- `ktlint` and `detekt` (baselined) must remain green. New baseline additions are allowed **only** in `FunctionNaming` / `LongParameterList` / `LongMethod` / `MatchingDeclarationName` (Compose false positives). Any other new category is a real signal — fix, don't baseline.
- Feature branch: `phase-3-5-insight-refactor` (created in Task 0).
- **Scope fence:** no changes outside `app/src/main/java/com/moodified/app/presentation/insight/`. In particular, no touching `presentation/devtools/`, `presentation/more/`, `src/debug/`, `InsightScreen.kt`, `InsightViewModel.kt`, `InsightGenerator.kt`, `InsightTab.kt`, `InsightUiState.kt`, `common/InsightUiState.kt`, or `InsightDomainTemplate.kt`. Also no changes to tab body *semantics* — only call-site edits swapping tab-prefixed helpers for consolidated ones.
- **Trailer policy:** subagents will append `Co-Authored-By: Claude <noreply@anthropic.com>` trailers by default. Do NOT strip per-commit. Task 4 runs `git filter-branch` once at the end to strip all such trailers before merge.
- **Detekt LOC guideline:** target files under 400 LOC. `InsightCharts.kt` will land at ~180 LOC (fine); `OverviewTab.kt` remains at ~500 LOC after workstream 1 removes ~40 LOC of chart helpers — that residual is out of scope for this phase (see Follow-ups).

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
Expected: `main` at `7ce488a` (Phase 3 merge) or later. If there are uncommitted changes to `.idea/claudeCodeTabState.xml` from the IDE, stash them: `git stash push -m "phase-3-5-scratch" .idea/`. If there are the two untracked docs (`docs/application-overview.md`, `docs/firebase-firestore-and-stripe-integration-plan.md`) noted in the session-start git status, leave them untracked — they are out of scope.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-3-5-insight-refactor
```
Expected: branch created and checked out. Verify with `git status`.

- [ ] **Step 3: Sanity check the pre-phase state**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If any failure, stop and diagnose before proceeding — Phase 3.5 needs a known-green baseline. In particular, `main` currently ends with commit `7ce488a` (temp More → Debug Drawer entry point) and was verified green at Phase 3 merge time.

No commit for this task — Task 1 makes the first commit.

---

### Task 1: Consolidate chart primitives into `InsightChartAtoms.kt`

Extract three byte-identical helper triples (12 functions total) into a single new file. Update the 13 call sites across the four tab files. Delete the 12 tab-prefixed copies.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightChartAtoms.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/insight/tabs/SleepTab.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/insight/tabs/ScreenUseTab.kt`

**Interfaces:**
- Consumes: nothing (first task with code changes).
- Produces:
  - `com.moodified.app.presentation.insight.components.drawInsightGridLines(steps: Int = 3)` — extension on `DrawScope`, top-level, package-visible (no `private`).
  - `com.moodified.app.presentation.insight.components.InsightChartSurface(content: @Composable ColumnScope.() -> Unit)` — top-level `@Composable`.
  - `com.moodified.app.presentation.insight.components.InsightLegendDot(color: Color, label: String)` — top-level `@Composable`.

- [ ] **Step 1: Create `InsightChartAtoms.kt` with the three consolidated helpers**

Create `app/src/main/java/com/moodified/app/presentation/insight/components/InsightChartAtoms.kt` with exact contents:

```kotlin
package com.moodified.app.presentation.insight.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.TextTertiary

fun DrawScope.drawInsightGridLines(steps: Int = 3) {
    val step = size.height / steps
    for (i in 0..steps) {
        val y = i * step
        drawLine(
            color = Color(0xFF465940).copy(alpha = 0.08f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

@Composable
fun InsightChartSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MilkDeep,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content,
        )
    }
}

@Composable
fun InsightLegendDot(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextTertiary,
        )
    }
}
```

- [ ] **Step 2: Update `OverviewTab.kt` call sites and delete prefixed copies**

In `app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt`:

1. Add these imports (alphabetized into the existing import block):
   ```kotlin
   import com.moodified.app.presentation.insight.components.InsightChartSurface
   import com.moodified.app.presentation.insight.components.InsightLegendDot
   import com.moodified.app.presentation.insight.components.drawInsightGridLines
   ```
2. Replace call sites:
   - Line 362: `OverviewChartSurface {` → `InsightChartSurface {`
   - Line 412: `OverviewLegendDot(color = ValencePositive, label = "Good")` → `InsightLegendDot(color = ValencePositive, label = "Good")`
   - Line 413: `OverviewLegendDot(color = ValenceNeutral, label = "So-so")` → `InsightLegendDot(color = ValenceNeutral, label = "So-so")`
   - Line 414: `OverviewLegendDot(color = ValenceNegative, label = "Low")` → `InsightLegendDot(color = ValenceNegative, label = "Low")`
   - Line 447: `overviewDrawGridLines(steps = 2)` → `drawInsightGridLines(steps = 2)`
3. Delete the three private helper definitions (`overviewDrawGridLines` at ~line 419, `OverviewChartSurface` at ~line 475, `OverviewLegendDot` at ~line 497 — verify with grep before deletion, delete the full function bodies including the trailing `}`).
4. Remove any now-unused imports (e.g., if `PathEffect`, `DrawScope`, `RoundedCornerShape`, `Surface`, `CircleShape` were only used by the deleted helpers — the file has other Compose content, so most stay). Let ktlint tell you: after edits run `./gradlew ktlintCheck` and address any `UnusedImport` or similar output.

Line numbers above are from the pre-edit file. Use grep to locate final positions if line numbers have shifted from a prior edit in the same session.

- [ ] **Step 3: Update `ActivityTab.kt` call sites and delete prefixed copies**

In `app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt`:

1. Add imports:
   ```kotlin
   import com.moodified.app.presentation.insight.components.InsightChartSurface
   import com.moodified.app.presentation.insight.components.InsightLegendDot
   import com.moodified.app.presentation.insight.components.drawInsightGridLines
   ```
2. Replace call sites (note: `drawBehind { activityTabDrawGridLines() }` at line 146 keeps the same body — just rename the invoked function):
   - Line 123: `ActivityTabChartSurface {` → `InsightChartSurface {`
   - Line 146: `drawBehind { activityTabDrawGridLines() }` → `drawBehind { drawInsightGridLines() }`
   - Line 270: `ActivityTabLegendDot(color = ColorLight, label = "Light")` → `InsightLegendDot(...)`
   - Line 271: `ActivityTabLegendDot(color = ColorModerate, label = "Moderate")` → `InsightLegendDot(...)`
   - Line 272: `ActivityTabLegendDot(color = ColorVigorous, label = "Vigorous")` → `InsightLegendDot(...)`
   - Line 274: `ActivityTabLegendDot(color = MilkDeep, label = "\"0.0k\" Step count")` → `InsightLegendDot(...)`
3. Delete the three prefixed helpers: `activityTabDrawGridLines` (~line 296), `ActivityTabChartSurface` (~line 311), `ActivityTabLegendDot` (~line 333).
4. **Do NOT touch** `activityTabFormatSteps` (line 279) or `activityTabDynamicChartMaxMinutes` (line 286) — those are handled in Task 2 and are out of scope respectively.
5. Remove unused imports after ktlint.

- [ ] **Step 4: Update `SleepTab.kt` call sites and delete prefixed copies**

In `app/src/main/java/com/moodified/app/presentation/insight/tabs/SleepTab.kt`:

1. Add imports:
   ```kotlin
   import com.moodified.app.presentation.insight.components.InsightChartSurface
   import com.moodified.app.presentation.insight.components.InsightLegendDot
   import com.moodified.app.presentation.insight.components.drawInsightGridLines
   ```
2. Replace call sites:
   - Line 114: `SleepTabChartSurface {` → `InsightChartSurface {`
   - Line 137: `drawBehind { sleepTabDrawGridLines() }` → `drawBehind { drawInsightGridLines() }`
   - Line 192: `SleepTabLegendDot(color = Color(0xAA67C967), label = "Restful sleep")` → `InsightLegendDot(...)`
   - Line 193: `SleepTabLegendDot(color = ValenceNegative.copy(alpha = 0.6f), label = "Short sleep")` → `InsightLegendDot(...)`
3. Delete: `sleepTabDrawGridLines` (~line 208), `SleepTabChartSurface` (~line 222), `SleepTabLegendDot` (~line 244). Keep `sleepTabDynamicChartMaxMinutes` (line 198) — out of scope.
4. Remove unused imports.

- [ ] **Step 5: Update `ScreenUseTab.kt` call sites and delete prefixed copies**

In `app/src/main/java/com/moodified/app/presentation/insight/tabs/ScreenUseTab.kt`:

1. Add imports:
   ```kotlin
   import com.moodified.app.presentation.insight.components.InsightChartSurface
   import com.moodified.app.presentation.insight.components.InsightLegendDot
   import com.moodified.app.presentation.insight.components.drawInsightGridLines
   ```
2. Replace call sites:
   - Line 126: `ScreenUseTabChartSurface {` → `InsightChartSurface {`
   - Line 149: `drawBehind { screenUseTabDrawGridLines() }` → `drawBehind { drawInsightGridLines() }`
   - Line 255: `ScreenUseTabLegendDot(color = ArousalLow.copy(alpha = 0.6f), label = "Screen time")` → `InsightLegendDot(...)`
   - Line 256: `ScreenUseTabLegendDot(color = ValenceNeutral.copy(alpha = 0.6f), label = "Late night")` → `InsightLegendDot(...)`
   - Line 258: `ScreenUseTabLegendDot(color = MilkDeep, label = "Tap bar")` → `InsightLegendDot(...)`
3. Delete: `screenUseTabDrawGridLines` (~line 273), `ScreenUseTabChartSurface` (~line 287), `ScreenUseTabLegendDot` (~line 309).
4. Remove unused imports.

- [ ] **Step 6: Grep-verify no stragglers**

Run:
```
grep -rnE "overviewDrawGridLines|activityTabDrawGridLines|sleepTabDrawGridLines|screenUseTabDrawGridLines|OverviewChartSurface|ActivityTabChartSurface|SleepTabChartSurface|ScreenUseTabChartSurface|OverviewLegendDot|ActivityTabLegendDot|SleepTabLegendDot|ScreenUseTabLegendDot" app/src/
```
Expected: **zero matches**. If any hit, resolve before continuing.

- [ ] **Step 7: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If `detekt` complains about `MatchingDeclarationName` on `InsightChartAtoms.kt` (multiple top-level Composables in a file not named after any of them), that is an acceptable baseline addition per Global Constraints — regenerate via `./gradlew detektBaseline` (root project task; baseline file lives at `config/detekt/detekt-baseline.xml` per root `build.gradle.kts:41`).

- [ ] **Step 8: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/insight/components/InsightChartAtoms.kt
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/SleepTab.kt
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/ScreenUseTab.kt
```
If detekt baseline was updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "refactor(insight): consolidate chart primitives into InsightChartAtoms"
```

---

### Task 2: Relocate `formatSteps` into Compose-free util

Move `activityTabFormatSteps` out of `ActivityTab.kt` into a new Compose-free util file, renamed to `formatSteps`. Single call site update.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/util/InsightFormatters.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt`

**Interfaces:**
- Consumes: nothing new (independent of Task 1).
- Produces: `com.moodified.app.presentation.insight.util.formatSteps(steps: Int): String` — top-level pure function, no `@Composable`.

- [ ] **Step 1: Create the util file**

Create `app/src/main/java/com/moodified/app/presentation/insight/util/InsightFormatters.kt` with exact contents:

```kotlin
package com.moodified.app.presentation.insight.util

fun formatSteps(steps: Int): String =
    when {
        steps >= 10_000 -> "${steps / 1000}k"
        steps >= 1_000 -> "${"%.1f".format(steps / 1000f)}k"
        else -> "$steps"
    }
```

- [ ] **Step 2: Update the ActivityTab.kt call site**

In `app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt`:

1. Add import:
   ```kotlin
   import com.moodified.app.presentation.insight.util.formatSteps
   ```
2. Line 211 (post-Task-1 line number may differ — grep for `activityTabFormatSteps(`): `text = activityTabFormatSteps(pt.totalSteps),` → `text = formatSteps(pt.totalSteps),`
3. Delete the `activityTabFormatSteps` function definition (was at line 279 pre-Task-1, will be at a different line after Task 1 deletions — grep to locate).

- [ ] **Step 3: Grep-verify no stragglers**

Run:
```
grep -rn "activityTabFormatSteps" app/src/
```
Expected: **zero matches**.

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/insight/util/InsightFormatters.kt
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/ActivityTab.kt
git commit -m "refactor(insight): move formatSteps into Compose-free util"
```

---

### Task 3: Split `InsightAtoms.kt` by concern into four files

Split the 515 LOC `InsightAtoms.kt` into four concern-bucketed files. Delete the original. No behavioral changes — all symbols keep the same package, same visibility (public top-level `@Composable`s and functions), same signatures, so import sites elsewhere in `presentation/insight/` continue to resolve without any call-site edits.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightHeaders.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightCards.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightStats.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightCharts.kt`
- Delete: `app/src/main/java/com/moodified/app/presentation/insight/components/InsightAtoms.kt`

**Interfaces:**
- Consumes: nothing new (independent of Tasks 1 and 2).
- Produces (all under package `com.moodified.app.presentation.insight.components`, all public, all top-level — same as pre-split):
  - In `InsightHeaders.kt`: `InsightHeader`, `SectionLabel`
  - In `InsightCards.kt`: `InsightCard`, `InsightCardEmpty`, `PermissionDeniedCard`, `IdleBanner`
  - In `InsightStats.kt`: `StatTile`, `BreakdownRow`, `StatusBadge`
  - In `InsightCharts.kt`: `WeeklyBarEntry` (data class), `WeeklyBarChart`, `ConsistencyScoreSection`, `consistencyColor` (public), `consistencyNote` (keep `private` — used only by `ConsistencyScoreSection` in the same file)

- [ ] **Step 1: Read the current `InsightAtoms.kt` in full**

Read `app/src/main/java/com/moodified/app/presentation/insight/components/InsightAtoms.kt`. It contains 14 top-level declarations at ~515 LOC. Every symbol must be preserved verbatim across the four new files — signatures, bodies, defaults, and comment/whitespace shape.

- [ ] **Step 2: Create `InsightHeaders.kt`**

Create with `package com.moodified.app.presentation.insight.components` and only the imports the file needs. Copy verbatim from `InsightAtoms.kt`:
- `@Composable fun InsightHeader(...)` (currently ~line 88-182)
- `@Composable fun SectionLabel(title: String)` (currently ~line 296-309)

Include only imports actually referenced by these two functions. Reference list to seed from (prune anything unused):
```
androidx.compose.animation.AnimatedVisibility
androidx.compose.animation.core.LinearEasing
androidx.compose.animation.core.RepeatMode
androidx.compose.animation.core.animateFloat
androidx.compose.animation.core.infiniteRepeatable
androidx.compose.animation.core.rememberInfiniteTransition
androidx.compose.animation.core.tween
androidx.compose.foundation.background
androidx.compose.foundation.layout.Box
androidx.compose.foundation.layout.Column
androidx.compose.foundation.layout.Row
androidx.compose.foundation.layout.Spacer
androidx.compose.foundation.layout.fillMaxWidth
androidx.compose.foundation.layout.height
androidx.compose.foundation.layout.padding
androidx.compose.foundation.layout.size
androidx.compose.foundation.layout.width
androidx.compose.foundation.shape.CircleShape
androidx.compose.foundation.shape.RoundedCornerShape
androidx.compose.material.icons.Icons
androidx.compose.material.icons.rounded.ArrowBackIosNew
androidx.compose.material3.Icon
androidx.compose.material3.IconButton
androidx.compose.material3.MaterialTheme
androidx.compose.material3.Surface
androidx.compose.material3.Text
androidx.compose.runtime.Composable
androidx.compose.runtime.getValue
androidx.compose.ui.Alignment
androidx.compose.ui.Modifier
androidx.compose.ui.draw.clip
androidx.compose.ui.graphics.Color
androidx.compose.ui.text.font.FontWeight
androidx.compose.ui.unit.dp
androidx.compose.ui.unit.sp
com.moodified.app.core.theme.DeepSage
com.moodified.app.core.theme.DmSerifDisplay
com.moodified.app.core.theme.MilkWhite
com.moodified.app.core.theme.TextPrimary
com.moodified.app.core.theme.TextSecondary
com.moodified.app.core.theme.TextTertiary
com.moodified.app.core.theme.ValenceNeutral
```

- [ ] **Step 3: Create `InsightCards.kt`**

Copy verbatim: `InsightCard`, `InsightCardEmpty`, `PermissionDeniedCard`, `IdleBanner`. Imports include `Icons.Rounded.Lock`, `OutlinedButton`, `MilkDeep`, `SageSurface`, `TextSecondary`, `TextPrimary`, `ValenceNegative`, layout primitives — prune to what these four functions actually reference.

- [ ] **Step 4: Create `InsightStats.kt`**

Copy verbatim: `StatTile`, `BreakdownRow`, `StatusBadge`. Imports include `AnimatedContent`, `fadeIn`/`fadeOut`/`tween`/`togetherWith`, `ImageVector`, `TextAlign`, layout + typography primitives, `TextPrimary`/`TextSecondary`/`TextTertiary`.

- [ ] **Step 5: Create `InsightCharts.kt`**

Copy verbatim: `data class WeeklyBarEntry`, `fun consistencyColor(score: Int): Color` (public, top-level), `private fun consistencyNote(score: Int): String`, `@Composable fun WeeklyBarChart(...)`, `@Composable fun ConsistencyScoreSection(...)`. Imports include `AnimatedContent`, `Icons.Rounded.TrendingUp`, `ArousalHigh`/`ArousalLow`/`ArousalMid`, `ValencePositive`, `SageDim`, layout primitives.

- [ ] **Step 6: Delete `InsightAtoms.kt`**

Run:
```
rm app/src/main/java/com/moodified/app/presentation/insight/components/InsightAtoms.kt
```

- [ ] **Step 7: Grep-verify all 14 symbols are present exactly once across the four new files**

Run:
```
grep -rn "^fun consistencyColor\|^private fun consistencyNote\|^@Composable$\|^data class WeeklyBarEntry\|^fun InsightHeader\|^fun StatTile\|^fun InsightCard\b\|^fun InsightCardEmpty\|^fun SectionLabel\|^fun BreakdownRow\|^fun StatusBadge\|^fun WeeklyBarChart\|^fun ConsistencyScoreSection\|^fun PermissionDeniedCard\|^fun IdleBanner" app/src/main/java/com/moodified/app/presentation/insight/components/
```
Then more precisely check by name:
```
grep -rn "^fun \(InsightHeader\|SectionLabel\|StatTile\|InsightCard\|InsightCardEmpty\|BreakdownRow\|StatusBadge\|WeeklyBarChart\|ConsistencyScoreSection\|PermissionDeniedCard\|IdleBanner\|consistencyColor\|consistencyNote\)\b" app/src/main/java/com/moodified/app/presentation/insight/components/
```
Expected: each name appears exactly once (each in its designated file). `InsightAtoms.kt` no longer exists.

- [ ] **Step 8: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Detekt may report new `MatchingDeclarationName` violations for the four new files (each holds multiple top-level Composables). That is an acceptable baseline addition — regenerate via `./gradlew detektBaseline` (baseline path: `config/detekt/detekt-baseline.xml`).

- [ ] **Step 9: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/insight/components/
```
This picks up the four new files and the deletion of `InsightAtoms.kt`. Also stage the detekt baseline if updated.

Then:
```
git commit -m "refactor(insight): split InsightAtoms.kt by concern"
```

---

### Task 4: Final verification and trailer strip

**Files:** none (git-only + verification).

**Interfaces:** none.

- [ ] **Step 1: Full gradle sanity pass**

Run:
```
./gradlew clean assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all green from a clean build. If anything fails, stop — the branch is not ready.

- [ ] **Step 2: Scope-fence audit**

Run:
```
git diff --stat main..HEAD
```
Expected: only files under `app/src/main/java/com/moodified/app/presentation/insight/` (plus possibly `config/detekt/detekt-baseline.xml` if regenerated). If any file outside that scope appears, that's a scope violation — investigate before merging.

- [ ] **Step 3: Verify `InsightScreen.kt` and `InsightViewModel.kt` are unchanged**

Run:
```
git diff main..HEAD -- app/src/main/java/com/moodified/app/presentation/insight/InsightScreen.kt app/src/main/java/com/moodified/app/presentation/insight/InsightViewModel.kt
```
Expected: **empty diff**. If either file changed, that's a scope violation.

- [ ] **Step 4: Stash IDE scratch state before filter-branch**

Run:
```
git status
```
If `.idea/claudeCodeTabState.xml` (or anything else) is dirty, stash it:
```
git stash push -m "phase-3-5-pre-filter-branch" .idea/
```

- [ ] **Step 5: Strip `Co-Authored-By: Claude` trailers from the branch's commits**

Run:
```
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f --msg-filter 'sed "/^Co-Authored-By:.*[Cc]laude/d; /^Co-Authored-By:.*noreply@anthropic/d"' main..HEAD
```
Expected: filter-branch rewrites the three phase commits, removing any Claude co-author trailers. Verify with:
```
git log main..HEAD --format='%h %s%n%b' | grep -i "co-authored" || echo "clean"
```
Expected output: `clean` (no matches).

- [ ] **Step 6: Restore IDE scratch state if stashed**

```
git stash list
```
If there's a `phase-3-5-pre-filter-branch` entry, restore:
```
git stash pop
```

- [ ] **Step 7: Final greens after filter-branch**

Filter-branch only touches commit messages — no code changes — but re-run to be certain:
```
./gradlew assembleDebug testDebugUnitTest ktlintCheck detekt
```
Expected: all green. (Skip `assembleRelease` here — filter-branch cannot have affected it and it's the slowest task.)

- [ ] **Step 8: Report readiness**

The branch is ready to merge. Report to the user:
- Branch: `phase-3-5-insight-refactor`
- Commits: 3 (chart consolidation, formatSteps relocation, atoms split)
- Diff scope: `presentation/insight/` only
- Trailers stripped: yes
- Greens: `assembleDebug` `assembleRelease` `testDebugUnitTest` `ktlintCheck` `detekt`
- Owed: manual smoke test on physical device (Phase 2 + Phase 3 + Phase 3.5 combined visual parity)

Do NOT merge automatically — the user runs the merge themselves after reviewing.

---

## Follow-ups (out of Phase 3.5 scope)

- **Manual smoke test still owed** — Phase 2 + Phase 3 + Phase 3.5 combined. Physical device: More → Debug Drawer renders all four diagnostics cards; Insight tabs (Overview/Activity/Sleep/Screen Use) all render with visual parity; legacy redirects `insight/activity`, `insight/sleep`, `insight/screen_use` still land on the tabbed shell. Requires user hands on device.
- **`OverviewTab.kt` residual size** — starts at 544 LOC; after Task 1 removes ~40 LOC of chart helpers, lands at ~500 LOC. Still over the 400 guideline. Not addressed here because the remaining content is tab body semantics (`overviewDrawMoodLine`, `overviewMoodLineChart` composition), which the phase scope explicitly protects. Candidate Phase 4+ split: extract `overviewDrawMoodLine` and related weekly-mood-line helpers into `presentation/insight/tabs/overview/OverviewMoodLine.kt` or similar.
- **Legacy `insight/{activity,sleep,screen_use}` redirect deletion** — still within the one-release-cycle deprecation window (no release has shipped since Phase 2). Defer to Phase 4+ or later.
- **Detekt baseline hygiene** — if `MatchingDeclarationName` baseline entries grow this phase, the entries for now-deleted `InsightAtoms.kt` should be pruned. Do this as part of the baseline regeneration in Tasks 1/3 (auto-correct via `./gradlew detekt --auto-correct` if the project's baseline task supports it).
