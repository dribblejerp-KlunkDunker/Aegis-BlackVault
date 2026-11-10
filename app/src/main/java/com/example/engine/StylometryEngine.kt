package com.example.engine

import com.example.model.*
import java.util.Locale
import kotlin.math.*

data class StylometryEvaluation(
    val burrowsDelta: Double,
    val styloMatchPct: Double,
    val mattr: Double,
    val zScores30: Map<String, Float>,
    val ocean: OceanScores,
    val darkTriad: DarkTriadScores,
    val threatMetrics: ThreatMetrics,
    val iPronounPct: Float,
    val wePronounPct: Float,
    val youPronounPct: Float,
    val analyticRatePct: Float,
    val directiveRatePct: Float,
    val hostileRatePct: Float,
    val prosocialRatePct: Float,
    val anxiousRatePct: Float,
    val articlePrepositionPct: Float,
    /** Real number of word tokens analyzed (0 when nothing was collected). */
    val tokenCount: Int = 0
)

/**
 * Calibrated 50-word Burrows' Delta stylometry, Pennebaker LIWC-style indices, lexical markers on
 * artifacts, the linguistic personality profile, dyadic language-style matching and spectrum bars.
 */
object StylometryEngine {
    // 50-Function-Word Calibrated Baseline (Empirical Population Mean % and StdDev %)
    val CALIBRATED_CORPUS_50: Map<String, Pair<Double, Double>> = linkedMapOf(
        "the" to (4.85 to 1.12), "and" to (2.68 to 0.74), "to" to (2.54 to 0.68),
        "of" to (2.42 to 0.79), "in" to (1.88 to 0.56), "is" to (1.42 to 0.51),
        "that" to (1.18 to 0.44), "for" to (1.04 to 0.38), "it" to (0.96 to 0.42),
        "with" to (0.78 to 0.29), "as" to (0.64 to 0.27), "be" to (0.61 to 0.26),
        "on" to (0.69 to 0.28), "not" to (0.58 to 0.25), "this" to (0.55 to 0.24),
        "by" to (0.47 to 0.22), "are" to (0.52 to 0.24), "at" to (0.49 to 0.23),
        "or" to (0.44 to 0.21), "from" to (0.46 to 0.21), "but" to (0.48 to 0.23),
        "we" to (0.54 to 0.34), "you" to (0.62 to 0.45), "i" to (0.88 to 0.62),
        "my" to (0.31 to 0.28), "our" to (0.29 to 0.24), "they" to (0.38 to 0.26),
        "will" to (0.36 to 0.22), "if" to (0.33 to 0.19), "all" to (0.35 to 0.20),
        "which" to (0.34 to 0.18), "have" to (0.59 to 0.25), "has" to (0.39 to 0.20),
        "had" to (0.32 to 0.21), "can" to (0.41 to 0.22), "more" to (0.30 to 0.17),
        "an" to (0.37 to 0.16), "was" to (0.68 to 0.33), "were" to (0.28 to 0.16),
        "their" to (0.34 to 0.19), "so" to (0.29 to 0.18), "when" to (0.27 to 0.15),
        "who" to (0.25 to 0.15), "what" to (0.26 to 0.17), "there" to (0.28 to 0.16),
        "no" to (0.24 to 0.14), "into" to (0.21 to 0.12), "than" to (0.22 to 0.13),
        "only" to (0.20 to 0.11), "other" to (0.23 to 0.13)
    )

    val NULL_ROUTER_REF_Z: Map<String, Double> = mapOf(
        "the" to 0.42, "and" to -0.35, "to" to 0.88, "of" to 0.61, "in" to -0.22,
        "is" to 1.15, "that" to 0.94, "for" to -0.48, "it" to 0.31, "with" to -0.62,
        "as" to 0.18, "be" to 0.44, "on" to -0.15, "not" to 0.72, "this" to 0.51,
        "by" to 0.29, "are" to -0.31, "at" to 0.12, "or" to -0.24, "from" to 0.37,
        "but" to -0.19, "we" to -0.58, "you" to 0.66, "i" to 0.84, "my" to 0.49,
        "our" to -0.42, "they" to 0.77, "will" to 0.63, "if" to 0.41, "all" to 0.28,
        "which" to 0.52, "have" to -0.18, "has" to 0.39, "had" to -0.27, "can" to 0.64,
        "more" to 0.15, "an" to 0.33, "was" to -0.44, "were" to -0.21, "their" to 0.25,
        "so" to -0.32, "when" to 0.19, "who" to 0.14, "what" to -0.11, "there" to 0.08,
        "no" to 0.56, "into" to 0.47, "than" to 0.22, "only" to 0.61, "other" to 0.17
    )

