# QBank contracts and continuation evidence

Inspected 2026-09-30 against `doitrous/synapse` commit `3d26ff80deac92aaaa4d3ccce1aadd48421c80c7`. Reference sources remain read-only and are not copied into this repository.

## Content and permissions

- `server/src/studentContent.js`: `/api/content/questions?view=summary` returns an `{items}` envelope, scoped by publication, student university and year. Summaries omit question bodies and answers.
- Full questions require explicit `?ids=` hydration, maximum 200 IDs. Student bulk full-bank requests are rejected. Full GETs can consume content budget and the free weekly question allowance. Android never warms/prefetches full questions from catalogue browsing.
- Full responses can include reference title stubs; only question rows are projected. Android preserves manifest order and refuses duplicate IDs, missing questions, invalid keys and unsupported formats without shortening a sitting. Missing content may still have consumed server allowance; the client cannot roll that back.
- `src/data/questionProjection.ts`: single-best answer ordering filters blank answer text first, then matches `correctAnswer` to answer label. Correct answer explanation is a fallback for `fields.Explanation`. Android text rendering is incomplete for rich content, media and equations.

## Shared documents and session rules

- Active session: `nishany.qbank.activeSession.v1`, through `/api/user-state/:key` with `{value}` envelope. Never write this through shared published `/api/state`.
- Unified history: `nishany.sittings.v1`, `{version:1,sittings:[...]}`. The native read-only list does not yet reconstruct older MCQ history from attempt shards.
- Additional website keys: `nishany.qbank.marked.v1`, `nishany.qbank.sessionNames.v1`, `nishany.qbank.sessionQuestions.v1`, `nishany.progress.attemptIndex.v1`, `nishany.progress.attempts.YYYY-MM`.
- `src/pages/student/qbank/state.ts` / `src/data/qbankSession.ts`: session keeps IDs/order, answers, checked map, mode, sessionId, elapsed, per-question seconds, visited indices, struck choices, reviewing, name, phase, submitted and startedAt. Android immutable patch helpers retain unrelated JSON fields; helpers are not a writable runner yet.
- A submitted/results/reviewing session or a session already in the finished ledger must not be resumed. Viewing history must not overwrite a different active session. Current Android viewer performs no writes, including no clock or navigation-position changes.
- Website timed allowance is 90 seconds per question, followed by overtime without automatic submission. Clock/background behavior is **not implemented** on Android.

## Submission is not a single transaction

`server/src/qbankAttempts.js` accepts POST `/api/qbank/attempts` with an attempts array. The server validates enrollment/content/allowance and returns results and skipped records; HTTP success alone is not proof every answer was recorded. Server attempt IDs are bounded and need careful stable identity handling. The server owns verified correctness.

The website separately maintains user-state month shards, index, active session, session manifest/names and sitting history. The attempt service does not make these writes atomic. The inspected routes offer no per-attempt receipt lookup sufficient to prove whether a lost POST response completed. Do not blindly replay a possibly completed POST or infer receipt from a separately saved client record. A complete submission journal and an approved backend receipt/conditional-update contract may be necessary; no backend changes were made.

`UserStateRepository` provides infrastructure for single-document writes only: owner-bound encrypted journal, first-base preservation, preflight comparison, pending journal before PUT, read-back equality, explicit reconciliation and explicit local discard. Any attempted write stays blocked if acknowledgement/readback is lost. A conflicting readback retains the local document. Neither preflight nor readback eliminates a racing write between those operations. Equality indicates desired content is present, not which client wrote it. This infrastructure is not connected to QBank mutations in this checkpoint.

## Actual checks and remaining acceptance

- Ten new JVM test methods authored (six QBank, four generic state). None executed because Gradle/dependencies and Android tools are unavailable.
- Static repository audit: 19 checks passed; total 29 JVM and 4 device tests authored. This is not compilation or runtime acceptance.
- No emulator screenshots, authenticated endpoint tests, real saved-session viewing, scoring or synchronization acceptance performed.
- Required next: build/compiler fixes as needed; native rich/media rendering; complete builder and writable runner; monotonic lifecycle-aware clock; durable coordinated submission; flags/reports; legacy history; test every supported format; all live account/round-trip/failure/RTL/accessibility/device checks.
