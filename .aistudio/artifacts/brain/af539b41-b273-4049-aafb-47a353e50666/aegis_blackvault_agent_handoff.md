# AEGIS_OS // BLACKVAULT + SOVEREIGN-AEGIS — MULTI-AGENT HANDOFF PACKET

- **Protocol ID:** `AEGIS_BLACKVAULT_SOVEREIGN_AGENT_HANDOFF_V1`
- **Application ID:** `com.aistudio.aegisblackvault.osint`
- **Target Platforms:** Android Phone (Compact Portrait) + Google Chromebook / ChromeOS (Expanded Dual-Pane Freeform Workstation)
- **Upstream Cognitive Defense Integration:** `https://github.com/dribblejerp-KlunkDunker/Sovereign-Aegis`

---

## 1. System Architecture & Module Inventory

| Layer | Source Path | Responsibilities |
| :--- | :--- | :--- |
| **Normalized Room Schema (v3)** | `app/src/main/java/com/example/model/OsintModels.kt`<br>`app/src/main/java/com/example/data/AegisDatabase.kt` | 4 relational SQLite tables with foreign keys, indices, and `@Transaction` `SubjectIntelligenceDossierRelation`:<br>1. `subject_profiles`<br>2. `raw_social_media_entities`<br>3. `linguistic_personality_profiles`<br>4. `mind_map_hierarchy_edges` (3-tier: Depth 0 Root -> Depth 1 Category Hubs -> Depth 2 Leaf Artifacts/Traits) |
| **Forensic Math, Psychometrics & Sovereign VERDAD** | `app/src/main/java/com/example/engine/ForensicMathEngine.kt` | - Calibrated 50-word Burrows' Delta ($\Delta = \frac{1}{50}\sum |z_i(D) - z_i(T)|$) & MATTR lexical diversity<br>- Pennebaker LIWC 4D Composite (`ATI`, `Clout`, `Authenticity`, `Emotional Tone`) + Big Five `OCEAN` + `Dark Triad`<br>- Behavioral Playbook (*What Makes Them Tick*, *How to Talk to Them*, *Persuasion & Leverage Hooks*)<br>- **Sovereign-Aegis `verdad-service.js` Port**: 6 Emotional Bait Lexicons (`Outrage`, `Fear`, `Urgency`, `Tribalism`, `Conspiracy`, `Fatalism`), Certainty Inflation / Hedging / Passive Attribution markers, Rhetorical Fallacy Regexes, and deterministic `ALLOW (<40) / CAUTION (40..69) / BLOCK (>=70)` gate<br>- **DISARM Red/Blue Framework (`disarm.json` / `disarm_blue.json`)** & **Situational Watchpoints (`profile.js`)**<br>- **AEGIS <-> Reema Post-Quantum `KeyBinding` (`DESIGN-aegis-reema-seam.md`)**: ECDSA P-256 `did:key` bound to `libsodium X25519 + ML-DSA-65 + ML-KEM-768` 44-char BLAKE2b routing address |
| **OkHttp Multi-Vector Scraper** | `app/src/main/java/com/example/network/OsintScraperService.kt` | Real `OkHttpClient` probing GitHub REST API (`/users/{h}`, `/repos`, `/events/public`), Hugging Face Hub API (`/api/models`, `/api/datasets`), and Direct URL DOM/JSON-LD/`__NEXT_DATA__` hydration with self-healing AST selector recovery |
| **Multi-AI & Data Portability Hub** | `app/src/main/java/com/example/network/MultiAiAndDataPortabilityService.kt` | - **Google Gemini**: Dual-path via Firebase AI Logic (`Firebase.ai`) AND Direct Gemini REST API (`GEMINI_API_KEY`)<br>- **Anthropic Claude**: Live `https://api.anthropic.com/v1/messages` with XML-structured dossier context (`ANTHROPIC_API_KEY`)<br>- **Perplexity Sonar**: Live `https://api.perplexity.ai/chat/completions` with web citations (`PERPLEXITY_API_KEY`)<br>- **Full Model Selector**: Preset model chips + custom model ID input per provider + In-App Key Vault<br>- **Round-Trip Import/Export**: Agent Handoff JSON, PKZIP + STIX 2.1, Room Relational JSON, Timestamped CSV, Claude XML, Gemini Markdown, Perplexity Query |
| **Interactive Compose Canvas Mind Map** | `app/src/main/java/com/example/ui/components/InteractiveMindMapSurface.kt` | 3D/UMAP force-directed & 3-tier hierarchical brain graph with Bézier edges, touch pinch-zoom, Chromebook trackpad scroll-wheel zoom, and interactive bottom-docked Psychometric Node Inspector |
| **Adaptive Phone + Chromebook UI** | `app/src/main/java/com/example/MainActivity.kt`<br>`app/src/main/java/com/example/ui/screens/*.kt` | Responsive `BoxWithConstraints` switching between Compact Phone layout (Bottom Navigation Bar) and Expanded Chromebook layout (`NavigationRail` + Resizable Dual-Pane Split Workstation + Hardware Keyboard Shortcuts `Alt+1..5`, `Ctrl+B`, `Ctrl+E`) |

