# Changes — v3.6 (audit fixes, Phase 0)

Branch: `audit-fixes`. Roadmap: "Aegis BlackVault — Integration Roadmap" (Phases 0 to 6).

> **Not yet compiled.** These edits were made without running Gradle (the review sandbox blocks
> executing the project's build scripts). Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`
> locally first; see "Verify locally" at the end.

## Data integrity
- **No fabricated data.** Removed the scraper fallbacks that, on any failure, invented a repo
  (340 stars), a Hugging Face model, two quotes attributed to the subject, and activity hours
  derived from a hash of the handle. A failed collection now yields an empty profile marked
  `NO_DATA`.
- **No misattribution.**
  - Removed the keyword-search fallbacks that credited other people's GitHub repos and Hugging Face
    models to the subject.
  - "Harvest GitHub + HF upgrades" no longer merges strangers' repo descriptions into the subject's
    corpus or re-scores the subject from them. Results are linked only as `REFERENCE_TOOL` graph nodes.
- **No default target.** Blank input no longer falls back to profiling `torvalds`. The scraper field
  starts empty.
- **No invented activity or location.**
  - Activity windows need at least 12 real timestamps.
  - Fetch time is never counted as the author's activity.
  - No latitude is invented from a UTC offset.
- **Honest scores.**
  - Removed artificial floors (psychopathy could never fall below 12, "pathway to violence" never
    below 15).
  - Burrows' Delta is withheld under 1,500 words.
  - Every score carries a data-quality label ("heuristic estimate, not a validated instrument").
- **Python server `/api/v1/scrape`** no longer generates a fixed paragraph, fake hours and Dark Triad
  scores for any URL. It returns 501 until a real connector backend exists.

## Consent gate (user choice: keep restricted outputs, gated by consent)
- New `ConsentStatus` (`UNVERIFIED`, `SELF`, `CONSENTED`, `DEMO`), plus `corpusWordCount` and
  `dataProvenance` on every subject. Room schema v3 → v4 with a non-destructive migration.
- `ForensicMathEngine.applyConsentGate` runs in `AegisRepository.saveProfile` and on scraper results.
  For unverified subjects, these are never stored, shown, exported or sent to an AI provider:
  - leverage/persuasion playbooks;
  - exploitable biases;
  - Dark Triad;
  - violence pathway;
  - activity window;
  - inferred location.
- Psychometric screen: consent selector, provenance and data-quality bar, and withheld notices.
  Consent changes are timestamped in the provenance trail and recompute from stored artifacts.
- AI prompts:
  - Every prompt bundle and system prompt carries a consent policy line.
  - Default directives no longer ask for leverage on unverified subjects.

## Security
- **Key vault:** API keys are stored with AES-256-GCM using an Android Keystore key (new
  `security/SecureKeyVault.kt`). Legacy plaintext keys are migrated and deleted. Key fields are
  masked on screen.
- **Backups:** `allowBackup="false"`, and the database and vaults are excluded from cloud backup and
  device transfer.
- **Gemini key** moves from the URL query string to the `x-goog-api-key` header.
- **Clipboard:** copies are flagged sensitive. Sharing no longer silently copies the whole dossier.
- **Network:**
  - honest User-Agent (browser spoofing and rotation removed);
  - 2 MB response cap;
  - real error reporting;
  - optional encrypted GitHub token (60 → 5,000 requests/hr);
  - direct page fetches only for explicitly given URLs.
- **Python server:**
  - `X-API-Key` auth on every endpoint except health, fails closed when no key is configured;
  - CORS allow-list (empty by default);
  - Cypher through `GRAPH.RO_QUERY`;
  - websocket key check;
  - binds 127.0.0.1;
  - "heal" no longer reports a made-up 98.4% confidence.
- `.env.example` warns that BuildConfig keys are extractable from the APK.

## Fixes and updates
- **Model IDs:**
  - Claude: Sonnet/Opus/Haiku 5.5 (3.x IDs are retired).
  - Gemini: 3.x presets (1.5, 2.0 and 2.5 Pro/Flash are shut down or shutting down).
  - Saved retired choices reset to the default.
- **ZIP export** is actually shared through a FileProvider (it was written to cache and never shared).
- **KeyBinding** relabeled as a placeholder in code, JSON (`"simulated": true`) and UI. Nothing was
  ever signed.
- **Build:**
  - OkHttp 4.10 → 4.12 (pulls an Okio without CVE-2023-3635);
  - Gradle wrapper script and jar committed;
  - release R8 minify on with keep rules;
  - Room schema export to `app/schemas`;
  - version 3.6 (code 2).
- **Docs:** handoff packet fixed (app ID, component path) and gained the no-fabrication, consent and
  data-sufficiency invariants.

## Phase 2b — database upgrade safety
- **Room 2.8.5 everywhere.** Runtime, KTX and compiler now share one version ref (Dependabot had
  started splitting them; PR #6 is superseded). `room-testing` added for migration tests.
- **Schemas committed** in `app/schemas/`: `5.json` is Room's export; `3.json` and `4.json` are the
  earlier shapes, derived from it and checked against the original v3 entity definitions.
- **Migration tests** (`MigrationTest`): a database created at v3 with a subject and an artifact is
  upgraded to v4 and to v5, validated column by column against the schema, and then opened with the
  real `AegisDatabase`. It checks:
  - rows survive and get the documented defaults (`UNVERIFIED`, `UNKNOWN (pre-v4)`);
  - legacy artifacts get empty custody fields rather than invented ones;
  - the audit-log triggers are installed by the migration;
  - erasure and the audit chain work on the upgraded file.

## Phase 2c — encryption at rest
- **The dossier database is encrypted with SQLCipher 4** (`net.zetetic:sqlcipher-android` 4.20.0,
  AES-256).
- **Device-bound key.** The 256-bit key is random. It is stored only wrapped by an Android Keystore
  AES-GCM key (`DatabaseKeyManager`) and is excluded from backup.
- **Existing installs are converted once** (`DatabaseEncryption`). The app writes an encrypted copy
  with `sqlcipher_export`, checks that the copy opens with the key and holds the same row counts, and
  only then swaps it in. A conversion interrupted by a crash or kill is finished or rolled back on
  the next launch.
  - If conversion fails, the app keeps working unencrypted, retries on the next launch, and the
    Governance tab says so.
  - A database whose key is gone is moved aside, not deleted.
- **Governance tab** shows the storage status.
- **Emulator CI job** (`instrumented-tests`, API 34) checks:
  - the file on disk is not readable as SQLite and holds no plaintext names;
  - a wrong key fails;
  - a plaintext install converts with dossiers, audit chain and triggers intact;
  - a v3 plaintext install encrypts and then migrates to v5;
  - an interrupted conversion recovers.
- **Limits.** Deleting the old plaintext file does not guarantee flash blocks are wiped. Code running
  inside the unlocked app can still read the data. Robolectric tests run unencrypted because the
  native library cannot load on the JVM.

## Not changed yet (see roadmap)
- Compose BOM 2024.09 and lifecycle 2.8.7 upgrades wait for CI (Phase 1).
- `firebase-appcheck-debug` is still an `implementation` dependency; moving it needs a debug-only
  source set for `AppCheckHelper` (Phase 1).
- The walled-garden catalog and its private-API snippets remain. The roadmap retires them in Phase 3.

## Verify locally
1. `./gradlew :app:assembleDebug :app:testDebugUnitTest`
2. Install over an existing v3.5 install and confirm dossiers survive the v3 → v4 migration.
3. Scrape a nonexistent handle and confirm the profile says NO DATA with no repos or quotes.
4. Set a subject to Self-analysis and confirm the playbook appears; set it back to Unverified and
   confirm it is withheld again.
5. Export as PKZIP and confirm a .zip arrives in the share target.
