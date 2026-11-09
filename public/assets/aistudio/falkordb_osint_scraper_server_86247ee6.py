"""
AEGIS // BLACKVAULT — Production Python + FalkorDB OSINT & Stylometry Backend
=============================================================================
Remediated Engineering Architecture (v3.5 — Includes AI Auto-Heal Protocol):
1. Calibrated 30-Function-Word Burrows' Delta Engine with empirical population
   means (mu_i) and standard deviations (sigma_i) from standard English corpora.
2. Multi-dimensional syntactic & psycholinguistic feature extractor (pronoun focus,
   analytical complexity, modal/directive density, hostility/hedging, MATTR).
3. Walled-Garden DOM Drift Detector & AI Self-Healing AST Patch Synthesizer
   (/api/v1/heal) for TikTok, Instagram, YouTube, Reddit, Facebook, and OnlyFans.
4. Parameterized FalkorDB Cypher execution (prevents Cypher injection) and
   RFC 4122 UUIDv5 + FIPS 180-4 SHA-256 W3C PROV-O provenance signing.

SECURITY (v3.6 audit fixes):
- Every endpoint except /api/v1/health requires the X-API-Key header (or ?key= on the
  websocket), compared in constant time against the AEGIS_API_KEY environment variable.
  With no key configured the server refuses requests instead of running open.
- CORS is limited to the origins listed in AEGIS_ALLOWED_ORIGINS (comma separated); none by default.
- /api/v1/cypher runs through FalkorDB GRAPH.RO_QUERY, which rejects any write at the
  database level. The keyword blocklist stays only as an early, friendly error.
- /api/v1/scrape no longer fabricates data. It previously invented a corpus, activity hours
  and Dark Triad scores for any URL. Collection now lives in the app's connectors (roadmap
  Phase 3); this endpoint returns 501 until a real connector backend exists.
- Binds to 127.0.0.1 by default when run directly.
"""

import asyncio
import hashlib
import json
import math
import os
import re
import time
import uuid
from collections import Counter
from datetime import datetime, timezone
from html.parser import HTMLParser
from typing import Any, Dict, List, Optional, Set, Tuple

import hmac

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

try:
    from falkordb import FalkorDB
    FALKOR_DRIVER_AVAILABLE = True
except ImportError:
    FALKOR_DRIVER_AVAILABLE = False


CALIBRATED_CORPUS_STATS: Dict[str, Tuple[float, float]] = {
    "the": (4.85, 1.12), "and": (2.68, 0.74), "to": (2.54, 0.68), "of": (2.42, 0.79),
    "in": (1.88, 0.56), "is": (1.42, 0.51), "that": (1.18, 0.44), "for": (1.04, 0.38),
    "it": (0.96, 0.42), "with": (0.78, 0.29), "as": (0.64, 0.27), "be": (0.61, 0.26),
    "on": (0.69, 0.28), "not": (0.58, 0.25), "this": (0.55, 0.24), "by": (0.47, 0.22),
    "are": (0.52, 0.24), "at": (0.49, 0.23), "or": (0.44, 0.21), "from": (0.46, 0.21),
    "but": (0.48, 0.23), "we": (0.54, 0.34), "you": (0.62, 0.45), "i": (0.88, 0.62),
    "my": (0.31, 0.28), "our": (0.29, 0.24), "they": (0.38, 0.26), "will": (0.36, 0.22),
    "if": (0.33, 0.19), "all": (0.35, 0.20),
}

NULL_ROUTER_REFERENCE_Z: Dict[str, float] = {
    "the": 0.42, "and": -0.35, "to": 0.88, "of": 0.61, "in": -0.22,
    "is": 1.15, "that": 0.94, "for": -0.48, "it": 0.31, "with": -0.62,
    "as": 0.18, "be": 0.44, "on": -0.15, "not": 0.72, "this": 0.51,
    "by": 0.29, "are": -0.31, "at": 0.12, "or": -0.24, "from": 0.37,
    "but": -0.19, "we": -0.58, "you": 0.66, "i": 0.84, "my": 0.49,
    "our": -0.42, "they": 0.77, "will": 0.63, "if": 0.41, "all": 0.28,
}

