package com.example.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.json.JSONArray
import org.json.JSONObject

data class OceanScores(
    val O: Int = 72,
    val C: Int = 80,
    val E: Int = 62,
    val A: Int = 45,
    val N: Int = 55
) {
    fun toMap(): Map<String, Int> = linkedMapOf(
        "Openness (O)" to O,
        "Conscientiousness (C)" to C,
        "Extraversion (E)" to E,
        "Agreeableness (A)" to A,
        "Neuroticism (N)" to N
    )

    fun toJson(): String = JSONObject().apply {
        put("O", O)
        put("C", C)
        put("E", E)
        put("A", A)
        put("N", N)
    }.toString()

    companion object {
        fun fromJson(raw: String): OceanScores = try {
            val o = JSONObject(raw)
            OceanScores(
                O = o.optInt("O", 72),
                C = o.optInt("C", 80),
                E = o.optInt("E", 62),
                A = o.optInt("A", 45),
                N = o.optInt("N", 55)
            )
        } catch (_: Exception) {
            OceanScores()
        }
    }
}

data class DarkTriadScores(
    val machiavellianism: Int = 64,
    val narcissism: Int = 58,
    val psychopathy: Int = 42
) {
    fun toJson(): String = JSONObject().apply {
        put("machiavellianism", machiavellianism)
        put("narcissism", narcissism)
        put("psychopathy", psychopathy)
    }.toString()

    companion object {
        fun fromJson(raw: String): DarkTriadScores = try {
            val o = JSONObject(raw)
            DarkTriadScores(
                machiavellianism = o.optInt("machiavellianism", 64),
                narcissism = o.optInt("narcissism", 58),
                psychopathy = o.optInt("psychopathy", 42)
            )
        } catch (_: Exception) {
            DarkTriadScores()
        }
    }
}

data class ThreatMetrics(
    val pathwayToViolence: Int = 68,
    val memeticVirality: Int = 84,
    val contextCollapse: Int = 75,
    val networkCentrality: Int = 91
) {
    val compositeScore: Int
        get() = (pathwayToViolence + memeticVirality + contextCollapse + networkCentrality) / 4

    fun toJson(): String = JSONObject().apply {
        put("pathwayToViolence", pathwayToViolence)
        put("memeticVirality", memeticVirality)
        put("contextCollapse", contextCollapse)
        put("networkCentrality", networkCentrality)
    }.toString()

    companion object {
        fun fromJson(raw: String): ThreatMetrics = try {
            val o = JSONObject(raw)
            ThreatMetrics(
                pathwayToViolence = o.optInt("pathwayToViolence", 68),
                memeticVirality = o.optInt("memeticVirality", 84),
                contextCollapse = o.optInt("contextCollapse", 75),
                networkCentrality = o.optInt("networkCentrality", 91)
            )
        } catch (_: Exception) {
            ThreatMetrics()
        }
    }
}

/**
 * Individual timestamped statement, commit message, issue comment, model card, or social post
 * scraped from GitHub, Hugging Face, Reddit, YouTube, TikTok, Instagram, or custom URLs.
 */
data class ScrapedArtifactItem(
    val id: String,
    val platform: String,
    val timestampIso: String,
    val utcHour: Int,
    val verbatimText: String,
    val contextLabel: String,
    val lexicalMarkers: List<String> = emptyList(),
    val sentimentScore: Int = 50
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("platform", platform)
        put("timestampIso", timestampIso)
        put("utcHour", utcHour)
        put("verbatimText", verbatimText)
        put("contextLabel", contextLabel)
        put("lexicalMarkers", JSONArray(lexicalMarkers))
        put("sentimentScore", sentimentScore)
    }

    fun toRoomEntity(
        subjectId: String,
        subjectHandle: String,
        provSha256: String,
        rawDomSignature: String = "AST_EXTRACTED_NODE"
    ): RawSocialMediaEntity = RawSocialMediaEntity(
        entityId = id,
        subjectId = subjectId,
        subjectHandle = subjectHandle,
        platform = platform,
        sourceUri = contextLabel,
        postedTimestampIso = timestampIso,
        utcHour = utcHour,
        rawPayloadText = verbatimText,
        rawDomSignature = rawDomSignature,
        lexicalMarkersCsv = lexicalMarkers.joinToString(","),
        sentimentScore = sentimentScore,
        provSha256 = provSha256,
        ingestedAtMillis = System.currentTimeMillis()
    )

    companion object {
        fun fromJsonObject(o: JSONObject): ScrapedArtifactItem {
            val arr = o.optJSONArray("lexicalMarkers") ?: JSONArray()
            val markers = mutableListOf<String>()
            for (i in 0 until arr.length()) markers.add(arr.optString(i))
            return ScrapedArtifactItem(
                id = o.optString("id", "art-1"),
                platform = o.optString("platform", "GITHUB_COMMIT"),
                timestampIso = o.optString("timestampIso", "2026-11-08T14:20:00Z"),
                utcHour = o.optInt("utcHour", 14),
                verbatimText = o.optString("verbatimText", ""),
                contextLabel = o.optString("contextLabel", ""),
                lexicalMarkers = markers,
                sentimentScore = o.optInt("sentimentScore", 50)
            )
        }
    }
}

/**
 * Pennebaker LIWC-calibrated & Psycholinguistic Deep Personality Dossier
 */
