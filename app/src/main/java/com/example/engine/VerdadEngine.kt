package com.example.engine

import com.example.model.*
import java.util.Locale
import kotlin.math.*
import org.json.JSONObject

/**
 * SOVEREIGN VERDAD: deterministic manipulation-risk scoring of text (emotional lexicons, certainty
 * inflation, hedging, passive attribution, fallacy patterns).
 *
 * Ported from Sovereign-Aegis (https://github.com/dribblejerp-KlunkDunker/Sovereign-Aegis):
 * `js/verdad-service.js`, `data/verdad_rules.json`, `data/disarm.json`, `js/profile.js`,
 * `data/profile-guides.json` and `DESIGN-aegis-reema-seam.md`.
 */
object VerdadEngine {
    private val VERDAD_EMOTIONAL_LEXICONS: Map<String, Set<String>> = linkedMapOf(
        "Outrage" to setOf(
            "treasonous", "traitor", "criminal", "evil", "disgrace", "disgusting",
            "monstrous", "corrupt", "tyranny", "tyrant", "scandalous", "depraved",
            "vile", "atrocity", "unforgivable", "sickening", "shameless", "infuriating",
            "abomination", "heinous", "outrageous", "despicable", "rot", "scum", "garbage", "idiot"
        ),
        "Fear" to setOf(
            "catastrophe", "apocalypse", "deadly", "fatal", "annihilation", "collapse",
            "peril", "lethal", "toxic", "destroy", "horrifying", "nightmare",
            "devastation", "doomsday", "massacre", "extinction", "poison", "biohazard",
            "threat", "panic", "ruin", "terror", "breach", "compromise"
        ),
        "Urgency" to setOf(
            "immediate", "immediately", "breaking", "emergency", "urgent", "critical",
            "warning", "censored", "viral", "flash", "now", "must", "shall", "mandate", "lock"
        ),
        "Tribalism" to setOf(
            "enemy", "patriots", "infiltrators", "puppets", "cabal", "sheep",
            "elites", "subversives", "pure", "traitors", "loyalists", "outsiders", "us", "them"
        ),
        "Conspiracy" to setOf(
            "coverup", "hiding", "clandestine", "psyop", "orchestrated", "blackout",
            "puppeteer", "hoax", "manufactured", "obfuscate", "shadow", "backdoor", "rigged"
        ),
        "Fatalism" to setOf(
            "futile", "hopeless", "doomed", "rigged", "surrender", "powerless",
            "fraud", "broken", "useless", "impossible", "irreversible"
        )
    )

    private val VERDAD_CERTAINTY_INFLATION = listOf(
        "indisputably", "undeniably", "categorically proven", "absolute fact",
        "100% conclusive", "beyond all doubt", "unquestionably", "definitive proof",
        "always", "never", "strictly", "invariant", "deterministic"
    )

    private val VERDAD_HEDGING_MARKERS = listOf(
        "it could be argued", "some might suggest", "sources speculate",
        "reportedly", "allegedly", "it remains possible", "unconfirmed rumors",
        "maybe", "perhaps", "might", "probably"
    )

    private val VERDAD_PASSIVE_ATTRIBUTION = listOf(
        "it is said that",
        "many people are saying",
        "critics claim",
        "concerns have been raised",
        "questions are swirling",
        "insiders whisper",
        "everyone knows",
        "rumor has it"
    )