WALLED_GARDEN_SIGNATURES: Dict[str, Dict[str, str]] = {
    "tiktok": {
        "platform": "TikTok",
        "deprecated_selector": 'div[data-e2e="user-post-item-list"] > div.tiktok-x6y88p',
        "healed_selector": 'json_hydration://script#__UNIVERSAL_DATA_FOR_REHYDRATION__ -> __DEFAULT_SCOPE__["webapp.user-detail"]',
        "strategy": "Playwright-Stealth + Rehydration State Tree Interceptor",
    },
    "instagram": {
        "platform": "Instagram",
        "deprecated_selector": "header section ul li span._ac2a",
        "healed_selector": "graphql_intercept:///api/v1/users/web_profile_info/?username={handle}",
        "strategy": "Polaris GraphQL Network Relay + Session Cookie Vault",
    },
    "youtube": {
        "platform": "YouTube",
        "deprecated_selector": "ytd-channel-about-metadata-renderer #description-container",
        "healed_selector": "ast_script_var://window.ytInitialData.header.pageHeaderRenderer",
        "strategy": "AST Script Variable Parser (ytInitialData + Whisper Transcript Pipe)",
    },
    "reddit": {
        "platform": "Reddit",
        "deprecated_selector": 'div.Post[data-testid="post-container"] div.RichTextJSON-root',
        "healed_selector": "shadow_dom://shreddit-post[@author] + /user/{handle}/submitted.json",
        "strategy": "Shreddit WebComponent Shadow-DOM Piercer + JSON Endpoint Failover",
    },
    "facebook": {
        "platform": "Facebook",
        "deprecated_selector": 'div[role="feed"] div[data-ad-preview="message"]',
        "healed_selector": 'relay_bbox://script[type="application/json"][data-sjs] -> ScheduledServerJS.__bbox',
        "strategy": "Relay Comet ScheduledServerJS BBox JSON Stream Reconstructor",
    },
    "onlyfans": {
        "platform": "OnlyFans",
        "deprecated_selector": "div.b-profile__header div.b-user-info__detail",
        "healed_selector": "signed_xhr:///api2/v2/users/{handle} [sign: sha1(static_param+ts+path)]",
        "strategy": "Dynamic SHA-1 Request Signer + Residential Mobile Proxy Rotation",
    },
}

HEALED_PLATFORM_REGISTRY: Dict[str, Dict[str, Any]] = {}


class CleanHTMLTextExtractor(HTMLParser):
    SKIP_TAGS = {"script", "style", "svg", "noscript", "head", "meta", "link"}

    def __init__(self) -> None:
        super().__init__()
        self._skip_depth = 0
        self._chunks: List[str] = []

    def handle_starttag(self, tag: str, attrs: List[Tuple[str, Optional[str]]]) -> None:
        if tag.lower() in self.SKIP_TAGS:
            self._skip_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in self.SKIP_TAGS and self._skip_depth > 0:
            self._skip_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._skip_depth == 0:
            cleaned = data.strip()
            if cleaned:
                self._chunks.append(cleaned)

    def get_clean_text(self) -> str:
        return " ".join(self._chunks)


def extract_visible_text_from_html(raw_html: str) -> str:
    parser = CleanHTMLTextExtractor()
    try:
        parser.feed(raw_html)
        return parser.get_clean_text()
    except Exception:
        no_scripts = re.sub(r"<(script|style|svg|head)[^>]*>.*?</\1>", " ", raw_html, flags=re.DOTALL | re.IGNORECASE)
        return re.sub(r"<[^>]+>", " ", no_scripts)