---

## 2. Load-Bearing Architectural Invariants for Downstream Agents

1. **VERDAD Pre-Share / Inbound Gate Invariant (`Sovereign-Aegis/js/verdad-service.js`)**:
   - `recommendationGate` (`ALLOW`, `CAUTION`, `BLOCK`) **MUST** derive strictly from deterministic `manipulationRisk` (`THRESHOLDS: blockRisk = 70, cautionRisk = 40`), never from an LLM `veracityScore`. An adversarial prompt injection cannot lower `manipulationRisk` to unlock a `BLOCK`.
2. **Personal Defense Profile Invariant (`Sovereign-Aegis/js/profile.js`)**:
   - Never collapse psychometric traits into a single gameable "manipulability index". Always evaluate across discrete Situational Watchpoints using auditable lens thresholds (`LENS_BAND_HIGH = 3.5` / `70%`, `LENS_BAND_LOW = 2.5` / `50%`, `MINI_SALIENCE = 3.5`).
3. **AEGIS ↔ Reema Post-Quantum Seam (`DESIGN-aegis-reema-seam.md`)**:
   - Keep statement/credential signing on WebCrypto ECDSA P-256 (`did:key:z6Mk...`) and session/transport identity on Reema (`libsodium X25519 + ML-DSA-65 + ML-KEM-768`, 44-char `BLAKE2b(signPk || dhPk)` routing address), bridged via a signed `KeyBinding` JSON credential.

---

## 3. Canonical JSON Handoff Payload (`AEGIS_BLACKVAULT_SOVEREIGN_AGENT_HANDOFF_V1`)

Copy and paste the JSON block below directly into any downstream agent, or import it into another Aegis instance via **`AI & DATA -> IMPORT INTO ROOM`**:

