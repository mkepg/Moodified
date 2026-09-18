# Phase 5b — Onboarding + First-Run Routing Implementation Plan

**Goal:** Ship a 4-slide onboarding surface driven by a persisted `hasCompletedOnboarding` DataStore flag. `MainActivity` picks `onboarding/` or `checkin/` at cold start based on the flag; the last slide flips the flag true and hard-routes to Check In. Permission priming is ordered Notifications → Activity Recognition → Usage Access.

**Architecture:**
- **Persistence** — a new `OnboardingPreferencesDataSource` in `data/local/datasource/` backed by DataStore-preferences (new dep in this phase). Single Boolean key `hasCompletedOnboarding`. Exposed as a suspending getter (for startup routing) + a `Flow<Boolean>` (for observers if ever needed) + a `suspend markCompleted()`.
- **Startup routing** — `MainActivity` reads the flag *synchronously* via `runBlocking` on cold start (single small IO read, fits into splash-screen timing), captures it as an `initialStartDestination: String` passed to `MoodifiedNavHost`. First-run is `AppRoutes.Onboarding.route`; returning-user is `AppRoutes.CheckIn.route`. The nav host's `startDestination` becomes a parameter, not a hard-coded constant.
- **Onboarding screen** — `HorizontalPager`-driven 4-slide carousel. Each slide is a `data class OnboardingSlide(...)` rendered by a shared `OnboardingSlideContent` composable. Slide 3 ("Passive context") is the permission priming stage: per-permission cards with Enable / Skip. Denial never blocks; there is no "gate" between slides.
- **Permission priming order (per user decision):**
  1. Notifications (API 33+ runtime dialog via `POST_NOTIFICATIONS`)
  2. Activity Recognition (API 29+ runtime dialog via `ACTIVITY_RECOGNITION`)
  3. Usage Access (system-settings deep-link to `Settings.ACTION_USAGE_ACCESS_SETTINGS`)
  Order is enforced by the slide-3 UI itself — three sub-cards top-to-bottom in that order.
- **Bottom bar hidden** — `AppRoutes.Onboarding.route` is added to `fullScreenRoutes` in `MoodifiedNavHost`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt (existing), Coroutines/Flow (existing), `androidx.activity.compose.rememberLauncherForActivityResult`, new dep `androidx.datastore:datastore-preferences:1.1.1`. No schema change.

**Spec:** [docs/specs/2026-09-11-moodified-consolidation-design.md](../specs/2026-09-11-moodified-consolidation-design.md) — §3.2.1 (onboarding surface + slides + priming), §6 Phase 5.

## Global Constraints

- Root package: `com.moodified.app`.
- Every task must leave the app **building and running**. `./gradlew assembleDebug assembleRelease` both pass after every commit.
- Green gate per task: `./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt` — all green before commit.
- Detekt baseline additions allowed **only** in `FunctionNaming` / `LongParameterList` / `LongMethod` / `MatchingDeclarationName` (Compose false positives). Any other new category is a real signal — fix, don't baseline. Wrap long constructor calls at authoring time so `MaxLineLength` never lands in the baseline.
- Feature branch: `phase-5b-onboarding` (created in Task 0). This branch is cut from `main` **after** Phase 5a has merged.- **DataStore version:** `androidx.datastore:datastore-preferences:1.1.1`. Add via `libs.versions.toml` `datastore = "1.1.1"` version + `androidx-datastore-preferences` library alias.
- **DataStore access pattern:** synchronous read via `runBlocking` in `MainActivity.onCreate` is intentional — the value is required to pick a start destination before the first composition. Do not turn `startDestination` into a mutable state that races the pager.
- **No push to origin:** the phase merges locally to `main` after review clears. Do not `git push` from any task in this plan.
- **Scope fences:**
  - No touching `presentation/inbox/`, `data/local/entity/notification/`, `data/local/dao/notification/` (Phase 5a).
  - No touching `BottomNavItem.kt` / bottom-nav slot wiring (Phase 5c).
  - No touching `presentation/devtools/` or `ProfileViewModel.triggerTestMicroPrompt` (Phase 5d).
  - Skip button is present on every slide; denial never navigates back.

---

### Task 0: Create the feature branch and verify pre-phase greens

**Files:** none (git-only).

- [ ] **Step 1: Ensure Phase 5a is on main**