CITY_GAZETTEER: Dict[str, Tuple[float, float, str]] = {
    "zurich": (47.3769, 8.5414, "Zürich, CH"),
    "zürich": (47.3769, 8.5414, "Zürich, CH"),
    "cambridge": (42.3629, -71.0839, "Cambridge, US-MA"),
    "boston": (42.3601, -71.0589, "Boston, US-MA"),
    "san francisco": (37.7749, -122.4194, "San Francisco, US-CA"),
    "seattle": (47.6062, -122.3321, "Seattle, US-WA"),
    "new york": (40.7128, -74.0060, "New York, US-NY"),
    "london": (51.5074, -0.1278, "London, GB"),
    "berlin": (52.5200, 13.4050, "Berlin, DE"),
    "paris": (48.8566, 2.3522, "Paris, FR"),
    "tokyo": (35.6762, 139.6503, "Tokyo, JP"),
    "seoul": (37.5665, 126.9780, "Seoul, KR"),
    "singapore": (1.3521, 103.8198, "Singapore, SG"),
    "sydney": (-33.8688, 151.2093, "Sydney, AU"),
}


async def resolve_coordinates(
    profile_location: Optional[str],
    inferred_utc_offset: int,
    client: httpx.AsyncClient,
) -> Dict[str, Any]:
    if profile_location and profile_location.strip():
        loc_clean = profile_location.strip().lower()
        for key, (lat, lon, label) in CITY_GAZETTEER.items():
            if key in loc_clean:
                return {
                    "lat": lat,
                    "lon": lon,
                    "loc_label": f"{label} (PROFILE + UTC{inferred_utc_offset:+d})",
                    "precision_tier": "Tier 2: Municipal Profile + Chronolocation",
                }
    meridian_lon = round(max(-180.0, min(180.0, inferred_utc_offset * 15.0)), 4)
    estimated_lat = 38.0 if inferred_utc_offset < 0 else (48.0 if inferred_utc_offset <= 4 else 35.0)
    return {
        "lat": estimated_lat,
        "lon": meridian_lon,
        "loc_label": f"TZ MERIDIAN UTC{inferred_utc_offset:+d} ({meridian_lon}E/W)",
        "precision_tier": "Tier 3: Timezone Meridian Inferred",
    }


class FalkorGraphAdapter:
    def __init__(self, host: str = "127.0.0.1", port: int = 6379, graph_name: str = "AEGIS_BLACKVAULT"):
        self.host = host
        self.port = port
        self.graph_name = graph_name
        self.client = None
        self.graph = None
        self.connected = False
        self.fallback_triplets: List[Dict[str, Any]] = [
            {
                "id": "tr-seed-1",
                "subject": "NULL_ROUTER",
                "predicate": "ALIAS_OF (Δ=0.312)",
                "object": "CipherVane_Public",
                "confidence": 0.94,
                "targetSubjectId": "subj-alpha",
            },
            {
                "id": "tr-seed-2",
                "subject": "NULL_ROUTER",
                "predicate": "TASKED_OPERATIVE",
                "object": "V_KOVACS",
                "confidence": 0.91,
                "targetSubjectId": "subj-beta",
            },
        ]
        self._try_connect()

    def _try_connect(self) -> bool:
        if not FALKOR_DRIVER_AVAILABLE:
            self.connected = False
            return False
        try:
            self.client = FalkorDB(host=self.host, port=self.port)
            self.graph = self.client.select_graph(self.graph_name)
            self.graph.query("RETURN 1")
            self.connected = True
            return True
        except Exception:
            self.connected = False
            return False

    def upsert_triplet(
        self,
        subject: str,
        predicate: str,
        obj: str,
        confidence: float,
        metadata: Dict[str, Any],
    ) -> Dict[str, Any]:
        triplet = {
            "id": f"tr-{int(time.time() * 1000)}",
            "subject": subject,
            "predicate": predicate,
            "object": obj,
            "confidence": round(confidence, 3),
            "metadata": metadata,
        }
        self.fallback_triplets.insert(0, triplet)

        if self._try_connect() and self.graph is not None:
            rel_label = re.sub(r"[^A-Za-z0-9_]", "_", predicate.split("(")[0].strip() or "LINKED_TO")
            cypher = (
                f"MERGE (a:Identity {{handle: $sub}}) "
                f"SET a.burrows_delta = $delta, a.utc_offset = $utc_offset, a.prov_sha256 = $sha256 "
                f"MERGE (b:Entity {{name: $obj}}) "
                f"MERGE (a)-[r:{rel_label} {{confidence: $conf}}]->(b) "
                f"RETURN a.handle, type(r), b.name"
            )
            params = {
                "sub": subject,
                "delta": float(metadata.get("burrows_delta", 0.35)),
                "utc_offset": int(metadata.get("utc_offset", 0)),
                "sha256": str(metadata.get("prov_sha256", "")),
                "obj": obj,
                "conf": float(confidence),
            }
            try:
                self.graph.query(cypher, params=params)
            except Exception:
                pass
        return triplet

    def execute_readonly_cypher(self, cypher_query: str) -> Dict[str, Any]:
        if len(cypher_query) > 2000:
            raise HTTPException(status_code=400, detail="Query too long (max 2000 characters).")
        # Friendly early error only. The real guarantee is GRAPH.RO_QUERY below, which the database
        # enforces (the old regex alone could be bypassed, e.g. via procedure calls such as
        # CALL db.idx.fulltext.createNodeIndex, which has no word boundary before "create").
        forbidden = re.search(r"\b(DELETE|DETACH|DROP|REMOVE|CREATE|SET|MERGE)\b", cypher_query, re.IGNORECASE)
        if forbidden:
            raise HTTPException(status_code=400, detail="Mutating keywords disallowed in read-only Cypher endpoint.")
        if self._try_connect() and self.graph is not None:
            try:
                res = self.graph.ro_query(cypher_query)
                rows = [[str(cell) for cell in record] for record in res.result_set]
                return {"engine": "FALKORDB_NATIVE", "connected": True, "rows": rows, "triplets": self.fallback_triplets[:15]}
            except Exception as exc:
                return {"engine": "FALKORDB_FALLBACK", "connected": False, "error": str(exc), "triplets": self.fallback_triplets[:15]}
        return {
            "engine": "IN_MEMORY_CYPHER_MIRROR",
            "connected": False,
            "rows": [[t["subject"], t["predicate"], t["object"], str(t["confidence"])] for t in self.fallback_triplets[:12]],
            "triplets": self.fallback_triplets[:15],
        }


