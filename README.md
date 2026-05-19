# Moodified

An Android mood tracking and wellbeing app that helps users log how they feel, surface patterns over time, and offers adaptive, in-the-moment care suggestions.

> Project codename / module name: `Moodified`. The shipped application name is **Moodified**.

## What it does

- **Quick mood logging** — capture mood entries from inside the app or via a `moodified://quicklog` deep link.
- **Insights** — a rule-based inference engine turns logged moods, activity, and sleep signals into interpretive labels and trends.
- **Adaptive Care** — interactive micro-interventions and routines that respond to the user's current state.
- **Calendar view** — browse past check-ins and see mood history over time.
- **Background tracking** — uses Activity Recognition and a foreground "health" service to enrich entries with context.
- **Micro-prompts** — scheduled, lightweight check-ins via WorkManager and notifications.

## Tech stack

- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose + Material 3, Compose Animation, Lottie
- **Architecture:** MVVM with a `core` / `data` / `domain` / `presentation` package split
- **DI:** Hilt (+ Hilt Work)
- **Persistence:** Room (with exported schemas under `app/schemas/`)
- **Async:** Kotlin Coroutines
- **Navigation:** Navigation Compose
- **Background work:** WorkManager, foreground service, boot receiver
- **Platform APIs:** Google Play Services Location, Activity Recognition, Usage Stats

## Module layout

```
app/src/main/java/com/moodified/app/
├── core/           # services, workers, theme, navigation host, permissions, utils
├── data/           # Room database, DAOs, repository impls, broadcast receivers
├── di/             # Hilt modules
├── domain/         # models, use cases, repository interfaces (mood, sleep, activity, inference, intervention)
└── presentation/   # Compose screens & ViewModels (checkin, insight, care, calendar, quicklog, more, devtools)
```

## Build requirements

- Android Studio (recent stable)
- JDK 17
- `compileSdk` 36, `minSdk` 26, `targetSdk` 36

## Build & run

```bash
./gradlew assembleDebug      # build debug APK
./gradlew installDebug       # install on a connected device/emulator
./gradlew test               # unit tests
./gradlew connectedAndroidTest  # instrumented tests
```

The debug build installs under the application id `com.moodified.app.debug`; release uses `com.moodified.app`.

## Permissions

Declared in `AndroidManifest.xml`:

- `ACTIVITY_RECOGNITION` — infer movement context for entries
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_HEALTH` — tracking service
- `POST_NOTIFICATIONS` — micro-prompt reminders
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — keep tracking reliable
- `RECEIVE_BOOT_COMPLETED` — restore scheduled work after reboot
- `PACKAGE_USAGE_STATS` — optional signal source

## Deep links

- `moodified://quicklog` — open the app directly into the quick-log flow.
