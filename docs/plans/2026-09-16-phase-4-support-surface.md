# Phase 4 — Support Surface + Profile Rename + OverviewTab Split Implementation Plan

**Goal:** Build the Support surface (Help / About / Feedback / Licenses), rename `More` → `Profile` with a regrouped section IA, delete the temporary MoreScreen → Debug Drawer button by replacing it with an About-version-label long-press gesture, and split the ~485 LOC `OverviewTab.kt` into a concern-bucketed `overview/` subdirectory. Every task leaves the app building and running.

**Architecture:**
- **Support routes** live under a new `AppRoutes.Support` object (`support/help`, `support/about`, `support/licenses`). `FeedbackSheet` is a modal — no route.
- **About long-press** uses the exact `DebugNavRegistrar` pattern Phase 3 established. `AboutScreen` takes `onNavigateToDebugDrawer: (() -> Unit)?`; `MoodifiedNavHost` wires it via `debugNavRegistrar.drawerRoute?.let { { navController.navigate(it) } }`. In release, `drawerRoute` is `null` (see `app/src/release/java/com/moodified/app/di/ReleaseDevToolsModule.kt:37`), so the callback is `null` and `combinedClickable(onLongClick = null)` attaches no long-press detector — zero release surface at both the Compose layer and the nav-graph layer.
- **More → Profile** is a pure rename (package, class, route, BottomNavItem) followed by a section restructure that deletes the redundant "Your Insights" and "Developer Tools" sections in favor of new Tracking / Support / Data & Privacy grouping.
- **OverviewTab split** mirrors Phase 3.5 Task 3: behavior-preserving concern move into a `presentation/insight/tabs/overview/` subdirectory. Three composed cards (+ their helpers) extract; the tab shell drops to ~140 LOC.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Compose Foundation (`combinedClickable`), Hilt (existing `NavHostEntryPoint`), Android `Intent.ACTION_SENDTO`. No new Gradle dependencies. No plugin adds. No schema changes. No tests (non-goal per spec §1; consolidation phases carry no new test scaffolding).