data class LinguisticPersonalityProfile(
    val archetypeTitle: String = "High-Autonomy Systems Architect",
    val archetypeSummary: String = "Driven by structural mastery, empirical proof, and intellectual sovereignty.",
    val analyticalThinkingIndex: Int = 88,
    val cloutDominanceIndex: Int = 82,
    val authenticityIndex: Int = 61,
    val emotionalToneIndex: Int = 44,
    val cognitiveComplexityIndex: Int = 86,
    val iPronounRatePct: Float = 1.4f,
    val wePronounRatePct: Float = 0.8f,
    val youPronounRatePct: Float = 0.9f,
    val certaintyMarkerPct: Float = 2.6f,
    val hedgingMarkerPct: Float = 0.5f,
    val whatMakesThemTick: List<String> = emptyList(),
    val egoCravingsAndRewards: List<String> = emptyList(),
    val fearAndFrictionTriggers: List<String> = emptyList(),
    val howToTalkToThem: List<String> = emptyList(),
    val exactPhrasesToUse: List<String> = emptyList(),
    val phrasesToAvoid: List<String> = emptyList(),
    val persuasionAndLeverageHooks: List<String> = emptyList(),
    val cognitiveBiasesToExploit: List<String> = emptyList(),
    val negotiationTactics: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("archetypeTitle", archetypeTitle)
        put("archetypeSummary", archetypeSummary)
        put("analyticalThinkingIndex", analyticalThinkingIndex)
        put("cloutDominanceIndex", cloutDominanceIndex)
        put("authenticityIndex", authenticityIndex)
        put("emotionalToneIndex", emotionalToneIndex)
        put("cognitiveComplexityIndex", cognitiveComplexityIndex)
        put("iPronounRatePct", iPronounRatePct.toDouble())
        put("wePronounRatePct", wePronounRatePct.toDouble())
        put("youPronounRatePct", youPronounRatePct.toDouble())
        put("certaintyMarkerPct", certaintyMarkerPct.toDouble())
        put("hedgingMarkerPct", hedgingMarkerPct.toDouble())
        put("whatMakesThemTick", JSONArray(whatMakesThemTick))
        put("egoCravingsAndRewards", JSONArray(egoCravingsAndRewards))
        put("fearAndFrictionTriggers", JSONArray(fearAndFrictionTriggers))
        put("howToTalkToThem", JSONArray(howToTalkToThem))
        put("exactPhrasesToUse", JSONArray(exactPhrasesToUse))
        put("phrasesToAvoid", JSONArray(phrasesToAvoid))
        put("persuasionAndLeverageHooks", JSONArray(persuasionAndLeverageHooks))
        put("cognitiveBiasesToExploit", JSONArray(cognitiveBiasesToExploit))
        put("negotiationTactics", JSONArray(negotiationTactics))
    }

    fun toRoomEntity(
        subjectId: String,
        subjectHandle: String,
        burrowsDelta: Double,
        styloMatch: Double,
        mattrScore: Double,
        ocean: OceanScores,
        darkTriad: DarkTriadScores,
        evaluatedArtifactCount: Int
    ): LinguisticPersonalityProfileEntity = LinguisticPersonalityProfileEntity(
        profileId = "lpp-$subjectId",
        subjectId = subjectId,
        subjectHandle = subjectHandle,
        archetypeTitle = archetypeTitle,
        archetypeSummary = archetypeSummary,
        analyticalThinkingIndex = analyticalThinkingIndex,
        cloutDominanceIndex = cloutDominanceIndex,
        authenticityIndex = authenticityIndex,
        emotionalToneIndex = emotionalToneIndex,
        cognitiveComplexityIndex = cognitiveComplexityIndex,
        iPronounRatePct = iPronounRatePct,
        wePronounRatePct = wePronounRatePct,
        youPronounRatePct = youPronounRatePct,
        certaintyMarkerPct = certaintyMarkerPct,
        hedgingMarkerPct = hedgingMarkerPct,
        burrowsDelta = burrowsDelta,
        styloMatchPct = styloMatch,
        mattrScore = mattrScore,
        openness = ocean.O,
        conscientiousness = ocean.C,
        extraversion = ocean.E,
        agreeableness = ocean.A,
        neuroticism = ocean.N,
        machiavellianism = darkTriad.machiavellianism,
        narcissism = darkTriad.narcissism,
        psychopathy = darkTriad.psychopathy,
        whatMakesThemTickJson = JSONArray(whatMakesThemTick).toString(),
        egoCravingsJson = JSONArray(egoCravingsAndRewards).toString(),
        fearTriggersJson = JSONArray(fearAndFrictionTriggers).toString(),
        howToTalkJson = JSONArray(howToTalkToThem).toString(),
        exactPhrasesToUseJson = JSONArray(exactPhrasesToUse).toString(),
        phrasesToAvoidJson = JSONArray(phrasesToAvoid).toString(),
        persuasionHooksJson = JSONArray(persuasionAndLeverageHooks).toString(),
        cognitiveBiasesJson = JSONArray(cognitiveBiasesToExploit).toString(),
        negotiationTacticsJson = JSONArray(negotiationTactics).toString(),
        evaluatedArtifactCount = evaluatedArtifactCount,
        updatedAtMillis = System.currentTimeMillis()
    )

    companion object {
        fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until length()) {
                val s = optString(i)
                if (s.isNotBlank()) list.add(s)
            }
            return list
        }

        fun parseJsonStringArray(raw: String): List<String> = try {
            JSONArray(raw).toStringList()
        } catch (_: Exception) {
            emptyList()
        }

        fun fromJson(raw: String): LinguisticPersonalityProfile = try {
            val o = JSONObject(raw)
            LinguisticPersonalityProfile(
                archetypeTitle = o.optString("archetypeTitle", "High-Autonomy Systems Architect"),
                archetypeSummary = o.optString("archetypeSummary", ""),
                analyticalThinkingIndex = o.optInt("analyticalThinkingIndex", 88),
                cloutDominanceIndex = o.optInt("cloutDominanceIndex", 82),
                authenticityIndex = o.optInt("authenticityIndex", 61),
                emotionalToneIndex = o.optInt("emotionalToneIndex", 44),
                cognitiveComplexityIndex = o.optInt("cognitiveComplexityIndex", 86),
                iPronounRatePct = o.optDouble("iPronounRatePct", 1.4).toFloat(),
                wePronounRatePct = o.optDouble("wePronounRatePct", 0.8).toFloat(),
                youPronounRatePct = o.optDouble("youPronounRatePct", 0.9).toFloat(),
                certaintyMarkerPct = o.optDouble("certaintyMarkerPct", 2.6).toFloat(),
                hedgingMarkerPct = o.optDouble("hedgingMarkerPct", 0.5).toFloat(),
                whatMakesThemTick = o.optJSONArray("whatMakesThemTick").toStringList(),
                egoCravingsAndRewards = o.optJSONArray("egoCravingsAndRewards").toStringList(),
                fearAndFrictionTriggers = o.optJSONArray("fearAndFrictionTriggers").toStringList(),
                howToTalkToThem = o.optJSONArray("howToTalkToThem").toStringList(),
                exactPhrasesToUse = o.optJSONArray("exactPhrasesToUse").toStringList(),
                phrasesToAvoid = o.optJSONArray("phrasesToAvoid").toStringList(),
                persuasionAndLeverageHooks = o.optJSONArray("persuasionAndLeverageHooks").toStringList(),
                cognitiveBiasesToExploit = o.optJSONArray("cognitiveBiasesToExploit").toStringList(),
                negotiationTactics = o.optJSONArray("negotiationTactics").toStringList()
            )
        } catch (_: Exception) {
            LinguisticPersonalityProfile()
        }
    }
}

