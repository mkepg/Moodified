# Phase 5c — Bottom-Nav Reorg + Today's Care Card Implementation Plan

**Goal:** Swap Care ↔ Calendar in the bottom bar (Calendar becomes a peer tab, Care becomes a full-screen route reachable from Check In), surface a single top-ranked Care intervention inline on Check In as the "Today's Care" card, and rename the ProfileScreen section header "Tracking Preferences" → "Tracking".

**Architecture:**
- **Bottom nav** — `BottomNavItem.Care` is deleted; a new `BottomNavItem.Calendar` slot occupies its place with `Icons.Rounded.CalendarMonth` iconography. `MoodifiedNavHost.navItems` list is reordered to `[CheckIn, Calendar, QuickLog, Insight, Profile]` matching spec §3.1 diagram.
- **Care route** — `AppRoutes.Care` is renamed to `care/all` (path change from `"care"` to `"care/all"`). The screen composable `CareScreen()` remains unchanged internally; only its route registration moves from a bottom-tab destination to a full-screen route (add to `fullScreenRoutes`).
- **`ObserveTopCareInterventionUseCase`** — new domain use case under `domain/usecase/intervention/`. Delegates to `CareEvaluationEngine` after combining the same eight upstream flows the existing `CareViewModel` already combines. Returns `Flow<InterventionAction?>` — the top-priority action from the sorted list, or `null` when the engine produces an empty list or upstream inputs are null. Used by both `CheckInViewModel` (for the card) and — as a plan-scope optional refactor — `CareViewModel` (to eliminate duplicated combine logic). This plan reuses it in `CheckInViewModel` only; the `CareViewModel` refactor is a Phase-6 follow-up if it lands cleanly, otherwise deferred.
- **Today's Care card** — a new `TodaysCareCard(action: InterventionAction, onOpenCareAll: () -> Unit)` composable rendered on `CheckInScreen`. When `topCareIntervention` is null (no ranked action), the card is not rendered at all — no placeholder.
- **Section header rename** — one-line change: `"Tracking Preferences"` → `"Tracking"` in `ProfileScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines/Flow. No new dependencies. No schema change.

**Spec:** [docs/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.1 (bottom-nav diagram + Today's Care card + Profile section labels), §6 Phase 5. Fixes the parked "Tracking Preferences" label minor noted in the Phase 4 kickoff.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- Green gate per task: `./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt` — all green before commit.
- Detekt baseline additions allowed **only** in `FunctionNaming` / `LongParameterList` / `LongMethod` / `MatchingDeclarationName`. Wrap long constructor calls at authoring time.
- Feature branch: `phase-5c-nav-reorg` (created in Task 0), cut from `main` **after** Phase 5b has merged.- **Route rename is a breaking change internally.** No external deep-link URI targets `moodified://care`, so no manifest change is needed — but if any *code* references `AppRoutes.Care.route` string equality (e.g., a `when` branch), those must be updated to `AppRoutes.Care.route` in place (the constant value changed, not the field name).
- **`ObserveTopCareInterventionUseCase` MUST NOT duplicate the 8-flow combine.** It takes a `CareEvaluationEngine` and the eight source repositories the engine's `invoke(...)` requires, combines them once, calls the engine, returns `first()` of the sorted list. If the exact shape of upstream flow names in `CareViewModel` diverges from what this plan spells out, defer to the actual `CareViewModel` — copy its combine block verbatim into the use case.
- **No push to origin.**
- **Scope fences:**
  - No touching `presentation/onboarding/` (Phase 5b).
  - No touching `presentation/inbox/` (Phase 5a).
  - No touching `presentation/devtools/` or `ProfileViewModel.triggerTestMicroPrompt` (Phase 5d).
  - Do not simplify `CareViewModel` (defer to Phase 6 follow-up).
  - `CareScreen`'s internal composables must not change — only its route registration and the removed bottom-bar entry.

---

### Task 0: Create the feature branch and verify pre-phase greens

**Files:** none.

- [ ] **Step 1: Ensure Phase 5b is on main**