**Spec:** [docs/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.1 (nav / Profile rename), §3.2.3 (Help / About / Feedback), §3.4 (hidden entry point). Rollout defined in §6 Phase 4. This plan also completes the fast-follow noted in [Phase 3.5's follow-ups](2026-09-16-phase-3-5-insight-refactor.md) — the ~500 LOC `OverviewTab.kt` residual split.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- Test suite: only the existing Phase 1 tests (`RuleBasedMoodInferenceEngineTest`, `CareEvaluationEngineTest`, `Migration13To14Test`). No new tests this phase.
- `ktlint` and `detekt` (baselined) must remain green. New baseline additions are allowed **only** in `FunctionNaming` / `LongParameterList` / `LongMethod` / `MatchingDeclarationName` (Compose false positives). Any other new category is a real signal — fix, don't baseline. Baseline path: `config/detekt/detekt-baseline.xml`. Regenerate via `./gradlew detektBaseline`.
- Detekt LOC guideline: target files under 400 LOC. New files must respect this; `OverviewTab.kt` shell after Task 2 should land at ~140 LOC.
- Feature branch: `phase-4-support-surface` (created in Task 0).- **Debug-visibility strategy (spec §3.4):** the About long-press gesture is wired via `combinedClickable(onClick = {}, onLongClick = onNavigateToDebugDrawer)` where `onNavigateToDebugDrawer` is nullable and resolves to `null` in release. This gives zero release surface at both the Compose layer (no detector) and the nav layer (`DebugRoutes.DRAWER` not in the graph). Do not add any release-only easter-egg discoverability — no "tap version 5 times" fallback, no visual affirmation on hover.
- **Feedback recipient value:** `BuildConfig.FEEDBACK_EMAIL = "feedback@moodified.app"` in both build types (placeholder matching the existing `PRIVACY_POLICY_URL` pattern in `app/build.gradle.kts:65,75`).
- **Licenses source:** hand-rolled `data class License` list in `presentation/support/data/Licenses.kt` — no `AboutLibraries` plugin.
- **Scope fences:**
  - No touching `presentation/devtools/` (Phase 3 territory).
  - No touching `presentation/insight/InsightScreen.kt`, `InsightViewModel.kt`, `InsightGenerator.kt`, `InsightTab.kt`, `InsightUiState.kt`, or the three non-Overview tab files (Phase 2/3.5 territory) — Task 2 only touches `OverviewTab.kt` and creates files under `insight/tabs/overview/`.
  - No Notifications inbox row on Profile (Phase 5 territory).
  - No new deep-link URIs beyond what's currently registered.
  - No changes to the "Debug Data" section on the current `MoreScreen` (mock-data seeder rows gated by `BuildConfig.ENABLE_MOCK_DATA`) — carry it forward verbatim into `ProfileScreen`.

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
Expected: `main` at `58ce86e` (Phase 3.5's "delete legacy insight redirects" commit) or later. If there are untracked docs under `docs/` from prior sessions (e.g., `docs/application-overview.md`, `docs/firebase-firestore-and-stripe-integration-plan.md`), leave them untracked — out of scope. If `.idea/` files are dirty, stash them: `git stash push -m "phase-4-scratch" .idea/`.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-4-support-surface
```
Expected: branch created and checked out. Verify with `git status`.

- [ ] **Step 3: Sanity-check the pre-phase greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If any failure, stop and diagnose — Phase 4 needs a known-green baseline.

No commit for this task — Task 1 makes the first commit.

---

### Task 1: Add `FEEDBACK_EMAIL` buildConfigField and `AppRoutes.Support` route constants

Wire the `BuildConfig.FEEDBACK_EMAIL` value that Task 6's FeedbackSheet will read, and add the three Support route constants that Tasks 3, 4, 5 will register composables against. Independent of everything else — small enablement commit.

**Files:**
- Modify: `app/build.gradle.kts` (both `release` and `debug` `buildConfigField` blocks)
- Modify: `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `BuildConfig.FEEDBACK_EMAIL: String` — value `"feedback@moodified.app"` in both build types.
  - `com.moodified.app.core.navigation.AppRoutes.Support` — sealed sub-object with three route constants (see Step 2).

- [ ] **Step 1: Add `FEEDBACK_EMAIL` to both build types**

In `app/build.gradle.kts`, inside the `release` block (currently lines 51-67), immediately after line 65's `buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://moodified.app/privacy\"")`, add:
```kotlin
buildConfigField("String", "FEEDBACK_EMAIL", "\"feedback@moodified.app\"")
```

In the `debug` block (currently lines 68-76), immediately after line 75's `buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://moodified.app/privacy\"")`, add:
```kotlin
buildConfigField("String", "FEEDBACK_EMAIL", "\"feedback@moodified.app\"")
```

- [ ] **Step 2: Add `AppRoutes.Support` route constants**

In `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`, after the existing `Privacy` object (line 30) but still inside the `AppRoutes` sealed class body (before the closing `}` at line 31), add:
```kotlin

    // Support surface (Phase 4)
    data object Support : AppRoutes("support") {
        const val HELP = "support/help"
        const val ABOUT = "support/about"
        const val LICENSES = "support/licenses"
    }
```

Note: `AppRoutes.Support.route = "support"` is unused by the navigation graph (it's a namespace, not a destination) — it exists only because `AppRoutes` is a `sealed class` with a `route: String` constructor. Do not register a composable for `"support"` itself.

- [ ] **Step 3: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. `BuildConfig.FEEDBACK_EMAIL` and `AppRoutes.Support.HELP/ABOUT/LICENSES` compile even though nothing references them yet.

- [ ] **Step 4: Commit**

Run:
```
git add app/build.gradle.kts app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt
git commit -m "feat(support): add FEEDBACK_EMAIL and Support route constants"
```
No `Co-Authored-By` trailer.

---

### Task 2: Split `OverviewTab.kt` into `overview/` subdirectory

Move three composed cards + their private draw/icon helpers out of `OverviewTab.kt` into three concern-bucketed files under a new `presentation/insight/tabs/overview/` package. Behavior-preserving — no signature changes, no semantic edits. Independent of Tasks 1, 3, 4, 5, 6, 7, 8, 9.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/OverviewTodayMoodCard.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/OverviewMoodStabilityCard.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/OverviewMoodLineChart.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt`

**Interfaces:**
- Consumes: existing `InsightUiState`, `MoodChartPoint`, `MoodStability`, `InferredMoodState`, `InsightChartSurface`, `InsightLegendDot`, `drawInsightGridLines` (all already imported by the current `OverviewTab.kt`).
- Produces (all under package `com.moodified.app.presentation.insight.tabs.overview`, all `public` top-level `@Composable` — visibility changed from `private` in shell to `public` here because the shell in `insight.tabs` is a different package):
  - `OverviewTodayMoodCard(mood: InferredMoodState)`
  - `OverviewMoodStabilityCard(stability: MoodStability)`
  - `OverviewMoodLineChart(points: List<MoodChartPoint>)`

- [ ] **Step 1: Create `OverviewTodayMoodCard.kt`**

Create `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/OverviewTodayMoodCard.kt`. Copy verbatim from `OverviewTab.kt`:
- `@Composable private fun OverviewTodayMoodCard(mood: InferredMoodState)` — currently lines 186-249. Change `private` → `` (public — remove the modifier).
- `private fun overviewMoodIcon(valence: Valence, arousal: Arousal): ImageVector` — currently lines 461-484. Keep `private` — only used inside this file.

Package declaration:
```kotlin
package com.moodified.app.presentation.insight.tabs.overview
```

Imports needed (verify by reading the two function bodies — do not blindly copy imports from the shell):
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.ValenceNegative
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.core.theme.ValencePositive
import com.moodified.app.domain.model.inference.InferredMoodState
import com.moodified.app.domain.model.mood.Arousal
import com.moodified.app.domain.model.mood.Valence
```

Trim to only what's referenced — ktlint will flag unused imports if any slip through.

- [ ] **Step 2: Create `OverviewMoodStabilityCard.kt`**

Create `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/OverviewMoodStabilityCard.kt`. Copy verbatim from `OverviewTab.kt`:
- `@Composable private fun OverviewMoodStabilityCard(stability: MoodStability)` — currently lines 251-343. Change `private` → `` (public).

Package declaration:
```kotlin
package com.moodified.app.presentation.insight.tabs.overview
```

Imports referenced by the body:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.ErrorRed
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.presentation.insight.MoodStability
```

- [ ] **Step 3: Create `OverviewMoodLineChart.kt`**

Create `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/OverviewMoodLineChart.kt`. Copy verbatim from `OverviewTab.kt`:
- `@Composable private fun OverviewMoodLineChart(points: List<MoodChartPoint>)` — currently lines 345-417. Change `private` → `` (public).
- `private fun DrawScope.overviewDrawMoodLine(averagedByDate: Map<LocalDate, Float?>, width: Float, height: Float)` — currently lines 419-459. Keep `private` — only called by `OverviewMoodLineChart` in the same file.

Package declaration:
```kotlin
package com.moodified.app.presentation.insight.tabs.overview
```

Imports referenced by the two function bodies:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.core.theme.ValenceNegative
import com.moodified.app.core.theme.ValenceNeutral
import com.moodified.app.core.theme.ValencePositive
import com.moodified.app.presentation.insight.MoodChartPoint
import com.moodified.app.presentation.insight.components.InsightChartSurface
import com.moodified.app.presentation.insight.components.InsightLegendDot
import com.moodified.app.presentation.insight.components.drawInsightGridLines
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
```

- [ ] **Step 4: Update `OverviewTab.kt` — delete extracted functions, add imports**

In `app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt`:

1. Delete the three now-migrated function bodies:
   - `OverviewTodayMoodCard` (lines 186-249)
   - `OverviewMoodStabilityCard` (lines 251-343)
   - `OverviewMoodLineChart` (lines 345-417)
   - `overviewDrawMoodLine` (lines 419-459) — helper moved with its caller
   - `overviewMoodIcon` (lines 461-484) — helper moved with its caller

   After deletion the file should contain: package + imports + `OverviewTab` shell composable + `OverviewSectionHeader` + `OverviewEmptyMoodCard`. Target ~140 LOC.

2. Add the three imports:
   ```kotlin
   import com.moodified.app.presentation.insight.tabs.overview.OverviewMoodLineChart
   import com.moodified.app.presentation.insight.tabs.overview.OverviewMoodStabilityCard
   import com.moodified.app.presentation.insight.tabs.overview.OverviewTodayMoodCard
   ```
   Alphabetize into the existing import block.

3. Prune imports now-unused by the trimmed shell. Candidates likely to become unused (verify against remaining code): `Bedtime`, `Insights`, `SentimentDissatisfied`, `SentimentNeutral`, `SentimentSatisfiedAlt`, `Spa`, `TrendingFlat`, `Waves`, `Air`, `Bolt`, `BatteryAlert`, `Warning`, `CircleShape`, `HorizontalDivider`, `drawBehind`, `Offset`, `StrokeCap`, `DrawScope`, `ImageVector`, `TextAlign`, `DateTimeFormatter`, `LocalDate`, `Locale`, `Arousal`, `Valence`, `InferredMoodState`, `ErrorRed`, `DeepSage`, `MilkWhite` (still needed for the shell's `background(MilkWhite)`), `SageDim`, `MoodChartPoint`, `MoodStability`. Let ktlint tell you: after edits run `./gradlew ktlintCheck` and address the `UnusedImport` output.

- [ ] **Step 5: Grep-verify no stragglers**

Run:
```
grep -rnE "OverviewTodayMoodCard|OverviewMoodStabilityCard|OverviewMoodLineChart|overviewDrawMoodLine|overviewMoodIcon" app/src/
```
Expected matches: exactly one definition of each in the corresponding new file (three composable definitions plus two private helper definitions), and three call sites inside the shell `OverviewTab.kt`. No definitions in `OverviewTab.kt`.

- [ ] **Step 6: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Detekt may report new `MatchingDeclarationName` on the three new files — acceptable baseline addition (regenerate via `./gradlew detektBaseline`, baseline path: `config/detekt/detekt-baseline.xml`).

- [ ] **Step 7: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/
git add app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt
```
If detekt baseline was updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "refactor(insight): split OverviewTab.kt into overview/ subdirectory"
```
No `Co-Authored-By` trailer.

---

### Task 3: Build `HelpScreen` + `HelpArticles` data

Static full-screen screen with a search field and expandable FAQ rows. Route registered but Profile does not yet link to it (Task 8 wires the row).

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/support/data/HelpArticles.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/support/HelpScreen.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `AppRoutes.Support.HELP` (from Task 1), `MilkWhite`/`TextPrimary`/`TextSecondary`/`TextTertiary`/`DeepSage`/`DmSerifDisplay`/`SageDim`/`SageSurface` (existing theme colors in `com.moodified.app.core.theme`).
- Produces:
  - `com.moodified.app.presentation.support.data.HelpArticle` — `data class` with `id: String`, `title: String`, `body: String`.
  - `com.moodified.app.presentation.support.data.HelpArticles.all: List<HelpArticle>` — object holding the FAQ dataset.
  - `com.moodified.app.presentation.support.HelpScreen(onBack: () -> Unit)` — full-screen composable.

- [ ] **Step 1: Create `HelpArticles.kt`**

Create `app/src/main/java/com/moodified/app/presentation/support/data/HelpArticles.kt` with:
```kotlin
package com.moodified.app.presentation.support.data

data class HelpArticle(
    val id: String,
    val title: String,
    val body: String,
)

object HelpArticles {
    val all: List<HelpArticle> =
        listOf(
            HelpArticle(
                id = "what-is-moodified",
                title = "What is Moodified?",
                body =
                    "Moodified is a mood-tracking companion that combines your quick self-check-ins " +
                        "with quiet passive signals (activity, sleep, screen use) to help you notice patterns " +
                        "in how you feel over time. Everything runs on your device — nothing leaves your phone.",
            ),
            HelpArticle(
                id = "privacy",
                title = "Where does my data live?",
                body =
                    "All your mood entries, activity summaries, and inferred insights are stored locally in " +
                        "an encrypted Room database on your phone. Moodified has no server, no analytics, and no " +
                        "account system. You can export or delete everything at any time from Profile → " +
                        "Privacy & data control.",
            ),
            HelpArticle(
                id = "permissions",
                title = "Why do you ask for permissions?",
                body =
                    "Activity Recognition lets us count steps and detect movement intensity. Usage Access " +
                        "lets us learn your sleep and screen-use patterns from device idle time. Notifications " +
                        "let us send optional check-in prompts. All are optional — Moodified works fine without " +
                        "them, just with fewer passive signals to draw on.",
            ),
            HelpArticle(
                id = "inferred-mood",
                title = "What does the inferred mood mean?",
                body =
                    "When you haven't logged a mood recently, Moodified estimates one from your passive " +
                        "signals: how much you slept, how active you were, how your screen time and late-night " +
                        "use trended. The confidence score reflects how much data was available. Your own " +
                        "logged moods always take priority over inference.",
            ),
            HelpArticle(
                id = "notifications",
                title = "How do check-in notifications work?",
                body =
                    "If notifications are enabled, Moodified may send you a gentle micro-prompt at moments " +
                        "when a quick mood check-in would be most valuable. Tapping opens the two-tap Quick Log " +
                        "sheet directly. You can turn these off at any time in your system notification settings.",
            ),
            HelpArticle(
                id = "export",
                title = "Can I export my data?",
                body =
                    "Yes. Profile → Privacy & data control → Export. You'll get a JSON file with every mood " +
                        "entry, care event, and daily summary Moodified has saved. This file is yours — nothing " +
                        "is uploaded.",
            ),
            HelpArticle(
                id = "delete",
                title = "How do I delete my data?",
                body =
                    "Profile → Privacy & data control → Delete all data. This wipes the local database and " +
                        "resets Moodified to a fresh state. There's no way to recover deleted data — it never " +
                        "leaves your phone in the first place.",
            ),
            HelpArticle(
                id = "feedback",
                title = "How do I send feedback?",
                body =
                    "Profile → Send feedback opens your email app with a pre-filled draft. You can optionally " +
                        "include your device info (Android version, model, build type) to help us diagnose issues. " +
                        "No feedback data leaves your phone until you tap send in your email app.",
            ),
        )
}
```

- [ ] **Step 2: Create `HelpScreen.kt`**

Create `app/src/main/java/com/moodified/app/presentation/support/HelpScreen.kt`:
```kotlin
package com.moodified.app.presentation.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.presentation.support.data.HelpArticle
import com.moodified.app.presentation.support.data.HelpArticles

@Composable
fun HelpScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filtered =
        remember(query) {
            if (query.isBlank()) {
                HelpArticles.all
            } else {
                val q = query.trim().lowercase()
                HelpArticles.all.filter {
                    it.title.lowercase().contains(q) || it.body.lowercase().contains(q)
                }
            }
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { HelpHeader(onBack = onBack) }
        item {
            Spacer(Modifier.height(8.dp))
            HelpSearchField(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(16.dp))
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "No articles match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        } else {
            items(filtered, key = { it.id }) { article ->
                HelpArticleRow(
                    article = article,
                    isExpanded = expandedId == article.id,
                    onToggle = {
                        expandedId = if (expandedId == article.id) null else article.id
                    },
                )
            }
        }
    }
}

@Composable
private fun HelpHeader(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
                Text(
                    text = "HELP",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            fontSize = 10.sp,
                        ),
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "How can we help?",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Answers to common questions about Moodified.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HelpSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        placeholder = { Text("Search articles") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextTertiary) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MilkDeep,
                unfocusedContainerColor = MilkDeep,
                focusedIndicatorColor = SageDim,
                unfocusedIndicatorColor = SageDim.copy(alpha = 0.4f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
    )
}

@Composable
private fun HelpArticleRow(
    article: HelpArticle,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = article.body,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Register the route in `MoodifiedNavHost.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`, after the existing `composable(AppRoutes.Privacy.route) { ... }` block (currently lines 188-190), add:
```kotlin
                composable(AppRoutes.Support.HELP) {
                    HelpScreen(onBack = { navController.popBackStack() })
                }
```

Also add the import for `HelpScreen` at the top of the file, alphabetized into the existing import block:
```kotlin
import com.moodified.app.presentation.support.HelpScreen
```

The route is now registered in the graph even though Profile does not yet link to it (Task 8 wires the row). This is intentional — an unlinked but registered route is harmless.

Also add `AppRoutes.Support.HELP` to the `fullScreenRoutes` set at MoodifiedNavHost:87-94 (bottom bar hidden on this route). Update that block:
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
                )
        }
```

Note: `AppRoutes.Support.ABOUT` and `AppRoutes.Support.LICENSES` don't yet have composables registered — that lands in Tasks 4 and 5. Adding them to `fullScreenRoutes` now is safe because the set is used only for `showBottomBar` computation, which never fires until the user actually navigates to those routes.

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Detekt may complain about `LongMethod` on `HelpScreen` — acceptable baseline addition (regenerate via `./gradlew detektBaseline`).

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/support/
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
```
If baseline updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "feat(support): add HelpScreen with local FAQ dataset"
```
No `Co-Authored-By` trailer.

---

### Task 4: Build `LicensesScreen` + `Licenses` data

Static full-screen list of open-source licenses, hand-rolled. Route registered but not yet linked from Profile — Task 5's AboutScreen wires the "Open-source licenses" MenuRow.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/support/data/Licenses.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/support/LicensesScreen.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `AppRoutes.Support.LICENSES` (Task 1).
- Produces:
  - `com.moodified.app.presentation.support.data.License` — `data class` with `name: String`, `version: String`, `licenseType: LicenseType`, `url: String`.
  - `com.moodified.app.presentation.support.data.LicenseType` — enum with `APACHE_2_0`, `MIT`, `BSD_3_CLAUSE`.
  - `com.moodified.app.presentation.support.data.Licenses.all: List<License>`.
  - `com.moodified.app.presentation.support.data.Licenses.textFor(type: LicenseType): String` — returns full license text.
  - `com.moodified.app.presentation.support.LicensesScreen(onBack: () -> Unit)` — full-screen composable.

- [ ] **Step 1: Create `Licenses.kt`**

Create `app/src/main/java/com/moodified/app/presentation/support/data/Licenses.kt`:
```kotlin
package com.moodified.app.presentation.support.data

enum class LicenseType {
    APACHE_2_0,
    MIT,
    BSD_3_CLAUSE,
}

data class License(
    val name: String,
    val version: String,
    val licenseType: LicenseType,
    val url: String,
)

object Licenses {
    val all: List<License> =
        listOf(
            License("Jetpack Compose", "BOM 2024.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/compose"),
            License("Material 3 for Compose", "1.x", LicenseType.APACHE_2_0, "https://m3.material.io"),
            License("Compose Material Icons Extended", "1.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/compose"),
            License("AndroidX Navigation Compose", "2.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/navigation"),
            License("AndroidX Lifecycle", "2.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
            License("AndroidX Room", "2.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/room"),
            License("AndroidX Activity Compose", "1.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/activity"),
            License("AndroidX AppCompat", "1.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/appcompat"),
            License("AndroidX Splashscreen", "1.x", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/core"),
            License("AndroidX WorkManager", "2.9.0", LicenseType.APACHE_2_0, "https://developer.android.com/jetpack/androidx/releases/work"),
            License("Hilt", "2.x", LicenseType.APACHE_2_0, "https://dagger.dev/hilt"),
            License("Kotlin", "1.9.x", LicenseType.APACHE_2_0, "https://kotlinlang.org"),
            License("Kotlinx Coroutines", "1.x", LicenseType.APACHE_2_0, "https://github.com/Kotlin/kotlinx.coroutines"),
            License("Lottie for Compose", "6.x", LicenseType.APACHE_2_0, "https://airbnb.io/lottie"),
            License("Play Services Location", "21.x", LicenseType.APACHE_2_0, "https://developers.google.com/android/guides/setup"),
        )

    fun textFor(type: LicenseType): String =
        when (type) {
            LicenseType.APACHE_2_0 -> APACHE_2_0_TEXT
            LicenseType.MIT -> MIT_TEXT
            LicenseType.BSD_3_CLAUSE -> BSD_3_CLAUSE_TEXT
        }

    private const val APACHE_2_0_TEXT =
        "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this " +
            "file except in compliance with the License. You may obtain a copy of the License at " +
            "http://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to " +
            "in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, " +
            "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License " +
            "for the specific language governing permissions and limitations under the License."

    private const val MIT_TEXT =
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this software " +
            "and associated documentation files (the \"Software\"), to deal in the Software without " +
            "restriction, including without limitation the rights to use, copy, modify, merge, publish, " +
            "distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom " +
            "the Software is furnished to do so, subject to the following conditions: The above " +
            "copyright notice and this permission notice shall be included in all copies or substantial " +
            "portions of the Software. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND."

    private const val BSD_3_CLAUSE_TEXT =
        "Redistribution and use in source and binary forms, with or without modification, are permitted " +
            "provided that the conditions of the BSD 3-Clause License are met. THIS SOFTWARE IS PROVIDED " +
            "BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES " +
            "ARE DISCLAIMED. See https://opensource.org/licenses/BSD-3-Clause for the full text."
}
```

Note: `LicenseType.MIT` and `LicenseType.BSD_3_CLAUSE` aren't used by any entry in `all` right now — kept in the enum for future flexibility. Detekt/ktlint won't complain since the values are enum members, not dead code.

- [ ] **Step 2: Create `LicensesScreen.kt`**

Create `app/src/main/java/com/moodified/app/presentation/support/LicensesScreen.kt`:
```kotlin
package com.moodified.app.presentation.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.presentation.support.data.License
import com.moodified.app.presentation.support.data.Licenses

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { LicensesHeader(onBack = onBack) }
        itemsIndexed(Licenses.all) { index, license ->
            LicenseRow(
                license = license,
                isExpanded = expandedIndex == index,
                onToggle = { expandedIndex = if (expandedIndex == index) null else index },
            )
        }
    }
}

@Composable
private fun LicensesHeader(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
                Text(
                    text = "LICENSES",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            fontSize = 10.sp,
                        ),
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Open-source",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Moodified is built on the shoulders of these projects.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LicenseRow(
    license: License,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = license.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Text(
                        text = "${license.version} · ${license.licenseType.name.replace('_', ' ').lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = Licenses.textFor(license.licenseType),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = license.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}
```

Note: the LazyColumn uses `itemsIndexed` (already listed in the imports above) rather than `items`, so each row can index-track which entry is currently expanded via `expandedIndex`.

- [ ] **Step 3: Register the route in `MoodifiedNavHost.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`, after the `composable(AppRoutes.Support.HELP) { ... }` block added in Task 3, add:
```kotlin
                composable(AppRoutes.Support.LICENSES) {
                    LicensesScreen(onBack = { navController.popBackStack() })
                }
```

Also add the import:
```kotlin
import com.moodified.app.presentation.support.LicensesScreen
```
Alphabetized into the existing import block.

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Detekt may report `LongMethod` on `LicensesScreen` — acceptable baseline addition.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/support/
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
```
If baseline updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "feat(support): add LicensesScreen with hand-rolled dependency list"
```
No `Co-Authored-By` trailer.

---

### Task 5: Build `AboutScreen` (no gesture yet)

Static full-screen AboutScreen with app metadata, mission text, credits, version label, and a MenuRow linking to LicensesScreen. The long-press gesture is not wired here — Task 9 adds it after the temp MoreScreen callback is gone. Route registered but not yet linked from Profile.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/support/AboutScreen.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `AppRoutes.Support.ABOUT` and `AppRoutes.Support.LICENSES` (Task 1, Task 4).
- Produces: `com.moodified.app.presentation.support.AboutScreen(onBack: () -> Unit, onNavigateToLicenses: () -> Unit)` — full-screen composable. The `onNavigateToDebugDrawer: (() -> Unit)?` parameter is added in Task 9, not here.

- [ ] **Step 1: Create `AboutScreen.kt`**

Create `app/src/main/java/com/moodified/app/presentation/support/AboutScreen.kt`:
```kotlin
package com.moodified.app.presentation.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.BuildConfig
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MilkWhite)
                .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item { AboutHeader(onBack = onBack) }
        item {
            Spacer(Modifier.height(8.dp))
            AboutMissionCard()
        }
        item {
            Spacer(Modifier.height(24.dp))
            AboutMadeWithSection()
        }
        item {
            Spacer(Modifier.height(24.dp))
            AboutLicensesRow(onClick = onNavigateToLicenses)
        }
        item {
            Spacer(Modifier.height(32.dp))
            AboutVersionLabel()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MilkWhite)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = DeepSage.copy(alpha = 0.08f)) {
                Text(
                    text = "ABOUT",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            fontSize = 10.sp,
                        ),
                    color = DeepSage,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Moodified",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A quiet companion for your emotional patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AboutMissionCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = SageSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text =
                "Moodified is a mood-tracking companion that combines your quick self-check-ins with " +
                    "quiet passive signals — activity, sleep, screen use — to help you notice patterns in " +
                    "how you feel. Everything runs on-device. Nothing leaves your phone unless you export it.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = TextSecondary,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun AboutMadeWithSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = "MADE WITH",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp,
                ),
            color = TextTertiary,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Compose", "Material 3", "Room", "Hilt", "Lottie", "WorkManager").forEach { name ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MilkDeep,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLicensesRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
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
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = DeepSage,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Open-source licenses", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                text = "The projects Moodified is built on",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AboutVersionLabel() {
    val buildLabel = if (BuildConfig.DEBUG) "debug" else "release"
    Text(
        text = "Version ${BuildConfig.VERSION_NAME} · $buildLabel",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = TextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}
```

- [ ] **Step 2: Register the route in `MoodifiedNavHost.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`, after the `composable(AppRoutes.Support.LICENSES) { ... }` block added in Task 4, add:
```kotlin
                composable(AppRoutes.Support.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate(AppRoutes.Support.LICENSES) },
                    )
                }
```

Also add the import:
```kotlin
import com.moodified.app.presentation.support.AboutScreen
```

- [ ] **Step 3: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Detekt may report `LongMethod` on `AboutScreen` — acceptable baseline addition.

- [ ] **Step 4: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/support/AboutScreen.kt
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
```
If baseline updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "feat(support): add AboutScreen with mission, credits, version label"
```
No `Co-Authored-By` trailer.

---

### Task 6: Build `FeedbackSheet`

Modal bottom sheet with an optional "include device info" checkbox and a Send button that fires an `ACTION_SENDTO` intent to `BuildConfig.FEEDBACK_EMAIL`. Not a route — the sheet is opened via state hoisted in the calling screen (Task 8 wires it from Profile).

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/support/FeedbackSheet.kt`

**Interfaces:**
- Consumes: `BuildConfig.FEEDBACK_EMAIL` (from Task 1), `BuildConfig.VERSION_NAME` (existing).
- Produces: `com.moodified.app.presentation.support.FeedbackSheet(onDismiss: () -> Unit)` — modal composable.

- [ ] **Step 1: Create `FeedbackSheet.kt`**

Create `app/src/main/java/com/moodified/app/presentation/support/FeedbackSheet.kt`:
```kotlin
package com.moodified.app.presentation.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodified.app.BuildConfig
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
fun FeedbackSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var includeDeviceInfo by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MilkWhite,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Send feedback",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    "This opens your email app with a pre-filled message to the Moodified team. " +
                        "Nothing sends until you tap send there.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            DeviceInfoToggle(
                checked = includeDeviceInfo,
                onCheckedChange = { includeDeviceInfo = it },
            )
            Spacer(Modifier.height(24.dp))
            SendButton(
                onClick = {
                    val intent = buildFeedbackIntent(includeDeviceInfo)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
            )
        }
    }
}

@Composable
private fun DeviceInfoToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onCheckedChange(!checked) },
        color = SageSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (checked) DeepSage else MilkDeep),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MilkWhite,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(end = 4.dp)) {
                Text(
                    text = "Include device info",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
                Text(
                    text = "Android version, device model, build type",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SendButton(onClick: () -> Unit) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
        color = DeepSage,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = "Open in email app",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = MilkWhite,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun buildFeedbackIntent(includeDeviceInfo: Boolean): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${BuildConfig.FEEDBACK_EMAIL}")
        putExtra(Intent.EXTRA_SUBJECT, "Moodified v${BuildConfig.VERSION_NAME} feedback")
        if (includeDeviceInfo) {
            val deviceInfo =
                buildString {
                    appendLine()
                    appendLine()
                    appendLine("---")
                    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Build: ${if (BuildConfig.DEBUG) "debug" else "release"} v${BuildConfig.VERSION_NAME}")
                }
            putExtra(Intent.EXTRA_TEXT, deviceInfo)
        }
    }

```

Note: if ktlint flags any import in the block above as unused after the code lands (e.g., a color from the theme that turned out unnecessary), remove it. The list is meant to over-approximate what's referenced by the function bodies below — err on the side of trimming, not adding.

- [ ] **Step 2: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. `FeedbackSheet` is not yet called anywhere, so it's built but unreferenced — Kotlin allows unused public functions, ktlint should not complain. Detekt may report `LongMethod` — acceptable baseline addition.

- [ ] **Step 3: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/support/FeedbackSheet.kt
```
If baseline updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "feat(support): add FeedbackSheet with device-info toggle and mailto intent"
```
No `Co-Authored-By` trailer.

---

### Task 7: Rename `more/` → `profile/` (pure rename, no behavior change)

Move package + class + route names from More to Profile. Zero behavioral change beyond names. This task deliberately does NOT restructure sections or delete the temp Debug Drawer callback — that's Task 8. Splitting the rename from the restructure keeps each commit small and reviewable.

**Files:**
- Move: `app/src/main/java/com/moodified/app/presentation/more/MoreScreen.kt` → `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt`
- Move: `app/src/main/java/com/moodified/app/presentation/more/MoreViewModel.kt` → `app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `com.moodified.app.core.navigation.AppRoutes.Profile : AppRoutes("profile")` (replaces `AppRoutes.More`).
  - `com.moodified.app.presentation.profile.ProfileScreen(...)` (identical signature to `MoreScreen` in this task — parameter cleanup happens in Task 8).
  - `com.moodified.app.presentation.profile.ProfileViewModel` (identical body to `MoreViewModel`).
  - `com.moodified.app.presentation.navigation.BottomNavItem.Profile` with `label = "Profile"`, `selectedIcon = Icons.Rounded.Person`, `unselectedIcon = Icons.Outlined.Person`.

- [ ] **Step 1: Create the new directory and move the two files**

Use IDE-assisted rename if working in Android Studio (Refactor → Rename). Otherwise, run:
```
mkdir -p app/src/main/java/com/moodified/app/presentation/profile
git mv app/src/main/java/com/moodified/app/presentation/more/MoreScreen.kt app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt
git mv app/src/main/java/com/moodified/app/presentation/more/MoreViewModel.kt app/src/main/java/com/moodified/app/presentation/profile/ProfileViewModel.kt
rmdir app/src/main/java/com/moodified/app/presentation/more
```

- [ ] **Step 2: Update package declarations and class names inside the moved files**

In `ProfileScreen.kt`:
1. Line 1 `package com.moodified.app.presentation.more` → `package com.moodified.app.presentation.profile`
2. Line 56 `fun MoreScreen(` → `fun ProfileScreen(`
3. Line 65 `viewModel: MoreViewModel = hiltViewModel()` → `viewModel: ProfileViewModel = hiltViewModel()`
4. Line 471 `private fun MoreHeader()` → `private fun ProfileHeader()`
5. Line 205 `item { MoreHeader() }` → `item { ProfileHeader() }`

In `ProfileViewModel.kt`:
1. Line 1 `package com.moodified.app.presentation.more` → `package com.moodified.app.presentation.profile`
2. Line 46 `class MoreViewModel` → `class ProfileViewModel`

- [ ] **Step 3: Update `AppRoutes.kt`**

In `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`, line 23:
```
    data object More : AppRoutes("more")
```
becomes:
```
    data object Profile : AppRoutes("profile")
```

- [ ] **Step 4: Update `BottomNavItem.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt`:

1. Swap the imports (remove `MoreHoriz`, add `Person`):
```kotlin
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Person
```
Remove:
```kotlin
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.MoreHoriz
```

2. Replace the `More` data object (lines 53-58) with:
```kotlin
    data object Profile : BottomNavItem(
        route = AppRoutes.Profile.route,
        label = "Profile",
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
    )
```

- [ ] **Step 5: Update `MoodifiedNavHost.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`:

1. Import: line 47 `import com.moodified.app.presentation.more.MoreScreen` → `import com.moodified.app.presentation.profile.ProfileScreen`

2. `navItems` list at lines 57-64:
```
        BottomNavItem.More,
```
becomes:
```
        BottomNavItem.Profile,
```

3. The composable block currently at lines 161-184:
```kotlin
                composable(AppRoutes.More.route) {
                    MoreScreen(
                        onNavigateToActivityInsight = ...,
                        ...
                    )
                }
```
becomes (this task: identical body, just renamed function + route — do NOT trim parameters yet, that's Task 8):
```kotlin
                composable(AppRoutes.Profile.route) {
                    ProfileScreen(
                        onNavigateToActivityInsight = { navController.navigate(AppRoutes.Insight.withTab(InsightTab.ACTIVITY)) },
                        onNavigateToSleepInsight = { navController.navigate(AppRoutes.Insight.withTab(InsightTab.SLEEP)) },
                        onNavigateToScreenUseInsight = { navController.navigate(AppRoutes.Insight.withTab(InsightTab.SCREEN_USE)) },
                        onNavigateToPrivacy = { navController.navigate(AppRoutes.Privacy.route) },
                        onNavigateToDebugDrawer =
                            debugNavRegistrar.drawerRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                        onNavigateToActivityMonitor =
                            debugNavRegistrar.activityMonitorRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                        onNavigateToSleepMonitor =
                            debugNavRegistrar.sleepMonitorRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                        onNavigateToInteractionMonitor =
                            debugNavRegistrar.interactionMonitorRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                    )
                }
```

- [ ] **Step 6: Grep-verify no lingering `More` references**

Run:
```
grep -rnE "MoreScreen|MoreViewModel|AppRoutes\.More\b|BottomNavItem\.More\b|presentation\.more\b" app/src/
```
Expected: **zero matches** (all References updated).

- [ ] **Step 7: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. The app behaves identically to before; only names changed.

- [ ] **Step 8: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/profile/
git add app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt
git add app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
```
(The `git mv`s from Step 1 are staged automatically by `git add` of the new directory since `git mv` recorded them, but confirm with `git status`.)

Then:
```
git commit -m "refactor(nav): rename More to Profile (package, class, route, BottomNavItem)"
```
No `Co-Authored-By` trailer.

---

### Task 8: Restructure Profile sections + delete temp Debug Drawer callback

Rewrite the `ProfileScreen` body from its current shape (header + Tracking + Your Insights + Privacy & Data + Developer Tools + Debug Data) into the spec §3.1 grouping: **Tracking / Support / Data & Privacy** (+ retained `ENABLE_MOCK_DATA`-gated Debug Data section). Delete the "Your Insights" and "Developer Tools" sections. Delete `onNavigateToActivityInsight`, `onNavigateToSleepInsight`, `onNavigateToScreenUseInsight`, `onNavigateToDebugDrawer`, `onNavigateToActivityMonitor`, `onNavigateToSleepMonitor`, `onNavigateToInteractionMonitor` from the signature. Wire the Support MenuRows (Help / About / Send feedback). This is the commit where the **temp Debug Drawer menu row disappears**.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `AppRoutes.Support.HELP`, `AppRoutes.Support.ABOUT`, `AppRoutes.Privacy.route` (existing); `FeedbackSheet` from Task 6.
- Produces:
  - `ProfileScreen` new signature:
    ```kotlin
    @Composable
    fun ProfileScreen(
        onNavigateToPrivacy: () -> Unit,
        onNavigateToHelp: () -> Unit,
        onNavigateToAbout: () -> Unit,
        viewModel: ProfileViewModel = hiltViewModel(),
    )
    ```
  - `FeedbackSheet` modal state hoisted inside `ProfileScreen` via `rememberSaveable` — no parent-provided callback.

- [ ] **Step 1: Rewrite `ProfileScreen` signature and header**

In `ProfileScreen.kt`:

1. Replace the current signature (lines 55-66) with:
```kotlin
@Composable
fun ProfileScreen(
    onNavigateToPrivacy: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFeedback by rememberSaveable { mutableStateOf(false) }
```

2. Update `ProfileHeader` (currently `MoreHeader` renamed in Task 7). Replace the "MORE" pill text and title:
   - Line ~482 `text = "MORE",` → `text = "PROFILE",`
   - Line ~495 `text = "More",` → `text = "Profile",`
   - Line ~501 subtitle: `text = "Settings, insights, and data management.",` → `text = "Your settings, support, and data.",`

- [ ] **Step 2: Rewrite the section content**

Delete the "Your Insights" section entirely (currently lines 255-283 in the pre-rewrite file, referring to `MenuRow`s that call `onNavigateToActivityInsight`, `onNavigateToSleepInsight`, `onNavigateToScreenUseInsight`).

Delete the "Developer Tools" section entirely (currently lines 299-350 — the block starting `val devNavAvailable = ...` and its enclosing `if (devNavAvailable) { item { ... } }`). This is where the temp Debug Drawer MenuRow lived — it goes away with this block.

Between the existing "Tracking Preferences" section (lines 207-253 — keep verbatim, three SwitchRows) and the existing "Privacy & Data" section (lines 285-297 — keep verbatim, single MenuRow), **insert a new "Support" section**:

```kotlin
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Support")

            MenuRow(
                icon = Icons.Rounded.HelpOutline,
                iconBgColor = DeepSage.copy(alpha = 0.12f),
                iconTint = DeepSage,
                title = "Help",
                description = "Answers to common questions about Moodified",
                onClick = onNavigateToHelp,
            )
            MenuRow(
                icon = Icons.Rounded.Info,
                iconBgColor = ValenceNeutral.copy(alpha = 0.12f),
                iconTint = ValenceNeutral,
                title = "About Moodified",
                description = "App version, mission, and open-source licenses",
                onClick = onNavigateToAbout,
            )
            MenuRow(
                icon = Icons.Rounded.Email,
                iconBgColor = ValencePositive.copy(alpha = 0.12f),
                iconTint = ValencePositive,
                title = "Send feedback",
                description = "Report a bug or suggest an improvement",
                onClick = { showFeedback = true },
            )
        }
```

Rename the "Privacy & Data" section header text from `"Privacy & Data"` to `"Data & Privacy"` (matches spec §3.1 section grouping).

Retain the "Debug Data" section (currently lines 352-385) verbatim. It's `ENABLE_MOCK_DATA`-gated and remains a debug-build-only surface for mock data seeding — orthogonal to the Debug Drawer path we just deleted.

- [ ] **Step 3: Add required icon imports and render the FeedbackSheet modal**

Add these imports to `ProfileScreen.kt`:
```kotlin
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.saveable.rememberSaveable
import com.moodified.app.presentation.support.FeedbackSheet
```

Remove now-unused imports (Task 7 preserved them; they can go now):
```kotlin
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
```
Wait — `DataArray` and `NotificationsActive` are still referenced by the retained "Debug Data" section (`MenuRow(icon = Icons.Rounded.NotificationsActive, ...)` and `MenuRow(icon = Icons.Rounded.DataArray, ...)`). Keep those imports. Only remove `DirectionsRun`, `Bedtime`, `PhoneAndroid` if the deleted Insights and Developer Tools sections were their only users. Verify by grepping the modified file: `grep -n "DirectionsRun\|Bedtime\|PhoneAndroid" app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt`. If zero matches remain in the file body after the rewrite, remove those three imports. If any remain (they were also used elsewhere in the file — unlikely but check), keep them.

Also remove `ArousalHigh` from the theme imports if unused after "Developer Tools" section deletion. Grep to confirm.

At the very end of the `ProfileScreen` composable body (after the `LazyColumn` closing brace but still inside the outer function), add the FeedbackSheet rendering:
```kotlin
    if (showFeedback) {
        FeedbackSheet(onDismiss = { showFeedback = false })
    }
```

- [ ] **Step 4: Delete the removed parameters and update `MoodifiedNavHost.kt`**

In `MoodifiedNavHost.kt`, replace the current `composable(AppRoutes.Profile.route) { ... }` block (post-Task-7 shape) with the trimmed shape:
```kotlin
                composable(AppRoutes.Profile.route) {
                    ProfileScreen(
                        onNavigateToPrivacy = { navController.navigate(AppRoutes.Privacy.route) },
                        onNavigateToHelp = { navController.navigate(AppRoutes.Support.HELP) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.Support.ABOUT) },
                    )
                }
```

- [ ] **Step 5: Grep-verify no stragglers**

Run:
```
grep -rnE "onNavigateToActivityInsight|onNavigateToSleepInsight|onNavigateToScreenUseInsight|onNavigateToActivityMonitor|onNavigateToSleepMonitor|onNavigateToInteractionMonitor|onNavigateToDebugDrawer" app/src/
```
Expected: **zero matches**. All the callbacks the temp button relied on are gone.

Also verify the Debug Drawer temp button strings are gone:
```
grep -rnE "\"Debug Drawer\"|\"Developer Tools\"|\"Activity Monitor\"|\"Sleep Monitor\"|\"Interaction Monitor\"" app/src/
```
Expected: **zero matches in `presentation/profile/`**. Some matches inside `presentation/devtools/` (the actual Debug Drawer screen's own labels) are expected and correct.

- [ ] **Step 6: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Detekt LongMethod on the `ProfileScreen` main composable is expected — acceptable baseline addition (baseline should already have a `LongMethod` entry for `MoreScreen`; regenerating should shift it under the new name `ProfileScreen`, and the ProfileScreen body is smaller now anyway).

- [ ] **Step 7: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
```
If baseline updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "refactor(profile): restructure sections, delete temp Debug Drawer callback"
```
No `Co-Authored-By` trailer.

---

### Task 9: Wire About long-press → Debug Drawer

Add the `onNavigateToDebugDrawer: (() -> Unit)?` parameter to `AboutScreen`, apply it to the version label via `combinedClickable(onLongClick = ...)`, and wire it from `MoodifiedNavHost` using the same `debugNavRegistrar.drawerRoute?.let { ... }` pattern the temp callback used. Debug builds land on the Debug Drawer via version-label long-press; release builds see no long-press detector at all.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/support/AboutScreen.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`

**Interfaces:**
- Consumes: `debugNavRegistrar.drawerRoute` (existing).
- Produces:
  - New `AboutScreen` signature:
    ```kotlin
    @Composable
    fun AboutScreen(
        onBack: () -> Unit,
        onNavigateToLicenses: () -> Unit,
        onNavigateToDebugDrawer: (() -> Unit)? = null,
    )
    ```
    Default `null` matters — release-build call sites can omit the parameter; debug-build call sites populate it.

- [ ] **Step 1: Update `AboutScreen` signature and thread the callback**

In `AboutScreen.kt`:

1. Change the signature (currently 2-param) to 3-param with the nullable callback:
```kotlin
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToDebugDrawer: (() -> Unit)? = null,
) {
    ...
```

2. Pass `onNavigateToDebugDrawer` down to `AboutVersionLabel` (currently invoked at `item { ... AboutVersionLabel() ...}` inside the `LazyColumn` — replace with):
```kotlin
        item {
            Spacer(Modifier.height(32.dp))
            AboutVersionLabel(onLongPress = onNavigateToDebugDrawer)
            Spacer(Modifier.height(24.dp))
        }
```

3. Update `AboutVersionLabel` signature and apply `combinedClickable`:
```kotlin
@Composable
private fun AboutVersionLabel(onLongPress: (() -> Unit)?) {
    val buildLabel = if (BuildConfig.DEBUG) "debug" else "release"
    Text(
        text = "Version ${BuildConfig.VERSION_NAME} · $buildLabel",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = TextTertiary,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 24.dp),
    )
}
```

4. Add the `combinedClickable` import:
```kotlin
import androidx.compose.foundation.combinedClickable
```

**Design note for the implementer:** `onClick = {}` is the no-op tap handler. Compose's `combinedClickable` requires an `onClick` — passing `{}` gives a tap that does nothing visually. This is intentional: we don't want the version label to react to taps at all, only long-press. The ripple appears briefly on tap (Material behavior) but no navigation occurs. If desired later, `indication = null` and `interactionSource = remember { MutableInteractionSource() }` can suppress the ripple, but for portfolio-quality the default is fine.

- [ ] **Step 2: Wire the callback in `MoodifiedNavHost.kt`**

In `MoodifiedNavHost.kt`, replace the `composable(AppRoutes.Support.ABOUT) { ... }` block from Task 5:
```kotlin
                composable(AppRoutes.Support.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate(AppRoutes.Support.LICENSES) },
                    )
                }
```
with:
```kotlin
                composable(AppRoutes.Support.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate(AppRoutes.Support.LICENSES) },
                        onNavigateToDebugDrawer =
                            debugNavRegistrar.drawerRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                    )
                }
```

- [ ] **Step 3: Verify release-build zero-surface property**

Run:
```
./gradlew assembleRelease
```
This builds the release APK. Then verify by inspecting the R8-minified output isn't strictly necessary here — the `DebugNavRegistrar` release-stub returns `null` for `drawerRoute` (see `app/src/release/java/com/moodified/app/di/ReleaseDevToolsModule.kt:37`), so at runtime `onNavigateToDebugDrawer` is `null` and `combinedClickable` attaches no long-press detector. Confirm the release build succeeds and no compilation warnings reference debug-only classes.

Also verify the release stub is what's actually bound in release: grep for the `DebugDrawerScreen` reference chain in release:
```
grep -rn "DebugDrawerScreen" app/src/release/ app/src/main/
```
Expected: **zero matches** (all references are under `app/src/debug/`).

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/support/AboutScreen.kt
git add app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt
```
If baseline updated, also stage `config/detekt/detekt-baseline.xml`.

Then:
```
git commit -m "feat(support): wire About version-label long-press to Debug Drawer"
```
No `Co-Authored-By` trailer.

---

### Task 10: Final verification and readiness report

**Files:** none (git-only + verification).

**Interfaces:** none.

- [ ] **Step 1: Full gradle sanity pass from clean**

Run:
```
./gradlew clean assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green from a clean build. If anything fails, stop — the branch is not ready.

- [ ] **Step 2: Scope-fence audit**

Run:
```
git diff --stat main..HEAD
```
Expected files (or roughly this scope):
- `app/build.gradle.kts`
- `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`
- `app/src/main/java/com/moodified/app/presentation/support/` (new)
- `app/src/main/java/com/moodified/app/presentation/profile/` (renamed from `more/`)
- `app/src/main/java/com/moodified/app/presentation/insight/tabs/OverviewTab.kt`
- `app/src/main/java/com/moodified/app/presentation/insight/tabs/overview/` (new)
- `app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt`
- `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`
- `docs/plans/2026-09-16-phase-4-support-surface.md` (this plan itself)
- Possibly `config/detekt/detekt-baseline.xml`

Any file outside this scope is a scope violation — investigate. In particular, **`presentation/devtools/` should have zero diff**.

Run:
```
git diff main..HEAD -- app/src/main/java/com/moodified/app/presentation/devtools/ app/src/debug/ app/src/release/
```
Expected: **empty diff** across all three paths. Phase 3 owns devtools; this phase doesn't touch it.

- [ ] **Step 3: Trailer audit**

Run:
```
git log main..HEAD --format='%h %s%n%b' | grep -i "co-authored" || echo "clean"
```
Expected: `clean`. If any `Co-Authored-By` trailer appears, **stop and roll the offending commit**. Use interactive rebase or `git commit --amend` to strip the trailer, then re-run this check. Do NOT run `git filter-branch` — the per-commit hygiene requirement means the offending trailer should not have been introduced in the first place; catching it here means a subagent violated the constraint and the commit should be rewritten cleanly.

- [ ] **Step 4: Commit count sanity**

Run:
```
git log main..HEAD --oneline
```
Expected: exactly 9 commits (Task 1 through Task 9). Task 0 makes no commit, Task 10 makes no commit. If the count differs, investigate — an extra commit likely indicates a subagent introduced an unplanned fix or "cleanup" commit that should be squashed or reviewed.

- [ ] **Step 5: Report readiness**

Report to the user:
- Branch: `phase-4-support-surface`
- Commits: 9 (Tasks 1-9, no phase-0 or phase-10 commits)
- Diff scope: build config, navigation, support/, profile/ (renamed), insight/tabs/overview/ split
- Trailers: clean (no `Co-Authored-By`)
- Greens: `assembleDebug` `assembleRelease` `testDebugUnitTest` `ktlintCheck` `detekt` (from clean)
- **Owed: manual smoke test on physical device** — Phases 2 + 3 + 3.5 + 4 combined. Test plan:
  1. Fresh install → land on Check In (bottom bar shows Check-in / Insight / QuickLog / Care / Profile).
  2. Bottom nav: Profile → header shows "Profile" not "More" → sections Tracking / Support / Data & Privacy appear.
  3. Profile → Help → 8 FAQ articles → search filters → expand/collapse works → back returns to Profile.
  4. Profile → About Moodified → sees mission text, "Made with" chips, "Open-source licenses" MenuRow, version label at bottom.
  5. About → tap Licenses MenuRow → LicensesScreen shows 15 entries → expand any row shows Apache-2.0 text and URL → back returns to About.
  6. About → **long-press version label** → **on debug build**: Debug Drawer opens. **On release build**: nothing happens.
  7. Profile → Send feedback → sheet opens → toggle device info → tap Open in email → email app opens with pre-filled subject.
  8. Bottom nav: Insight → Overview tab → mood-line chart, today's mood card, mood stability card all render with visual parity (post-split).
  9. Bottom nav: Check-in, Calendar, Care, QuickLog all still work.

Do NOT merge automatically — the user runs the merge themselves after reviewing.

---

## Follow-ups (out of Phase 4 scope)

- **Onboarding + Notifications inbox + bottom-nav reorg (Care ↔ Calendar swap)** — Phase 5 territory per spec §6.
- **Deep-link `support/help/{articleId}`** — spec §3.1 mentions it; not needed until notifications open articles. Defer to Phase 5+ when inbox lands.
- **`FEEDBACK_EMAIL` real address** — placeholder `feedback@moodified.app` ships in Phase 4; replace with a real recipient before any external distribution.
- **Manual smoke test on device** — owed at end of Phase 4 covering Phases 2+3+3.5+4. If it uncovers a regression, that becomes a follow-up work item (fix in a hot-fix branch, don't roll Phase 4).
- **Full localization / mass string extraction to `strings.xml`** — non-goal per spec §1. All Phase 4 strings are hardcoded English.