data class GithubRepoItem(
    val name: String,
    val fullName: String,
    val description: String,
    val language: String,
    val stars: Int,
    val forks: Int,
    val url: String,
    val updatedAtIso: String = "2026-11-01T12:00:00Z",
    val topics: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("fullName", fullName)
        put("description", description)
        put("language", language)
        put("stars", stars)
        put("forks", forks)
        put("url", url)
        put("updatedAtIso", updatedAtIso)
        put("topics", JSONArray(topics))
    }

    companion object {
        fun fromJsonObject(o: JSONObject): GithubRepoItem {
            val topicsArr = o.optJSONArray("topics") ?: JSONArray()
            val topics = mutableListOf<String>()
            for (i in 0 until topicsArr.length()) {
                topics.add(topicsArr.optString(i))
            }
            return GithubRepoItem(
                name = o.optString("name", "repo"),
                fullName = o.optString("fullName", "user/repo"),
                description = o.optString("description", "No description"),
                language = o.optString("language", "Python"),
                stars = o.optInt("stars", 0),
                forks = o.optInt("forks", 0),
                url = o.optString("url", ""),
                updatedAtIso = o.optString("updatedAtIso", "2026-11-01T12:00:00Z"),
                topics = topics
            )
        }
    }
}

data class HuggingFaceAsset(
    val id: String,
    val kind: String,
    val pipelineTag: String,
    val downloads: Int,
    val likes: Int,
    val summary: String,
    val lastModifiedIso: String = "2026-10-28T09:30:00Z",
    val tags: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind)
        put("pipelineTag", pipelineTag)
        put("downloads", downloads)
        put("likes", likes)
        put("summary", summary)
        put("lastModifiedIso", lastModifiedIso)
        put("tags", JSONArray(tags))
    }

    companion object {
        fun fromJsonObject(o: JSONObject): HuggingFaceAsset {
            val arr = o.optJSONArray("tags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                tags.add(arr.optString(i))
            }
            return HuggingFaceAsset(
                id = o.optString("id", "hf/model"),
                kind = o.optString("kind", "MODEL"),
                pipelineTag = o.optString("pipelineTag", "text-classification"),
                downloads = o.optInt("downloads", 0),
                likes = o.optInt("likes", 0),
                summary = o.optString("summary", ""),
                lastModifiedIso = o.optString("lastModifiedIso", "2026-10-28T09:30:00Z"),
                tags = tags
            )
        }
    }
}

data class ActionableInsight(
    val id: String,
    val category: String,
    val title: String,
    val priority: String,
    val summary: String,
    val recommendation: String,
    val confidencePct: Int
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("category", category)
        put("title", title)
        put("priority", priority)
        put("summary", summary)
        put("recommendation", recommendation)
        put("confidencePct", confidencePct)
    }

    companion object {
        fun fromJsonObject(o: JSONObject): ActionableInsight = ActionableInsight(
            id = o.optString("id", "ins-1"),
            category = o.optString("category", "WHAT_MAKES_THEM_TICK"),
            title = o.optString("title", "Insight"),
            priority = o.optString("priority", "HIGH"),
            summary = o.optString("summary", ""),
            recommendation = o.optString("recommendation", ""),
            confidencePct = o.optInt("confidencePct", 90)
        )
    }
}

