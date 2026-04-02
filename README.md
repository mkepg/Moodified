# Karamay 🌿

A privacy-first mood tracking app built with Kotlin + Jetpack Compose.

---

## Tech Stack

| Layer        | Technology                                      |
|-------------|------------------------------------------------|
| UI           | Jetpack Compose + Material 3                   |
| Architecture | MVVM + Lightweight Clean Architecture          |
| DI           | Hilt                                           |
| Database     | Room (on-device only)                          |
| Navigation   | Navigation Compose                             |
| Animation    | Lottie Compose 6.6.6                           |
| Coroutines   | Kotlin Coroutines + Flow                       |
| Build        | Gradle Version Catalog (`libs.versions.toml`)  |

---

## Color Palette

| Token        | Hex       | Usage                        |
|-------------|-----------|------------------------------|
| Milk White  | `#FDFBF0` | App background               |
| Deep Sage   | `#465940` | Brand primary, buttons, nav  |

---

## Project Structure

```
com.karamay.app
├── core/
│   ├── navigation/       # Route definitions
│   ├── theme/            # Color, Type, Theme
│   └── utils/            # DateTimeUtils
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAOs
│   │   ├── entity/       # Room entities
│   │   └── database/     # KaramayDatabase
│   └── repository/       # Repository implementations
├── di/                   # Hilt modules
├── domain/
│   ├── model/            # Domain models (MoodEntry, Valence, Arousal)
│   ├── repository/       # Repository interfaces
│   └── usecase/mood/     # Use cases
└── presentation/
    ├── checkin/          # Check-in screen + ViewModel
    ├── insight/          # Insight screen (stub)
    ├── intervention/     # Intervention screen (stub)
    ├── more/             # More screen (stub)
    ├── quicklog/         # Quick log sheet + ViewModel
    ├── navigation/       # NavHost + bottom nav
    └── components/       # Shared UI components
```

---

## Setup

### 1. Fonts

See [`FONTS.md`](FONTS.md) for font download instructions.  
Place `.ttf` files in `app/src/main/res/font/`.

### 2. Lottie Files

All `.lottie` files are already placed in `app/src/main/res/raw/`:
- `girl_exploring.lottie` → Check-in hero
- `sad.lottie`, `meh.lottie`, `happy.lottie` → Valence selection
- `no_energy.lottie`, `mid_energy.lottie`, `high_energy.lottie` → Arousal selection

### 3. Build

```bash
./gradlew assembleDebug
```

---

## Phase Roadmap

| Phase | Status      | Scope                                          |
|-------|------------|------------------------------------------------|
| 1     | ✅ Partial  | UI/UX, Manual Mood Entry, Bottom Navigation    |
| 2     | ⏳ Planned  | Mood inference engine, state interpretation    |
| 3     | ⏳ Planned  | Insights, pattern detection, timeline          |
| 4     | ⏳ Planned  | Real-time guidance, motivational support       |

---

## API Targets

- **compileSdk**: 36
- **targetSdk**: 36
- **minSdk**: 26

---

## Notes

- All data stays **on-device** — no external network calls
- Room database uses `fallbackToDestructiveMigration` during development
- Switch to proper migrations before production release
