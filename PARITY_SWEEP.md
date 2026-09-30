# Android student parity ledger

Updated 2026-09-30 UTC. Full requested parity remains the target. **No Android feature is verified yet.**

Status meanings: **missing** = no Android implementation; **partial** = some source exists but scope remains incomplete; **implemented** = scoped source exists, still awaiting runtime acceptance; **verified** = required runtime/live evidence attached. Buildability is itself unverified, so this checkpoint conservatively uses partial/missing only.

Initial website evidence below is pinned to `dbe891d416b0247560dd48a9ab363c4749fde98e`, not inferred from old iOS ledgers. Paths are in `doitrous/synapse` unless prefixed Android. The source manifest records hashes for the downloaded reference files. QBank-specific contracts were additionally inspected at `3d26ff80deac92aaaa4d3ccce1aadd48421c80c7`; see `docs/QBANK.md`. All fixtures below are authored tests, not execution results.

## Product availability

`src/router.tsx` excludes Practical and Essays with `COMING_SOON_PATHS` and redirects their paths to Home. Adaptive Study is unpublished; World redirects to Home; Maristanas is withdrawn/admin-only. Do not publish invented student versions of those features.

There is a **website availability inconsistency**: `src/pages/student/Revise.tsx` lists Whiteboard, Oral questions, Anatomy Atlas and Performance under `SOON`, but `src/router.tsx` still mounts those routes. Their source exists, but their student launch status needs product confirmation before Android exposes them. This differs from the explicitly redirected Practical/Essays/World routes.

## Accounts and shell

| Student feature | Android | Website evidence | Android evidence / remaining acceptance |
|---|---|---|---|
| Password sign-in using existing accounts | partial | `server/src/authRoutes.js` /login | `AccountRepository`, Compose form; live login not tested |
| TOTP challenge | partial | /mfa/factors, /challenge, /verify | Existing verified-factor selection; no enrollment/recovery-factor management |
| Session refresh/relaunch | partial | `auth.js`, `sessionStore.js`, `/api/session` | Encrypted cookie, server-owned refresh; emulator expiry/rotation tests needed |
| Signup/verification/social/passkeys | missing | `authRoutes.js`, `routes/auth.js`, router auth pages | Turnstile, OAuth/PKCE, app links and native passkey contracts required |
| Password recovery/reset | partial | /auth/recover, /auth/password, /auth/verify | Recovery email request only; link completion and password change missing |
| University enrollment/onboarding | missing | `routes/me.js`, `accounts.js`, `data/onboarding.ts` | Need real catalogue selection, general track, locks, username/phone conflicts |
| Profile and entitlements read | partial | GET /api/me | Account screen displays name/email/plan/expiry; live validation pending |
| Profile editing/avatar/discoverability | missing | `routes/me.js`, `accounts.js` | Must preserve omitted fields, locked username, upload ownership |
| Account export/deletion/management | missing | `routes/me.js`, Account page | Lifecycle and retention semantics need complete audit |
| Local sign-in removal | partial | Native device behavior | Retired cookie jar, canceled client, owner-scoped drafts; device tests unrun |
| Global sign-out | partial | POST /api/auth/logout | Explicit all-device confirmation; live web/iOS revocation not tested |
| Native home/navigation | partial | `src/router.tsx`, Dashboard page | Five native destinations; full dashboard metrics/navigation missing |
| Global search | missing | Shell/library/QBank sources | Notebook summary filtering alone is not global search |
| Notification inbox | partial | `routes/notifications.js`, `notifications.js` | Reads real array-shaped inbox; unread marking, routing, push and preferences missing |
| Deep links | partial | `src/router.tsx` | Exact HTTPS notebook/university routes; no verified App Links association or detail/invite routing |
| Entitlement UI gate | partial | `src/lib/entitlement.ts` | Notebook active/trialing + studyTools gate; full plan matrix incomplete |

## Study workflows