enum class NodeCategory(val label: String) {
    IDENTITY("IDENTITY CORE"),
    GITHUB_REPO("GITHUB REPO"),
    HF_MODEL("HUGGING FACE"),
    PSYCH_TRAIT("PSYCHOMETRIC"),
    GEO_CHRONO("CHRONO / GEO"),
    WALLED_GARDEN("SOCIAL ALIAS"),
    OSINT_TOOL("LEVERAGE / INSIGHT")
}

data class MindMapNode(
    val id: String,
    val label: String,
    val subtitle: String,
    val category: NodeCategory,
    val weight: Float,
    val x3d: Float,
    val y3d: Float,
    val z3d: Float,
    val umapX: Float,
    val umapY: Float,
    val umapZ: Float,
    val details: String,
    val subjectId: String? = null
)

data class MindMapEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val predicate: String,
    val confidence: Float
)

data class SpectrumBar(
    val index: Int,
    val token: String,
    val zScore: Float,
    val freqPct: Float,
    val barHeightPct: Int
)

data class WalledGardenSpec(
    val key: String,
    val name: String,
    val sampleUrl: String,
    val deprecatedSelector: String,
    val mutatedDom: String,
    val healedSelector: String,
    val strategy: String,
    val pythonSnippet: String
)

data class DomDriftState(
    val active: Boolean = false,
    val platform: String = "",
    val platformKey: String = "",
    val deprecatedSelector: String = "",
    val mutatedDom: String = "",
    val proposedSelector: String = "",
    val strategy: String = "",
    val pythonSnippet: String = "",
    val healing: Boolean = false,
    val healStep: Int = 0,
    val healProgress: Int = 0,
    val healStatusText: String = ""
)

data class TelemetryLog(
    val ts: String,
    val category: String,
    val message: String
)

enum class ScraperPlatformMode(val label: String) {
    ALL("ALL VECTORS"),
    GITHUB("GITHUB API"),
    HUGGINGFACE("HUGGING FACE"),
    DIRECT_URL("DIRECT URL / HTML")
}

data class HttpEndpointProbe(
    val id: String,
    val url: String,
    val platform: String,
    val httpStatusCode: Int,
    val latencyMs: Long,
    val payloadBytes: Int,
    val extractedItemsCount: Int,
    val serverHeader: String,
    val timestampHeader: String,
    val statusBadge: String
)

data class AiModelOption(
    val modelId: String,
    val displayName: String,
    val tierBadge: String,
    val summary: String
)

enum class AiProviderEngine(
    val label: String,
    val defaultModelId: String,
    val secretKeyName: String,
    val presetModels: List<AiModelOption>
) {
    GEMINI(
        label = "GOOGLE GEMINI",
        defaultModelId = "gemini-3.8-flash",
        secretKeyName = "GEMINI_API_KEY",
        presetModels = listOf(
            AiModelOption(
                modelId = "gemini-3.8-flash",
                displayName = "Gemini 3.8 Flash",
                tierBadge = "DEFAULT • FAST",
                summary = "High-speed multimodal OSINT & LIWC profiling (Firebase AI + Direct Key)"
            ),
            AiModelOption(
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                tierBadge = "DEEP REASONING",
                summary = "Maximum reasoning depth for complex stylometry & negotiation strategy"
            ),
            AiModelOption(
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                tierBadge = "BALANCED",
                summary = "Low-latency thinking model for rapid behavioural synthesis"
            ),
            AiModelOption(
                modelId = "gemini-2.5-flash-lite",
                displayName = "Gemini 2.5 Flash-Lite",
                tierBadge = "ULTRA-LIGHT",
                summary = "High-throughput batch artifact extraction"
            ),
            AiModelOption(
                modelId = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                tierBadge = "WORKHORSE",
                summary = "Low-latency multimodal analysis and structured JSON extraction"
            ),
            AiModelOption(
                modelId = "gemini-1.5-pro",
                displayName = "Gemini 1.5 Pro",
                tierBadge = "2M CONTEXT",
                summary = "Long-context corpus cross-examination across full repo histories"
            )
        )
    ),
    CLAUDE(
        label = "ANTHROPIC CLAUDE",
        defaultModelId = "claude-3-7-sonnet-20250219",
        secretKeyName = "ANTHROPIC_API_KEY",
        presetModels = listOf(
            AiModelOption(
                modelId = "claude-3-7-sonnet-20250219",
                displayName = "Claude 3.7 Sonnet",
                tierBadge = "DEFAULT • HYBRID",
                summary = "Extended reasoning & nuanced psychological / LIWC decomposition"
            ),
            AiModelOption(
                modelId = "claude-sonnet-4-20250514",
                displayName = "Claude Sonnet 4",
                tierBadge = "FRONTIER SONNET",
                summary = "Next-generation high-precision behavioural & code analysis"
            ),
            AiModelOption(
                modelId = "claude-opus-4-20250514",
                displayName = "Claude Opus 4",
                tierBadge = "FRONTIER OPUS",
                summary = "Deepest executive profiling, counter-manipulation & strategy"
            ),
            AiModelOption(
                modelId = "claude-3-5-sonnet-20241022",
                displayName = "Claude 3.5 Sonnet",
                tierBadge = "PROVEN v2",
                summary = "Industry standard for XML-structured dossier & AST analysis"
            ),
            AiModelOption(
                modelId = "claude-3-5-haiku-20241022",
                displayName = "Claude 3.5 Haiku",
                tierBadge = "FAST TACTICAL",
                summary = "Rapid rapport openers and real-time conversation scripts"
            ),
            AiModelOption(
                modelId = "claude-3-opus-20240229",
                displayName = "Claude 3 Opus",
                tierBadge = "DEEP ARCHIVE",
                summary = "Classic high-nuance literary & stylometric author attribution"
            )
        )
    ),
    PERPLEXITY(
        label = "PERPLEXITY SONAR",
        defaultModelId = "sonar-pro",
        secretKeyName = "PERPLEXITY_API_KEY",
        presetModels = listOf(
            AiModelOption(
                modelId = "sonar-pro",
                displayName = "Sonar Pro",
                tierBadge = "DEFAULT • WEB",
                summary = "Deep web-grounded OSINT corroboration with dense source citations"
            ),
            AiModelOption(
                modelId = "sonar-reasoning-pro",
                displayName = "Sonar Reasoning Pro",
                tierBadge = "CoT + CITATIONS",
                summary = "Chain-of-thought reasoning combined with live multi-query web search"
            ),
            AiModelOption(
                modelId = "sonar-deep-research",
                displayName = "Sonar Deep Research",
                tierBadge = "EXHAUSTIVE OSINT",
                summary = "Multi-step autonomous web investigation across public footprints"
            ),
            AiModelOption(
                modelId = "sonar-reasoning",
                displayName = "Sonar Reasoning",
                tierBadge = "FAST CoT",
                summary = "Fast reasoning with live web grounding"
            ),
            AiModelOption(
                modelId = "sonar",
                displayName = "Sonar Standard",
                tierBadge = "LIGHTWEIGHT",
                summary = "Rapid fact-checking and handle verification"
            ),
            AiModelOption(
                modelId = "r1-1776",
                displayName = "R1-1776 (Offline CoT)",
                tierBadge = "UNCENSORED CoT",
                summary = "Perplexity's post-trained DeepSeek R1 reasoning model without web filter"
            )
        )
    );

    val modelId: String
        get() = defaultModelId
}