Run:
```
git status
git checkout main
git pull --ff-only
git log --oneline -5
```
Expected: Phase 5a's 8 commits sit at the tip of `main`, ending with the `feat(inbox): add Notifications row on Profile + FeedbackSheet snackbar fix` commit. If `main` doesn't have Phase 5a, stop — 5b depends on 5a's `AppRoutes` file layout.

- [ ] **Step 2: Create and check out the feature branch**

Run:
```
git checkout -b phase-5b-onboarding
```

- [ ] **Step 3: Sanity-check the pre-phase greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

No commit for this task.

---

### Task 1: Add DataStore-preferences dependency + `OnboardingPreferencesDataSource`

Introduce the new library, wire the DataStore instance, and add the datasource + a Hilt provider. Independent of the rest — first commit.

**Files:**
- Modify: `gradle/libs.versions.toml` (add `datastore` version + `androidx-datastore-preferences` library alias)
- Modify: `app/build.gradle.kts` (add `implementation(libs.androidx.datastore.preferences)`)
- Create: `app/src/main/java/com/moodified/app/data/local/datasource/OnboardingPreferencesDataSource.kt`

**Interfaces:**
- Consumes: `@ApplicationContext Context` via Hilt.
- Produces:
  - `com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource` — `@Singleton`, `@Inject` constructor. Public API:
    - `suspend fun hasCompletedOnboarding(): Boolean` — one-shot read; returns `false` by default.
    - `suspend fun markCompleted()`.
    - `fun observe(): Flow<Boolean>`.

- [ ] **Step 1: Add DataStore version + library alias**

In `gradle/libs.versions.toml`, in the `[versions]` block, add near the other AndroidX versions (after `activityCompose = "1.10.1"` on line 9):
```toml
datastore = "1.1.1"
```

In the `[libraries]` block, near the other AndroidX libraries (after `androidx-appcompat` on line 54), add:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Reference the library in `app/build.gradle.kts`**

Grep `app/build.gradle.kts` for `implementation(libs.` — find the existing block of `libs.*` implementations and add:
```kotlin
implementation(libs.androidx.datastore.preferences)
```
Place it alphabetically near `implementation(libs.androidx.core.ktx)` or wherever the existing block groups AndroidX libs.

- [ ] **Step 3: Create `OnboardingPreferencesDataSource.kt`**

Create `app/src/main/java/com/moodified/app/data/local/datasource/OnboardingPreferencesDataSource.kt`:
```kotlin
package com.moodified.app.data.local.datasource

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

private val KEY_COMPLETED = booleanPreferencesKey("has_completed_onboarding")

@Singleton
class OnboardingPreferencesDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun hasCompletedOnboarding(): Boolean =
            context.onboardingDataStore.data.map { prefs -> prefs[KEY_COMPLETED] ?: false }.first()

        fun observe(): Flow<Boolean> =
            context.onboardingDataStore.data.map { prefs -> prefs[KEY_COMPLETED] ?: false }

        suspend fun markCompleted() {
            context.onboardingDataStore.edit { prefs -> prefs[KEY_COMPLETED] = true }
        }
    }
```
Rationale: DataStore singletons must be process-wide (multiple `preferencesDataStore(name = "...")` at the same name in different call sites throws at runtime). Declaring the delegate as a top-level `private` file property guarantees a single instance per process.

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Hilt validates the new `@Inject` constructor.

- [ ] **Step 5: Commit**

Run:
```
git add gradle/libs.versions.toml \
        app/build.gradle.kts \
        app/src/main/java/com/moodified/app/data/local/datasource/OnboardingPreferencesDataSource.kt

git commit -m "feat(onboarding): add DataStore-preferences dep + OnboardingPreferencesDataSource"
```

---

### Task 2: `AppRoutes.Onboarding` + `OnboardingSlide` model + `OnboardingViewModel`

Add the route constant, the slide data class, and the ViewModel. Screen composable ships in Task 3.

**Files:**
- Modify: `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt` (add `AppRoutes.Onboarding`)
- Create: `app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingSlide.kt`
- Create: `app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingViewModel.kt`

