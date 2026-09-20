# Contributing to Moodified

Thanks for looking. Moodified is a single-maintainer project shared publicly for reference. Small fixes and thoughtful proposals are welcome — bigger changes should start as a discussion first so time doesn't get spent on something that won't merge.

## Ground rules

- The code is source-available, not open-source. See `LICENSE`. External contributions are accepted only under the same terms; by opening a PR, you agree the copyright holder can incorporate your change under that license.
- Moodified is intentionally on-device and privacy-first. Proposals that add servers, telemetry, or third-party trackers will be declined unless they are strictly opt-in and off by default.
- The app is not a medical device. Nothing here diagnoses, treats, or manages a mental health condition.

## Local setup

See [`docs/clone-and-run.md`](docs/clone-and-run.md) for the full walkthrough. Short version:

```bash
git clone https://github.com/mkepg/Moodified.git
cd Moodified
./gradlew assembleDebug
```

Debug builds install as `com.moodified.app.debug` and don't require the release keystore.

## Green gates

Every PR must pass:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew ktlintCheck
./gradlew detekt
```

`ktlint` and `detekt` are strict. If a rule genuinely does not fit your change, prefer a scoped `@Suppress` with a one-line reason over updating the baseline.

## Style

- Kotlin, Jetpack Compose, Material 3.
- Follow the patterns already in the module you're touching. Composables are `PascalCase`; the baseline captures that intentionally.
- Keep composables small. If a `@Composable` exceeds ~80 lines, extract a helper.
- No comments that restate what the code does. A comment should explain *why* something non-obvious is the way it is.

## PR shape

- One concern per PR. Split refactors from features.
- Commit messages: `<type>(<scope>): <what>` — `feat`, `fix`, `refactor`, `chore`, `docs`, `test`.
- Fill in the PR template. If the change touches UI, include a screenshot or short recording.

## Reporting bugs

Use the bug report template. A logcat snippet or a screen recording is worth ten paragraphs of prose.