enum class DataExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    AGENT_HANDOFF("AGENT HANDOFF BUNDLE (.JSON)", "json", "application/json"),
    PKZIP_STIX("PKZIP + STIX 2.1 (.ZIP)", "zip", "application/zip"),
    ROOM_JSON("ROOM DOSSIER (.JSON)", "json", "application/json"),
    DISCOURSE_CSV("TIMESTAMPED CSV (.CSV)", "csv", "text/csv"),
    CLAUDE_XML("CLAUDE XML BUNDLE (.XML)", "xml", "application/xml"),
    GEMINI_PROMPT("GEMINI CONTEXT (.MD)", "md", "text/markdown"),
    PERPLEXITY_QUERY("PERPLEXITY OSINT (.TXT)", "txt", "text/plain")
}

sealed interface AiSynthesisState {
    data object Idle : AiSynthesisState
    data class Loading(
        val provider: AiProviderEngine,
        val modelId: String,
        val statusText: String
    ) : AiSynthesisState
    data class Streaming(
        val provider: AiProviderEngine,
        val modelId: String,
        val partialText: String
    ) : AiSynthesisState
    data class Success(
        val provider: AiProviderEngine,
        val modelId: String,
        val responseText: String,
        val citations: List<String> = emptyList(),
        val authSourceLabel: String = ""
    ) : AiSynthesisState
    data class Error(
        val provider: AiProviderEngine,
        val modelId: String,
        val errorMessage: String
    ) : AiSynthesisState
}

// ============================================================================
// NORMALIZED ROOM DATABASE ENTITIES & RELATIONAL GRAPH SCHEMAS
// ============================================================================

/**
 * Table 1: Core Subject Identity Dossiers (`subject_profiles`)
 */
@Entity(
    tableName = "subject_profiles",
    indices = [
        Index(value = ["handle"]),
        Index(value = ["updatedAt"])
    ]
)
data class SubjectProfileEntity(
    @PrimaryKey val id: String,
    val stixId: String,
    val handle: String,
    val codeId: String,
    val alias: String,
    val cluster: String,
    val loc: String,
    val lat: Double,
    val lon: Double,
    val utcOffset: Int,
    val troughWindowLabel: String,
    val burrowsDelta: Double,
    val styloMatch: Double,
    val mattrScore: Double,
    val oceanJson: String,
    val darkTriadJson: String,
    val threatMetricsJson: String,
    val zScoresJson: String,
    val hourlyBinsJson: String,
    val frontStageQuote: String,
    val backStageQuote: String,
    val githubReposJson: String,
    val huggingFaceModelsJson: String,
    val insightsJson: String,
    val artifactsJson: String,
    val linguisticProfileJson: String,
    val healedPatchId: String,
    val provSha256: String,
    val updatedAt: Long
) {
    fun toDomain(
        relationalArtifacts: List<RawSocialMediaEntity>? = null,
        relationalProfile: LinguisticPersonalityProfileEntity? = null
    ): SubjectProfile {
        val zMap = linkedMapOf<String, Float>()
        try {
            val zObj = JSONObject(zScoresJson)
            val keys = zObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                zMap[k] = zObj.optDouble(k, 0.0).toFloat()
            }
        } catch (_: Exception) {}

        val bins = IntArray(24)
        try {
            val arr = JSONArray(hourlyBinsJson)
            for (i in 0 until minOf(24, arr.length())) {
                bins[i] = arr.optInt(i, 0)
            }
        } catch (_: Exception) {}

        val repos = mutableListOf<GithubRepoItem>()
        try {
            val arr = JSONArray(githubReposJson)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { repos.add(GithubRepoItem.fromJsonObject(it)) }
            }
        } catch (_: Exception) {}

        val hfAssets = mutableListOf<HuggingFaceAsset>()
        try {
            val arr = JSONArray(huggingFaceModelsJson)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { hfAssets.add(HuggingFaceAsset.fromJsonObject(it)) }
            }
        } catch (_: Exception) {}

        val insights = mutableListOf<ActionableInsight>()
        try {
            val arr = JSONArray(insightsJson)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { insights.add(ActionableInsight.fromJsonObject(it)) }
            }
        } catch (_: Exception) {}

        val artifacts = if (!relationalArtifacts.isNullOrEmpty()) {
            relationalArtifacts.sortedByDescending { it.postedTimestampIso }.map { it.toDomainArtifact() }
        } else {
            val list = mutableListOf<ScrapedArtifactItem>()
            try {
                val arr = JSONArray(artifactsJson)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(ScrapedArtifactItem.fromJsonObject(it)) }
                }
            } catch (_: Exception) {}
            list
        }

        val lingProfile = relationalProfile?.toDomainProfile()
            ?: LinguisticPersonalityProfile.fromJson(linguisticProfileJson)

        return SubjectProfile(
            id = id,
            stixId = stixId,
            handle = handle,
            codeId = codeId,
            alias = alias,
            cluster = cluster,
            loc = loc,
            lat = lat,
            lon = lon,
            utcOffset = utcOffset,
            troughWindowLabel = troughWindowLabel,
            burrowsDelta = burrowsDelta,
            styloMatch = styloMatch,
            mattrScore = mattrScore,
            ocean = OceanScores.fromJson(oceanJson),
            darkTriad = DarkTriadScores.fromJson(darkTriadJson),
            threatMetrics = ThreatMetrics.fromJson(threatMetricsJson),
            zScores = zMap,
            hourlyBins = bins.toList(),
            frontStageQuote = frontStageQuote,
            backStageQuote = backStageQuote,
            githubRepos = repos,
            huggingFaceAssets = hfAssets,
            actionableInsights = insights,
            scrapedArtifacts = artifacts,
            linguisticProfile = lingProfile,
            healedPatchId = healedPatchId,
            provSha256 = provSha256,
            updatedAt = updatedAt
        )
    }
}