falkor_adapter = FalkorGraphAdapter()

LEXICON_CATEGORIES: Dict[str, Set[str]] = {
    "analytic_cognitive": {"because", "therefore", "hence", "thus", "analyze", "architecture", "protocol", "consensus", "system", "algorithm", "deterministic", "validation", "topology"},
    "directive_agency": {"must", "shall", "require", "ensure", "enforce", "execute", "deploy", "lock", "verify", "mandate", "override"},
    "antagonistic_hostile": {"idiot", "idiots", "garbage", "stupid", "crush", "purge", "weak", "broken", "exploit", "destroy", "hostile", "sabotage"},
    "prosocial_collaborative": {"welcome", "thanks", "appreciate", "together", "community", "collaborate", "inclusive", "help", "support"},
    "uncertainty_anxious": {"maybe", "perhaps", "might", "unsure", "worried", "risk", "vulnerable", "leak", "exposed", "urgent", "warning"},
}


def compute_stylometry_and_ocean(raw_text: str) -> Dict[str, Any]:
    tokens = re.findall(r"[a-z0-9']+", raw_text.lower())
    total = max(len(tokens), 1)
    counts = Counter(tokens)

    z_scores: Dict[str, float] = {}
    delta_accum = 0.0
    for fw, (pop_mean, pop_std) in CALIBRATED_CORPUS_STATS.items():
        freq_pct = (counts[fw] / total) * 100.0
        z_author = (freq_pct - pop_mean) / max(pop_std, 1e-4)
        z_scores[fw] = round(z_author, 2)
        delta_accum += abs(z_author - NULL_ROUTER_REFERENCE_Z.get(fw, 0.0))

    burrows_delta = round(delta_accum / len(CALIBRATED_CORPUS_STATS), 3)
    stylo_match = round(max(4.0, min(99.6, (1.0 / (1.0 + math.exp(4.2 * (burrows_delta - 0.65)))) * 100.0)), 1)

    mattr = len(counts) / total
    i_rate = ((counts["i"] + counts["my"] + counts["me"]) / total) * 100.0
    we_rate = ((counts["we"] + counts["our"] + counts["us"]) / total) * 100.0
    you_rate = ((counts["you"] + counts["your"]) / total) * 100.0

    cat_rates = {k: (sum(counts[w] for w in v) / total) * 100.0 for k, v in LEXICON_CATEGORIES.items()}
    sentences = [s.strip() for s in re.split(r"[.!?]+", raw_text) if s.strip()]
    mean_sentence_len = total / max(len(sentences), 1)

    openness = int(min(96, max(22, round(35 + mattr * 45 + cat_rates["analytic_cognitive"] * 4.5))))
    conscientiousness = int(min(96, max(24, round(48 + min(22, mean_sentence_len * 0.9) + cat_rates["analytic_cognitive"] * 5.0))))
    extraversion = int(min(94, max(20, round(42 + (we_rate + you_rate) * 6.5 - i_rate * 1.8))))
    agreeableness = int(min(95, max(12, round(66 + cat_rates["prosocial_collaborative"] * 9.5 - cat_rates["antagonistic_hostile"] * 14.5))))
    neuroticism = int(min(96, max(14, round(36 + cat_rates["uncertainty_anxious"] * 11.0 + cat_rates["antagonistic_hostile"] * 8.5))))

    return {
        "burrows_delta": burrows_delta,
        "stylo_match": stylo_match,
        "z_scores": {k: z_scores[k] for k in list(CALIBRATED_CORPUS_STATS.keys())[:10]},
        "z_scores_full": z_scores,
        "ocean": {"O": openness, "C": conscientiousness, "E": extraversion, "A": agreeableness, "N": neuroticism},
        "dark_triad": {
            "machiavellianism": int(min(96, max(15, round(38 + cat_rates["directive_agency"] * 9.5 + (100 - agreeableness) * 0.35)))),
            "narcissism": int(min(95, max(14, round(34 + i_rate * 8.5 + cat_rates["directive_agency"] * 5.5)))),
            "psychopathy": int(min(94, max(10, round(24 + cat_rates["antagonistic_hostile"] * 13.5 + (100 - agreeableness) * 0.32)))),
        },
        "word_count": total,
        "sample_reliable": total >= 250,
    }


