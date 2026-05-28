# Moodified — Refactor & Production-Readiness Plan

> Scope: Refactor the dev-tools Activity / Sleep / Interaction monitors into user-facing screens, and prepare the app for production with proper debug/release separation.

---

## 0. Current state (what was found)

- **Monitors are already running in release builds.** `TrackingService` drives `ActivityRepositoryImpl`, `SleepRepositoryImpl`, `InteractionRepositoryImpl` continuously; their data feeds `RuleBasedMoodInferenceEngine`. Only the *screens* in `presentation/devtools/{activity,sleep,interaction}monitor/` are dev-flavored — and they are already routable from `MoreScreen` via `AppRoutes.ActivityMonitor / SleepMonitor / InteractionMonitor`.
- **No source-set or flavor separation today.** Everything lives in `src/main`. `release` enables minify+shrink; `debug` only adds `.debug` suffix. No `src/debug`, no flavors, no signing config, no `keystore.properties`.
- **The monitor screens mix two concerns:** user-facing summary/charts and developer-facing raw signal cards (`accelAvailable`, sensor IDs, live `Signal` objects, mock generators in `core/debug/`). Refactor = split these.
- **Permissions UX is partial.** `ACTIVITY_RECOGNITION` + `POST_NOTIFICATIONS` are requested via launcher in `MoreScreen`; `PACKAGE_USAGE_STATS` is only checked, never explained. No rationale screens, no data export/delete.

---

## 1. Refactor strategy — split user vs. debug surfaces

**Recommended UI integration:** promote each monitor out of `/devtools` into a proper user screen (`presentation/insights/`), with friendlier names: **Activity**, **Sleep**, **Screen Use**. Surfaced from `MoreScreen` initially; later we can choose whether to give them dedicated tab placement.

**Per monitor, split into three layers:**

```
presentation/insights/activity/
  ActivityInsightScreen.kt        ← user-facing: summary card, weekly chart, intensity ring
  ActivityInsightViewModel.kt     ← exposes ActivityInsightUiState (no raw Signal)
  components/                     ← reusable chart cards already in devtools/, moved + renamed
presentation/devtools/activity/   ← debug-only (gated, see §2)
  ActivityDebugScreen.kt          ← raw ActivitySignal dump, sensor availability, mock toggle
```

Same shape for Sleep and Interaction. Key transformations:

- **Strip "raw debug" cards** from the user screens (current `RawDebugCard` / `LiveSignalCard` content goes to `ActivityDebugScreen`).
- **Convert ViewModel state to a UI-shaped model**, not the `Signal` data class. `ActivityInsightUiState(stepsToday: Int, intensityRing: List<IntensityBand>, weekly: List<DayBucket>, lastUpdated: Instant)`. This makes labels translatable and decouples UI from sensor schema.
- **Move mock data generators** in `core/debug/` to `src/debug/` (§2).
- **Empty/loading/permission-denied states**: today the screens assume data is flowing. Add explicit `Empty`, `PermissionRequired`, `Loading`, `Error` UI states with friendly copy — non-negotiable for user-facing screens.

**Navigation changes** in `presentation/navigation/AppRoutes.kt` + `MoodifiedNavHost.kt`:

- New routes: `insight/activity`, `insight/sleep`, `insight/screen_use` (user-visible).
- Old routes `dev/activity_monitor` etc. become **debug-only** and registered in a `DebugNavGraph` that's only added to the NavHost in debug builds (§2).
- `MoreScreen` shows the three user routes always; a "Developer tools" section appears only when `BuildConfig.DEBUG`.

---

## 2. Build / environment configuration

**Recommended debug-split:** `src/debug` source set, because:

- Release APK *physically cannot* contain dev tools (Play Store scrutiny, reverse-engineering risk).
- The debug surface is already isolated under `presentation/devtools/`.
- Cost is one indirection: a small `DebugEntryPoints` interface in `main` with a stub release impl and a real debug impl. Hilt makes this trivial.

**Concrete Gradle changes** (`app/build.gradle.kts`):

```kotlin
buildTypes {
  debug {
    applicationIdSuffix = ".debug"
    versionNameSuffix = "-debug"
    isMinifyEnabled = false
    isDebuggable = true
    buildConfigField("boolean", "ENABLE_MOCK_DATA", "true")
    buildConfigField("String", "LOG_LEVEL", "\"VERBOSE\"")
  }
  release {
    isMinifyEnabled = true
    isShrinkResources = true
    isDebuggable = false
    signingConfig = signingConfigs.getByName("release")
    buildConfigField("boolean", "ENABLE_MOCK_DATA", "false")
    buildConfigField("String", "LOG_LEVEL", "\"WARN\"")
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  }
}
signingConfigs {
  create("release") {
    val props = Properties().apply {
      file("keystore.properties").takeIf { it.exists() }?.let { load(it.inputStream()) }
    }
    storeFile = props.getProperty("storeFile")?.let(::file)
    storePassword = props.getProperty("storePassword")
    keyAlias = props.getProperty("keyAlias")
    keyPassword = props.getProperty("keyPassword")
  }
}
```

- `keystore.properties` added to `.gitignore`; a `keystore.properties.example` committed.
- No flavors yet — overkill until we have a separate "internal" track.

**New source sets:**