    private val VERDAD_FALLACY_PATTERNS: List<Pair<String, Regex>> = listOf(
        "Ad Hominem / Poisoning the Well" to
            Regex("\\b(don't listen to|convicted|idiot|incompetent|garbage|trash)\\b", RegexOption.IGNORE_CASE),
        "Circumstantial Ad Hominem (Motive Bias)" to
            Regex("\\b(only says that because|owns stock|paid shill|stands to profit)\\b", RegexOption.IGNORE_CASE),
        "Tu Quoque / Whataboutism" to Regex("\\b(how can you criticize|what about when|you did the exact)\\b", RegexOption.IGNORE_CASE),
        "Strawman / Extreme Distortion" to Regex("\\b(literally wants to|wants to destroy every|ban all)\\b", RegexOption.IGNORE_CASE),
        "False Dilemma / Binary Coercion" to
            Regex("\\b(either we|or .* will be destroyed|only two choices|with us or against)\\b", RegexOption.IGNORE_CASE),
        "Urgency / Scarcity Coercion" to
            Regex("\\b(act now|before it is too late|share before deleted|time is running out|immediately)\\b", RegexOption.IGNORE_CASE),
        "Appeal to Popularity / Bandwagon" to
            Regex("\\b(everyone knows|because it is popular|millions of people agree)\\b", RegexOption.IGNORE_CASE),
        "Authority / absolutist Decree" to Regex("\\b(must|shall|mandate|enforce|categorically|unquestionably)\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * Evaluates a SubjectProfile's scraped corpus (or a custom message draft) using the
     * Sovereign-Aegis `verdad-service.js` + `profile.js` + `disarm.json` + `keybinding.js` architecture.
     *
     * Load-bearing invariant from `Sovereign-Aegis/js/verdad-service.js`:
     * `recommendationGate` derives ONLY from `manipulationRisk` (`THRESHOLDS: blockRisk = 70, cautionRisk = 40`),
     * never from LLM veracity score, preventing prompt injection from unlocking a `BLOCK`.
     */
    fun evaluateSovereignVerdad(subject: SubjectProfile, customDraftText: String = ""): SovereignVerdadAssessment {
        val corpusText = customDraftText.trim().ifBlank {
            buildString {
                append(subject.frontStageQuote).append(" ")
                append(subject.backStageQuote).append(" ")
                subject.scrapedArtifacts.forEach { append(it.verbatimText).append(" ") }
            }
        }
        val lower = corpusText.lowercase(Locale.US)
        val tokens = Regex("[a-z0-9']+").findAll(lower).map { it.value }.toList()
        val totalTokens = max(tokens.size, 1)

        // 1. 6 Emotional Bait Lexicon Scores (0..100)
        val emotionalScores = linkedMapOf<String, Int>()
        var weightedEmotionSum = 0.0
        for ((cat, lex) in VERDAD_EMOTIONAL_LEXICONS) {
            val hits = tokens.count { it in lex }
            val densityScore = ((hits.toDouble() / totalTokens) * 950.0).roundToInt().coerceIn(8, 96)
            emotionalScores[cat] = densityScore
            weightedEmotionSum += densityScore
        }
        val meanEmotion = weightedEmotionSum / VERDAD_EMOTIONAL_LEXICONS.size

        // 2. Epistemic Hygiene Markers (Certainty Inflation, Hedging, Passive Attribution)
        val certaintyHits = VERDAD_CERTAINTY_INFLATION.filter { lower.contains(it) }
        val hedgingHits = VERDAD_HEDGING_MARKERS.filter { lower.contains(it) }
        val passiveHits = VERDAD_PASSIVE_ATTRIBUTION.filter { lower.contains(it) }

        // 3. Rhetorical Fallacy Regex Scan
        val fallacies = VERDAD_FALLACY_PATTERNS.mapNotNull { (label, regex) ->
            if (regex.containsMatchIn(corpusText)) label else null
        }

        // 4. Deterministic Manipulation Risk (verdad_rules.json scoringWeights:
        // emotionalMultiplier=0.35, conspiracyMultiplier=0.40, urgencyMultiplier=0.30, fallacyMultiplier=0.25)
        val conspiracyScore = emotionalScores["Conspiracy"] ?: 10
        val urgencyScore = emotionalScores["Urgency"] ?: 10
        val outrageScore = emotionalScores["Outrage"] ?: 10
        val fallacyScore = (fallacies.size * 22 + certaintyHits.size * 9 + passiveHits.size * 12).coerceAtMost(100)

        val rawRisk = (
            meanEmotion * 0.35 +
                conspiracyScore * 0.40 +
                urgencyScore * 0.30 +
                fallacyScore * 0.25 +
                (outageOrDarkTriadBoost(subject, outrageScore, customDraftText.isNotBlank()))
            ).roundToInt().coerceIn(5, 98)

        // Load-bearing Sovereign-Aegis `verdad-service.js` THRESHOLDS: blockRisk = 70, cautionRisk = 40
        val (gateLabel, gateRationale) = when {
            rawRisk >= 70 ->
                "BLOCK (RISK >= 70)" to
                    "High manipulation & coercive framing density detected. Sovereign-Aegis pre-send/inbound gate recommends BLOCK & prebunk inoculation."

            rawRisk >= 40 ->
                "CAUTION (RISK 40..69)" to
                    "Elevated directive/certainty inflation or urgency framing. Verify primary sources (SIFT) before acting or sharing."

            else -> "ALLOW (RISK < 40)" to "Low manipulation risk. Discourse exhibits bounded epistemic claims and low emotional baiting."
        }

        // 5. DISARM Red/Blue Framework Mapping (from Sovereign-Aegis data/disarm.json & disarm_blue.json)
        val disarmMappings = buildList {
            if (urgencyScore >= 35 || certaintyHits.isNotEmpty()) {
                add(
                    "DISARM RED [T0019 / Execute]: Manufactured Urgency & Certainty Inflation -> BLUE COUNTER: Enforce 60-Second Epistemic Cooling Delay & Demand Raw Artifact Hashes."
                )
            }
            if (conspiracyScore >= 30 || passiveHits.isNotEmpty()) {
                add(
                    "DISARM RED [T0007 / Plan]: In-Group/Out-Group Narrative & Unverified Attribution -> BLUE COUNTER: Apply SIFT Source Trace & Strip Anonymous Weasel Framing."
                )
            }
            if (subject.darkTriad.machiavellianism >= 60 || fallacies.isNotEmpty()) {
                add(
                    "DISARM RED [T0029 / Microtarget]: Strategic Framing & Binary Coercion -> BLUE COUNTER: Prebunk False Dilemmas & Anchor to Written Acceptance Criteria."
                )
            }
            add(
                "DISARM BLUE [COUNTER-INOCULATION]: Pre-expose rhetorical hooks before engagement so coercive framing loses persuasive leverage."
            )
        }

        // 6. Personal Defense Profile Situational Watchpoints (ported from Sovereign-Aegis js/profile.js & data/profile-guides.json)
        // Uses LENS_BAND_HIGH = 3.5 (~70%) and LENS_BAND_LOW = 2.5 (~50%)
        val watchpoints = buildList {
            if (subject.ocean.O >= 70) {
                add(
                    "OPENNESS LENS (HIGH >= 3.5): Investigates anomalies & novel architectures — starves narrative capture when given raw primary data, but vulnerable to 'rabbit-hole' complexity bait."
                )
            }
            if (subject.ocean.C >= 70) {
                add(
                    "CONSCIENTIOUSNESS LENS (HIGH >= 3.5): Tracks provenance & deterministic benchmarks — resists rushed social-proof pressure when checklists are enforced."
                )
            }
            if (subject.ocean.A <= 50) {
                add(
                    "AGREEABLENESS LENS (LOW <= 2.5): Skeptical of flattery & consensus appeals — immune to guilt-tripping, but watch for adversarial escalation under public criticism."
                )
            } else {
                add(
                    "RECIPROCITY WATCHPOINT (ACTIVE >= 3.5): High cooperative norm sensitivity — guard against 'unsolicited favor / concession' traps with strings attached."
                )
            }
            if (subject.ocean.N >= 58 || urgencyScore >= 40) {
                add(
                    "URGENCY-SCARCITY WATCHPOINT (MINI_SALIENCE >= 3.5): Vulnerable to artificial deadline pressure — counter by decoupling status alerts from immediate commitment."
                )
            }
        }

        val prebunkScript = "PREBUNK INOCULATION FOR [${subject.handle}]: \"Notice how this framing uses ${
            (fallacies.firstOrNull() ?: "absolutist certainty markers")
        } and artificial urgency ($rawRisk/100 risk) to bypass verification. Let's isolate the verifiable telemetry from the rhetorical wrapper before deciding.\""

        // 7. AEGIS <-> Reema KeyBinding PLACEHOLDER (DESIGN-aegis-reema-seam.md)
        // IMPORTANT: nothing below is real cryptography. No keys are generated and nothing is signed;
        // the values are SHA-256 digests shaped like the real format so the UI/export schema can be built.
        // Replace with real ECDSA P-256 (Android Keystore) + ML-DSA-65 signing before treating as attestation.
        val didSuffix = ForensicCommon.sha256("did:p256:${subject.handle}:${subject.provSha256}").take(28)
        val simulatedRoutingAddress = ForensicCommon.sha256("reema:blake2b:x25519:mldsa65:${subject.stixId}").take(44)
        val mldsaPk = ForensicCommon.sha256("mldsa65:pk:${subject.provSha256}").take(36)
        val keyBindingJson = JSONObject().apply {
            put("@context", "https://aegis.example/v1")
            put("type", "KeyBinding")
            put("simulated", true)
            put("warning", "PLACEHOLDER: values are SHA-256 digests, not keys or signatures. Not verifiable.")
            put("did", "did:key:z6Mk$didSuffix")
            put("cryptoAegis", "WebCrypto ECDSA P-256 (Statement & Provenance Signer)")
            put("routingAddress", simulatedRoutingAddress)
            put("cryptoReemaSpine", "libsodium X25519 + ML-DSA-65 + ML-KEM-768 (Post-Quantum Session Spine)")
            put("signPk", "mldsa65:$mldsaPk")
            put("subjectHandle", subject.handle)
            put("provSha256", subject.provSha256)
        }.toString(2)

        return SovereignVerdadAssessment(
            manipulationRisk = rawRisk,
            recommendationGate = gateLabel,
            gateRationale = gateRationale,
            emotionalBaitScores = emotionalScores,
            certaintyInflationHits = certaintyHits,
            hedgingMarkerHits = hedgingHits,
            passiveAttributionHits = passiveHits,
            detectedFallacies = fallacies,
            disarmRedBlueMappings = disarmMappings,
            situationalWatchpoints = watchpoints,
            prebunkInoculationScript = prebunkScript,
            reemaKeyBindingJson = keyBindingJson
        )
    }

    private fun outageOrDarkTriadBoost(subject: SubjectProfile, outrageScore: Int, isCustomDraft: Boolean): Double = if (isCustomDraft) {
        outrageScore * 0.25
    } else {
        (subject.darkTriad.machiavellianism * 0.18) + (subject.darkTriad.psychopathy * 0.12)
    }
}