/**
 * Table 2: Raw Social Media & OSINT Scraped Entities (`raw_social_media_entities`)
 * Stores every verbatim statement, commit, comment, model card, or post with its exact timestamp
 * and foreign key link to `subject_profiles`.
 */
@Entity(
    tableName = "raw_social_media_entities",
    foreignKeys = [
        ForeignKey(
            entity = SubjectProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["subjectHandle"]),
        Index(value = ["platform"]),
        Index(value = ["postedTimestampIso"])
    ]
)
data class RawSocialMediaEntity(
    @PrimaryKey val entityId: String,
    val subjectId: String,
    val subjectHandle: String,
    val platform: String,
    val sourceUri: String,
    val postedTimestampIso: String,
    val utcHour: Int,
    val rawPayloadText: String,
    val rawDomSignature: String,
    val lexicalMarkersCsv: String,
    val sentimentScore: Int,
    val provSha256: String,
    val ingestedAtMillis: Long
) {
    fun toDomainArtifact(): ScrapedArtifactItem = ScrapedArtifactItem(
        id = entityId,
        platform = platform,
        timestampIso = postedTimestampIso,
        utcHour = utcHour,
        verbatimText = rawPayloadText,
        contextLabel = sourceUri,
        lexicalMarkers = lexicalMarkersCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
        sentimentScore = sentimentScore
    )
}

/**
 * Table 3: Parsed Linguistic Personality Profiles (`linguistic_personality_profiles`)
 * Stores the scientific LIWC metrics, Burrows' Delta, Big Five OCEAN, Dark Triad,
 * What Makes Them Tick, How to Talk to Them, and Persuasion/Leverage hooks per subject.
 */
