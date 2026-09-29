# Verification and acceptance procedure

No GitHub runners. Run on an Android development machine or permitted cloud workspace with SDK/dependency access. Do not restart a still-running Gradle/connected-test session because a monitoring call times out; retain the session ID and poll it.

## What happened in this cloud session

- Java: OpenJDK 17.0.20. Gradle, Android SDK/ADB/emulator and Kotlin compiler were not installed/on PATH; no `/dev/kvm`.
- Official Gradle wrapper obtained through GitHub source access; no GitHub Actions used.
- `./gradlew :core:test :app:assembleDebug --no-daemon` attempted. Wrapper failed downloading Gradle 8.13: `java.net.SocketException: Network is unreachable`. See `evidence/gradle-attempt.log`.
- **No Kotlin compilation, JVM tests, Android lint, instrumented tests or APK creation occurred.**
- Static audit checks repository structure/security invariants only. Its exact output is preserved in `evidence/static-audit.log`; it does not validate Kotlin types, Compose APIs or runtime behavior.
- Actual emulator screenshots: **none**. `captures/` is reserved for real captures, not populated with mockups.

## Automated tests authored

`core/src/test/.../NotebookRepositoryTest.kt`: current endpoint, unknown-field retention, lost-response reconciliation without replay, restart after SENDING, stale edit, conflict after preflight, deletion detection, owner isolation, invalid IDs, encoded journal round trip, rich-body preservation, legacy-source preservation, blocked uncertain-write replay.

`core/src/test/.../ApiContractTest.kt`: real login path/body, Origin/JSON headers, no 503 retry, no redirects, malformed-success uncertainty, invalid/cleartext path rejection, identifier encoding, notebook entitlement matrix.

`app/src/androidTest`: deterministic English/Arabic sign-in UI and busy/disabled states; Android Keystore draft persistence and cookie-host/generation isolation. Instrumentation uses a separate `instrumented-v1` encrypted vault. These tests do not authenticate real users and do not establish live API compatibility.

## Required commands

```sh
python3 scripts/static_audit.py
./gradlew :core:test :app:assembleDebug :app:lintDebug --no-daemon
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Record exit codes and attach standard Gradle reports from `core/build/reports/tests/test`, `app/build/reports/androidTests/connected`, and `app/build/reports/lint-results-debug.html`. Preserve failing reports before fixing and rerunning only the relevant gates. Passing mocked transport tests are not permission to mark a live workflow verified.

## Screen capture matrix

Use a dedicated test install and owner-authorized accounts. Never publish credentials, private study notes or identifiable production data in this public repository. Use `scripts/capture-emulator.sh emulator-5554 <state>` after navigating to each state and inspecting its capture.

Required initial screens: sign-in idle/busy/error, password recovery notice, MFA picker/error, Home with/without profile, inbox empty/populated, notebook empty/list/search/no-match, new/plain/rich note, local-save busy/failure, confirmed save, uncertain save, conflict with remote copy, history/restore/discard confirmation, university missing/pending/terms/schedule, account free/paid/expired, global-signout confirmation. Every future feature adds its own screens and important states.

Capture phone and tablet portrait/landscape, English/Arabic RTL, light/dark, large system font and TalkBack traversal. Inspect clipping, focus order, touch targets, contrast, keyboard occlusion and Arabic date/time presentation. Current responsive styling has not undergone that review.

## Live account/data tests

Need two explicitly authorized student accounts (free and paid; MFA-enabled account if available) and real deployment access. Use dedicated synthetic student notes. Never invent credentials, trials, entitlements or passing results.

1. Sign in on website and Android as A. Read the same account ID/profile/university/entitlement. Restart Android and verify cookie/session restoration. Test revoked/expired cookie and optional/required MFA.
2. Create a uniquely named note on website. Read it on Android with attachment/rich/unknown fields. Edit only its title/tags and verify website/iOS retained all body and attachment data.
3. Create/edit a plain note on Android; verify website and iOS see it. Open history and stage/confirm a restore. Verify exact content and version behavior.
4. Open the same note on two clients; save on the other client first. Android must retain its draft and display conflict; no silent overwrite. Repeat a race between preflight and PUT to exercise server 409. Record the tombstone race limitation separately; do not mark deletion concurrency solved.
5. Drop the response after a successful PUT. Kill/reopen Android. Check remote copy: exact match acknowledges without another PUT. If another client edited again, Android must retain conflict and never replay the old PUT.
6. Kill before dispatch, after durable SENDING, during request and after response but before journal removal. Observe network request counts and draft state. Kill while typing before local-save completes and document the bounded recovery gap.
7. Switch to B while A's requests/disk operations are pending. B must never see A's notes or use A's cookies. Switching back to A restores A's encrypted drafts only. Test offline switching and late Set-Cookie responses.
8. Test offline, high latency, TLS failure, malformed responses, storage full, 401/402/403/404/409/429/5xx. No retry loops, swallowed save failures or fake success states.
9. Confirm explicit global logout ends website/iOS sessions; local device removal must not claim to revoke them. Restart after each option.

Google Play and cross-platform media acceptance are separate gates in RELEASE.md and ROOMS.md. Do not test paid transactions, enrollment changes or destructive account operations on production student accounts without explicit test authorization.
