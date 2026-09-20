# Clone and Run

A short, practical guide to getting **Moodified** running on your machine.

> The release signing key is **not** in the repository. Debug builds work out of the box; if you need a signed release build you'll have to generate your own keystore (see §5).

---

## 1. Prerequisites

Install these once:

| Tool                | Version                        | Notes |
|---------------------|--------------------------------|-------|
| **JDK**             | 17 (LTS)                       | Android Studio's bundled JDK is fine. |
| **Android Studio**  | Recent stable (Hedgehog or newer) | Bundles the SDK, emulator, and Gradle support. |
| **Android SDK**     | Platform 36                    | Install via Android Studio's SDK Manager. |
| **Git**             | Any recent                     | For cloning. |

`minSdk` is 26 — any Android device or emulator on Android 8.0+ will work.

---

## 2. Clone the repository

```bash
git clone <repository-url> Moodified
cd Moodified
```

---

## 3. Open in Android Studio

1. **File → Open** → select the `Moodified` directory.
2. Wait for Gradle sync to finish (first sync downloads Gradle 9.3.1 and all dependencies — give it 5–10 min on a fresh machine).
3. If prompted, accept any SDK component installs.

`local.properties` is **not** committed (it points to *your* Android SDK location). Android Studio creates it automatically the first time you open the project. If you ever build from the command line on a machine where Android Studio hasn't generated it, create it manually:

```properties
# local.properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk   # Windows
# sdk.dir=/Users/<you>/Library/Android/sdk               # macOS
# sdk.dir=/home/<you>/Android/Sdk                        # Linux
```

---

## 4. Run a debug build

The fastest path — no signing, mock data enabled, debuggable.

**From Android Studio:** pick the `app` run configuration and press ▶️.

**From the command line:**

```bash
./gradlew installDebug      # build + install on a connected device/emulator
./gradlew assembleDebug     # build only
```

Debug builds install under the application id `com.moodified.app.debug`, so they can live side-by-side with a release install.

---

## 5. Run a release build

Release builds are minified, shrunk, and signed. The keystore is **not** in the repository — you need to bring your own.

**Generate a keystore** (once):

```bash
keytool -genkeypair -v \
  -keystore moodified-release.jks \
  -alias moodified \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Then create `keystore.properties` in the project root (copy `keystore.properties.example` as a starting point):

```
storeFile=../moodified-release.jks
storePassword=<your keystore password>
keyAlias=moodified
keyPassword=<your key password>
```

Both files are gitignored. Then:

```bash
./gradlew assembleRelease   # signed release APK
./gradlew bundleRelease     # signed AAB (for Play Store upload)
```

If you only want to run the app on your own device, use debug builds (§4) — no keystore needed.

---

## 6. Where the generated APKs live

| Build type     | Output |
|----------------|--------|
| Debug APK      | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK    | `app/build/outputs/apk/release/app-release.apk` |
| Release AAB    | `app/build/outputs/bundle/release/app-release.aab` |
| ProGuard map   | `app/build/outputs/mapping/release/mapping.txt` |

Install a built APK manually with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 7. Tests

```bash
./gradlew test                  # JVM unit tests
./gradlew connectedAndroidTest  # instrumented tests (needs device/emulator)
```

---

## 8. Useful Gradle tasks

```bash
./gradlew clean              # wipe build outputs
./gradlew tasks              # list all available tasks
./gradlew --stop             # stop background Gradle daemons
```

On Windows use `gradlew.bat` if you're not in Git Bash / WSL.

---

## 9. Troubleshooting

- **"SDK location not found"** — open the project once in Android Studio, or create `local.properties` as shown in step 3.
- **"Unsupported Java version"** — set Project SDK to JDK 17 under **File → Project Structure → SDK Location → Gradle JDK**.
- **First sync is very slow** — Gradle 9.3.1 and all dependencies are being downloaded on first run. Subsequent builds are fast.
- **Release build fails with a signing error** — the keystore is not committed. Follow §5 to create your own, or stick to debug builds.
- **`adb: device not found`** — enable USB debugging on your device, or start an emulator from Android Studio's Device Manager.

---

## 10. Deep link (handy for testing)

```bash
adb shell am start -W -a android.intent.action.VIEW -d "moodified://quicklog"
```

Opens the app directly into the quick-log flow.