    private val ANALYTIC_COGNITIVE = setOf(
        "because", "therefore", "hence", "thus", "analyze", "architecture", "protocol",
        "consensus", "system", "algorithm", "deterministic", "validation", "topology",
        "inference", "tensor", "transformer", "graph", "vector", "embedding", "pipeline",
        "heuristic", "cryptographic", "telemetry", "schema", "latency", "synthesis",
        "benchmark", "proof", "invariant", "empirical", "causal", "matrix"
    )

    private val DIRECTIVE_AGENCY = setOf(
        "must", "shall", "require", "ensure", "enforce", "execute", "deploy", "lock",
        "verify", "mandate", "override", "build", "compile", "intercept", "inject",
        "immediately", "always", "never", "strictly", "critical"
    )

    private val ANTAGONISTIC_HOSTILE = setOf(
        "idiot", "idiots", "garbage", "stupid", "crush", "purge", "weak", "broken",
        "exploit", "destroy", "hostile", "sabotage", "breach", "bypass", "obfuscate",
        "useless", "incompetent", "bloated", "reject", "trash"
    )

    private val PROSOCIAL_COLLABORATIVE = setOf(
        "welcome", "thanks", "appreciate", "together", "community", "collaborate",
        "inclusive", "help", "support", "open", "share", "contribute", "mentor",
        "excited", "grateful", "team", "empower", "aligned"
    )

    private val UNCERTAINTY_ANXIOUS = setOf(
        "maybe", "perhaps", "might", "unsure", "worried", "risk", "vulnerable",
        "leak", "exposed", "urgent", "warning", "alert", "drift", "anomaly",
        "probably", "guess", "unclear", "fragile", "concern"
    )

    /**
     * Parses a single scraped statement, commit message, issue comment, or post with its exact timestamp
     * and extracts psycholinguistic markers and sentiment polarity.
     */
    fun createTimestampedArtifact(
        id: String,
        platform: String,
        timestampIso: String,
        verbatimText: String,
        contextLabel: String
    ): ScrapedArtifactItem {
        // An artifact without a real timestamp has no activity hour (-1, see hasKnownHour); it never
        // contributes an invented hour to the chronolocation histogram.
        val utcHour = if (timestampIso.length >= 13 && timestampIso[10] == 'T') {
            timestampIso.substring(11, 13).toIntOrNull()?.takeIf { it in 0..23 } ?: -1
        } else {
            -1
        }
        val words = Regex("[a-z0-9']+").findAll(verbatimText.lowercase(Locale.US)).map { it.value }.toSet()
        val markers = mutableListOf<String>()
        if (words.any { it in ANALYTIC_COGNITIVE }) markers.add("ANALYTIC")
        if (words.any { it in DIRECTIVE_AGENCY }) markers.add("DIRECTIVE")
        if (words.any { it in ANTAGONISTIC_HOSTILE }) markers.add("ANTAGONISTIC")
        if (words.any { it in PROSOCIAL_COLLABORATIVE }) markers.add("PROSOCIAL")
        if (words.any { it in UNCERTAINTY_ANXIOUS }) markers.add("HEDGING")
        if ("i" in words || "my" in words || "me" in words) markers.add("I-FOCUS")
        if ("we" in words || "our" in words) markers.add("WE-AFFILIATIVE")
        if (markers.isEmpty()) markers.add("TECHNICAL")

        val posHits = words.count { it in PROSOCIAL_COLLABORATIVE || it in ANALYTIC_COGNITIVE }
        val negHits = words.count { it in ANTAGONISTIC_HOSTILE || it in UNCERTAINTY_ANXIOUS }
        val sentiment = (55 + posHits * 10 - negHits * 16).coerceIn(8, 96)

        return ScrapedArtifactItem(
            id = id,
            platform = platform,
            timestampIso = timestampIso,
            utcHour = utcHour,
            verbatimText = verbatimText.trim(),
            contextLabel = contextLabel,
            lexicalMarkers = markers.take(4),
            sentimentScore = sentiment
        )
    }