| Student feature | Android | Website evidence | Remaining scope / evidence |
|---|---|---|---|
| QBank formats and rendering | partial | `questionProjection.ts`, `studentContent.js` | Single-best saved-session text viewer only; images/math/rich renderers and other formats missing |
| QBank filters and session builder | partial | QuestionBank + qbank builder/state | Summary topic/subject/difficulty filters; new-session builder, source/flag/incorrect/omitted filters missing |
| Timed sessions and resume | partial | QuestionBank / qbank state | Explicit read-only saved-session fetch preserving exact order; answering, timing and writable resume missing |
| Flags, explanations, reports | partial | QuestionBank + `routes/contentReports.js` | Existing checked tutor explanation displayed; checking answers, flags and reports missing |
| Verified attempts/history/performance | partial | `routes/qbank.js`, `qbankAttempts.js`, `data/sittings.ts` | Unified history list read only; legacy shard history, server submission/scoring and performance missing |
| Flashcard review and scheduling | missing | `pages/student/Flashcards.tsx`, `lib/useFlashcards.ts` | Match actual scheduler and log schema; generic user-state has no conditional-write contract |
| Basic/cloze/rich/occlusion authoring | missing | `components/flashcards/*`, `data/flashcards/*` | Full native editors/renderers; unknown-field preservation |
| Flashcard undo/bury/suspend/settings | missing | Flashcards study/browse controllers | Review state transitions, restored logs and day boundaries |
| Flashcard import/export/sharing/stats | missing | Flashcards add/browse/stats/shared pages | File/media handling, visibility/ownership, scheduled-card stats |
| Library article browsing/reading | missing | `pages/student/Library.tsx` | Content-slice/index APIs, full rich reader, images, plan gates |
| PDFs/resources and recent items | missing | `Resources.tsx`, ResourceReader page | Native PDF, search, managed authenticated media, recent-state schema |
| Bookmarks/uploads/annotations/sharing | missing | Resources + documents/shares routes | Ownership, byte limits, draft recovery and collaborative versions |
| Notebook summaries/search/create | partial | current notebook client/routes/model | `NotebookRepository`, Compose list; only loaded summaries searched |
| Notebook plain-body/title/tag editing | partial | `src/data/notebook.ts` | Native fields and immutable JSON patches; fixtures authored, no build yet |
| Notebook rich formatting and attachments | partial | Notebook + editor JSON schema | Rich/unknown bodies retained and body-read-only; native rich/image/drawing authoring missing |
| Notebook versions/restore | partial | /notes/:id/versions | History list and explicit staged restore; live history retention/restore test pending |
| Notebook durable drafts/account isolation | partial | notebookClient recovery ownership | Encrypted journal; save indicator; kill/relaunch/device tests pending |
| Notebook conflicts/uncertain delivery | partial | notebookNotes saveNote transaction | No blind replay; remote comparison/copy/discard; tombstone race remains |
| Notebook delete/sharing | missing | notebook delete + shares routes | No remote delete until conflict semantics are addressed; sharing not implemented |
| Calendar tasks/subtasks/groups | missing | `pages/student/Calendar.tsx` | Preserve task/group unknown fields and completion rules |
| Study blocks/timetable/material links | missing | Calendar + academic projection | Scheduling, related content and owner data |
| Month/week/day views | missing | Calendar page | Native date navigation, timezone and DST tests |
| Essays | missing | router excludes essays | Website coming soon; do not expose as launched |
| Clinical/OSCE Practical page | missing | router excludes practical | Website coming soon; distinguish Skills and Histology routes |
| Histology | missing | `pages/student/Histology.tsx` | Native slide viewer and real media/progress |
| Skills | missing | router skills + Revise links | Checklists and progress; current native Android route absent |
| Oral rehearsal | missing | OralQuestions + router; Revise SOON | Publication inconsistent; native timer/rehearsal/progress absent |
| Medical terminology | missing | router terminology/taxonomy | Glossary/search/known-term state and native related practice |
| Daily question | missing | `routes/qotd.js` | Server day's question/answer/streak/leaderboards; no client invented day/scoring |
| Performance | missing | router + Performance; Revise SOON | Availability clarification and full native analytics required |
| Study Tools hub | missing | `pages/student/Revise.tsx` | Full navigation, due counts, progress and current publication state |
| University enrollment projection | partial | `routes/me.js`, `academic.js` | Native GET /me/university, null/pending states |
| University terms/modules/schedules | partial | academic studentUniversityProjection | Reads published schedules incl. date/weekday/startTime/endTime; live content test pending |
| Curriculum topics/assessments/materials | missing | academic projection subjects/assessment/coverage | Full rich native navigation and related study links |
| Tutorial | missing | router/tutorial page and video routes | Native walkthrough and video/accessibility |
| Anatomy atlas | missing | `pages/student/AnatomyAtlas.tsx`; Revise SOON | Native models/rendering/cache/license audit, availability clarification |
| Teaching hospital/World/Maristanas | missing | router redirects, world services | Currently withdrawn/admin-only; no invented student launch |

