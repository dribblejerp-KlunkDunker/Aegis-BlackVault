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

## Phase 2d — projects as the isolation boundary (schema v6)
- **Every subject belongs to exactly one project, and so do its artifacts, linguistic profile,
  mind-map edges and knowledge triplets.** The idea comes from GHOST; no code was copied.
  - Every list in the app shows only the active project.
  - Erasing a subject only removes triplets in its own project, even when another project holds the
    same handle.
- **A subject never changes project.** Saving a profile whose id belongs to another project is
  refused (`CrossProjectWriteException`).
- **Cross-project links are opt-in.** Mind maps link to subjects in another project only when both
  projects allow it.
- **Retention is per project.** The old global setting moves into the default project once.
- **Erase project** removes everything in it and nothing else. The default project cannot be erased.
- **Audit entries** name projects only by a pseudonymous reference, never by name.
- **UI:**
  - an always-visible project bar with a switcher and "+ New";
  - a project card in Governance with cross-link and erase controls;
  - an empty-project screen with a collect field, instead of the endless spinner shown when there
    are no subjects.
- **Migration 5 → 6** moves all existing data into the default project.
- **Tests:**
  - `ProjectIsolationTest` covers scoping, same handle in two projects, refusal to move, the
    two-sided opt-in, per-project retention, and erasing a project without leaking its name to the
    audit log;
  - migration tests extended to v6;
  - screenshots of the project bar, dialog and empty state.

## Phase 1b — code health
- **`ForensicMathEngine.kt` (1,667 lines) is split by domain** into `StylometryEngine`,
  `ChronoGeoEngine`, `ConsentGate`, `PlaybookEngine`, `MindMapEngine`, `ExportEngine`,
  `VerdadEngine`, `WalledGardenCatalog` and `ForensicCommon`, all in `com.example.engine`.
  - The code was moved, not rewritten.
  - Every call site now names the object that owns the function, for example
    `ConsentGate.applyConsentGate`.
- **ktlint 1.8.0.** The whole codebase is formatted to the `android_studio` style in
  `.editorconfig`. CI fails on any new formatting issue.
- **detekt 1.23.8.**
  - The configuration is in `config/detekt/detekt.yml`: Compose-aware, with MagicNumber off.
  - Existing findings live in `config/detekt/baseline.xml`, mostly long lines and long or complex
    methods in the large screens. New code must pass.
- **New CI job `kotlin-lint`.** It downloads pinned, SHA-256-verified tool jars from Maven
  Central and uploads HTML and SARIF reports.
- **Governance rows wrap long values** instead of running into the label.

## Phase 3a — collection through declared connectors
- **Connector contract** (`com.example.connectors`): each source declares its kind, whether it
  needs opt-in, a per-minute request budget, its endpoints and a terms note. Every artifact is
  stamped with its connector's run id. The pattern comes from OpenOSINT.
  - **Passive** connectors read a platform's public API and always run.
  - **Active** connectors contact the target's own site. They stay off until you switch them on in
    the Scraper tab; the switch is remembered and written to the audit log.
- **The connectors:**
  - `GithubConnector` and `HuggingFaceConnector` (passive) are the previous collection code,
    restructured.
  - `DirectUrlConnector` (active):
    - reads `robots.txt` first, following RFC 9309 (longest match wins, Allow wins ties, `*` and
      `$` supported, unreachable means disallow);
    - refuses platforms whose terms prohibit automated collection (TikTok, Instagram, Facebook,
      Threads, X, LinkedIn, Snapchat, OnlyFans, YouTube, Reddit, Discord), pointing to their
      own-account data export instead.
- **Run log.** The Scraper tab lists every connector with its kind, endpoints, budget and the
  outcome of the last run. The audit entry for a collection records each connector's status.
- **Removed:**
  - the walled-garden catalog;
  - the "FORCE DOM MUTATION" button;
  - the scripted four-stage "AI auto-heal", which saved invented "patches" claiming to unlock those
    platforms, and the seeded fake TikTok patch. Existing patch rows stay in the database, and
    exports still list them;
  - the quick-select presets that profiled named real people.
- **Tests:** `ConnectorTest` (11 cases) covers:
  - the contract for every connector;
  - robots rules, group selection and fetch failures;
  - rate limits;
  - no requests without opt-in;
  - the robots-then-page order with custody;
  - robots blocks, platform refusals with zero requests, and passive-only runs for a handle.

## Phase 3b — feeds, manual import custody, honest timestamps
- **`FeedConnector`** (active, opt-in) reads one RSS 2.0 or Atom feed.
  - It turns each entry into a statement with a UTC date. HTML in feed bodies is reduced to plain
    text.
  - It reads `robots.txt` first and refuses the same platforms as the web-page connector, through
    a shared `WebFetchPolicy`.
  - Feed URLs (`.rss`, `.atom`, `.xml`, `/feed`, `/rss`, `/atom`) select it automatically, and a
    new **RSS / ATOM** mode forces it.
- **`ManualImportConnector`** (new kind **Local**, contacts nothing): every imported statement
  records its source (file name or "pasted text"), when it was received, the SHA-256 of the exact
  bytes imported, and a run id. The audit entry includes the run id and hash.
- **No more invented times or hours.**
  - **Undated statements:** a statement without a real timestamp now has no activity hour.
    Previously it was given 14:00 and counted in the timezone and schedule estimates.
  - **Dates without times:** these stay dates. Previously they became 12:00 UTC.
  - **Timezone offsets:** a timestamp with an offset (`+02:00`) is converted to UTC. Previously the
    local hour was mislabelled as UTC.
  - **Imports and pages without a date:** eight import paths and the web-page connector no longer
    stamp missing dates with the import or fetch time. Those statements stay undated, and the
    fetch time lives in custody.
  - **Re-scoring after import:** it no longer assumes 400 followers or counts unknown hours.
- **No lookups of a handle guessed from another site's URL.** GitHub and Hugging Face now run only
  for a bare handle or a URL on their own domain. Previously, a URL for any other site in "ALL" mode
  made them look up a "handle" taken from that URL's path, which could attach an unrelated person's
  account.
- **Mode chips renamed** to fit five: ALL, GITHUB, HUGGING FACE, WEB PAGE, RSS / ATOM.
- **Tests:** `FeedAndImportTest` (9 cases) covers:
  - UTC conversion and date-only values;
  - undated artifacts having no hour;
  - RSS and Atom parsing;
  - feed URL routing;
  - opt-in and robots order for feeds;
  - refusal of prohibited platforms;
  - no handle lookups for another site's URL;
  - custody on imports.

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