Run:
```
git status
git checkout main
git pull --ff-only
git log --oneline -6
```
Expected: Phase 5b's 4 commits sit at the tip of `main`, ending with `feat(onboarding): route onboarding at cold start via hasCompletedOnboarding flag`.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-5c-nav-reorg
```

- [ ] **Step 3: Sanity-check the pre-phase greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

No commit for this task.

---

### Task 1: Swap Care ↔ Calendar in `BottomNavItem` + reorder `navItems`

Delete `BottomNavItem.Care`, add `BottomNavItem.Calendar`, reorder the list. Then update `AppRoutes.Care.route` from `"care"` to `"care/all"`.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt` (delete Care, add Calendar)
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` (reorder `navItems`, register `AppRoutes.Care.route` as full-screen, register a `AppRoutes.Calendar.route` bottom-tab composable, wire Check-In's `onViewCalendar` to `navigate(AppRoutes.Calendar.route)` — already does)
- Modify: `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt` (change `Care` value from `"care"` to `"care/all"`)
- Modify: `app/src/main/java/com/moodified/app/presentation/calendar/CalendarScreen.kt` (make `onBack` optional / removable — Calendar is now a tab, not a full-screen route with back)

**Interfaces:**
- Consumes: nothing external.
- Produces:
  - `BottomNavItem.Calendar` — new; label "Calendar", icon `Icons.Rounded.CalendarMonth` selected + `Icons.Outlined.CalendarMonth` unselected.
  - `BottomNavItem.Care` — deleted.
  - `AppRoutes.Care.route` — value changes to `"care/all"`. Field name is unchanged.

- [ ] **Step 1: Update `BottomNavItem.kt`**

Replace `app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt` body with:
```kotlin
package com.moodified.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.moodified.app.core.navigation.AppRoutes

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isAction: Boolean = false,
) {
    data object CheckIn : BottomNavItem(
        route = AppRoutes.CheckIn.route,
        label = "Check-in",
        selectedIcon = Icons.Rounded.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder,
    )

    data object Calendar : BottomNavItem(
        route = AppRoutes.Calendar.route,
        label = "Calendar",
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
    )

    data object QuickLog : BottomNavItem(
        route = AppRoutes.QuickLog.route,
        label = "",
        selectedIcon = Icons.Rounded.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        isAction = true,
    )

    data object Insight : BottomNavItem(
        route = AppRoutes.Insight.route,
        label = "Insight",
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
    )

    data object Profile : BottomNavItem(
        route = AppRoutes.Profile.route,
        label = "Profile",
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
    )
}
```
Note: `SelfImprovement` icons are removed. Care no longer has a bottom-tab presence.

- [ ] **Step 2: Rename `AppRoutes.Care` route value**

In `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`, change line 21:
```kotlin
    data object Care : AppRoutes("care/all")
```
The field name stays `Care`; the string value changes to `"care/all"` matching spec §3.1's full-screen route.

- [ ] **Step 3: Update `MoodifiedNavHost` — nav items, full-screen set, Care composable, Calendar composable**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`:

3a. Update the `navItems` list (lines 60-67) to the new ordering:
```kotlin
private val navItems =
    listOf(
        BottomNavItem.CheckIn,
        BottomNavItem.Calendar,
        BottomNavItem.QuickLog,
        BottomNavItem.Insight,
        BottomNavItem.Profile,
    )
```

3b. Add `AppRoutes.Care.route` to `fullScreenRoutes` (currently the set includes Onboarding + Inbox + Support + Privacy + Calendar):
```kotlin
    val fullScreenRoutes =
        remember(debugNavRegistrar) {
            debugNavRegistrar.routes +
                setOf(
                    AppRoutes.Privacy.route,
                    AppRoutes.Support.HELP,
                    AppRoutes.Support.ABOUT,
                    AppRoutes.Support.LICENSES,
                    AppRoutes.Inbox.route,
                    AppRoutes.Onboarding.route,
                    AppRoutes.Care.route,
                )
        }
```
Note: `AppRoutes.Calendar.route` is REMOVED from `fullScreenRoutes` — Calendar is now a bottom-tab destination so the bottom bar should render on it.

