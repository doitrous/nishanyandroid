# Reference contracts and client safety

Inspected website commit: `dbe891d416b0247560dd48a9ab363c4749fde98e`.
During the session `main` advanced to `3d26ff80deac92aaaa4d3ccce1aadd48421c80c7`; the GitHub comparison listed only public-page/SEO changes (`publicFrame`, `seo`, `seoArticles` and their tests). No inspected account/notebook contracts changed in that comparison.

iOS branch: `native/full-student-workflows`, commit `5f52dcc104f21fa27a6c179ee916a88166733fa4`. Read `RELEASE.md`, `AppSession.swift`, `Networking/StudentAPI.swift` and repository file inventory. `PARITY_SWEEP.md` is absent from the committed tree. The user's substantial uncommitted local iOS work is unavailable here and remains uninspected and untouched. The committed Rooms directory has a store/view; this does not establish the status of the newer local calling implementation.

## Authentication

Evidence: `server/src/routes/auth.js`, `authRoutes.js`, `auth.js`, `sessionStore.js`, `routes/me.js` and `src/lib/auth/client.ts` in the website.

| Operation | Existing contract | Android use |
|---|---|---|
| Sign in | POST `/api/auth/login`, `{email,password}`; response `{ok,mfaPending}` and HttpOnly `nsid` cookie | Native form and isolated OkHttp cookie jar |
| Restore | GET `/api/session`, `{user:null}` or authenticated user | Identity checked before creating an owner-scoped repository |
| Account | GET `/api/me`, `{user,profile,subscription,entitlement,freeAllowance}` | Display profile and server entitlement; null profile is not a network error |
| MFA | GET factors; POST `/api/auth/mfa/challenge` `{factorId}`; POST verify `{factorId,challengeId,code}` | Existing verified TOTP factors; enrollment not yet implemented |
| Password recovery | POST `/api/auth/recover` `{email}` | Native request only; callback/password-reset completion missing |
| Global logout | POST `/api/auth/logout`, 204 | Confirmation explicitly warns that website/iOS sessions also end |
| Local removal | No server call | Deactivate jar, cancel its client, erase local cookie, clear visible account data; server session is not revoked |
| Enrolment | PUT `/api/me/enrolment` | Inspected, not yet implemented; server locks and phoneConflict must be honored |
| Profile | PUT `/api/me/profile` | Inspected, not yet implemented; username can be locked |

Cookie-authenticated writes need an exact allowlisted `Origin` (default includes `https://nishany.com`). Android sends that origin on **every non-GET/HEAD request**, including bodyless operations. No CORS bypass, token scraping or separate authentication service is added. Existing native bearer support is not required for this cookie-session slice. Server-side token refresh remains server-owned.

Cookies are encrypted using Android Keystore AES-GCM, stored under `noBackupFilesDir`, and only sent to the exact HTTPS host. Redirects and OkHttp connection retries are disabled. Retired cookie jars cannot write late `Set-Cookie` responses into the next login. The manifest disables cleartext and app backup. Do not log request/response bodies or credentials.

## Notebook

Evidence: `server/src/routes/notebook.js`, `server/src/notebookNotes.js`, `server/shared/notebookModel.js`, `src/lib/notebook/notebookClient.ts`, `src/data/notebook.ts`.

| Operation | Contract |
|---|---|
| List | GET `/api/notebook/notes` → `{notes,fresh}`; summaries only |
| Read | GET `/api/notebook/notes/:id` → `{note,version,seq,updatedAt}`; 404 if missing/deleted |
| Save | PUT same path → `{note,baseVersion,seq?,restoredFrom?}`; success includes version; 409 includes `current` |
| History | GET `/:id/versions`; GET `/:id/versions/:versionId` |
| Restore | Stage historical note with the current baseVersion and restoredFrom; explicit save creates a history entry |
| Legacy | `nishany.notebook.notes` is a server import source; no Android write path |

Use immutable `JsonObject` patches, retaining unknown fields, attachments, resource references, rich editor JSON and drawing data. Rich notes are body-read-only in this checkpoint; metadata edits do not flatten them. Full rich authoring/rendering is still missing. Plain-body editing retains legacy markdown source. Search is local over loaded summary titles/excerpts/tags, not full-body server search.

The server selects the existing note `FOR UPDATE` and compares `baseVersion` in a transaction. Unlike generic user-state, this is a real server concurrency check for an existing live note. A new-row race may surface as a server error and must be reconciled. A **delete-versus-edit race remains**: the current server allows a write to a tombstoned note regardless of baseVersion, so the Android preflight GET cannot prevent a deletion occurring after that check from being resurrected. Conditional deletion is also absent. Android exposes no remote delete in this checkpoint. Fixing those server semantics requires separate approval; do not describe preflight as a complete solution.

Before any PUT, the encrypted journal stores `SENDING`. A timeout, lost response, malformed success or uncertain error keeps the entry. Recovery only reads the server; exact payload equality acknowledges delivery without another PUT. A mismatch remains a conflict and is never automatically resent. The student may explicitly keep a separate draft or discard only their local draft; discarding does not undo a possibly delivered write. Original conflicts remain until explicit discard.

Typing is serialized through an asynchronous disk queue. The UI distinguishes saving locally, durable local draft, and server-confirmed save. A process kill **before the local-save indicator completes can lose those latest keystrokes**. Uninstalling/clearing app data removes drafts and keys. Account switching hides other owners' drafts. Persistence failures are surfaced, not treated as successful local saves. Pending storage is a per-owner encrypted document; large notebooks need a future per-note storage/index migration and quota testing.

## Shared account data and permissions

- GET `/api/me/university` is the authoritative projection. It returns enrollment, terms, modules, assessments, subjects and published schedules. Android currently renders terms/modules and schedule labels only; full subjects/assessments/navigation is pending.
- `/api/user-state/:key` is private account-owned state; `/api/state/:key` is published/admin-authored state with server gates. Do not exchange them.
- User-state PUT uses a transaction but accepts no client expected-version/ETag. It can overwrite a concurrent writer. No generic state writer has been added to Android yet.
- Minigame records remain `nishany.minigames.records.v1`; Android gameplay/records are missing.
- `src/lib/entitlement.ts` grants Notebook for active/trialing access and permitted `studyTools` inclusion (`full`/`limited`); legacy missing includes-map remains allowed. Android mirrors that UI gate and still respects server 402/403.
- Current payment routes integrate Kashier; no Android purchase-token verification contract was found in the inspected payment routes. Do not award entitlement locally or pretend a refresh is Google Play restoration.

## Dependencies

Pinned AGP 8.13.2, Gradle 8.13, JDK 17, Kotlin/Compose compiler plugin 2.2.21, Compose BOM 2025.10.00. The Gradle wrapper scripts/JAR were retrieved from the official `gradle/gradle` `v8.13.0` tag. JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

Official compatibility references: https://developer.android.com/build/releases/agp-8-13-0-release-notes and https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler . Dependency resolution and compilation remain unverified. Add a verified distribution checksum before release; never invent one.