**Interfaces:**
- Consumes: `OnboardingPreferencesDataSource` from Task 1.
- Produces:
  - `AppRoutes.Onboarding` — `data object Onboarding : AppRoutes("onboarding")`.
  - `OnboardingSlide` — `data class` with `key: String`, `title: String`, `subtitle: String`, and `kind: Kind` sealed enum (`Welcome | QuickLog | Permissions | Ready`). Slide list built statically in the file, not fetched from DataStore.
  - `OnboardingViewModel` — `@HiltViewModel` with `slides: List<OnboardingSlide>` and `fun completeOnboarding()`.

- [ ] **Step 1: Add `AppRoutes.Onboarding`**

In `app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt`, after `data object Calendar : AppRoutes("calendar")` (line 27) and before the `Privacy` object, add:
```kotlin

    // Onboarding (Phase 5b) — first-run only, bottom bar hidden
    data object Onboarding : AppRoutes("onboarding")
```

- [ ] **Step 2: Create `OnboardingSlide.kt`**

Create `app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingSlide.kt`:
```kotlin
package com.moodified.app.presentation.onboarding

data class OnboardingSlide(
    val key: String,
    val title: String,
    val subtitle: String,
    val kind: Kind,
) {
    enum class Kind {
        WELCOME,
        QUICK_LOG,
        PERMISSIONS,
        READY,
    }
}

internal val defaultOnboardingSlides: List<OnboardingSlide> =
    listOf(
        OnboardingSlide(
            key = "welcome",
            title = "Welcome to Moodified",
            subtitle = "A calmer way to notice how you feel and what shapes it.",
            kind = OnboardingSlide.Kind.WELCOME,
        ),
        OnboardingSlide(
            key = "quicklog",
            title = "Two taps to log a mood",
            subtitle = "Pick how you feel and how much energy you have. That's it.",
            kind = OnboardingSlide.Kind.QUICK_LOG,
        ),
        OnboardingSlide(
            key = "permissions",
            title = "Ambient context, only if you want",
            subtitle = "Moodified can pick up gentle signals — activity, sleep patterns, screen use — to surface trends you'd otherwise miss. Everything is optional and stays on this device.",
            kind = OnboardingSlide.Kind.PERMISSIONS,
        ),
        OnboardingSlide(
            key = "ready",
            title = "You're set",
            subtitle = "You can revisit any of this from Profile whenever you'd like.",
            kind = OnboardingSlide.Kind.READY,
        ),
    )
```

- [ ] **Step 3: Create `OnboardingViewModel.kt`**

Create `app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingViewModel.kt`:
```kotlin
package com.moodified.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val prefs: OnboardingPreferencesDataSource,
    ) : ViewModel() {
        val slides: List<OnboardingSlide> = defaultOnboardingSlides

        fun completeOnboarding(onFinished: () -> Unit) {
            viewModelScope.launch {
                prefs.markCompleted()
                onFinished()
            }
        }
    }
```
The `onFinished` callback is invoked on the main-dispatcher continuation of `viewModelScope.launch` after `markCompleted()` returns — order guarantees the flag is durable before the screen hard-navigates to Check In.

- [ ] **Step 4: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/core/navigation/AppRoutes.kt \
        app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingSlide.kt \
        app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingViewModel.kt

git commit -m "feat(onboarding): add Onboarding route + slide model + ViewModel"
```

---

### Task 3: `OnboardingScreen` — pager, per-slide content, permission priming

Full-screen composable, `HorizontalPager` over 4 slides. Slide 3 renders three permission-prime sub-cards in the mandated order (Notifications → Activity Recognition → Usage Access), each with Enable + Skip actions. Slide 4's CTA calls `completeOnboarding { onFinished() }`.

**Files:**
- Create: `app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingScreen.kt`

**Interfaces:**
- Consumes: `OnboardingViewModel` from Task 2, `Settings.ACTION_USAGE_ACCESS_SETTINGS`, `Manifest.permission.POST_NOTIFICATIONS`, `Manifest.permission.ACTIVITY_RECOGNITION`.
- Produces: `OnboardingScreen(onFinished: () -> Unit)` — public composable.

- [ ] **Step 1: Create `OnboardingScreen.kt`**

Create `app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingScreen.kt`:
```kotlin
package com.moodified.app.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.SageSurface
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val slides = viewModel.slides
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilkWhite)
            .statusBarsPadding(),
    ) {
        TopBar(onSkip = { viewModel.completeOnboarding(onFinished) }, showSkip = pagerState.currentPage < slides.lastIndex)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingPageContent(slide = slides[page])
        }

        PagerFooter(
            slideCount = slides.size,
            currentPage = pagerState.currentPage,
            onNext = {
                if (pagerState.currentPage < slides.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    viewModel.completeOnboarding(onFinished)
                }
            },
            isLastSlide = pagerState.currentPage == slides.lastIndex,
        )
    }
}

