# Android release verification

**Not release-ready. Full student parity is unfinished. No successful Android build or runtime verification exists for this checkpoint.**

## Source checkpoint

Initial native Kotlin/Compose implementation includes existing-account password/TOTP sign-in, encrypted session persistence, profile/entitlement display, native navigation, notification inbox read, university published projection, and notebook list/search/plain editing/history/draft/conflict workflows. Existing rich note data is retained rather than flattened; full rich authoring remains missing.

The complete requested scope and remaining work are in [PARITY_SWEEP.md](PARITY_SWEEP.md). No source-only feature is labeled verified. Account/API contracts were inspected in the website; newer local uncommitted iOS work was not accessible.

## Exact execution status

| Check | Result | Evidence |
|---|---|---|
| Repository access | Android repository initially empty; references readable | GitHub connector reads; source manifest |
| Source boundaries | Only Android repository written | No reference sources included; no backend/iOS commits |
| Gradle wrapper launch | FAILED before compilation | `docs/evidence/gradle-attempt.log`, network unreachable downloading Gradle 8.13 |
| JVM unit/API tests | NOT RUN | Test sources authored; no passing report |
| APK/Compose compilation | NOT RUN | Gradle could not start dependency resolution |
| Android lint | NOT RUN | Toolchain unavailable |
| Emulator/device/UI/storage tests | NOT RUN | No Android SDK/ADB/emulator/device |
| Screenshots and visual refinement | NOT RUN | No actual emulator captures exist |
| Static audit | See saved output | `docs/evidence/static-audit.log`; not compilation/runtime evidence |
| Live web/Android/iOS data | NOT RUN | Authorized test accounts/device access not supplied |
| Voice/video/screen share | NOT IMPLEMENTED / NOT TESTED | `docs/ROOMS.md` is contract analysis only |
| Google Play purchase/restore | NOT IMPLEMENTED / NOT TESTED | No Android products, verification endpoint or signing/test track supplied |
| GitHub Actions/runners | NOT USED | No workflows added or invoked |

## Known limitations and release gates

1. Resolve dependencies and compile; fix actual Kotlin/Compose/build failures, run authored tests and Android lint, then capture and inspect every implemented state.
2. Complete all launched website student workflows in the parity ledger. Clarify inconsistent hub “coming soon” labels vs mounted routes. Do not ship withdrawn/coming-soon features as invented functionality.
3. Run actual free/paid/MFA accounts and web/Android/iOS data round trips, simultaneous edits, interrupted saves, expired sessions and account-switch isolation. Unit fixtures cannot satisfy these gates.
4. Notebook edit/delete concurrency is not completely safe: existing server tombstone writes can resurrect a concurrently deleted note. Generic user-state still lacks conditional client updates. Backend changes require separate approval; do not edit synapse in this task.
5. Draft persistence is asynchronous; latest keystrokes before the local-save indicator completes can be lost on process death. Storage is encrypted and excluded from backup; uninstall/clear-data removes local drafts. Large-note/storage-full tests are required.
6. Implement and validate full rich note rendering/authoring/attachments, all QBank/flashcard/calendar/library/game/collaboration features, and complete accessibility/localization/tablet behavior.
7. Implement compatible room signaling and native mediasoup/WebRTC media; validate with real website and physical iPhone peers. iOS calling has not been proven. Explicitly test background/incoming-call and screen-broadcast behavior.
8. Configure Android application identity/signing and Google Play products/test accounts; implement server-verified purchase/restore with approval for any required backend work. Existing web Kashier payments are not Android purchase restoration.
9. Complete privacy/data-retention review, dependency/checksum/license review, signed installable builds and full regression before any release claim.

## Needed to continue acceptance

- A build environment with Android SDK and dependency downloads enabled (cloud is fine; no GitHub runners required).
- Two authorized test student accounts and test permission for the intended endpoint; a paid/free pair and MFA coverage.
- Current iOS reference snapshot including the local Rooms work and PARITY_SWEEP.md, plus physical Android/iPhone access for media testing.
- Android signing/package/product configuration and approved server verification plan for Play Billing.

These prerequisites block their corresponding tests/integrations, not source development. The first implementation is a checkpoint toward full parity, not a redefinition of completion.
