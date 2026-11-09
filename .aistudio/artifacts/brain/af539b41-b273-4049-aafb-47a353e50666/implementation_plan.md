# Aegis Blackvault — OkHttp OSINT Scraper Service & Reactive ViewModel-to-Room Pipeline

An end-to-end network ingestion and forensic processing pipeline that uses `OkHttpClient` to harvest live telemetry from arbitrary target URLs, social media endpoints, GitHub REST APIs, and Hugging Face Hub APIs, parses timestamped discourse and hydration state, computes scientific linguistic personality profiles (Pennebaker LIWC, 50-word Burrows' Delta, Big Five OCEAN, Dark Triad, and Dyadic LSM), and atomically persists the normalized entity graph into the on-device Room database via `AegisViewModel`.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Please review the following architectural choices for the `OkHttp` Scraper Service and `ViewModel` → `Room` persistence pipeline before execution:

- **Confirmed Decision 1 — Multi-Target OkHttp Ingestion Strategy**: The scraper service automatically inspects the target input (raw URL, `@handle`, or platform selector) and routes requests through specialized `OkHttp` pipelines:
  1. **GitHub REST API v3** (`/users/{handle}`, `/users/{handle}/events/public`, `/users/{handle}/repos`) with `ISO-8601` commit/event timestamp extraction.
  2. **Hugging Face Hub API** (`/api/models?author={handle}`, `/api/datasets?author={handle}`, `/api/spaces?author={handle}`) with `lastModified` timestamp and tag extraction.
  3. **Direct Target URL / Walled-Garden HTML & JSON-LD / Hydration Scraper**: Fetches arbitrary HTTP/HTTPS URLs with custom User-Agent rotation, extracts HTTP response headers (`Date`, `Last-Modified`, `Server`, `ETag`), parses embedded JSON hydration blocks (`__NEXT_DATA__`, `application/ld+json`, OpenGraph `<meta>` tags, `<article>` / `<time datetime="...">` tags), and strips DOM chrome into clean, timestamped discourse segments.
- **Confirmed Decision 2 — Atomic Room Database Persistence (`@Transaction`)**: All scraped artifacts for a subject are processed off the main thread (`Dispatchers.IO`) inside `AegisViewModel`, transformed into normalized Room entities (`SubjectProfileEntity`, `RawSocialMediaEntity`, `LinguisticPersonalityProfileEntity`, `MindMapHierarchyEntity`, and `KnowledgeTripletEntity`), and committed in a single atomic `@Transaction` so the UI never observes partial or orphaned records.
- **Open Choice — Unreachable / Rate-Limited Target Fallback (Recommended Default: Live HTTP Telemetry + Deterministic Forensic Diagnostic Synthesis)**: When a target URL returns HTTP 403/429 (e.g., anti-bot WAF challenge or GitHub unauthenticated rate limit), the scraper records the exact HTTP status code, latency, and response headers, triggers the AST Self-Healing bridge status, and synthesizes a clearly labeled forensic diagnostic entry so the operator can test the full downstream LIWC + Mind Map pipeline without dead-ending.

---

## 1. Overview & Core Concept

- **What It Does**:
  - Provides a real-time `OkHttp` network scraper capable of ingesting both structured API endpoints (GitHub, Hugging Face) and arbitrary web/social URLs entered by the analyst.
  - Extracts granular temporal metadata (exact `epochMillis` when each statement, commit, comment, or article was posted), raw content, platform source URLs, and structural tags.
  - Feeds the harvested corpus directly into the `AegisViewModel`, which invokes the deterministic `ForensicMathEngine` (Pennebaker LIWC dictionary analyzer, 50-word calibrated Burrows' Delta stylometry, Big Five OCEAN & Dark Triad regressors, and Dyadic LSM calculator) and writes the resulting relational dossier into Room.
- **Target Audience / Persona**:
  - OSINT investigators, cyber-threat intelligence analysts, and behavioral researchers operating from a mobile tactical command deck.
- **Key Value**:
  - Eliminates manual copy-pasting between browser tabs and analysis tools by unifying live HTTP scraping, timestamped discourse extraction, scientific psycholinguistic profiling, and persistent local SQLite/Room graph storage into a single reactive flow.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Target URL / Handle Acquisition (`SCRAPER` & `CMD_DECK` Tabs)**:
     - The analyst enters either a full URL (e.g., `https://github.com/torvalds`, `https://news.ycombinator.com/...`, or any public profile/article URL) or a target handle/alias, selects the target vector (`ALL`, `GITHUB`, `HUGGINGFACE`, `DIRECT_URL`), and taps **`SCRAPE`**.
  2. **Live OkHttp Telemetry & Progress Feedback**:
     - The ViewModel immediately transitions into an active scraping state, streaming live status banners (`[OKHTTP] Handshake ->GET https://...`, HTTP status code, payload byte size, latency in `ms`, and extracted artifact count).
  3. **Automated Forensic Synthesis & Atomic Room Commit**:
     - As soon as `OkHttp` returns the response body, the ViewModel parses timestamped posts, runs the Pennebaker LIWC and 50-word Burrows' Delta algorithms, builds the parent-child Mind Map hierarchy (`SUBJECT_ROOT` → `CATEGORY_HUB` → `ARTIFACT_LEAF`), and inserts all records into Room.
  4. **Reactive Multi-Screen Inspection**:
     - Because the ViewModel observes Room `Flow` queries (`observeCompleteDossier` and `observeAllSubjects`), the **3D/2D Mind Map**, **Timestamped Discourse Feed**, **Psychometric Matrix**, and **Scraper Bridge Matrix** update instantaneously without manual refresh.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: High-Contrast Tactical SIGINT / Obsidian CRT Command Deck (`AEGIS_OS // BLACKVAULT`).
  - *Color Palette & Mood*:
    - `--background`: `#050508` (Obsidian Void)
    - `--surface`: `#0A0B10` (Tactical Panel Surface with `#1A1D26` 1dp hairlines)
    - `--primary` / `--accent`: `#00F0FF` (Cyber Cyan for active network telemetry & primary controls)
    - `--secondary`: `#A855F7` (Neural Purple for LIWC & psychometric vectors)
    - `--success`: `#10B981` (Matrix Emerald for HTTP 200 OK & verified Room transactions)
    - `--warning` / `--danger`: `#F59E0B` (Amber) and `#FF2A54` (Crimson Alert for WAF blocks, high Dark Triad risk, or stylometric anomalies)
  - *Typography & Hierarchy*: `Share Tech Mono` for tactical headers and HUD metrics paired with `JetBrains Mono` for timestamped raw social media payloads, HTTP headers, and numerical psychometrics.
- **Interactive Feedback & Motion**:
  - Real-time pulsing telemetry badge during active `OkHttp` calls, live latency/byte-count readouts, interactive node inspection in the Mind Map canvas, and instant filter chips for inspecting raw Room database entities by platform or category.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Dual-Mode Scraper (Structured REST APIs + Generic HTML/DOM Hydration Parser)**
  - *Chosen Approach*: Combine dedicated JSON endpoints (GitHub & Hugging Face APIs) with a resilient generic URL HTML/JSON-LD/Hydration parser in `OkHttpClient`.
  - *Why*: Pure HTML scraping on modern SPAs often hits empty `<div id="root">` shells, whereas extracting `<script id="__NEXT_DATA__">`, `application/ld+json`, OpenGraph meta tags, and semantic `<time>` / `<article>` / `<p>` blocks yields rich timestamped content from almost any public URL while preserving structured API accuracy for developer platforms.
  - *Alternatives Considered*: Headless WebView scraping (heavy memory footprint and slow startup on mobile) or API-only scraping (cannot scrape arbitrary blogs, forums, or news URLs).
- **Decision 2 — Normalized Relational Room Storage with `@Transaction` Orchestration**
  - *Chosen Approach*: Store scraped intelligence across four linked Room tables (`subject_profiles`, `raw_social_media_entities`, `linguistic_personality_profiles`, `mind_map_hierarchy`) joined via `@Relation` in `SubjectIntelligenceDossierRelation`.
  - *Why*: Allows the UI to independently query timestamp-ordered social media posts, inspect the 1:1 psycholinguistic profile, or traverse the recursive `parentNodeId -> childNodeId` mind-map adjacency list with full foreign-key CASCADE integrity.
  - *Alternatives Considered*: Storing the entire dossier as a single serialized JSON blob in one table (prevents indexed SQL queries on timestamps, platforms, or mind-map hierarchy levels).

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                        JETPACK COMPOSE TACTICAL UI                           │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────┐ ┌──────────────┐ │
│  │  Command Deck    │ │ Psychometric HUD │ │ Scraper HUD  │ │ Insights &   │ │
│  │ (Mind Map + Feed)│ │ (LIWC / OCEAN /  │ │ (URL Input,  │ │ STIX / PKZIP │ │
│  │                  │ │  Tick / Persuade)│ │  HTTP Logs)  │ │ Exporter     │ │
│  └────────┬─────────┘ └────────┬─────────┘ └──────┬───────┘ └──────┬───────┘ │
└───────────┼────────────────────┼──────────────────┼────────────────┼─────────┘
            │                    │                  │                │
            ▼                    ▼                  ▼                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     AEGIS VIEWMODEL (StateFlow + Dispatchers.IO)             │
│  • Validates target URL / handle & dispatches async OkHttp scrape jobs       │
│  • Pipes raw scraped items into ForensicMathEngine for linguistic analysis   │
│  • Maps domain models to Room Entities & commits atomic @Transaction         │
│  • Exposes reactive Room Flows (Dossier, Raw Entities, Mind Map Hierarchy)   │
└───────────────┬───────────────────────────────────┬──────────────────────────┘
                │                                   │
                ▼                                   ▼
┌───────────────────────────────────────┐ ┌────────────────────────────────────┐
│     OKHTTP SCRAPER SERVICE            │ │      FORENSIC MATH ENGINE          │
│  • OkHttpClient (Timeouts, Redirects, │ │  • Pennebaker LIWC Psycholinguistic│
│    User-Agent & Header Interceptor)   │ │    Analyzer (I-words, Clout, Tone) │
│  • GitHub REST API v3 Harvester       │ │  • 50-Word Calibrated Burrows'     │
│  • Hugging Face Hub API Harvester     │ │    Delta Z-Score Stylometry        │
│  • Generic URL HTML / JSON-LD /       │ │  • Big Five OCEAN & Dark Triad     │
│    __NEXT_DATA__ / Meta Extractor     │ │  • Mind Map Hierarchy Generator    │
│  • ISO-8601 / RFC-2822 / DOM <time>   │ │    (Root -> Category -> Leaf)      │
│    Timestamp Normalizer (epochMillis) │ │                                    │
└───────────────────────────────────────┘ └─────────────────┬──────────────────┘
                                                            │
                                                            ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     ROOM DATABASE (AegisDatabase v3 / SQLite)                │
│  ┌─────────────────────────┐       ┌──────────────────────────────────────┐  │
│  │ subject_profiles        │1─────*│ raw_social_media_entities            │  │
│  │ (subjectId PK, alias..) │       │ (entityId PK, subjectId FK,          │  │
│  └────────────┬────────────┘       │  platform, rawText, postedAtEpochMs, │  │
│               │                    │  sourceUrl, metadataJson)            │  │
│               │1                   └──────────────────────────────────────┘  │
│               │1                   ┌──────────────────────────────────────┐  │
│  ┌────────────▼────────────┐       │ mind_map_hierarchy                   │  │
│  │ linguistic_personality_ │       │ (hierarchyId PK, subjectId FK,       │  │
│  │ profiles (subjectId PK) │       │  parentNodeId, childNodeId,          │  │
│  │ (OCEAN, Dark Triad,     │       │  relationshipType, depthLevel,       │  │
│  │  LIWC, Tick, Persuade)  │       │  3D coords, weight)                  │  │
│  └─────────────────────────┘       └──────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

1. **Scraper Network Telemetry Models**:
   - **`ScraperRequestConfig`**: Encapsulates the target query (`handleOrUrl`), explicit target URL list, platform mode (`ALL`, `GITHUB`, `HUGGINGFACE`, `DIRECT_URL`), and custom request headers.
   - **`HttpEndpointProbe`**: Captures live `OkHttp` diagnostics per request: target URL, HTTP status code (`200`, `403`, `404`, etc.), latency in milliseconds, payload byte count, extracted item count, and server/timestamp headers.
   - **`ScraperBatchResult`**: Aggregates all harvested `ScrapedPost` artifacts, discovered alias/bio metadata, per-endpoint `HttpEndpointProbe` logs, and total execution metrics.
2. **Normalized Room Entities**:
   - **`SubjectProfileEntity` (`subject_profiles`)**: Core identity record (`subjectId`, `handle`, `displayName`, `primaryDomain`, `opsecScore`, `burrowsDeltaScore`, `authoredVerification`, `dyadicLsmScore`, `peakActivityHourUtc`, `inferredTimezone`).
   - **`RawSocialMediaEntity` (`raw_social_media_entities`)**: Timestamped raw intelligence table indexed on `(subjectId, postedAtEpochMillis)` and `platform`, storing `rawContent`, `postedAtIso`, `sourceUrl`, `engagementCount`, `sentimentValence`, and `extractedTagsCsv`.
   - **`LinguisticPersonalityProfileEntity` (`linguistic_personality_profiles`)**: 1:1 foreign-keyed table storing Big Five OCEAN scores, Dark Triad metrics, Pennebaker LIWC percentages (`iWordPct`, `weWordPct`, `analyticThinkingScore`, `cloutScore`, `authenticityScore`, `emotionalToneScore`, `cognitiveProcessPct`, `socialWordsPct`, `wordsPerSentence`), and structured behavioral intelligence (`coreMotivatorsJson`, `communicationPlaybookJson`, `influenceVectorsJson`, `cognitiveBiasesJson`, `psychologicalTriggersJson`).
   - **`MindMapHierarchyEntity` (`mind_map_hierarchy`)**: Adjacency-list hierarchy table storing `parentNodeId`, `childNodeId`, `nodeCategory`, `relationshipType`, `depthLevel`, `weight`, and 3D spatial coordinates (`coordX`, `coordY`, `coordZ`, `colorHex`).

### Interactive Component & State Mapping

- **Direct URL & Multi-Source Input Bar (`CommandDeckScreen` & `ScraperBridgeScreen`)**:
  - Accepts both `@username` handles and full `https://...` URLs.
  - Provides quick-select target presets and platform mode toggles (`ALL`, `GITHUB`, `HUGGINGFACE`, `DIRECT_URL`) so the operator can test live `OkHttp` scraping with a single tap or custom URL entry.
- **`AegisViewModel.scrapeAndProfileTarget(query, mode)`**:
  - Validates input → sets `isScraping = true` → calls `OsintScraperService` on `Dispatchers.IO` → records `HttpEndpointProbe` telemetry into `UiState` → executes `ForensicMathEngine.compileDossier(...)` → invokes `AegisRepository.saveDossier(...)` which executes `AegisDao.persistCompleteIntelligenceDossier(...)` inside a Room `@Transaction` → emits updated `SubjectIntelligenceDossierRelation` via `StateFlow` to all screens.
- **Live Room Database Inspector & HTTP Telemetry Log (`ScraperBridgeScreen`)**:
  - Displays the real-time `HttpEndpointProbe` log (URL, status badge, latency `ms`, bytes fetched, items parsed) alongside live counts of persisted rows in `raw_social_media_entities`, `linguistic_personality_profiles`, and `mind_map_hierarchy`.