@Entity(
    tableName = "linguistic_personality_profiles",
    foreignKeys = [
        ForeignKey(
            entity = SubjectProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"], unique = true),
        Index(value = ["subjectHandle"])
    ]
)
data class LinguisticPersonalityProfileEntity(
    @PrimaryKey val profileId: String,
    val subjectId: String,
    val subjectHandle: String,
    val archetypeTitle: String,
    val archetypeSummary: String,
    val analyticalThinkingIndex: Int,
    val cloutDominanceIndex: Int,
    val authenticityIndex: Int,
    val emotionalToneIndex: Int,
    val cognitiveComplexityIndex: Int,
    val iPronounRatePct: Float,
    val wePronounRatePct: Float,
    val youPronounRatePct: Float,
    val certaintyMarkerPct: Float,
    val hedgingMarkerPct: Float,
    val burrowsDelta: Double,
    val styloMatchPct: Double,
    val mattrScore: Double,
    val openness: Int,
    val conscientiousness: Int,
    val extraversion: Int,
    val agreeableness: Int,
    val neuroticism: Int,
    val machiavellianism: Int,
    val narcissism: Int,
    val psychopathy: Int,
    val whatMakesThemTickJson: String,
    val egoCravingsJson: String,
    val fearTriggersJson: String,
    val howToTalkJson: String,
    val exactPhrasesToUseJson: String,
    val phrasesToAvoidJson: String,
    val persuasionHooksJson: String,
    val cognitiveBiasesJson: String,
    val negotiationTacticsJson: String,
    val evaluatedArtifactCount: Int,
    val updatedAtMillis: Long
) {
    fun toDomainProfile(): LinguisticPersonalityProfile = LinguisticPersonalityProfile(
        archetypeTitle = archetypeTitle,
        archetypeSummary = archetypeSummary,
        analyticalThinkingIndex = analyticalThinkingIndex,
        cloutDominanceIndex = cloutDominanceIndex,
        authenticityIndex = authenticityIndex,
        emotionalToneIndex = emotionalToneIndex,
        cognitiveComplexityIndex = cognitiveComplexityIndex,
        iPronounRatePct = iPronounRatePct,
        wePronounRatePct = wePronounRatePct,
        youPronounRatePct = youPronounRatePct,
        certaintyMarkerPct = certaintyMarkerPct,
        hedgingMarkerPct = hedgingMarkerPct,
        whatMakesThemTick = LinguisticPersonalityProfile.parseJsonStringArray(whatMakesThemTickJson),
        egoCravingsAndRewards = LinguisticPersonalityProfile.parseJsonStringArray(egoCravingsJson),
        fearAndFrictionTriggers = LinguisticPersonalityProfile.parseJsonStringArray(fearTriggersJson),
        howToTalkToThem = LinguisticPersonalityProfile.parseJsonStringArray(howToTalkJson),
        exactPhrasesToUse = LinguisticPersonalityProfile.parseJsonStringArray(exactPhrasesToUseJson),
        phrasesToAvoid = LinguisticPersonalityProfile.parseJsonStringArray(phrasesToAvoidJson),
        persuasionAndLeverageHooks = LinguisticPersonalityProfile.parseJsonStringArray(persuasionHooksJson),
        cognitiveBiasesToExploit = LinguisticPersonalityProfile.parseJsonStringArray(cognitiveBiasesJson),
        negotiationTactics = LinguisticPersonalityProfile.parseJsonStringArray(negotiationTacticsJson)
    )
}

/**
 * Table 4: Hierarchical Mind-Map Relationships (`mind_map_hierarchy_edges`)
 * Stores the parent-child hierarchical brain/mind-map nodes, 3D/UMAP coordinates,
 * depth level (0 = Root Identity, 1 = Cluster Branch, 2 = Leaf Node), and edge confidence.
 */
@Entity(
    tableName = "mind_map_hierarchy_edges",
    foreignKeys = [
        ForeignKey(
            entity = SubjectProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["rootSubjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rootSubjectId"]),
        Index(value = ["parentNodeId"]),
        Index(value = ["childNodeId"]),
        Index(value = ["hierarchyDepth"])
    ]
)
data class MindMapHierarchyEntity(
    @PrimaryKey val hierarchyId: String,
    val rootSubjectId: String,
    val parentNodeId: String,
    val childNodeId: String,
    val childLabel: String,
    val childSubtitle: String,
    val nodeCategory: String,
    val hierarchyDepth: Int, // 0 = Root Core, 1 = Primary Cluster Branch, 2 = Leaf Artifact/Trait
    val relationshipPredicate: String,
    val confidenceWeight: Float,
    val nodeWeight: Float,
    val x3d: Float,
    val y3d: Float,
    val z3d: Float,
    val umapX: Float,
    val umapY: Float,
    val umapZ: Float,
    val nodeDetails: String,
    val linkedSubjectId: String? = null,
    val provSha256: String,
    val updatedAtMillis: Long
) {
    fun toDomainNode(): MindMapNode {
        val cat = try {
            NodeCategory.valueOf(nodeCategory)
        } catch (_: Exception) {
            NodeCategory.OSINT_TOOL
        }
        return MindMapNode(
            id = childNodeId,
            label = childLabel,
            subtitle = childSubtitle,
            category = cat,
            weight = nodeWeight,
            x3d = x3d,
            y3d = y3d,
            z3d = z3d,
            umapX = umapX,
            umapY = umapY,
            umapZ = umapZ,
            details = nodeDetails,
            subjectId = linkedSubjectId
        )
    }

    fun toDomainEdge(): MindMapEdge = MindMapEdge(
        id = "edge-$hierarchyId",
        sourceId = parentNodeId,
        targetId = childNodeId,
        predicate = relationshipPredicate,
        confidence = confidenceWeight
    )
}

/**
 * Room `@Transaction` Relational POJO joining a Subject Profile with its
 * Raw Social Media Entities, Parsed Linguistic Personality Profile, and Hierarchical Mind Map.
 */
data class SubjectIntelligenceDossierRelation(
    @Embedded val subject: SubjectProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "subjectId"
    )
    val rawSocialEntities: List<RawSocialMediaEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subjectId"
    )
    val linguisticProfile: LinguisticPersonalityProfileEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "rootSubjectId"
    )
    val mindMapHierarchy: List<MindMapHierarchyEntity>
)