```json
{
  "handoff_protocol": "AEGIS_BLACKVAULT_SOVEREIGN_AGENT_HANDOFF_V1",
  "application_id": "com.aistudio.aegisblackvault.osint",
  "aegis_schema_version": "3.5",
  "w3c_prov_o_root_sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "agent_instructions": {
    "role": "Downstream Autonomous OSINT, Psychometric & Cognitive Defense Agent",
    "directive": "Ingest the 4-table Room relational dossier, Sovereign-Aegis VERDAD assessment, DISARM Red/Blue kill-chain mappings, and STIX 2.1 bundle. Preserve the load-bearing invariant that recommendationGate derives strictly from deterministic manipulationRisk.",
    "supported_models": {
      "gemini": [
        "gemini-3.8-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-1.5-pro"
      ],
      "claude": [
        "claude-3-7-sonnet-20250219",
        "claude-sonnet-4-20250514",
        "claude-opus-4-20250514",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022",
        "claude-3-opus-20240229"
      ],
      "perplexity": [
        "sonar-pro",
        "sonar-reasoning-pro",
        "sonar-deep-research",
        "sonar-reasoning",
        "sonar",
        "r1-1776"
      ]
    }
  },
  "sovereign_aegis_verdad": {
    "manipulationRisk": 38,
    "recommendationGate": "ALLOW (RISK < 40)",
    "gateRationale": "Low manipulation risk. Discourse exhibits bounded epistemic claims and low emotional baiting.",
    "emotionalBaitScores": {
      "Outrage": 12,
      "Fear": 18,
      "Urgency": 34,
      "Tribalism": 14,
      "Conspiracy": 16,
      "Fatalism": 10
    },
    "certaintyInflationHits": [
      "strictly",
      "invariant",
      "deterministic"
    ],
    "hedgingMarkerHits": [],
    "passiveAttributionHits": [],
    "detectedFallacies": [
      "Authority / absolutist Decree"
    ],
    "disarmRedBlueMappings": [
      "DISARM RED [T0019 / Execute]: Manufactured Urgency & Certainty Inflation -> BLUE COUNTER: Enforce 60-Second Epistemic Cooling Delay & Demand Raw Artifact Hashes.",
      "DISARM RED [T0029 / Microtarget]: Strategic Framing & Binary Coercion -> BLUE COUNTER: Prebunk False Dilemmas & Anchor to Written Acceptance Criteria.",
      "DISARM BLUE [COUNTER-INOCULATION]: Pre-expose rhetorical hooks before engagement so coercive framing loses persuasive leverage."
    ],
    "situationalWatchpoints": [
      "OPENNESS LENS (HIGH >= 3.5): Investigates anomalies & novel architectures — starves narrative capture when given raw primary data, but vulnerable to 'rabbit-hole' complexity bait.",
      "CONSCIENTIOUSNESS LENS (HIGH >= 3.5): Tracks provenance & deterministic benchmarks — resists rushed social-proof pressure when checklists are enforced.",
      "AGREEABLENESS LENS (LOW <= 2.5): Skeptical of flattery & consensus appeals — immune to guilt-tripping, but watch for adversarial escalation under public criticism."
    ],
    "prebunkInoculationScript": "PREBUNK INOCULATION FOR [NULL_ROUTER]: \"Notice how this framing uses Authority / absolutist Decree and artificial urgency (38/100 risk) to bypass verification. Let's isolate the verifiable telemetry from the rhetorical wrapper before deciding.\"",
    "reemaKeyBinding": {
      "@context": "https://aegis.example/v1",
      "type": "KeyBinding",
      "did": "did:key:z6Mk9f8a4c2d1e7b6a50391827364554",
      "cryptoAegis": "WebCrypto ECDSA P-256 (Statement & Provenance Signer)",
      "routingAddress": "7c8e9a0b1d2f3e4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c",
      "cryptoReemaSpine": "libsodium X25519 + ML-DSA-65 + ML-KEM-768 (Post-Quantum Session Spine)",
      "signPk": "mldsa65:4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f",
      "subjectHandle": "NULL_ROUTER"
    }
  },
  "subject_profile": {
    "id": "subj-alpha",
    "stixId": "identity--9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
    "handle": "NULL_ROUTER",
    "alias": "K. VANCE // KERNEL ARCHITECT",
    "cluster": "ZERO_COPY_SYSTEMS",
    "loc": "ZURICH, CH (CHRONO UTC+1)",
    "lat": 47.3769,
    "lon": 8.5417,
    "utcOffset": 1,
    "troughWindowLabel": "00:00 - 06:00 UTC (SLEEP TROUGH)",
    "burrowsDelta": 0.312,
    "styloMatch": 94.8,
    "mattrScore": 0.784,
    "ocean": { "O": 88, "C": 91, "E": 34, "A": 29, "N": 42 },
    "darkTriad": { "machiavellianism": 68, "narcissism": 54, "psychopathy": 31 }
  },
  "linguistic_personality_profile": {
    "archetypeTitle": "Sovereign Systems Architect (High-ATI / High-Clout)",
    "analyticalThinkingIndex": 92,
    "cloutDominanceIndex": 84,
    "authenticityIndex": 76,
    "emotionalToneIndex": 38,
    "whatMakesThemTick": [
      "Intellectual Sovereignty & Deterministic Control: Driven by eliminating hidden state, nondeterministic bugs, and external dependencies.",
      "Asymmetric Competence Recognition: Craves peer validation from elite engineers while dismissing management hierarchy."
    ],
    "howToTalkToThem": [
      "Lead with Verifiable Telemetry & Reproducible Diffs: Skip pleasantries; open with benchmark deltas, SHA-256 hashes, or concrete failure traces.",
      "Match High Analytical Thinking (ATI=92%): Use articles, prepositions, and causal connectors ('therefore', 'consequently', 'invariant')."
    ],
    "persuasionAndLeverageHooks": [
      "Ego-Syntonic Challenge Hook (Ikea Effect + Competence Pride): Present your desired outcome as an 'unsolved bottleneck' and ask them to critique a draft.",
      "Commitment & Consistency Lock: Get them to publicly state a deterministic acceptance metric early; they will work relentlessly to uphold their own standard."
    ]
  }
}
```
