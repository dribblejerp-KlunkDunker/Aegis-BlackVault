# AEGIS_OS // BLACKVAULT + SOVEREIGN-AEGIS — MULTI-AGENT HANDOFF PACKET

- **Protocol ID:** `AEGIS_BLACKVAULT_SOVEREIGN_AGENT_HANDOFF_V1`
- **Application ID:** `com.aistudio.aegisblackvault.kxmpzq` (namespace `com.example`, version 3.6)
- **Target Platforms:** Android Phone (Compact Portrait) + Google Chromebook / ChromeOS (Expanded Dual-Pane Freeform Workstation)
- **Upstream Cognitive Defense Integration:** `https://github.com/dribblejerp-KlunkDunker/Sovereign-Aegis`

---

## 1. System Architecture & Module Inventory

| Layer | Source Path | Responsibilities |
| :--- | :--- | :--- |
| **Normalized Room Schema (v4)** | `app/src/main/java/com/example/model/OsintModels.kt`<br>`app/src/main/java/com/example/data/AegisDatabase.kt` | 4 relational SQLite tables with foreign keys, indices, and `@Transaction` `SubjectIntelligenceDossierRelation`:<br>1. `subject_profiles`<br>2. `raw_social_media_entities`<br>3. `linguistic_personality_profiles`<br>4. `mind_map_hierarchy_edges` (3-tier: Depth 0 Root -> Depth 1 Category Hubs -> Depth 2 Leaf Artifacts/Traits) |
| **Forensic Math, Psychometrics & Sovereign VERDAD** | `app/src/main/java/com/example/engine/ForensicMathEngine.kt` | - Calibrated 50-word Burrows' Delta ($\Delta = \frac{1}{50}\sum |z_i(D) - z_i(T)|$) & MATTR lexical diversity<br>- Pennebaker LIWC 4D Composite (`ATI`, `Clout`, `Authenticity`, `Emotional Tone`) + Big Five `OCEAN` + `Dark Triad`<br>- Behavioral Playbook (*What Makes Them Tick*, *How to Talk to Them*, *Persuasion & Leverage Hooks*)<br>- **Sovereign-Aegis `verdad-service.js` Port**: 6 Emotional Bait Lexicons (`Outrage`, `Fear`, `Urgency`, `Tribalism`, `Conspiracy`, `Fatalism`), Certainty Inflation / Hedging / Passive Attribution markers, Rhetorical Fallacy Regexes, and deterministic `ALLOW (<40) / CAUTION (40..69) / BLOCK (>=70)` gate<br>- **DISARM Red/Blue Framework (`disarm.json` / `disarm_blue.json`)** & **Situational Watchpoints (`profile.js`)**<br>- **AEGIS <-> Reema `KeyBinding` (`DESIGN-aegis-reema-seam.md`)**: currently a PLACEHOLDER. Values are SHA-256 digests shaped like the target format; nothing is generated or signed. Real signing is roadmap Phase 5 |
| **OkHttp Multi-Vector Scraper** | `app/src/main/java/com/example/network/OsintScraperService.kt` | Real `OkHttpClient` probing GitHub REST API (`/users/{h}`, `/repos`, `/events/public`; optional token), Hugging Face Hub API (`/api/models?author=`), and explicitly given URLs (JSON-LD, `__NEXT_DATA__`, OpenGraph). Honest User-Agent, 2 MB response cap, no fabricated fallbacks |
| **Multi-AI & Data Portability Hub** | `app/src/main/java/com/example/network/MultiAiAndDataPortabilityService.kt` | - **Google Gemini**: Dual-path via Firebase AI Logic (`Firebase.ai`) AND Direct Gemini REST API (`GEMINI_API_KEY`)<br>- **Anthropic Claude**: Live `https://api.anthropic.com/v1/messages` with XML-structured dossier context (`ANTHROPIC_API_KEY`)<br>- **Perplexity Sonar**: Live `https://api.perplexity.ai/chat/completions` with web citations (`PERPLEXITY_API_KEY`)<br>- **Full Model Selector**: Preset model chips + custom model ID input per provider + In-App Key Vault<br>- **Round-Trip Import/Export**: Agent Handoff JSON, PKZIP + STIX 2.1, Room Relational JSON, Timestamped CSV, Claude XML, Gemini Markdown, Perplexity Query |
| **Interactive Compose Canvas Mind Map** | `app/src/main/java/com/example/ui/components/MindMapCanvas.kt` (composable `InteractiveMindMapSurface`) | 3D/UMAP force-directed & 3-tier hierarchical brain graph with Bézier edges, touch pinch-zoom, Chromebook trackpad scroll-wheel zoom, and interactive bottom-docked Psychometric Node Inspector |
| **Adaptive Phone + Chromebook UI** | `app/src/main/java/com/example/MainActivity.kt`<br>`app/src/main/java/com/example/ui/screens/*.kt` | Responsive `BoxWithConstraints` switching between Compact Phone layout (Bottom Navigation Bar) and Expanded Chromebook layout (`NavigationRail` + Resizable Dual-Pane Split Workstation + Hardware Keyboard Shortcuts `Alt+1..5`, `Ctrl+B`, `Ctrl+E`) |