data class SubjectProfile(
    val id: String,
    val stixId: String,
    val handle: String,
    val codeId: String,
    val alias: String,
    val cluster: String,
    val loc: String,
    val lat: Double,
    val lon: Double,
    val utcOffset: Int,
    val troughWindowLabel: String,
    val burrowsDelta: Double,
    val styloMatch: Double,
    val mattrScore: Double,
    val ocean: OceanScores,
    val darkTriad: DarkTriadScores,
    val threatMetrics: ThreatMetrics,
    val zScores: Map<String, Float>,
    val hourlyBins: List<Int>,
    val frontStageQuote: String,
    val backStageQuote: String,
    val githubRepos: List<GithubRepoItem>,
    val huggingFaceAssets: List<HuggingFaceAsset>,
    val actionableInsights: List<ActionableInsight>,
    val scrapedArtifacts: List<ScrapedArtifactItem> = emptyList(),
    val linguisticProfile: LinguisticPersonalityProfile = LinguisticPersonalityProfile(),
    val healedPatchId: String,
    val provSha256: String,
    val updatedAt: Long
) {
    fun toEntity(): SubjectProfileEntity {
        val zObj = JSONObject()
        zScores.forEach { (k, v) -> zObj.put(k, v.toDouble()) }
        val binsArr = JSONArray()
        hourlyBins.forEach { binsArr.put(it) }
        val reposArr = JSONArray()
        githubRepos.forEach { reposArr.put(it.toJsonObject()) }
        val hfArr = JSONArray()
        huggingFaceAssets.forEach { hfArr.put(it.toJsonObject()) }
        val insArr = JSONArray()
        actionableInsights.forEach { insArr.put(it.toJsonObject()) }
        val artArr = JSONArray()
        scrapedArtifacts.forEach { artArr.put(it.toJsonObject()) }

        return SubjectProfileEntity(
            id = id,
            stixId = stixId,
            handle = handle,
            codeId = codeId,
            alias = alias,
            cluster = cluster,
            loc = loc,
            lat = lat,
            lon = lon,
            utcOffset = utcOffset,
            troughWindowLabel = troughWindowLabel,
            burrowsDelta = burrowsDelta,
            styloMatch = styloMatch,
            mattrScore = mattrScore,
            oceanJson = ocean.toJson(),
            darkTriadJson = darkTriad.toJson(),
            threatMetricsJson = threatMetrics.toJson(),
            zScoresJson = zObj.toString(),
            hourlyBinsJson = binsArr.toString(),
            frontStageQuote = frontStageQuote,
            backStageQuote = backStageQuote,
            githubReposJson = reposArr.toString(),
            huggingFaceModelsJson = hfArr.toString(),
            insightsJson = insArr.toString(),
            artifactsJson = artArr.toString(),
            linguisticProfileJson = linguisticProfile.toJsonObject().toString(),
            healedPatchId = healedPatchId,
            provSha256 = provSha256,
            updatedAt = updatedAt
        )
    }

    fun toRawSocialMediaEntities(): List<RawSocialMediaEntity> =
        scrapedArtifacts.map { it.toRoomEntity(subjectId = id, subjectHandle = handle, provSha256 = provSha256) }

    fun toLinguisticProfileEntity(): LinguisticPersonalityProfileEntity =
        linguisticProfile.toRoomEntity(
            subjectId = id,
            subjectHandle = handle,
            burrowsDelta = burrowsDelta,
            styloMatch = styloMatch,
            mattrScore = mattrScore,
            ocean = ocean,
            darkTriad = darkTriad,
            evaluatedArtifactCount = scrapedArtifacts.size
        )
}

@Entity(tableName = "knowledge_triplets")
data class KnowledgeTripletEntity(
    @PrimaryKey val id: String,
    val subjectHandle: String,
    val predicate: String,
    val objectNode: String,
    val nodeCategory: String,
    val confidence: Float,
    val provSha256: String,
    val timestamp: Long
)

@Entity(tableName = "ast_patches")
data class AstPatchEntity(
    @PrimaryKey val platformKey: String,
    val patchId: String,
    val platformName: String,
    val deprecatedSelector: String,
    val mutatedDom: String,
    val healedSelector: String,
    val strategy: String,
    val pythonSnippet: String,
    val confidence: String,
    val timestamp: String
)

/**
 * Ported from https://github.com/dribblejerp-KlunkDunker/Sovereign-Aegis:
 * - `js/verdad-service.js` + `data/verdad_rules.json` (VERDAD Manipulation Risk & Pre-Share Gate)
 * - `data/disarm.json` + `data/disarm_blue.json` (DISARM Red/Blue Influence Kill-Chain)
 * - `js/profile.js` + `data/profile-guides.json` (Situational Pressure Watchpoints & Lens Assets)
 * - `DESIGN-aegis-reema-seam.md` (AEGIS P-256 `did:key` <-> Reema Post-Quantum ML-DSA-65 `KeyBinding`)
 */
data class SovereignVerdadAssessment(
    val manipulationRisk: Int,
    val recommendationGate: String,
    val gateRationale: String,
    val emotionalBaitScores: Map<String, Int>,
    val certaintyInflationHits: List<String>,
    val hedgingMarkerHits: List<String>,
    val passiveAttributionHits: List<String>,
    val detectedFallacies: List<String>,
    val disarmRedBlueMappings: List<String>,
    val situationalWatchpoints: List<String>,
    val prebunkInoculationScript: String,
    val reemaKeyBindingJson: String
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("manipulationRisk", manipulationRisk)
        put("recommendationGate", recommendationGate)
        put("gateRationale", gateRationale)
        put(
            "emotionalBaitScores",
            JSONObject().apply {
                emotionalBaitScores.forEach { (k, v) -> put(k, v) }
            }
        )
        put("certaintyInflationHits", JSONArray(certaintyInflationHits))
        put("hedgingMarkerHits", JSONArray(hedgingMarkerHits))
        put("passiveAttributionHits", JSONArray(passiveAttributionHits))
        put("detectedFallacies", JSONArray(detectedFallacies))
        put("disarmRedBlueMappings", JSONArray(disarmRedBlueMappings))
        put("situationalWatchpoints", JSONArray(situationalWatchpoints))
        put("prebunkInoculationScript", prebunkInoculationScript)
        try {
            put("reemaKeyBinding", JSONObject(reemaKeyBindingJson))
        } catch (_: Exception) {
            put("reemaKeyBindingRaw", reemaKeyBindingJson)
        }
    }
}