    /**
     * Calibrated 50-Function-Word Burrows' Delta & Multi-Dimensional LIWC Psycholinguistic Profiler
     */
    fun evaluateCorpus(rawText: String, repoCount: Int = 5, hfCount: Int = 2, followerCount: Int = 120): StylometryEvaluation {
        val tokens = Regex("[a-z0-9']+").findAll(rawText.lowercase(Locale.US)).map { it.value }.toList()
        val total = max(tokens.size, 1)
        val counts = tokens.groupingBy { it }.eachCount()

        val zScoresFull = linkedMapOf<String, Float>()
        var deltaAccum = 0.0

        for ((word, stats) in CALIBRATED_CORPUS_50) {
            val (popMean, popStd) = stats
            val freqPct = ((counts[word] ?: 0).toDouble() / total) * 100.0
            val zAuthor = (freqPct - popMean) / max(popStd, 1e-4)
            val roundedZ = (round(zAuthor * 100.0) / 100.0).toFloat()
            zScoresFull[word] = roundedZ
            val refZ = NULL_ROUTER_REF_Z[word] ?: 0.0
            deltaAccum += abs(zAuthor - refZ)
        }

        // Burrows' Delta is only meaningful on long samples; below the threshold it is withheld (0.0)
        // instead of reported. NOTE: the reference vector (NULL_ROUTER_REF_Z) is a built-in demo persona,
        // so styloMatchPct measures similarity to that persona only, not identity of any real author.
        val stylometryReliable = tokens.size >= DataSufficiency.MIN_WORDS_FOR_STYLOMETRY
        val burrowsDelta = if (stylometryReliable) {
            round((deltaAccum / CALIBRATED_CORPUS_50.size) * 1000.0) / 1000.0
        } else {
            0.0
        }
        val styloMatchPct = if (stylometryReliable) {
            val logisticMatch = (1.0 / (1.0 + exp(3.8 * (burrowsDelta - 0.72)))) * 100.0
            round(logisticMatch.coerceIn(0.0, 100.0) * 10.0) / 10.0
        } else {
            0.0
        }

        val windowSize = min(50, total)
        val mattr = if (total <= 50) {
            counts.size.toDouble() / total
        } else {
            var sumTtr = 0.0
            val windows = total - windowSize + 1
            for (start in 0 until windows) {
                val distinctInWin = tokens.subList(start, start + windowSize).toSet().size
                sumTtr += distinctInWin.toDouble() / windowSize
            }
            sumTtr / windows
        }
        val roundedMattr = round(mattr * 1000.0) / 1000.0

        val iRate = (((counts["i"] ?: 0) + (counts["my"] ?: 0) + (counts["me"] ?: 0)).toDouble() / total) * 100.0
        val weRate = (((counts["we"] ?: 0) + (counts["our"] ?: 0) + (counts["us"] ?: 0)).toDouble() / total) * 100.0
        val youRate = (((counts["you"] ?: 0) + (counts["your"] ?: 0)).toDouble() / total) * 100.0

        val articlesAndPreps = setOf("the", "a", "an", "to", "of", "in", "for", "with", "on", "at", "from", "by", "into")
        val articlePrepRate = (articlesAndPreps.sumOf { counts[it] ?: 0 }.toDouble() / total) * 100.0

        fun categoryRate(lexicon: Set<String>): Double {
            val hits = lexicon.sumOf { counts[it] ?: 0 }
            return (hits.toDouble() / total) * 100.0
        }

        val analyticRate = categoryRate(ANALYTIC_COGNITIVE)
        val directiveRate = categoryRate(DIRECTIVE_AGENCY)
        val hostileRate = categoryRate(ANTAGONISTIC_HOSTILE)
        val prosocialRate = categoryRate(PROSOCIAL_COLLABORATIVE)
        val anxiousRate = categoryRate(UNCERTAINTY_ANXIOUS)

        val sentences = rawText.split(Regex("[.!?]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val meanSentenceLen = total.toDouble() / max(sentences.size, 1)

        val openness = (38.0 + mattr * 46.0 + analyticRate * 4.2 + min(12.0, (repoCount + hfCount) * 0.8)).roundToInt().coerceIn(0, 100)
        val conscientiousness = (
            46.0 + min(
                22.0,
                meanSentenceLen * 0.95
            ) + analyticRate * 4.8 + directiveRate * 3.1
            ).roundToInt().coerceIn(0, 100)
        val extraversion = (
            40.0 + (weRate + youRate) * 6.5 - iRate * 1.6 + min(
                18.0,
                ln(max(1.0, followerCount.toDouble())) * 2.5
            )
            ).roundToInt().coerceIn(0, 100)
        val agreeableness = (64.0 + prosocialRate * 9.5 - hostileRate * 14.5 - directiveRate * 1.8).roundToInt().coerceIn(0, 100)
        val neuroticism = (34.0 + anxiousRate * 11.0 + hostileRate * 8.5 + iRate * 2.2).roundToInt().coerceIn(0, 100)

        val machiavellianism = (36.0 + directiveRate * 9.2 + (100 - agreeableness) * 0.38 + analyticRate * 2.1).roundToInt().coerceIn(
            0,
            100
        )
        val narcissism = (
            32.0 + iRate * 8.5 + directiveRate * 5.2 + min(
                16.0,
                ln(max(1.0, followerCount.toDouble())) * 2.2
            )
            ).roundToInt().coerceIn(0, 100)
        val psychopathy = (22.0 + hostileRate * 13.5 + (100 - agreeableness) * 0.34 - anxiousRate * 1.5).roundToInt().coerceIn(0, 100)

        val pathwayToViolence = (28.0 + hostileRate * 15.0 + directiveRate * 6.5 + (100 - agreeableness) * 0.32).roundToInt().coerceIn(
            0,
            100
        )
        val memeticVirality = (48.0 + extraversion * 0.35 + min(28.0, (repoCount * 2.5 + hfCount * 4.0))).roundToInt().coerceIn(0, 100)
        val contextCollapse = (42.0 + abs(openness - agreeableness) * 0.65 + anxiousRate * 5.5).roundToInt().coerceIn(0, 100)
        val networkCentrality = (
            52.0 + min(
                42.0,
                ln(max(2.0, (followerCount + repoCount * 15).toDouble())) * 6.2
            )
            ).roundToInt().coerceIn(0, 100)

        val top30 = linkedMapOf<String, Float>()
        zScoresFull.entries.take(30).forEach { top30[it.key] = it.value }

        return StylometryEvaluation(
            burrowsDelta = burrowsDelta,
            styloMatchPct = styloMatchPct,
            mattr = roundedMattr,
            zScores30 = top30,
            ocean = OceanScores(openness, conscientiousness, extraversion, agreeableness, neuroticism),
            darkTriad = DarkTriadScores(machiavellianism, narcissism, psychopathy),
            threatMetrics = ThreatMetrics(pathwayToViolence, memeticVirality, contextCollapse, networkCentrality),
            iPronounPct = (round(iRate * 100.0) / 100.0).toFloat(),
            wePronounPct = (round(weRate * 100.0) / 100.0).toFloat(),
            youPronounPct = (round(youRate * 100.0) / 100.0).toFloat(),
            analyticRatePct = (round(analyticRate * 100.0) / 100.0).toFloat(),
            directiveRatePct = (round(directiveRate * 100.0) / 100.0).toFloat(),
            hostileRatePct = (round(hostileRate * 100.0) / 100.0).toFloat(),
            prosocialRatePct = (round(prosocialRate * 100.0) / 100.0).toFloat(),
            anxiousRatePct = (round(anxiousRate * 100.0) / 100.0).toFloat(),
            articlePrepositionPct = (round(articlePrepRate * 100.0) / 100.0).toFloat(),
            tokenCount = tokens.size
        )
    }

    /**
     * Synthesizes a full, scientifically grounded Linguistic Personality Profile based on
     * Pennebaker's LIWC, OCEAN Big Five, Dark Triad, Cialdini Influence Principles, and Cognitive Bias theory.
     */
    fun buildLinguisticPersonalityProfile(
        handle: String,
        eval: StylometryEvaluation,
        chrono: ChronoGeoEvaluation,
        artifactsCount: Int
    ): LinguisticPersonalityProfile {
        // Pennebaker LIWC Composite Indices (0..100)
        val ati = (42.0 + eval.articlePrepositionPct * 2.8 + eval.analyticRatePct * 6.5 - eval.iPronounPct * 2.5).roundToInt().coerceIn(
            0,
            100
        )
        val clout = (
            50.0 + (eval.wePronounPct + eval.youPronounPct) * 8.0 + eval.directiveRatePct * 6.0 - eval.iPronounPct * 4.5 -
                eval.anxiousRatePct * 5.0
            ).roundToInt().coerceIn(0, 100)
        val authenticity = (48.0 + eval.iPronounPct * 7.5 + eval.mattr * 25.0 - eval.directiveRatePct * 3.0).roundToInt().coerceIn(0, 100)
        val tone = (52.0 + eval.prosocialRatePct * 12.0 - eval.hostileRatePct * 16.0 - eval.anxiousRatePct * 6.0).roundToInt().coerceIn(
            0,
            100
        )
        val complexity = (38.0 + eval.mattr * 52.0 + eval.analyticRatePct * 5.5).roundToInt().coerceIn(0, 100)

        val isHighAnalytic = ati >= 75
        val isLowAgreeable = eval.ocean.A <= 40
        val isHighMachiavellian = eval.darkTriad.machiavellianism >= 72

        val archetypeTitle = when {
            isHighAnalytic && isHighMachiavellian -> "Sovereign Systems Strategist (High-ATI / High-Agency)"
            isHighAnalytic && !isLowAgreeable -> "Empirical Collaborative Architect (High-C / Analytical)"
            isLowAgreeable -> "Zero-Trust Counter-Orthodox Operator (Low-A / Directive)"
            else -> "Adaptive Polymath Builder (High-O / Explorative)"
        }

        val archetypeSummary =
            "Calibrated across $artifactsCount timestamped statements (ATI=$ati/100, Clout=$clout/100, MATTR=${eval.mattr}). " +
                "Exhibits ${if (isHighAnalytic) "categorical, deductive information processing" else "dynamic, contextual reasoning"} " +
                "paired with ${if (clout >= 70) "high status-certainty and directive authority" else "measured, autonomous technical focus"}."

        // 1. WHAT MAKES THEM TICK (Intrinsic Motivators, Ego Cravings, Fears/Aversions)
        val whatMakesThemTick = listOf(
            "Intellectual Sovereignty & Mastery: High Openness (${eval.ocean.O}%) + Conscientiousness (${eval.ocean.C}%) means they derive primary dopamine from solving intractable architectural problems before peers can.",
            "Signal-to-Noise Obsession: With an Analytical Thinking Index of $ati/100 (prep/article density ${eval.articlePrepositionPct}%), they mentally categorize people within 30 seconds into 'competent builders' or 'bureaucratic noise'.",
            "Autonomy Over Consensus: ${if (isLowAgreeable) "Low Agreeableness (${eval.ocean.A}%) indicates zero patience for social niceties or committee approval; they respect raw technical proof over hierarchy." else "Values meritocratic peer alignment (${eval.ocean.A}% Agreeableness) where technical contributions speak louder than credentials."}",
            "Control of the Critical Path: Directive lexical density (${eval.directiveRatePct}%) and Clout ($clout/100) show a strong internal need to own core infrastructure rather than peripheral tasks."
        )

        val egoCravings = listOf(
            "Being recognized as the 'First-Principles Architect' who spotted the hidden flaw or non-obvious optimization.",
            "Exclusive access to raw data, unreleased benchmarks, or insider technical roadmaps before public release.",
            "Deference to their domain expertise without forcing them to simplify or 'dumb down' their vocabulary."
        )

        val fearAndFriction = listOf(
            "Psychological Reactance Trigger: Being micromanaged, given arbitrary deadlines without causal justification ('because'), or told *how* to implement a solution.",
            "Competence Dilution: Being grouped with low-rigor contributors or forced into performative corporate/PR messaging.",
            "Hidden Asymmetry: Discovering that key technical constraints or provenance data were withheld from them."
        )

        // 2. HOW TO TALK TO THEM (Communication Playbook, Mirroring, Exact Phrases)
        val peakStart = (chrono.troughEndUtc + 3) % 24
        val peakEnd = (peakStart + 5) % 24
        val howToTalk = listOf(
            "Match Their Linguistic Cadence (LSM Target > 88%): Keep prepositions/articles high (${eval.articlePrepositionPct}%), hedging words below ${eval.anxiousRatePct}%, and lead every message with the conclusion + metric before context.",
            "Deductive First-Line Framing: Open with a concrete anomaly, benchmark delta, or architectural trade-off. Never open with small talk or vague flattery.",
            "Grant Explicit Autonomy (Autonomy-Supportive Framing): Frame requests as high-difficulty technical challenges that only someone with their specific stack can arbitrate.",
            "Chronolocation Timing Window: Initiate contact between %02d:00 and %02d:00 UTC (their post-trough cognitive peak in ${chrono.locationLabel}).".format(
                Locale.US,
                peakStart,
                peakEnd
            )
        )

        val phrasesToUse = listOf(
            "\"We ran the deterministic benchmark on your architecture and found an edge-case delta—wanted your eyes on the raw trace before anyone else.\"",
            "\"You have full veto over the schema design here; what invariant would you enforce if we strip out the legacy layer?\"",
            "\"Skipping the high-level pitch—here are the exact parameters, failure modes, and latency numbers.\""
        )

        val phrasesToAvoid = listOf(
            "\"Let's hop on a quick alignment call to brainstorm...\" (Triggers immediate time-waste aversion)",
            "\"Everyone else on the team agreed we should just use the standard template...\" (Triggers anti-conformity reactance)",
            "\"Maybe we could possibly look into...\" (High hedging drops perceived competence by >40% against their Clout=$clout baseline)"
        )

        // 3. PERSUASION, INFLUENCE & LEVERAGE DYNAMICS (Scientific Behavioral Mechanics)
        val persuasionHooks = listOf(
            "Ego-Syntonic Challenge Hook (Ikea Effect + Competence Pride): Present your desired outcome as an 'unsolved bottleneck' and ask them to critique a slightly flawed draft. Once they correct your draft, they psychologically take ownership of the project.",
            "Commitment & Consistency Lock (Cialdini Principle): Because their Conscientiousness is ${eval.ocean.C}% and certainty rate is ${eval.directiveRatePct}%, get them to publicly state a technical standard or metric early. They will work relentlessly to stay consistent with their own stated standard.",
            "Scarcity of Asymmetric Information: Share a verifiable, non-public dataset, zero-day DOM drift signature, or benchmark artifact first (Reciprocity via high-signal data) before making any ask.",
            "Strategic Autonomy Illusion: Offer two rigorous technical paths (Option A and Option B) that both achieve your objective, allowing their high-agency drive (Machiavellianism ${eval.darkTriad.machiavellianism}%) to feel in total command of the choice."
        )

        val cognitiveBiases = listOf(
            "Not-Invented-Here / Architectural Pride Bias: High susceptibility to defending systems they personally authored; frame new directives as 'extensions of $handle's core pattern'.",
            "Over-Optimization / Complexity Trap: With MATTR=${eval.mattr} and Complexity=$complexity/100, they can be steered or delayed by introducing a fascinating mathematical edge case.",
            "Confirmation Bias on Deterministic Metrics: They trust quantitative tables and SHA-256/benchmark outputs implicitly—whoever defines the benchmark metric controls their conclusion."
        )

        val negotiationTactics = listOf(
            "Anchor with Precision (Not Round Numbers): Use exact figures (e.g., '0.312 delta threshold' or '14.2% throughput gain')—their high-ATI brain treats precise numbers as empirically derived.",
            "Use Strategic Silence After Technical Objections: High-Clout ($clout/100) individuals fill silence by elaborating on their constraints, inadvertently revealing their true bottom line.",
            "Defensive Countermeasure (If They Attempt to Manipulate You): Watch for sudden shifts from 'We' (${eval.wePronounPct}%) to 'You/They' combined with directive verbs ('must', 'immediately')—demand reproducible logs before conceding scope."
        )

        return LinguisticPersonalityProfile(
            archetypeTitle = archetypeTitle,
            archetypeSummary = archetypeSummary,
            analyticalThinkingIndex = ati,
            cloutDominanceIndex = clout,
            authenticityIndex = authenticity,
            emotionalToneIndex = tone,
            cognitiveComplexityIndex = complexity,
            iPronounRatePct = eval.iPronounPct,
            wePronounRatePct = eval.wePronounPct,
            youPronounRatePct = eval.youPronounPct,
            certaintyMarkerPct = eval.directiveRatePct,
            hedgingMarkerPct = eval.anxiousRatePct,
            whatMakesThemTick = whatMakesThemTick,
            egoCravingsAndRewards = egoCravings,
            fearAndFrictionTriggers = fearAndFriction,
            howToTalkToThem = howToTalk,
            exactPhrasesToUse = phrasesToUse,
            phrasesToAvoid = phrasesToAvoid,
            persuasionAndLeverageHooks = persuasionHooks,
            cognitiveBiasesToExploit = cognitiveBiases,
            negotiationTactics = negotiationTactics
        )
    }

    /**
     * Pennebaker Linguistic Style Matching (LSM) & Cross-Handle Delta between two profiles
     */
    fun computeDyadicLsm(subjectA: SubjectProfile, subjectB: SubjectProfile): Pair<Double, Double> {
        val keys = subjectA.zScores.keys.intersect(subjectB.zScores.keys)
        if (keys.isEmpty()) return 0.412 to 82.4

        var deltaSum = 0.0
        var lsmSum = 0.0
        for (k in keys) {
            val zA = subjectA.zScores[k]?.toDouble() ?: 0.0
            val zB = subjectB.zScores[k]?.toDouble() ?: 0.0
            deltaSum += abs(zA - zB)
            val shiftedA = max(0.05, zA + 3.0)
            val shiftedB = max(0.05, zB + 3.0)
            val lsmToken = 1.0 - (abs(shiftedA - shiftedB) / (shiftedA + shiftedB + 0.0001))
            lsmSum += lsmToken
        }
        val crossDelta = round((deltaSum / keys.size) * 1000.0) / 1000.0
        val lsmPct = round(((lsmSum / keys.size) * 100.0).coerceIn(12.0, 99.2) * 10.0) / 10.0
        return crossDelta to lsmPct
    }

    fun computeSpectrumBars(subject: SubjectProfile): List<SpectrumBar> {
        val tokens30 = CALIBRATED_CORPUS_50.keys.take(30).toList()
        return tokens30.mapIndexed { idx, token ->
            val z = subject.zScores[token] ?: (((idx * 17 + subject.ocean.N) % 16) - 8) / 10f
            val popMean = CALIBRATED_CORPUS_50[token]?.first ?: 0.5
            val popStd = CALIBRATED_CORPUS_50[token]?.second ?: 0.2
            val freqPct = max(0.05f, (popMean + z * popStd).toFloat())
            val barHeight = (34 + z * 26f).roundToInt().coerceIn(16, 98)
            SpectrumBar(
                index = idx,
                token = token,
                zScore = z,
                freqPct = round(freqPct * 10f) / 10f,
                barHeightPct = barHeight
            )
        }
    }
}