```
app/src/debug/java/com/moodified/app/
  presentation/devtools/...        ← moved from main
  core/debug/MockDataGenerators.kt ← moved from main
  di/DebugModule.kt                ← provides DebugEntryPoints real impl
app/src/release/java/com/moodified/app/
  di/ReleaseModule.kt              ← provides DebugEntryPoints stub (no-op)
```

**Manifest hygiene:**

- `src/debug/AndroidManifest.xml`: add `tools:replace` to allow `usesCleartextTraffic=true` only in debug, declare any debug-only activities.
- `src/main/AndroidManifest.xml`: `android:allowBackup="false"` (Moodified holds sensitive behavioral data), `android:dataExtractionRules` for Android 12+.
- `android:exported` audit: `BootReceiver` is currently `exported="true"`, which is correct (system broadcast), but double-check.

**Logging:** Introduce a thin `Logger` wrapper that no-ops at WARN level for release. Today there are scattered `Log.d`/`Log.i` calls in repositories — these will leak telemetry detail in release.

---

## 3. Security considerations

| Area | Current | Action |
|---|---|---|
| Sensitive data at rest | Room DB unencrypted; DataStore plaintext | Add SQLCipher to Room (or at minimum, mark DB `noBackup`) — sleep/screen-use data is sensitive. |
| Backup | Default `allowBackup` is true | Set `allowBackup=false` + add `dataExtractionRules` XML. |
| PACKAGE_USAGE_STATS | Used to infer sleep + screen use | Add prominent in-app disclosure ("we read screen-on times to infer your sleep window") before deep-linking to Settings. Required by Play policy. |
| Privacy policy | None | Add a stub `PrivacyPolicyScreen` accessible from More, plus URL field in BuildConfig for hosted version. Required for Play submission. |
| Data export / delete | None | Add export-to-JSON and "delete all my data" in More → Privacy. GDPR-style hygiene. |
| ProGuard exposure | Default rules | Add `-keep` for Room entities, Hilt-generated, Lottie if reflection used; verify release build runs end-to-end. |
| Foreground service notification | Already required, but text matters | Audit copy — must clearly state passive monitoring is active. |
| Debug paths in release | Currently impossible to verify | After src/debug move, grep release APK to confirm no `devtools` classes remain. |

---

## 4. Performance optimizations

- **Polling intervals** are aggressive: Interaction monitor polls every 60s, sleep every 5min, activity has a 10s cadence ticker. Consider:
  - Move interaction polling to **WorkManager periodic work** (15-min minimum) once we leave the screen — Hilt-Work is already wired.
  - Activity 10s ticker should pause when screen is off (`PowerManager.isInteractive`).
- **`TrackingService` lifecycle**: today it's started indefinitely. Add an idle-timeout — if no monitor is active for X minutes, stop the service.
- **Room queries** in monitor ViewModels: ensure all use `Flow` with `distinctUntilChanged()` and that DAOs return room-paginated results for weekly views.
- **Charts**: the existing devtools charts likely recompose excessively. Use `derivedStateOf` and stable model classes for chart data when promoting to user screens.
- **Startup**: defer non-critical work (7-day backfill) until after first frame via `Lifecycle.Event.ON_START`.

---

## 5. Deployment readiness checklist

1. **Versioning**: introduce `versionCode`/`versionName` driven from git tags or a `version.properties`.
2. **Release build verification job** (later, but stub now): a Gradle task `assembleRelease` must succeed on CI; add a smoke test that installs the release APK and launches MainActivity.
3. **R8 mapping file**: ensure `release/mapping.txt` is preserved for crash deobfuscation.
4. **App Bundle (AAB)**: enable `bundle { ... }` config splits by ABI + density.
5. **Play prerequisites**: target SDK 36 ✅ already; data-safety form draft (will need exact list of collected data — Activity Recognition, Usage Stats); content rating; privacy policy URL.
6. **Crash reporting**: recommend Firebase Crashlytics, initialized only in release (`if (!BuildConfig.DEBUG) FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)`).
7. **Pre-launch checks**: run on Firebase Test Lab matrix once release build is green.

---

## 6. Execution order (suggested)

1. **Phase A — Build config plumbing** (low risk, no UI changes): signingConfig, keystore.properties, BuildConfig fields, allowBackup=false, ProGuard pass with release build verification.
2. **Phase B — Source set split**: move `presentation/devtools/` and `core/debug/` to `src/debug/`. Introduce `DebugEntryPoints` interface + release stub. Update `MoreScreen` to conditionally show dev section. *Release build still compiles.*
3. **Phase C — User-facing screens**: create `presentation/insights/{activity,sleep,screen_use}/`, lift the chart components, write new UiState models, add empty/permission/error states, wire new routes.
4. **Phase D — Privacy UX**: usage-access disclosure, privacy policy stub, export/delete.
5. **Phase E — Performance & polling**: WorkManager migration where appropriate, service idle timeout.
6. **Phase F — Crashlytics + deployment**: hook crash reporting, generate signed AAB, draft Play data-safety entry.

---

## Open decisions before starting

- **UI placement** (More vs. dedicated tab vs. under Insight) — assumed More + detail screens.
- **Debug split** — assumed `src/debug` source set.
- **Privacy + crash reporting scope** — included by default; flag if you want to defer.
- **Encrypted DB** — SQLCipher adds ~6 MB and a key-management story. Worth it for sleep/screen data, but can be deferred.