3c. Update the Care composable registration. The old registration at `composable(AppRoutes.Care.route)` (currently line 164) previously served the bottom tab. Now it's a full-screen route. Add back-button wiring:
```kotlin
                composable(AppRoutes.Care.route) {
                    CareScreen()
                }
```
(No signature change to `CareScreen()` — the current composable doesn't take an `onBack` callback; it's reachable via the system back gesture which pops the back stack correctly. If `CareScreen` needs an explicit back button in the top-bar area to match other full-screen screens, that's a follow-up polish item.)

3d. Update the Calendar composable — it's now a bottom-tab, no `onBack` needed. Currently line 174:
```kotlin
                composable(AppRoutes.Calendar.route) {
                    CalendarScreen(onBack = { navController.popBackStack() })
                }
```
Change to:
```kotlin
                composable(AppRoutes.Calendar.route) {
                    CalendarScreen()
                }
```
This requires `CalendarScreen`'s `onBack` param to default to `{}` (see Step 4).

3e. `CheckInScreen`'s `onViewCalendar` callback (line 146 area) already routes to `AppRoutes.Calendar.route` — no change needed there. Verify by reading the existing line.

- [ ] **Step 4: Make `CalendarScreen.onBack` optional**

In `app/src/main/java/com/moodified/app/presentation/calendar/CalendarScreen.kt`, find the composable signature. If it's declared as:
```kotlin
fun CalendarScreen(onBack: () -> Unit) {
```
Change to:
```kotlin
fun CalendarScreen(onBack: () -> Unit = {}) {
```
This is minimally invasive — the parameter still exists for any future full-screen usage (e.g., opening a date-specific detail view from Check In), but the bottom-tab call site can omit it.

If the file also renders a top-bar back button that calls `onBack`, keep it — with the default `{}`, it just does nothing when Calendar is the tab. Alternatively wrap the back-button render in `if (onBack !== {})` — but that's a reference-equality check that's unreliable. Best: keep the top-bar back button as-is; the no-op is silent. If the bottom-tab UX ends up showing a phantom back-arrow, that's a Phase 5c follow-up commit.

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If any file references `BottomNavItem.Care` (grep the codebase), delete those references — the type is now gone. If any file references `AppRoutes.Care.route == "care"`, update it.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/navigation/BottomNavItem.kt \
        app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt \
        app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt \
        app/src/main/java/com/moodified/app/presentation/calendar/CalendarScreen.kt

git commit -m "feat(nav): swap Care ↔ Calendar; Care becomes care/all full-screen route"
```

---

### Task 2: `ObserveTopCareInterventionUseCase`

New domain use case. Combines the same upstream flows the existing `CareViewModel` uses (via use cases, not repositories — the codebase's actual pattern), delegates to `CareEvaluationEngine` inside a `flatMapLatest` chain rooted on `midnightTickerFlow()`, and returns `Flow<InterventionAction?>` — the top-priority action from the engine's sorted result, or `null` when the list is empty.

**Files:**
- Create: `app/src/main/java/com/moodified/app/domain/usecase/intervention/ObserveTopCareInterventionUseCase.kt`

**Interfaces:**
- Consumes:
  - `BuildDailyBehaviorSnapshotUseCase` (produces `Flow<DailyBehaviorSnapshot>`)
  - `RuleBasedMoodInferenceEngine` (`operator fun invoke(snapshot): InferredMoodState`)
  - `CareEvaluationEngine` (existing — 8-param `suspend operator fun invoke`)
  - `ObserveActivitySignalUseCase`, `ObserveInteractionSignalUseCase`
  - `GetWeeklySleepSummariesUseCase`, `GetWeeklyActivitySummariesUseCase`, `GetWeeklyInteractionSummariesUseCase`, `GetMoodHistoryUseCase`
  - `midnightTickerFlow()` from `com.moodified.app.core.utils`
- Produces: `ObserveTopCareInterventionUseCase.invoke(): Flow<InterventionAction?>`.

**Reference:** the exact combine-shape lives in `app/src/main/java/com/moodified/app/presentation/care/CareViewModel.kt` lines 62-155 (uiState builder). This use case reuses that structure minus the CareUiState assembly + dismissed-ids / active-domain state — the only output is `actions.firstOrNull()`.

- [ ] **Step 1: Create `ObserveTopCareInterventionUseCase.kt`**

Create `app/src/main/java/com/moodified/app/domain/usecase/intervention/ObserveTopCareInterventionUseCase.kt`:
```kotlin
package com.moodified.app.domain.usecase.intervention

import com.moodified.app.core.utils.midnightTickerFlow
import com.moodified.app.domain.model.activity.ActivityDailySummary
import com.moodified.app.domain.model.interaction.InteractionDailySummary
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.model.mood.MoodEntry
import com.moodified.app.domain.model.sleep.DailySleepSummary
import com.moodified.app.domain.usecase.activity.GetWeeklyActivitySummariesUseCase
import com.moodified.app.domain.usecase.activity.ObserveActivitySignalUseCase
import com.moodified.app.domain.usecase.inference.BuildDailyBehaviorSnapshotUseCase
import com.moodified.app.domain.usecase.inference.RuleBasedMoodInferenceEngine
import com.moodified.app.domain.usecase.interaction.GetWeeklyInteractionSummariesUseCase
import com.moodified.app.domain.usecase.interaction.ObserveInteractionSignalUseCase
import com.moodified.app.domain.usecase.mood.GetMoodHistoryUseCase
import com.moodified.app.domain.usecase.sleep.GetWeeklySleepSummariesUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveTopCareInterventionUseCase
    @Inject
    constructor(
        private val buildDailyBehaviorSnapshot: BuildDailyBehaviorSnapshotUseCase,
        private val ruleBasedMoodInferenceEngine: RuleBasedMoodInferenceEngine,
        private val careEvaluationEngine: CareEvaluationEngine,
        private val observeActivitySignal: ObserveActivitySignalUseCase,
        private val observeInteractionSignal: ObserveInteractionSignalUseCase,
        private val getWeeklySleepSummaries: GetWeeklySleepSummariesUseCase,
        private val getWeeklyActivitySummaries: GetWeeklyActivitySummariesUseCase,
        private val getWeeklyInteractionSummaries: GetWeeklyInteractionSummariesUseCase,
        private val getMoodHistory: GetMoodHistoryUseCase,
    ) {
        private data class HistoricalData(
            val sleep: List<DailySleepSummary>,
            val activity: List<ActivityDailySummary>,
            val interaction: List<InteractionDailySummary>,
            val moods: Map<LocalDate, List<MoodEntry>>,
        )

        operator fun invoke(): Flow<InterventionAction?> =
            midnightTickerFlow()
                .flatMapLatest { today ->
                    val snapshotFlow = buildDailyBehaviorSnapshot(today)

                    val historicalDataFlow =
                        combine(
                            getWeeklySleepSummaries(today),
                            getWeeklyActivitySummaries(today),
                            getWeeklyInteractionSummaries(today),
                            getMoodHistory(),
                        ) { sleep, activity, interaction, moods ->
                            HistoricalData(sleep, activity, interaction, moods)
                        }

                    val synchronizedDbFlow =
                        combine(snapshotFlow, historicalDataFlow) { snapshot, history ->
                            Pair(snapshot, history)
                        }.debounce(250)

                    combine(
                        synchronizedDbFlow,
                        observeActivitySignal(),
                        observeInteractionSignal(),
                    ) { (snapshot, history), activity, interaction ->
                        val moodState = ruleBasedMoodInferenceEngine(snapshot)
                        val actions =
                            careEvaluationEngine(
                                moodState = moodState,
                                snapshot = snapshot,
                                liveActivity = activity,
                                liveInteraction = interaction,
                                historicalSleep = history.sleep,
                                historicalActivity = history.activity,
                                historicalInteraction = history.interaction,
                                historicalMoods = history.moods.values.flatten(),
                            )
                        actions.firstOrNull()
                    }
                }
    }
```
This mirrors `CareViewModel.uiState`'s combine graph verbatim minus the `_activeDomain` / `_dismissedIds` / `refreshTrigger` extras and the `CareUiState` assembly. The `combine`-lambda's suspending context is legitimate — `combine` in Kotlin coroutines evaluates its transform inside a suspending scope, which is why `CareViewModel` can call the `suspend` `careEvaluationEngine(...)` directly and this use case can too.

- [ ] **Step 3: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. If Hilt cannot resolve the constructor (e.g., a repository doesn't have the accessor method used), the compile error will point directly at the line — read `CareViewModel` again for the correct accessor names.

- [ ] **Step 4: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/domain/usecase/intervention/ObserveTopCareInterventionUseCase.kt

git commit -m "feat(care): add ObserveTopCareInterventionUseCase wrapping CareEvaluationEngine"
```

---

### Task 3: Today's Care card on Check-in + `CheckInViewModel` integration

`CheckInViewModel` gets a `topCareIntervention: StateFlow<InterventionAction?>` from the new use case. `CheckInScreen` renders `TodaysCareCard(action, onOpenCareAll)` when the value is non-null. `MoodifiedNavHost` passes `onOpenCareAll = { navController.navigate(AppRoutes.Care.route) }` into `CheckInScreen`.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/checkin/CheckInViewModel.kt` (inject use case, expose StateFlow)
- Modify: `app/src/main/java/com/moodified/app/presentation/checkin/CheckInScreen.kt` (accept `onOpenCareAll`, render card)
- Create: `app/src/main/java/com/moodified/app/presentation/checkin/components/TodaysCareCard.kt`
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` (pass `onOpenCareAll` into `CheckInScreen`)

**Interfaces:**
- Consumes: `ObserveTopCareInterventionUseCase` from Task 2, `InterventionAction` domain model.
- Produces:
  - `TodaysCareCard(action, onOpenCareAll)` — public composable.
  - `CheckInScreen` gains a new `onOpenCareAll: () -> Unit` parameter.

- [ ] **Step 1: Extend `CheckInViewModel`**

Read `app/src/main/java/com/moodified/app/presentation/checkin/CheckInViewModel.kt` first — its current constructor + state shape drives the exact edits.

Then add:
- Constructor parameter: `private val observeTopCareInterventionUseCase: ObserveTopCareInterventionUseCase`
- New public property:
```kotlin
val topCareIntervention: StateFlow<InterventionAction?> =
    observeTopCareInterventionUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
```
- Imports:
```kotlin
import com.moodified.app.domain.model.intervention.InterventionAction
import com.moodified.app.domain.usecase.intervention.ObserveTopCareInterventionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 2: Create `TodaysCareCard.kt`**

Create `app/src/main/java/com/moodified/app/presentation/checkin/components/TodaysCareCard.kt`:
```kotlin
package com.moodified.app.presentation.checkin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.domain.model.intervention.InterventionAction

@Composable
fun TodaysCareCard(
    action: InterventionAction,
    onOpenCareAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenCareAll),
        color = SageSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "TODAY'S CARE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 10.sp,
                ),
                color = DeepSage,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = action.displayTitle(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = action.displayBody(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = DeepSage,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = MilkWhite,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun InterventionAction.displayTitle(): String =
    when (this) {
        is InterventionAction.Guidance -> title
        is InterventionAction.Motivation -> title
        is InterventionAction.TrendAlert -> "Something's shifting"
        is InterventionAction.MicroIntervention -> "Micro-routine ready"
        is InterventionAction.GuidedRoutine -> "Guided routine"
        is InterventionAction.MotivationNudge -> "You're doing well"
        is InterventionAction.MicroConfirmation -> prompt
    }

private fun InterventionAction.displayBody(): String =
    when (this) {
        is InterventionAction.Guidance -> description
        is InterventionAction.Motivation -> description
        is InterventionAction.TrendAlert -> "Check trends in ${domain.name.lowercase()}"
        is InterventionAction.MicroIntervention -> "${steps.size} steps · ${durationSeconds}s"
        is InterventionAction.GuidedRoutine -> "$estimatedMinutes min · ${routineType.name.lowercase()}"
        is InterventionAction.MotivationNudge -> "Keep it up"
        is InterventionAction.MicroConfirmation -> "Tap to confirm"
    }
```
The `when` above is exhaustive over the seven subtypes declared in `app/src/main/java/com/moodified/app/domain/model/intervention/InterventionModels.kt` — `Guidance`, `Motivation`, `MicroConfirmation`, `MicroIntervention`, `GuidedRoutine`, `TrendAlert`, `MotivationNudge`. If a new subtype is added between plan-writing and plan-execution, the compiler will flag the non-exhaustive `when` — add the branch then.

- [ ] **Step 3: Render the card on `CheckInScreen`**

In `app/src/main/java/com/moodified/app/presentation/checkin/CheckInScreen.kt`:

3a. Add `onOpenCareAll: () -> Unit` to the `CheckInScreen` signature (currently line 56):
```kotlin
@Composable
fun CheckInScreen(
    onQuickLog: () -> Unit,
    onViewCalendar: () -> Unit,
    onOpenCareAll: () -> Unit,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
```

3b. Collect the new StateFlow near the existing state collection (immediately after `val state by viewModel.uiState.collectAsStateWithLifecycle()` on line 61):
```kotlin
    val topCareIntervention by viewModel.topCareIntervention.collectAsStateWithLifecycle()
```

3c. Insert the card composable render into the screen's LazyColumn/Column layout at a natural spot below the header and above the mood-log rows. Find the `LazyColumn { ... }` block in the file; add an `item` conditionally:
```kotlin
        topCareIntervention?.let { action ->
            item(key = "todays_care") {
                Spacer(Modifier.height(12.dp))
                TodaysCareCard(action = action, onOpenCareAll = onOpenCareAll, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
```
Add the import:
```kotlin
import com.moodified.app.presentation.checkin.components.TodaysCareCard
```
The exact placement inside `LazyColumn` depends on the existing screen structure — insert it after the intro/header content and before any long-form rows. If the screen uses a plain `Column` (not `LazyColumn`), wrap the conditional in an `if (topCareIntervention != null)` block instead of `item {}`.

- [ ] **Step 4: Pass `onOpenCareAll` from `MoodifiedNavHost`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`, update the `composable(AppRoutes.CheckIn.route) { ... }` block (lines 143-148):
```kotlin
                composable(AppRoutes.CheckIn.route) {
                    CheckInScreen(
                        onQuickLog = { showQuickLog = true },
                        onViewCalendar = { navController.navigate(AppRoutes.Calendar.route) },
                        onOpenCareAll = { navController.navigate(AppRoutes.Care.route) },
                    )
                }
```

- [ ] **Step 5: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. `LongParameterList` on `CheckInScreen` (4 params + viewModel) may trigger — baseline it, allowed category.

- [ ] **Step 6: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/checkin/CheckInViewModel.kt \
        app/src/main/java/com/moodified/app/presentation/checkin/CheckInScreen.kt \
        app/src/main/java/com/moodified/app/presentation/checkin/components/TodaysCareCard.kt \
        app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt

# Only if detekt baseline was regenerated:
git add config/detekt/detekt-baseline.xml

git commit -m "feat(checkin): add Today's Care card wired to ObserveTopCareInterventionUseCase"
```

---

### Task 4: Rename "Tracking Preferences" → "Tracking" on Profile

Bundled Phase 4 minor. One line.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt` (line 206)

- [ ] **Step 1: Rename the section header**

In `app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt`, change line 206 from:
```kotlin
            SectionHeader("Tracking Preferences")
```
to:
```kotlin
            SectionHeader("Tracking")
```

- [ ] **Step 2: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 3: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/profile/ProfileScreen.kt

git commit -m "chore(profile): rename Tracking Preferences → Tracking per spec §3.1"
```

---

### Task 5: Final full-greens gate

No file changes.

- [ ] **Step 1: Clean and re-run all gates**

Run:
```
./gradlew clean
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green from a clean state.

- [ ] **Step 2: Verify branch shape**

Run:
```
git log main..phase-5c-nav-reorg --oneline
git log main..phase-5c-nav-reorg --format="%an <%ae>" | sort -u
```
Expected: 4 commits (one per Task 1-4). Only Mikhael Edman Gomez as author.

No commit in this task.

---

## Follow-ups (out of scope for Phase 5c)

- **`CareViewModel` refactor to consume `ObserveTopCareInterventionUseCase`** — duplicated combine logic in `CareViewModel` can be replaced with a wider use case call plus the trend-alert / motivation-nudge derived views. Deferred to Phase 6 unless a subagent finds the shape trivial to sub in during Task 2.
- **Calendar screen top-bar cleanup** — if the phantom back-arrow shows on the bottom-tab, remove it (small commit).
- **`Icons.Rounded.SelfImprovement` import cleanup** — the old Care icons in `BottomNavItem.kt` are already removed in Task 1; verify no residual imports remain elsewhere.

## Merge steps (executed by the human, not by the plan)

After all reviews clear:
```
git checkout main
git merge --ff-only phase-5c-nav-reorg
# Do NOT git push — bundled with 5d after full-phase review.
```