---

## 2. Load-Bearing Architectural Invariants for Downstream Agents

1. **VERDAD Pre-Share / Inbound Gate Invariant (`Sovereign-Aegis/js/verdad-service.js`)**:
   - `recommendationGate` (`ALLOW`, `CAUTION`, `BLOCK`) **MUST** derive strictly from deterministic `manipulationRisk` (`THRESHOLDS: blockRisk = 70, cautionRisk = 40`), never from an LLM `veracityScore`. An adversarial prompt injection cannot lower `manipulationRisk` to unlock a `BLOCK`.
2. **Personal Defense Profile Invariant (`Sovereign-Aegis/js/profile.js`)**:
   - Never collapse psychometric traits into a single gameable "manipulability index". Always evaluate across discrete Situational Watchpoints using auditable lens thresholds (`LENS_BAND_HIGH = 3.5` / `70%`, `LENS_BAND_LOW = 2.5` / `50%`, `MINI_SALIENCE = 3.5`).
3. **AEGIS ↔ Reema Post-Quantum Seam (`DESIGN-aegis-reema-seam.md`)**:
   - Keep statement/credential signing on WebCrypto ECDSA P-256 (`did:key:z6Mk...`) and session/transport identity on Reema (`libsodium X25519 + ML-DSA-65 + ML-KEM-768`, 44-char `BLAKE2b(signPk || dhPk)` routing address), bridged via a signed `KeyBinding` JSON credential.
4. **No Fabricated Data Invariant (v3.6)**:
   - A failed or empty collection produces an empty profile marked `dataProvenance = NO_DATA` and `corpusWordCount = 0`. Never insert placeholder repos, models, quotes, activity hours or coordinates, and never attribute search results for other accounts to the subject.
5. **Consent Gate Invariant (v3.6)**:
   - `ForensicMathEngine.applyConsentGate` runs inside `AegisRepository.saveProfile` (the single persistence choke point) and on every scraper result. For subjects whose `consentStatus` is `UNVERIFIED`, persuasion/leverage playbooks, exploitable-bias lists, Dark Triad, violence pathway, activity window and inferred location are never stored, shown, exported or sent to an AI provider, and every AI prompt carries `consentPreamble()`. Unlocking (`SELF` / `CONSENTED`) recomputes from stored artifacts and is recorded in `dataProvenance`. `DEMO` is reserved for the two built-in fictional personas.
6. **Data Sufficiency Invariant (v3.6)**:
   - Scores below `DataSufficiency.MIN_WORDS_FOR_TRAITS` (300) are labeled insufficient; Burrows' Delta is withheld below `MIN_WORDS_FOR_STYLOMETRY` (1,500); activity windows need `MIN_TIMESTAMPS_FOR_CHRONO` (12) real timestamps. Every score is a heuristic estimate, not a validated psychometric instrument.

See `CHANGES.md` for the v3.6 audit fixes and the roadmap doc for Phases 1 to 6.