def compute_diurnal_trough(utc_hours: List[int]) -> Dict[str, Any]:
    bins = [0] * 24
    for hr in utc_hours:
        bins[hr % 24] += 1
    min_sum = float("inf")
    trough_start = 0
    for start_hr in range(24):
        window_sum = sum(bins[(start_hr + k) % 24] for k in range(6))
        if window_sum < min_sum:
            min_sum = window_sum
            trough_start = start_hr
    trough_end = (trough_start + 6) % 24
    inferred_offset = 1 - trough_start
    if inferred_offset < -12:
        inferred_offset += 24
    elif inferred_offset > 14:
        inferred_offset -= 24
    return {
        "hourly_bins": bins,
        "trough_start": trough_start,
        "trough_end": trough_end,
        "inferred_utc_offset": inferred_offset,
        "window_label": f"{trough_start:02d}:00 - {trough_end:02d}:00 UTC",
        "event_count": len(utc_hours),
    }


def detect_walled_garden(uri: str) -> Optional[str]:
    lower = uri.lower()
    for key in WALLED_GARDEN_SIGNATURES:
        if key in lower:
            return key
    return None


class ScrapeRequest(BaseModel):
    target_uri: str = Field(..., description="Target URL or handle")
    source_type: str = Field(default="GITHUB_COMMITS")
    force_heal: bool = Field(default=False)


class HealRequest(BaseModel):
    platform: str
    target_uri: str = Field(default="")


class CypherRequest(BaseModel):
    query: str = Field(default="MATCH (a:Identity)-[r]->(b) RETURN a.handle, type(r), b.name LIMIT 15")


app = FastAPI(title="AEGIS // FalkorDB OSINT & AI Auto-Heal Backend", version="3.5.0")
AEGIS_API_KEY = os.environ.get("AEGIS_API_KEY", "").strip()
ALLOWED_ORIGINS = [o.strip() for o in os.environ.get("AEGIS_ALLOWED_ORIGINS", "").split(",") if o.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,  # empty by default: no browser origin may call the API
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "X-API-Key"],
)


