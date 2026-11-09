# AEGIS // BLACKVAULT: Master Engineering & Integration Roadmap

This document consolidates the complete four-phase engineering roadmap for the **AEGIS // BLACKVAULT SIGINT & Psycholinguistic Intelligence Workbench**, covering the 3D WebGL semantic spaces, temporal chronolocation engines, FalkorDB GraphRAG backend, and Path-B Walled-Garden Stealth Fleet with AI Self-Healing DOM extraction.

---

## Repository Directory Structure (Included in `.ZIP` Archive)

```text
aegis-blackvault-complete-stack/
├── README.md                              # Quickstart & 3-command launch guide
├── docker-compose.yml                     # FalkorDB + FastAPI container orchestration
├── docs/
│   ├── ROADMAP.md                         # Phased engineering & integration roadmap
│   ├── ARCHITECTURE_SCALING_AUDIT.md      # Walled-garden social media scaling analysis
│   └── OPERATIONAL_READINESS_AUDIT.md     # Forensic & mathematical readiness audit
├── backend/
│   ├── falkor_scraper_server.py           # FastAPI + FalkorDB + Burrows' Delta + AI Auto-Heal
│   └── requirements.txt                   # Python dependencies
└── frontend/
    ├── package.json                       # Vite + React frontend manifest
    └── src/
        └── App.jsx                        # Obsidian CRT Dashboard & PKZIP Exporter
```

---

## Phase 1: Neural Semantic Embedding Space (UMAP / t-SNE 3D Projection)

### 1. Objective
Project high-dimensional transformer embeddings (`text-embedding-3-small` or local `all-MiniLM-L6-v2` 384-d vectors) into 3D space so semantically aligned threat actors and manifestos cluster organically inside Surface 01.

### 2. Architectural Pipeline
```text
[Raw OSINT Artifacts] 
       │
       ▼
[Embedding Worker (MiniLM / OpenAI)] ──> Generates 384-dim Vectors
       │
       ▼
[UMAP / Cosine Reduction Matrix]     ──> Maps to 3D Coordinates (x, y, z)
       │
       ▼
[Surface 01 3D Perspective Engine]   ──> Renders Target Clusters & Gaussian Nebulae
```

### 3. Acceptance Criteria
* Semantically linked artifacts cluster within $\Delta < 0.25$ Euclidean distance in 3D space.
* Clicking any 3D target node raycasts back to the active subject dossier at 60fps.

---

## Phase 2: Temporal Attack Tree & Diurnal Chronolocation Engine

### 1. Objective
Correlate 24-hour UTC commit/post timestamps with a sliding 6-hour minimum activity window to infer local rest troughs (01:00–07:00 local time), detect physical timezone relocations, and map operational phases onto an interactive Attack Tree.

### 2. Mathematical Model
For a 24-hour UTC histogram $H = [h_0, h_1, \dots, h_{23}]$, the diurnal rest trough start hour $t^*$ is:
$$t^* = \arg\min_{t \in \{0..23\}} \sum_{k=0}^{5} h_{(t + k) \bmod 24}$$
Assuming the rest trough corresponds to 01:00–07:00 local time, the inferred UTC offset $\Delta_{\text{UTC}}$ is:
$$\Delta_{\text{UTC}} = \left((1 - t^* + 12) \bmod 24\right) - 12$$

---

## Phase 3: Calibrated Forensic Stylometry & Dyadic LSM

### 1. Burrows' Delta ($\Delta$) Authorship Attribution
Uses a 30-function-word calibrated baseline with empirical population means ($\mu_i$) and standard deviations ($\sigma_i$):
$$\Delta(A, B) = \frac{1}{N} \sum_{i=1}^{N} \left| z_i(A) - z_i(B) \right|, \quad z_i(X) = \frac{f_i(X) - \mu_i}{\sigma_i}$$

### 2. Pennebaker Linguistic Style Matching (LSM)
Measures dyadic accommodation across function-word categories between Speaker A and Speaker B:
$$\text{LSM}_c = 1 - \frac{|f_{c,A} - f_{c,B}|}{f_{c,A} + f_{c,B} + 0.0001}$$

---

## Phase 4: Path-B Walled-Garden Stealth Fleet & AI Auto-Heal Protocol

### 1. Objective
Enable resilient OSINT extraction across JavaScript-heavy, obfuscated social platforms (**TikTok, Instagram, YouTube, Reddit, Facebook, OnlyFans**) with automatic **DOM Drift Detection** and **AI Self-Healing Selector Synthesis**.

### 2. State Machine Workflow
1. **Selector Probe & AST Watchdog:** When scraping a target URL, the scraper checks whether the primary CSS/XPath selector returns valid hydration nodes.
2. **DOM Drift Trap (`DOM_DRIFT_DETECTED`):** If a platform updates its HTML/React class names and returns `0` nodes, the pipeline halts and raises a visual & audible alarm in the `AI_SCRAPER_BRIDGE` console.
3. **4-Stage Neural Auto-Heal (`/api/v1/heal`):**
   * **Stage 1:** Snapshots the mutated DOM and shadow hydration scripts (`__UNIVERSAL_DATA_FOR_REHYDRATION__`, `ytInitialData`, `ScheduledServerJS`).
   * **Stage 2:** Diffs the broken selector against available JSON hydration trees or GraphQL relay endpoints.
   * **Stage 3:** Synthesizes a hot-patched Python extractor and selector rule.
   * **Stage 4:** Injects the `AST-PATCH-*` adapter into the runtime registry and automatically re-engages the scrape.

---

## Phase 5: W3C PROV-O Cryptographic Ledger & OASIS STIX 2.1 Export

### 1. Standards Compliance
* All STIX 2.1 Domain Objects (`identity`, `location`, `indicator`, `relationship`) use strict RFC 4122 UUIDv4/v5 identifiers.
* Every ingested artifact is hashed with FIPS 180-4 **SHA-256** to guarantee tamper-evident W3C PROV-O lineage.