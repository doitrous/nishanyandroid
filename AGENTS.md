# Android development boundaries

- Work only in `doitrous/nishanyandroid`. Website/backend `doitrous/synapse` and iOS `doitrous/nishanyios` are read-only references.
- Do not invoke GitHub Actions, hosted runners or self-hosted GitHub runners. Execute builds/tests on the available development machine.
- Kotlin + Jetpack Compose. Never replace native student workflows with a WebView app shell.
- The website determines current publication status, permissions, scoring and schemas. iOS is additional evidence, not the definition of parity.
- Maintain `RELEASE.md`, `PARITY_SWEEP.md`, source reference pins and actual test evidence. Missing tooling is not a passed build. Test fixtures are not real cross-platform acceptance.
- Preserve unknown JSON fields. Current notebook writes use `/api/notebook/notes/:id`; never write the legacy notebook state document.
- Account data and pending mutations must be owner scoped. Never replay uncertain writes automatically. Do not bypass CSRF, entitlement or server authorization.
- Backend changes need the owner's approval; generic user-state preflight checks do not implement atomic compare-and-swap.
- Keep credentials, personal account data, reference-repository code and content, signing material and SDKs out of this public repository.
- Full parity remains the goal. Every unimplemented requested feature must stay visible in the ledger.