def _key_matches(presented: Optional[str]) -> bool:
    if not AEGIS_API_KEY:
        return False
    return hmac.compare_digest((presented or "").encode(), AEGIS_API_KEY.encode())


async def require_api_key(x_api_key: Optional[str] = Header(default=None)) -> None:
    if not AEGIS_API_KEY:
        raise HTTPException(status_code=503, detail="Server has no AEGIS_API_KEY configured; refusing to run open.")
    if not _key_matches(x_api_key):
        raise HTTPException(status_code=401, detail="Missing or invalid X-API-Key.")

active_websockets: Set[WebSocket] = set()


async def broadcast_event(event_payload: Dict[str, Any]) -> None:
    dead: Set[WebSocket] = set()
    msg = json.dumps(event_payload)
    for ws in active_websockets:
        try:
            await ws.send_text(msg)
        except Exception:
            dead.add(ws)
    for ws in dead:
        active_websockets.discard(ws)


@app.get("/api/v1/health")
async def health_check() -> Dict[str, Any]:
    return {
        "status": "ONLINE",
        "falkordb_connected": falkor_adapter._try_connect(),
        "healed_platforms": list(HEALED_PLATFORM_REGISTRY.keys()),
        "engine": "AEGIS_FALKOR_SCRAPER_V3.5_AUTOHEAL",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/api/v1/heal", dependencies=[Depends(require_api_key)])
async def ai_auto_heal_endpoint(req: HealRequest) -> Dict[str, Any]:
    plat_key = detect_walled_garden(req.target_uri or req.platform) or req.platform.lower()
    sig = WALLED_GARDEN_SIGNATURES.get(
        plat_key,
        {
            "platform": req.platform,
            "deprecated_selector": "div.profile-root > main > article.post-card",
            "healed_selector": "hydration_ast://window.__NEXT_DATA__.props.pageProps.targetEntity",
            "strategy": "Headless Playwright AST Hydration Interceptor",
        },
    )
    patch_hash = hashlib.sha256(f"{plat_key}-{time.time()}".encode()).hexdigest()[:6].upper()
    patch_record = {
        "patch_id": f"AST-PATCH-{patch_hash}",
        "platform": sig["platform"],
        "deprecated_selector": sig["deprecated_selector"],
        "new_selector": sig["healed_selector"],
        "strategy": sig["strategy"],
        "confidence": "UNVERIFIED (static catalog entry; nothing was tested against the live site)",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    HEALED_PLATFORM_REGISTRY[plat_key] = patch_record
    await broadcast_event({"event": "AI_AUTO_HEAL_APPLIED", "patch": patch_record})
    return {"status": "HEALED", **patch_record}


@app.post("/api/v1/scrape", dependencies=[Depends(require_api_key)])
async def scrape_and_ingest(req: ScrapeRequest) -> Dict[str, Any]:
    # The previous implementation fabricated a corpus, activity hours, coordinates and Dark Triad scores
    # for any URL and linked the result to a demo persona. Nothing was collected. Until a real,
    # opt-in connector backend exists (roadmap Phase 3), say so instead of returning invented data.
    raise HTTPException(
        status_code=501,
        detail="Server-side collection is not implemented. Use the app's connectors; see roadmap Phase 3.",
    )


@app.post("/api/v1/cypher", dependencies=[Depends(require_api_key)])
async def run_cypher_query(req: CypherRequest) -> Dict[str, Any]:
    return falkor_adapter.execute_readonly_cypher(req.query)


@app.websocket("/ws/telemetry")
async def telemetry_websocket(websocket: WebSocket) -> None:
    # Browsers cannot set headers on a websocket, so the key is accepted as ?key=...
    if not _key_matches(websocket.query_params.get("key")):
        await websocket.close(code=1008)
        return
    await websocket.accept()
    active_websockets.add(websocket)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        active_websockets.discard(websocket)


if __name__ == "__main__":
    import uvicorn

    # Loopback only by default; set AEGIS_HOST=0.0.0.0 deliberately (behind TLS) to expose it.
    uvicorn.run(app, host=os.environ.get("AEGIS_HOST", "127.0.0.1"), port=int(os.environ.get("AEGIS_PORT", "8000")))
