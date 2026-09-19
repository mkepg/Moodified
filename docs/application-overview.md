# Moodified — Application Overview & Technical Documentation

> A comprehensive reference describing what Moodified is, the problems it solves, its feature set, architecture, technologies, and user workflows.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement & Purpose](#2-problem-statement--purpose)
3. [Target Users](#3-target-users)
4. [Feature Overview](#4-feature-overview)
5. [User Workflows](#5-user-workflows)
6. [Application Architecture](#6-application-architecture)
7. [Data Layer & Persistence](#7-data-layer--persistence)
8. [Inference & Care Engines](#8-inference--care-engines)
9. [Background Processing & Services](#9-background-processing--services)
10. [Permissions Model](#10-permissions-model)
11. [Design System & Accessibility](#11-design-system--accessibility)
12. [Technology Stack](#12-technology-stack)
13. [Build, Release & Tooling](#13-build-release--tooling)
14. [Privacy, Security & Compliance](#14-privacy-security--compliance)
15. [Repository Layout](#15-repository-layout)
16. [Glossary](#16-glossary)

---

## 1. Executive Summary

**Moodified** is a portfolio-quality native Android application for mood tracking, behavioral context awareness, and adaptive wellbeing support. It enables users to log how they feel in seconds, passively enriches those entries with sensor-derived context (activity, sleep, screen interaction), and surfaces personalized micro-interventions when users need them most. All data lives on-device; there is no cloud backend, no account creation, and no network dependency.

The app is built end-to-end in **Kotlin** using **Jetpack Compose**, follows a **Clean Architecture + MVVM** pattern, uses **Hilt** for dependency injection, **Room** for local persistence, and **WorkManager** for periodic background work. A foreground **health-type service** continuously aggregates passive signals when the user has granted the relevant permissions.

| Attribute | Value |
|-----------|-------|
| Platform | Android (native) |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 36 (Android 15) |
| Language | Kotlin 2.1.0 |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| Persistence | Room (schema v15) |
| Distribution | Android App Bundle (ABI / density / language splits) |
| Current Version | 1.0.0 (versionCode 1) |

---

## 2. Problem Statement & Purpose

### The problem

People who want to understand and improve their mental wellbeing face three persistent obstacles:

1. **Logging friction** — Traditional mood journals demand effort and consistency. Users abandon them within weeks.
2. **Context gap** — A mood rating in isolation ("I felt 3/5 today") tells you nothing about *why*. Sleep, activity, and digital habits all influence mood, but those data points usually live in separate apps.
3. **Generic advice** — Most wellness apps recommend the same breathing exercise to every user regardless of their current state. Real support has to be responsive.

### What Moodified does about it

Moodified addresses each obstacle directly:

- **Two-tap quick logging.** A modal sheet captures valence (positive/neutral/negative) and arousal (calm/balanced/elevated) in under five seconds.
- **Passive enrichment.** With permission, Moodified continuously tracks step counts and activity intensity (via the Activity Recognition API and on-device sensors), sleep windows (inferred from screen-off patterns via Usage Stats), and screen interaction (unlock counts, session length). This context is correlated with mood automatically.
- **Adaptive Care.** A rule-based inference engine reads the user's current behavioral snapshot and chooses interventions matched to that state — calming routines for elevated arousal, motivational nudges for sustained low valence, recovery suggestions when sleep is below baseline.

### Purpose

Moodified exists to give users a low-effort, high-context way to **notice, understand, and respond to** their emotional state — so that small daily adjustments become possible without sprawling self-tracking systems.

---

## 3. Target Users

| Persona | Description | Primary value |
|---------|-------------|---------------|
| **Health-conscious individuals (25–45)** | Already track fitness or sleep; want mood added to the picture. | Trend correlation between sleep / activity and mood. |
| **Stress- and anxiety-aware users** | Want to identify mood triggers and have a tool to reach for in difficult moments. | Quick logging plus adaptive Care suggestions. |
| **Digital-wellness advocates** | Concerned about screen-time impact on mood and rest. | Screen Use insights and late-night usage signals. |

The app is intentionally lighter than a full mental-health clinical product — it is a **self-reflection and behavior-context tool**, not a diagnostic platform.

---

## 4. Feature Overview

### 4.1 Quick mood logging

A modal bottom sheet (`QuickLogSheet`) presents a two-step flow:

1. **Valence selection** — sad / meh / happy.
2. **Arousal selection** — calm / balanced / elevated.

An optional note can be attached, the entry is persisted to Room, and a Lottie success animation plays before the sheet auto-dismisses. The sheet is deep-linkable via `moodified://quicklog` so notifications and shortcuts can drop the user straight into logging.

### 4.2 Daily check-in dashboard

The `CheckIn` screen is the primary landing surface. It shows:

- The current inferred mood state (when no manual entry exists).
- Today's manual mood entries.
- The **Today's Care** card — a single top-priority intervention card from the Care engine. Tapping it opens the full-screen Care route.
- Permission prompts when tracking is incomplete.
- An entry-point to the calendar history and the quick-log sheet.

### 4.3 Insights

The **Insight** screen is a single surface with four tabs, all backed by a single `InsightViewModel`:

| Tab | Shown content |
|-----|---------------|
| **Overview** | Weekly mood distribution, inferred trends, data completeness summary. |
| **Activity** | Step trends, intensity bands, active minutes, weekly graphs. |
| **Sleep** | Sleep duration trends, efficiency, consistency score over 7+ days. |
| **Screen Use** | Daily screen time, unlock counts, late-night-usage flags, session analysis. |

### 4.4 Calendar browse

A month-grid calendar lets the user tap any past date to review every mood entry logged that day, supporting longitudinal reflection.

### 4.5 Adaptive Care

The `Care` screen presents a swipeable stack of cards generated by the `CareEvaluationEngine`. Card categories include:

- **Micro-confirmations** — gentle acknowledgements ("You seem a bit low — that's okay.").
- **Guided routines** — short, actionable exercises (e.g. 5-minute breathing).
- **Trend alerts** — notifications about week-over-week shifts ("Your sleep is down this week.").
- **Motivation nudges** — positive reinforcement for consistent logging or behavioral wins.

User engagement (dismiss / engage / feedback) is persisted to refine subsequent suggestions.

### 4.6 Notifications inbox

The notifications inbox (`NotificationsInboxScreen`) shows a chronological list of micro-prompt and Care notifications that have been delivered to the device. It is reachable from:

- **Profile → Notifications** row.
- The `moodified://inbox` deep link (used by notification taps).

### 4.7 Onboarding

A first-run onboarding flow presents 4 slides: Welcome, Quick Log, Permissions, and Ready. Completion is persisted to DataStore; the flow is skipped on all subsequent launches.

### 4.8 Support

Under **Profile → Support**, users can access:

- **Help** — contextual help articles.
- **About** — app version info (long-pressing the version label in debug builds opens the Debug Drawer).
- **Feedback** — an in-app feedback sheet.
- **Licenses** — open-source license acknowledgements.

### 4.9 Debug Drawer

In debug builds only, a full-screen Debug Drawer is accessible by long-pressing the version label on the About screen. It provides diagnostic data, mock data seeding actions, and navigation to sensor-monitor screens. None of this tooling ships in release builds.

### 4.10 Profile & settings

The `Profile` screen consolidates:

- Per-domain **Tracking** toggles (Activity, Sleep, Interaction) under Profile → Tracking.
- **Notifications** entry point.
- **Support** entry point (Help, About, Feedback, Licenses).
- Privacy policy and data deletion controls.

### 4.11 Micro-prompt notifications

When eligibility conditions are satisfied (no recent log, appropriate time-of-day, etc.), the app fires a notification that deep-links back into the quick-log sheet.

---

## 5. User Workflows

### 5.1 First launch

1. User installs and opens Moodified.
2. `MainActivity` evaluates the DataStore onboarding flag; if unset, it routes to the Onboarding screen.
3. The 4-slide onboarding flow presents the app's core concepts and optional permission prompts.
4. On completion the flag is set and the user lands on the **Check-in** tab on every subsequent launch.

### 5.2 Daily mood logging

1. User taps the central **Quick Log** button (FAB in bottom nav).
2. Selects valence → arousal → optional note.
3. Entry persists; Lottie success animation plays; sheet auto-dismisses after ~1.6 s.
4. The new entry appears immediately on the Check-in dashboard.

### 5.3 Passive enrichment (background)

1. `TrackingService` runs as a foreground health-type service.
2. Activity, sleep, and interaction trackers stream signals into their repositories.
3. `MidnightRolloverWorker` produces a daily summary at 00:05.
4. `TelemetryWorker` flushes in-flight data every 15 minutes.
5. `PurgeWorker` evicts data older than the retention window weekly.

### 5.4 Reviewing insights

1. User taps **Insight** in the bottom nav.
2. The Overview tab shows weekly mood distribution and completeness.
3. Tabs for Activity, Sleep, and Screen Use offer domain-specific charts.
4. Navigation state is preserved on return.

### 5.5 Receiving Care

1. The **Today's Care** card on the Check-in screen shows the top-priority intervention.
2. Tapping it navigates to the full-screen **Care** route (`care/all`).
3. The `CareViewModel` invokes the `CareEvaluationEngine` to produce a ranked card list.
4. User engages or dismisses cards; feedback is persisted.

### 5.6 Deep-link logging (e.g., from a notification)

1. External intent carries `moodified://quicklog`.
2. `MainActivity` emits the trigger to a Flow.
3. `QuickLogSheet` appears immediately on top of whichever tab the user was viewing.

### 5.7 Checking the notifications inbox

1. User taps **Profile → Notifications**, or taps a delivered notification.
2. The `moodified://inbox` deep link (or direct navigation) opens `NotificationsInboxScreen`.
3. The list shows all delivered notifications; tapping an item marks it read.

---

## 6. Application Architecture

Moodified follows a **Clean Architecture** layering with **MVVM** in the presentation layer.

```
presentation/  →  domain/  →  data/
   (Compose +       (models,      (Room DAOs,
    ViewModels)      use cases,    repository impls,
                     repo IFs)     sensors, receivers)
                          ↑
                          └── di/ (Hilt modules wire it together)
```

### 6.1 Layers

- **`presentation/`** — Compose screens, `ViewModel`s, navigation graph. Knows nothing about Room or sensor APIs.
- **`domain/`** — Pure Kotlin: data classes, enums, repository interfaces, use cases (including the `RuleBasedMoodInferenceEngine` and `CareEvaluationEngine`). Has no Android imports.
- **`data/`** — Room database, DAOs, entities, repository implementations, broadcast receivers, sensor data sources, DataStore preference reads/writes.
- **`core/`** — Cross-cutting concerns: theme, navigation host, foreground service, WorkManager workers, utility helpers, debug tooling.
- **`di/`** — Hilt modules binding repository interfaces to implementations and providing the Room database / DAOs.

### 6.2 Navigation

- **Single-Activity** host (`MainActivity`, `launchMode=singleTop`).
- **Jetpack Navigation Compose** with a top-level `MoodifiedNavHost`.
- **Bottom navigation** has five slots: **Check-in**, **Calendar**, **Quick Log** (FAB action), **Insight**, **Profile**.
- **Care** is a full-screen route (`care/all`) reachable from the Today's Care card on the Check-in screen — it does not appear in the bottom bar.
- Full-screen routes (Care, Calendar, Onboarding, detailed Support screens, Privacy, Notifications Inbox) hide the bottom bar.
- Deep links:
  - `moodified://quicklog` — triggers the quick-log sheet from any state.
  - `moodified://inbox` — opens the notifications inbox directly.

### 6.3 Dependency injection

Hilt is the DI container:

- **`DatabaseModule`** provides the Room database and every DAO.
- **`RepositoryModule`** binds each `*Repository` interface to its `*RepositoryImpl`.
- **`@HiltViewModel`** is used on every ViewModel; `hiltViewModel()` is used in Compose for retrieval.
- **`HiltWorker`** is used for WorkManager workers requiring injection.

### 6.4 UI

- 100% Jetpack Compose; **no XML layouts** in the presentation layer.
- **Material 3** components and typography.
- **Lottie Compose** for success animations and illustrations.
- **AnimatedContent / AnimatedVisibility** drive in-screen transitions.

---

## 7. Data Layer & Persistence

### 7.1 Local database

- **Engine:** Room v2.6.1.
- **Class:** `MoodifiedDatabase`, currently at **schema version 15**.
- **Schemas:** exported under `/app/schemas/` for migration testing.
- **Migrations:** explicit SQL migrations from v6 → v15. Migration tests cover v13 → v14 (intervention history table) and v14 → v15 (notification records table). Destructive migration is allowed only as an absolute fallback.

### 7.2 Entities

| Entity | Purpose |
|--------|---------|
| `MoodEntryEntity` | Mood entries with valence, arousal, timestamp, optional note, manual/inferred flag. |
| `SleepSegmentEntity` | Detected sleep windows: start, end, efficiency, awakenings. |
| `ActivityTelemetryEntity` | Raw activity samples: steps, intensity, cadence. |
| `ActivityDailySummaryEntity` | Aggregated daily totals: steps, active / sedentary minutes. |
| `InteractionSessionEntity` | Screen unlock sessions with duration. |
| `InteractionDailySummaryEntity` | Daily screen time, late-night usage, unlock counts. |
| `InterventionHistoryEntity` | Care interventions delivered and user feedback. |
| `NotificationRecordEntity` | Delivered notifications with read/dismissed timestamps. |

### 7.3 DAOs

DAOs expose query methods as Kotlin **`Flow`** for reactive collection in ViewModels, along with suspend insert/delete functions. Examples include `MoodEntryDao`, `SleepSegmentDao`, `ActivityTelemetryDao`, `ActivityDailySummaryDao`, `InteractionSessionDao`, `InteractionDailySummaryDao`, `InterventionHistoryDao`, and `NotificationRecordDao`.

### 7.4 Repositories

Each domain area has an interface in `domain/repository/` and an implementation in `data/repository/`:

| Repository | Responsibility |
|------------|----------------|
| `MoodRepository` | Persist mood entries; expose Flow streams. |
| `ActivityRepository` | Step counter & Activity Recognition integration; activity daily summaries. |
| `SleepRepository` | Sleep windows from Usage Stats; trend computations. |
| `InteractionRepository` | Screen sessions, daily roll-up, rollover handling. |
| `InterventionRepository` | Care delivery history and feedback. |
| `NotificationHistoryRepository` | Persist delivered notifications; mark read/dismissed. |

### 7.5 Preferences

DataStore Preferences is used for lightweight settings — tracking toggles, prompt scheduling state, last-rollover date keys, onboarding completion flag, etc.

### 7.6 Cross-cutting domain models

```text
MoodEntry(valence, arousal, timestamp, note?, isManual)
ActivitySignal(steps, intensity, activeMinutes, sensorAvailability)
SleepSignal(status, confidence, timestamp)
InferredMoodState(valence, arousal, label, confidence, explanation, isFallback)
```

---

## 8. Inference & Care Engines

### 8.1 Rule-based mood inference

`RuleBasedMoodInferenceEngine` (in `domain/usecase/inference/`) takes a `DailyBehaviorSnapshot` for "today" and produces an `InferredMoodState`.

**Logic outline:**

1. If manual mood entries exist for the day, return the most representative one directly (user input always outranks inference).
2. Otherwise compute passive scores using hand-tuned constants in `InferenceConstants`:
   - **Sleep:** good sleep raises valence; poor sleep reduces both valence and arousal.
   - **Activity:** high activity raises valence and arousal; sustained sedentary behavior reduces arousal.
   - **Digital fatigue:** elevated screen time combined with short, fragmented sessions reduces arousal.
   - **Vigorous exercise:** boosts arousal with exponential decay over ~6 hours.
3. If completeness is below a minimum threshold (~30%), produce a flagged **fallback state**.
4. Attach a human-readable **explainability string** describing why the inference was made.
5. Report a confidence score (0–100).

No neural networks or ML models are used in version 1.0.0 — the rules are interpretable and tuned for transparency.

### 8.2 Care evaluation

`CareEvaluationEngine` reads the latest inferred state, recent intervention history, and trend data, and emits a ranked list of intervention cards. Categories include micro-confirmations, guided routines, trend alerts, and motivation nudges. Each delivered intervention is recorded so the engine can avoid repetition and learn from feedback.

---

## 9. Background Processing & Services

### 9.1 Foreground tracking service

`TrackingService` (`core/service/TrackingService.kt`) is a **foreground service of type `health`** that hosts three independent trackers (activity, sleep, interaction). Each tracker can be paused / resumed / stopped via intents, independent of the others.

- A `BootReceiver` re-establishes service state after device reboot.
- Micro-prompt eligibility is evaluated on schedule, and notifications fire when conditions are met.

### 9.2 WorkManager jobs

| Worker | Cadence | Responsibility |
|--------|---------|----------------|
| `TelemetryWorker` | Every 15 minutes | Flush in-flight activity/sleep/interaction data from memory to Room (idempotent). |
| `MidnightRolloverWorker` | 00:05 daily | Compute daily summaries even after reboots or service interruptions. |
| `PurgeWorker` | Weekly | Delete telemetry older than the retention window (default 90 days). |

Workers are Hilt-aware via `HiltWorker` so they can resolve repositories and use cases.

### 9.3 Broadcast receivers

- **`BootReceiver`** restores tracking state after boot.
- **`ActivityReceiver`** receives Activity Recognition transitions.
- **`MicroPromptReceiver`** delivers scheduled prompt notifications.

---

## 10. Permissions Model

| Permission | Type | Reason |
|------------|------|--------|
| `ACTIVITY_RECOGNITION` | Runtime (API 29+) | Step counting and activity classification. |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | Micro-prompt and Care notifications. |
| `PACKAGE_USAGE_STATS` | Special access | Infer sleep and screen-time patterns from system usage. |
| `FOREGROUND_SERVICE_HEALTH` | Manifest | Long-running tracking as a health-type foreground service. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional | Improve tracking reliability on aggressive OEMs. |

The UI degrades gracefully when permissions are revoked: trackers pause, inference flags reduced confidence, and the Check-in dashboard surfaces re-prompt cards.

---

## 11. Design System & Accessibility

- **Material 3** color system with a custom palette.
  - Primary: **DeepSage** (`#465940`, deep olive).
  - Background: **MilkWhite** (`#FFFDFBF0`, warm cream).
  - Mood valence colors: positive (gold), neutral (purple), negative (cyan).
  - Mood arousal colors: low (blue), mid (green), high (red).
- **Typography** follows Material 3 type scales — no hardcoded sizes.
- **Edge-to-edge** rendering with explicit `statusBarsPadding` / `navigationBarsPadding` handling.
- **Errors** surface as snackbars (e.g., save failures in QuickLogSheet) so users always receive feedback.
- **Empty states** are designed for first-run and low-data scenarios.

---

## 12. Technology Stack

### 12.1 Languages & runtime

- Kotlin **2.1.0**, targeting **JVM 17**.
- Android **minSdk 26**, **targetSdk / compileSdk 36**.
- AGP **8.7.3**.

### 12.2 UI & navigation

- Jetpack Compose BOM **2024.12.01**, Material 3.
- Jetpack Navigation Compose **2.8.5**.
- Lottie Compose **6.6.6**.

### 12.3 Architecture & DI

- Hilt **2.54**, Hilt Navigation Compose **1.2.0**, Hilt Work **1.2.0**.
- Jetpack ViewModel & lifecycle libraries.

### 12.4 Persistence & coroutines

- Room **2.6.1** (KSP-processed), schema version 15.
- Kotlin Coroutines **1.9.0** (Flow / suspend).
- AndroidX DataStore for preferences.

### 12.5 Background work & platform

- WorkManager **2.9.0**.
- Google Play Services Location **21.3.0** (Activity Recognition).
- Android Usage Stats API, Sensor APIs (step counter, accelerometer).

### 12.6 Testing

- JUnit **4.13.2**.
- AndroidX Test, Espresso, Compose UI test.
- Room schema export enabled; migration tests cover v13→v14 and v14→v15.

### 12.7 Build tooling

- Gradle **8.7.3** (AGP **8.7.3**), Kotlin Symbol Processing **2.1.0-1.0.29**.
- ProGuard (minify + resource shrink in release builds).
- App Bundle splits by ABI, density, and language.

---

## 13. Build, Release & Tooling

- **Application ID:** `com.moodified.app`.
- **Version:** `versionName 1.0.0`, `versionCode 1` (debug builds suffix `-debug`).
- **Signing:** release builds use `keystore.properties` (excluded from VCS).
- **Splits:** Android App Bundle with ABI / density / language splits to minimize download size.
- **Debug tooling:** debug-only navigation entries are registered through a `DebugNavRegistrar` interface (`DebugNavRegistrarImpl` in `src/debug`), ensuring debug screens never ship in release artifacts. The Debug Drawer and sensor-monitor screens live exclusively in `src/debug/`.
- **Documentation directory:** `/docs/` contains setup guides (`clone-and-run.md`) and this overview.

---

## 14. Privacy, Security & Compliance

- All mood, sleep, activity, interaction, and notification data lives **on-device** in Room.
- No PII is collected; the app does not require account creation.
- The **Privacy** screen exposes the policy and a data-deletion action.
- Permissions that read sensitive data (Usage Stats) are explicitly disclosed before being requested.

---

## 15. Repository Layout

```
Moodified/
├── app/
│   ├── build.gradle.kts
│   ├── schemas/                                  # Exported Room schemas (v6–v15)
│   └── src/
│       ├── debug/java/com/moodified/app/
│       │   ├── core/devtools/                    # DebugNavRegistrarImpl, DebugRoutes, MockDataSeeder
│       │   └── presentation/devtools/            # DebugDrawerScreen, sensor monitors
│       └── main/
│           ├── AndroidManifest.xml
│           └── java/com/moodified/app/
│               ├── MainActivity.kt
│               ├── core/
│               │   ├── coordination/             # TrackingCoordinator
│               │   ├── devtools/                 # DebugNavRegistrar interface (release stub)
│               │   ├── navigation/               # AppRoutes, MoodifiedNavHost, BottomNavItem
│               │   ├── permission/               # Permission request helpers
│               │   ├── service/                  # TrackingService (foreground health)
│               │   ├── theme/                    # Material 3 theme, colors, typography
│               │   ├── utils/                    # Date, battery, logging utilities
│               │   └── worker/                   # Telemetry, MidnightRollover, Purge workers
│               ├── data/
│               │   ├── local/
│               │   │   ├── dao/                  # Room DAOs
│               │   │   ├── database/             # MoodifiedDatabase (v15)
│               │   │   ├── datasource/           # DataStore prefs, sensors
│               │   │   └── entity/               # Room entities
│               │   ├── receiver/                 # Boot, activity, micro-prompt
│               │   └── repository/               # *RepositoryImpl
│               ├── di/                           # DatabaseModule, RepositoryModule
│               ├── domain/
│               │   ├── model/                    # Mood, activity, sleep, intervention, notification models
│               │   ├── repository/               # Repository interfaces
│               │   └── usecase/                  # InferenceEngine, CareEvaluation, ObserveTopCareIntervention
│               └── presentation/
│                   ├── calendar/                 # Calendar browse
│                   ├── care/                     # Adaptive Care screen
│                   ├── checkin/                  # Main dashboard + Today's Care card
│                   ├── components/               # Reusable UI
│                   ├── insight/                  # InsightScreen (4 tabs), InsightViewModel
│                   ├── onboarding/               # OnboardingScreen, slides, VM
│                   ├── privacy/                  # Privacy policy & data deletion
│                   ├── profile/                  # Profile screen + VM
│                   ├── navigation/               # NavHost composition
│                   ├── quicklog/                 # QuickLogSheet (deep-linkable)
│                   └── support/                  # Help, About, Feedback, Licenses
├── docs/
│   ├── application-overview.md                   # This document
│   └── clone-and-run.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── keystore.properties.example
└── README.md
```

---

## 16. Glossary

| Term | Meaning |
|------|---------|
| **Valence** | The pleasantness dimension of mood — negative, neutral, or positive. |
| **Arousal** | The activation dimension of mood — low (calm), mid (balanced), high (elevated). |
| **Inferred mood state** | A mood estimate produced by the rule-based engine from passive signals when no manual entry exists. |
| **Daily Behavior Snapshot** | An aggregate of today's mood entries, activity, sleep, and interaction data fed to the inference engine. |
| **Care card** | A single intervention surfaced by the Care engine (micro-confirmation, guided routine, trend alert, or motivation nudge). |
| **Today's Care card** | The top-priority Care card shown inline on the Check-in screen as an entry-point to the full Care route. |
| **Micro-prompt** | A scheduled, lightweight notification that deep-links into the quick-log sheet. |
| **Rollover** | The midnight transition that finalizes one day's summaries and starts the next. |
| **Telemetry flush** | The periodic write of in-memory activity / sleep / interaction signals to Room. |
| **Debug Drawer** | A debug-only full-screen overlay (accessible via long-press on the About-screen version label) providing diagnostic tooling. |

---

*This document describes Moodified version 1.0.0 as implemented in the repository at the time of writing.*