@Composable
private fun TopBar(onSkip: () -> Unit, showSkip: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f))
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text(text = "Skip", color = TextTertiary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(slide: OnboardingSlide) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DmSerifDisplay),
            color = TextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = slide.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        when (slide.kind) {
            OnboardingSlide.Kind.WELCOME, OnboardingSlide.Kind.QUICK_LOG, OnboardingSlide.Kind.READY -> {
                // Illustration slot — text-only for now; a Lottie can drop in later without shape churn.
            }
            OnboardingSlide.Kind.PERMISSIONS -> PermissionPrimingList()
        }
    }
}

@Composable
private fun PermissionPrimingList() {
    val context = LocalContext.current

    val notifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* denial is fine */ }
    val activityLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* denial is fine */ }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionCard(
            title = "Notifications",
            body = "Occasional gentle check-ins asking how you're feeling. Nothing else.",
            onEnable = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        PermissionCard(
            title = "Activity Recognition",
            body = "Sense whether you're moving or resting, so mood context can be smarter.",
            onEnable = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        )
        PermissionCard(
            title = "Usage Access",
            body = "See when your screen is on or off, without knowing which app.",
            onEnable = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
    }
}

@Composable
private fun PermissionCard(title: String, body: String, onEnable: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        color = SageSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onEnable),
                    color = DeepSage,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = MilkWhite,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Skip is implicit — the slide has a global Skip in the top bar and swiping past is always allowed.
            }
        }
    }
}

@Composable
private fun PagerFooter(
    slideCount: Int,
    currentPage: Int,
    onNext: () -> Unit,
    isLastSlide: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            repeat(slideCount) { index ->
                val isActive = index == currentPage
                Box(
                    modifier = Modifier
                        .size(if (isActive) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isActive) DeepSage else MilkDeep),
                )
                if (index < slideCount - 1) Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onNext),
            color = DeepSage,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = if (isLastSlide) "Get started" else "Continue",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                color = MilkWhite,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

```

Note: the file uses `Modifier.statusBarsPadding()` (Foundation 1.7+, already on Compose BOM 2024.12.01) rather than a manual `WindowInsets.asPaddingValues()` call — same effect, fewer imports, matches the pattern used in `ProfileScreen.kt`.

- [ ] **Step 2: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green. Compose HorizontalPager is part of the existing BOM (2024.12.01). If detekt flags `LongMethod` on `OnboardingScreen` or `LongParameterList` on `PagerFooter`, baseline them.

- [ ] **Step 3: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/presentation/onboarding/OnboardingScreen.kt

# Only if detekt baseline was regenerated:
git add config/detekt/detekt-baseline.xml

git commit -m "feat(onboarding): OnboardingScreen with 4-slide pager + permission priming"
```

---

### Task 4: Route registration + `MainActivity` startDestination gating