## Minigames

Evidence: router studentPages, `src/components/games/MinigamesHubPage.tsx`, games kit catalogue. Every game below is **missing** on Android; record migration must retain `nishany.minigames.records.v1`. Each needs pack availability, server content gating, scoring and simultaneous-device save tests; the hub alone does not count as implementation.

| Game route | Android |
|---|---|
| term-grid | missing |
| spotter | missing |
| term-match | missing |
| clinical-sequence | missing |
| mechanism-chain | missing |
| red-flag-sort | missing |
| true-false | missing |
| word-builder | missing |
| system-sort | missing |
| label-drop | missing |
| tissue-id | missing |
| lab-range | missing |
| drug-class | missing |
| eponym-pairs | missing |

## Collaboration, media and payments

| Feature | Android | Evidence / acceptance still needed |
|---|---|---|
| Whiteboard object editing/persistence | missing | Whiteboard page and shares; routable but Study Tools says coming soon |
| Whiteboard sharing/live collaboration | missing | Versioned share contracts and permission audit before native implementation |
| Room discovery/create/join/seats | missing | `StudyRooms.tsx`, `routes/studyRooms.js`, `studyRooms.js`, room channel |
| Focus/chat/replies/pins | missing | StudyRooms and roomsRealtime/party chat; native persistence/events absent |
| Friends/invites/shared activities | missing | friends/parties routes and rooms modules; no Android feature yet |
| Host/member/viewer permissions | missing | Server roster refreshed on protected operations; never trust only UI role |
| Room WebSocket/SFU signaling | missing | Inspected protocol documented in `docs/ROOMS.md`; documentation is not an implementation |
| Audio/video production/consumption | missing | No mediasoup/WebRTC native dependency or media engine added |
| Remote screen-share reception | missing | Must consume video source=screen with compatible codecs/transport |
| Mute/deafen/routes/camera/reconnect | missing | Need actual Android audio focus, Bluetooth/wired routes, permissions and lifecycle |
| Background calls/incoming calls | missing | Explicit Android foreground-service/notification/background restrictions evaluation required |
| Android screen broadcasting | missing | MediaProjection consent, foreground service, stop/revocation and rotation tests required |
| Web ↔ Android ↔ physical iPhone calls | missing | No live calling acceptance; iOS local calling source not available and not proven |
| Existing account entitlement | partial | /api/me read plus notebook UI gate; real paid/free accounts not tested |
| Google Play products/purchase/restore | missing | Need configured products, package/signing, testing track, server verification; Kashier is not Play Billing |

## Quality and evidence

| Requirement | Status | Evidence |
|---|---|---|
| Cloud source upload; no GitHub runners | first checkpoint uploaded and read back | Initial commit `efc071c`; no workflows or Actions invocation. Later uploads recorded in Git history |
| Kotlin/Android compile | blocked | `docs/evidence/gradle-attempt.log`: distribution download failed before compilation |
| Unit/API tests | authored, not run | 29 JVM test methods; inventory updated by static audit |
| Instrumented UI/storage tests | authored, not run | 4 methods; deterministic sign-in form and isolated test vault |
| Static repository audit | see evidence log | XML/wrapper/source boundary checks only, not a Kotlin compiler |
| Actual emulator screenshots | missing | None captured; no preview render is claimed as an emulator screenshot |
| Loading/empty/errors | partial source | Busy progress, no data, auth/entitlement/rate-limit/network/local-save/conflict messaging |
| Offline/slow/expired/failed-save runtime | unverified | Execute fault matrix in TESTING.md |
| Relaunch and simultaneous edits | unverified | Fixtures only; live test requires accounts + devices + endpoints |
| English/Arabic/RTL | partial | Bilingual Compose text and direction; native-speaker/layout/device audit pending |
| TalkBack/large text/tablets | partial | Labeled controls, headings, live regions, scrollable width-limited layout; no device audit or tablet layout refinement |
| Dark/light | partial | System Material3 color schemes; captures/contrast audit pending |
| Signing/installable release | missing | No signing configuration supplied; no APK claimed |

Completion cannot be declared from this ledger until every launched website feature has Android implementation and the applicable runtime/live evidence. Deferred/withdrawn features retain their actual product status; missing Android work is not redefined as completion.
