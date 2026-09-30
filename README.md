# Nishany Android

Native Kotlin / Jetpack Compose student app, using the existing Nishany accounts and backend.

**Development checkpoint, not a release.** This repository does not yet have full student parity. Android compilation, emulator tests and live account round trips have not passed. See [RELEASE.md](RELEASE.md) and the full [PARITY_SWEEP.md](PARITY_SWEEP.md).

## Present in source

- Password sign-in, TOTP challenge, session recovery, password-recovery request, account and entitlement display.
- Native home, QBank, notebook, university and account screens; English/Arabic, RTL and system light/dark appearance.
- Current per-note notebook API: list, local search, create, plain-body editing, metadata editing without flattening rich notes, history and prepared restore.
- Encrypted account-scoped draft journal, server-version conflict handling and read-only reconciliation of uncertain writes.
- QBank summary filtering, explicit saved-session viewing and unified test-history reading. Answers, timers and submissions are not enabled.
- Generic user-state write journal infrastructure with preflight/read-back checks; not yet connected to QBank mutations.
- University modules and published schedules from `/api/me/university`.

No WebView application shell, separate account system, fabricated student data, or GitHub Actions workflow is included.

## Build in a development environment

Requirements: JDK 17, Android SDK platform 36/build tools 35.0.0, network access to Gradle, Maven Central and Google's Maven repository. Android Studio can open this directory. `local.properties` may contain your `sdk.dir`; do not commit it.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug --no-daemon
# With an authorized emulator/device running:
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

`core` contains portable Kotlin contracts and notebook recovery logic; `app` contains Compose UI and Android Keystore persistence. All checks run on the invoking machine. **Do not add GitHub runners or Actions.**

The default endpoint is `https://nishany.com`. Use authorized test accounts only. No credentials, signing keys, user exports, private reference source code or production content are included.

The cloud build attempt is preserved in [docs/evidence/gradle-attempt.log](docs/evidence/gradle-attempt.log): the Gradle distribution could not be downloaded. This is a tooling failure before compilation, not a passing or failing Kotlin compilation result.

Read [docs/CONTRACTS.md](docs/CONTRACTS.md), [docs/TESTING.md](docs/TESTING.md), and [docs/ROOMS.md](docs/ROOMS.md) before extending the app. `doitrous/synapse` and `doitrous/nishanyios` are reference-only; never modify them during Android development.