Register the onboarding composable in `MoodifiedNavHost`. `MainActivity.onCreate` reads `hasCompletedOnboarding` synchronously, decides the start destination, passes it into `MoodifiedNavHost`. On completion, `OnboardingScreen` calls `onFinished` which pops the onboarding destination and navigates to Check In (so back-press from Check In can't return to onboarding).

**Files:**
- Modify: `app/src/main/java/com/moodified/app/MainActivity.kt` (inject `OnboardingPreferencesDataSource`, `runBlocking` read on cold start, pass `startDestination: String` to `MoodifiedNavHost`)
- Modify: `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt` (accept `startDestination`, register `AppRoutes.Onboarding.route` composable, add to `fullScreenRoutes`)

**Interfaces:**
- Consumes: `OnboardingPreferencesDataSource` from Task 1, `AppRoutes.Onboarding` from Task 2, `OnboardingScreen` from Task 3.
- Produces: `MoodifiedNavHost(quickLogTrigger, openInboxTrigger, startDestination)` — third parameter added, default `AppRoutes.CheckIn.route` to keep tests / previews happy.

- [ ] **Step 1: Update `MainActivity` to read the flag synchronously and pass startDestination**

In `app/src/main/java/com/moodified/app/MainActivity.kt`, replace the file body with:
```kotlin
package com.moodified.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moodified.app.core.navigation.AppRoutes
import com.moodified.app.core.theme.MoodifiedTheme
import com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource
import com.moodified.app.presentation.navigation.MoodifiedNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var onboardingPrefs: OnboardingPreferencesDataSource

    private val quickLogTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val openInboxTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val startDestination =
            runBlocking {
                if (onboardingPrefs.hasCompletedOnboarding()) {
                    AppRoutes.CheckIn.route
                } else {
                    AppRoutes.Onboarding.route
                }
            }

        setContent {
            MoodifiedTheme {
                MoodifiedNavHost(
                    quickLogTrigger = quickLogTrigger,
                    openInboxTrigger = openInboxTrigger,
                    startDestination = startDestination,
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
Note: `runBlocking` at Activity.onCreate is a deliberate, one-time, single-key read. Do not turn this into a coroutine post-hoc — the splash screen is on-screen during this call.

- [ ] **Step 2: Register the route + accept startDestination in `MoodifiedNavHost.kt`**

In `app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt`:

2a. Add imports:
```kotlin
import com.moodified.app.presentation.onboarding.OnboardingScreen
```

2b. Change the function signature (currently `fun MoodifiedNavHost(quickLogTrigger, openInboxTrigger)` after Phase 5a Task 7):
```kotlin
@Composable
fun MoodifiedNavHost(
    quickLogTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    openInboxTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    startDestination: String = AppRoutes.CheckIn.route,
) {
```

2c. Add `AppRoutes.Onboarding.route` to `fullScreenRoutes` (the set inside the `remember` block at line 90):
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
                    AppRoutes.Onboarding.route,
                )
        }
```

2d. Change the `NavHost(...)` `startDestination` from the hard-coded `AppRoutes.CheckIn.route` to the parameter:
```kotlin
            NavHost(
                navController = navController,
                startDestination = startDestination,
                // ...unchanged...
```

2e. Register the composable inside `NavHost { ... }`, immediately after the `AppRoutes.CheckIn` block (currently lines 143-148):
```kotlin
                composable(AppRoutes.Onboarding.route) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(AppRoutes.CheckIn.route) {
                                popUpTo(AppRoutes.Onboarding.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
```
The `popUpTo(Onboarding) { inclusive = true }` guarantees back-press from Check In cannot re-enter onboarding.

- [ ] **Step 3: Verify all greens**

Run:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck detekt
```
Expected: all five green.

- [ ] **Step 4: Manual sanity — first-run vs. returning-user**

Not automated. If time permits, install the debug APK on a clean emulator (`./gradlew :app:installDebug` after `adb shell pm clear com.moodified.app`) and verify onboarding shows. Skip if this is being executed by a subagent — the check-in-first behavior in the returning-user path is exercised by existing debug installs, and full-device verification is deferred to the end-of-consolidation smoke pass.

- [ ] **Step 5: Commit**

Run:
```
git add app/src/main/java/com/moodified/app/MainActivity.kt \
        app/src/main/java/com/moodified/app/presentation/navigation/MoodifiedNavHost.kt

git commit -m "feat(onboarding): route onboarding at cold start via hasCompletedOnboarding flag"
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
git log main..phase-5b-onboarding --oneline
git log main..phase-5b-onboarding --format="%an <%ae>" | sort -u
```
Expected: 4 commits (one per Task 1-4). Only Mikhael Edman Gomez as author.

No commit in this task.

---

## Follow-ups (out of scope for Phase 5b)

- Lottie / illustration slots on slides 1, 2, 4 — the current text-only slides pass user acceptance for Phase 5b; visual polish is a Phase-6-or-later cosmetic pass.
- Post-onboarding cross-links (e.g., link Notifications permission-prime card into the shipped inbox) — deferred; the priming card already asks for `POST_NOTIFICATIONS` which is what unlocks inbox delivery.

## Merge steps (executed by the human, not by the plan)

After all reviews clear:
```
git checkout main
git merge --ff-only phase-5b-onboarding
# Do NOT git push — bundled with 5c/5d after full-phase review.
```
