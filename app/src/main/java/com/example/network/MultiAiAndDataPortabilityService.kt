package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.connectors.ManualImportConnector
import com.example.engine.ChronoGeoEngine
import com.example.engine.ExportEngine
import com.example.engine.ForensicCommon
import com.example.engine.MindMapEngine
import com.example.engine.PlaybookEngine
import com.example.engine.StylometryEngine
import com.example.engine.VerdadEngine
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MultiAiPortability"

/**
 * Unified Multi-AI Provider Bridge (Google Gemini via Firebase AI Logic, Anthropic Claude 3.7,
 * and Perplexity Sonar Pro) + Multi-Format OSINT Data Import/Export Engine (Room JSON, STIX 2.1,
 * CSV, Claude XML, Gemini Markdown, Perplexity Query, and PKZIP).
 */
class MultiAiAndDataPortabilityService {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    // 1. Lazy Firebase AI Logic default model for streaming/unary tactical briefings (gemini-3.8-flash)
    private val geminiBriefingModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    // 2. Lazy Firebase AI Logic structured-output model for parsing unstructured imported text into JSON
    private val structuredImportSchema = Schema.obj(
        properties = mapOf(
            "handle" to Schema.string(description = "Inferred subject handle or alias"),
            "extractedStatements" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "platform" to Schema.string(description = "Source platform or channel label"),
                        "timestampIso" to Schema.string(description = "ISO-8601 UTC timestamp e.g. 2026-11-08T14:30:00Z"),
                        "verbatimText" to Schema.string(description = "Exact quote, statement, or observation")
                    )
                ),
                description = "Extracted timestamped statements from the raw input"
            ),
            "executiveNote" to Schema.string(description = "Brief psycholinguistic synthesis of the imported text")
        )
    )

    private val geminiStructuredImportModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = structuredImportSchema
            }
        )
    }

    /**
     * Chain of custody for imported material (the "manual import" connector): where it came from, when it
     * was received, the SHA-256 of the exact bytes imported and the run id. Applied to every new artifact.
     */
    data class ImportCustody(val runId: String, val source: String, val receivedAtMillis: Long, val rawSha256: String) {
        fun stamp(art: ScrapedArtifactItem): ScrapedArtifactItem = art.copy(
            sourceUrl = source,
            fetchedAtMillis = receivedAtMillis,
            httpStatus = 0,
            rawResponseSha256 = rawSha256,
            connectorRunId = runId
        )

        companion object {
            fun of(rawPayload: String, source: String, nowMillis: Long = System.currentTimeMillis()): ImportCustody {
                val sha = ForensicCommon.sha256(rawPayload)
                return ImportCustody("run-$nowMillis-${sha.take(8)}-${ManualImportConnector.spec.id}", source, nowMillis, sha)
            }
        }
    }

    data class ImportedDossierResult(
        val updatedProfile: SubjectProfile,
        val importedStatementsCount: Int,
        val formatDetected: String,
        val summaryMessage: String
    )

    /**
     * Policy line placed at the top of every prompt bundle sent to an AI provider or copied for pasting.
     * For subjects without recorded consent it tells the model not to regenerate the restricted outputs
     * that the app itself withholds.
     */
    fun consentPreamble(subject: SubjectProfile): String = if (subject.restrictedOutputsUnlocked) {
        "AEGIS POLICY: consent=${subject.consentStatus.name}; data quality: ${subject.dataQualityLabel}. Scores are heuristic estimates, not validated psychometric instruments; say so where relevant."
    } else {
        "AEGIS POLICY: consent=UNVERIFIED; data quality: ${subject.dataQualityLabel}. The person described has not consented to profiling. Do NOT produce persuasion or leverage tactics, exploitable biases, rapport or manipulation scripts, personality-disorder or Dark Triad judgments, violence-risk estimates, or inferences about their location, schedule or sleep. Limit the answer to a factual, sourced summary of the collected public material and its reliability."
    }

    private fun isConfiguredSecretKey(candidate: String): Boolean {
        val trimmed = candidate.trim()
        return trimmed.isNotEmpty() &&
            !trimmed.startsWith("YOUR_") &&
            trimmed != "MY_GEMINI_API_KEY" &&
            trimmed != "DEFAULT_VALUE" &&
            trimmed != "null"
    }

    /**
     * Resolves the effective API key and human-readable key source badge for a given provider.
     * Priority:
     * 1. In-App Runtime Key Vault override (entered directly on Phone / Chromebook)
     * 2. AI Studio Secrets (`BuildConfig.GEMINI_API_KEY`, `BuildConfig.ANTHROPIC_API_KEY`, `BuildConfig.PERPLEXITY_API_KEY`)
     * 3. For Gemini: Firebase AI Logic (`Firebase.ai` with App Check attestation) when no explicit Gemini key is set.
     */
    fun resolveApiKeyAndSource(provider: AiProviderEngine, inAppKeyOverride: String = ""): Pair<String, String> {
        if (isConfiguredSecretKey(inAppKeyOverride)) {
            return inAppKeyOverride.trim() to "IN-APP KEY VAULT"
        }
        val buildConfigKey = when (provider) {
            AiProviderEngine.GEMINI -> runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("")
            AiProviderEngine.CLAUDE -> runCatching { BuildConfig.ANTHROPIC_API_KEY }.getOrDefault("")
            AiProviderEngine.PERPLEXITY -> runCatching { BuildConfig.PERPLEXITY_API_KEY }.getOrDefault("")
        }
        if (isConfiguredSecretKey(buildConfigKey)) {
            return buildConfigKey.trim() to "AI STUDIO SECRETS (${provider.secretKeyName})"
        }
        return when (provider) {
            AiProviderEngine.GEMINI -> "" to "FIREBASE AI LOGIC (ZERO-KEY)"
            AiProviderEngine.CLAUDE -> "" to "KEY NOT SET (ADD IN SECRETS OR IN-APP VAULT)"
            AiProviderEngine.PERPLEXITY -> "" to "KEY NOT SET (ADD IN SECRETS OR IN-APP VAULT)"
        }
    }

    /**
     * Streams or executes deep psycholinguistic & tactical OSINT analysis using any user-selected
     * provider AND any user-selected or custom model ID:
     * - GOOGLE GEMINI (`Firebase.ai` streaming OR direct `GEMINI_API_KEY` REST API for any Gemini model)
     * - ANTHROPIC CLAUDE (`https://api.anthropic.com/v1/messages` with any Claude 3 / 3.5 / 3.7 / 4 model)
     * - PERPLEXITY SONAR (`https://api.perplexity.ai/chat/completions` with any Sonar / Reasoning / Deep Research model)
     */
    fun streamAiDossierAnalysis(
        provider: AiProviderEngine,
        subject: SubjectProfile,
        customDirective: String,
        selectedModelId: String = provider.defaultModelId,
        inAppKeyOverride: String = ""
    ): Flow<AiSynthesisState> = flow {
        val effectiveModel = selectedModelId.trim().ifBlank { provider.defaultModelId }
        val directive = customDirective.trim().ifBlank {
            if (subject.restrictedOutputsUnlocked) {
                "Synthesize an executive behavioral briefing covering: (1) What makes ${subject.handle} tick, (2) Exact rapport & communication openers, (3) Cognitive biases & negotiation leverage vectors, and (4) OPSEC / stylometric verification."
            } else {
                "Summarize, factually and with sources, the public material collected for ${subject.handle}: what was collected, from where, when, and how reliable the scores are given the data-quality label."
            }
        }
        val (apiKey, keySourceLabel) = resolveApiKeyAndSource(provider, inAppKeyOverride)

        emit(
            AiSynthesisState.Loading(
                provider = provider,
                modelId = effectiveModel,
                statusText = "INITIALIZING ${provider.label} [$effectiveModel] VIA $keySourceLabel..."
            )
        )

        when (provider) {
            AiProviderEngine.GEMINI -> {
                val prompt = buildGeminiMarkdownContext(subject, directive)
                // If user explicitly provided a GEMINI_API_KEY (via AI Studio Secrets or In-App Key Vault),
                // call Google's Generative Language REST API first with the exact chosen modelId, and fall back
                // to Firebase AI Logic if needed. Otherwise stream via Firebase AI Logic!
                if (apiKey.isNotEmpty()) {
                    try {
                        val directResult = withTimeout(60_000L) {
                            withContext(Dispatchers.IO) {
                                callDirectGeminiRestApi(
                                    apiKey = apiKey,
                                    modelId = effectiveModel,
                                    prompt = prompt,
                                    authSourceLabel = keySourceLabel
                                )
                            }
                        }
                        if (directResult is AiSynthesisState.Success) {
                            emit(directResult)
                            return@flow
                        } else if (directResult is AiSynthesisState.Error) {
                            Log.w(
                                TAG,
                                "Direct Gemini API returned notice (${directResult.errorMessage}), attempting Firebase AI Logic fallback..."
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct Gemini API threw exception, falling back to Firebase AI Logic", e)
                    }
                }

                val accumulated = StringBuilder()
                try {
                    val firebaseModel = if (effectiveModel == "gemini-3.8-flash") {
                        geminiBriefingModel
                    } else {
                        Firebase.ai.generativeModel(modelName = effectiveModel)
                    }
                    withTimeout(60_000L) {
                        firebaseModel.generateContentStream(prompt).collect { chunk ->
                            val text = chunk.text.orEmpty()
                            if (text.isNotEmpty()) {
                                accumulated.append(text)
                                emit(
                                    AiSynthesisState.Streaming(
                                        provider = provider,
                                        modelId = effectiveModel,
                                        partialText = accumulated.toString()
                                    )
                                )
                            }
                        }
                    }
                    val finalText = accumulated.toString().trim()
                    if (finalText.isNotEmpty()) {
                        emit(
                            AiSynthesisState.Success(
                                provider = provider,
                                modelId = effectiveModel,
                                responseText = finalText,
                                authSourceLabel = "FIREBASE AI LOGIC"
                            )
                        )
                    } else {
                        emit(
                            AiSynthesisState.Error(
                                provider = provider,
                                modelId = effectiveModel,
                                errorMessage = "Gemini ($effectiveModel) returned an empty response."
                            )
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Gemini streaming timed out", e)
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = "Gemini ($effectiveModel) timed out after 60 seconds. Please try again."
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase AI Gemini generation failed for $effectiveModel", e)
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = e.localizedMessage
                                ?: "Gemini ($effectiveModel) generation failed. You can also enter a GEMINI_API_KEY in the Key Vault or AI Studio Secrets."
                        )
                    )
                }
            }

            AiProviderEngine.CLAUDE -> {
                if (apiKey.isEmpty()) {
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = "ANTHROPIC_API_KEY is not configured yet. Enter your Anthropic key in the AI Studio Secrets panel or paste it in the In-App API Key Vault below, or use 'COPY CLAUDE XML' to paste directly into Claude."
                        )
                    )
                    return@flow
                }

                try {
                    val result = withTimeout(60_000L) {
                        withContext(Dispatchers.IO) {
                            callAnthropicClaudeApi(
                                apiKey = apiKey,
                                modelId = effectiveModel,
                                subject = subject,
                                directive = directive,
                                authSourceLabel = keySourceLabel
                            )
                        }
                    }
                    emit(result)
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Claude API timed out", e)
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = "Anthropic Claude ($effectiveModel) request timed out after 60 seconds."
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Claude API call failed", e)
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = e.localizedMessage ?: "Claude ($effectiveModel) API request failed."
                        )
                    )
                }
            }

            AiProviderEngine.PERPLEXITY -> {
                if (apiKey.isEmpty()) {
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = "PERPLEXITY_API_KEY is not set. You can switch to Google Gemini or Anthropic Claude right now, paste a Perplexity key later in the In-App Key Vault / Secrets panel, or use 'COPY PERPLEXITY QUERY' below."
                        )
                    )
                    return@flow
                }

                try {
                    val result = withTimeout(60_000L) {
                        withContext(Dispatchers.IO) {
                            callPerplexitySonarApi(
                                apiKey = apiKey,
                                modelId = effectiveModel,
                                subject = subject,
                                directive = directive,
                                authSourceLabel = keySourceLabel
                            )
                        }
                    }
                    emit(result)
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Perplexity API timed out", e)
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = "Perplexity ($effectiveModel) request timed out after 60 seconds."
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Perplexity API call failed", e)
                    emit(
                        AiSynthesisState.Error(
                            provider = provider,
                            modelId = effectiveModel,
                            errorMessage = e.localizedMessage ?: "Perplexity ($effectiveModel) API request failed."
                        )
                    )
                }
            }
        }
    }

    private fun callDirectGeminiRestApi(apiKey: String, modelId: String, prompt: String, authSourceLabel: String): AiSynthesisState {
        val cleanModel = modelId.removePrefix("models/").trim()
        // Key goes in the x-goog-api-key header, never the URL (URLs end up in proxy and server logs).
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent"
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("text", prompt)
                                }
                            )
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.35)
                    put("maxOutputTokens", 1400)
                }
            )
        }

        val req = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return AiSynthesisState.Error(
                    provider = AiProviderEngine.GEMINI,
                    modelId = cleanModel,
                    errorMessage = "Gemini REST API HTTP ${resp.code} ($cleanModel): ${raw.take(240)}"
                )
            }
            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates") ?: JSONArray()
            val parts = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            val text = sb.toString().trim()
            return if (text.isNotEmpty()) {
                AiSynthesisState.Success(
                    provider = AiProviderEngine.GEMINI,
                    modelId = cleanModel,
                    responseText = text,
                    authSourceLabel = authSourceLabel
                )
            } else {
                AiSynthesisState.Error(
                    provider = AiProviderEngine.GEMINI,
                    modelId = cleanModel,
                    errorMessage = "Gemini ($cleanModel) returned an empty candidate."
                )
            }
        }
    }

    private fun callAnthropicClaudeApi(
        apiKey: String,
        modelId: String,
        subject: SubjectProfile,
        directive: String,
        authSourceLabel: String
    ): AiSynthesisState {
        val xmlContext = buildClaudeXmlBundle(subject, directive)
        val payload = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 1400)
            put(
                "system",
                "You are a careful psycholinguistic and open-source research analyst inside Aegis Blackvault. Analyze the provided XML dossier and be explicit about data quality and uncertainty. " +
                    consentPreamble(subject)
            )
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", xmlContext)
                    }
                )
            )
        }

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return AiSynthesisState.Error(
                    provider = AiProviderEngine.CLAUDE,
                    modelId = modelId,
                    errorMessage = "Claude API HTTP ${resp.code} ($modelId): ${raw.take(240)}"
                )
            }
            val json = JSONObject(raw)
            val contentArr = json.optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until contentArr.length()) {
                val block = contentArr.optJSONObject(i)
                if (block != null && block.optString("type") == "text") {
                    sb.append(block.optString("text"))
                }
            }
            val text = sb.toString().trim()
            return if (text.isNotEmpty()) {
                AiSynthesisState.Success(
                    provider = AiProviderEngine.CLAUDE,
                    modelId = modelId,
                    responseText = text,
                    authSourceLabel = authSourceLabel
                )
            } else {
                AiSynthesisState.Error(
                    provider = AiProviderEngine.CLAUDE,
                    modelId = modelId,
                    errorMessage = "Claude ($modelId) returned an empty content block."
                )
            }
        }
    }

    private fun callPerplexitySonarApi(
        apiKey: String,
        modelId: String,
        subject: SubjectProfile,
        directive: String,
        authSourceLabel: String
    ): AiSynthesisState {
        val queryPrompt = buildPerplexitySearchBundle(subject, directive)
        val payload = JSONObject().apply {
            put("model", modelId)
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "You verify public technical footprints (repositories, publications, models) with citations. " +
                                    consentPreamble(subject)
                            )
                        }
                    )
                    .put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", queryPrompt)
                        }
                    )
            )
        }

        val req = Request.Builder()
            .url("https://api.perplexity.ai/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return AiSynthesisState.Error(
                    provider = AiProviderEngine.PERPLEXITY,
                    modelId = modelId,
                    errorMessage = "Perplexity API HTTP ${resp.code} ($modelId): ${raw.take(240)}"
                )
            }
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices") ?: JSONArray()
            val messageText = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()

            val citationsArr = json.optJSONArray("citations") ?: JSONArray()
            val citations = mutableListOf<String>()
            for (i in 0 until citationsArr.length()) {
                val c = citationsArr.optString(i)
                if (c.isNotBlank()) citations.add(c)
            }

            return if (messageText.isNotEmpty()) {
                AiSynthesisState.Success(
                    provider = AiProviderEngine.PERPLEXITY,
                    modelId = modelId,
                    responseText = messageText,
                    citations = citations,
                    authSourceLabel = authSourceLabel
                )
            } else {
                AiSynthesisState.Error(
                    provider = AiProviderEngine.PERPLEXITY,
                    modelId = modelId,
                    errorMessage = "Perplexity ($modelId) returned an empty response."
                )
            }
        }
    }

    /**
     * Uses Firebase AI Logic (`gemini-3.8-flash` with `responseSchema`) to parse unstructured raw text
     * (e.g., pasted Claude/Perplexity/Gemini outputs, interview transcripts, or OSINT notes) into
     * structured timestamped artifacts and merges them into the active SubjectProfile!
     */
    suspend fun importUnstructuredTextWithGemini(
        rawText: String,
        activeSubject: SubjectProfile,
        custody: ImportCustody? = null
    ): ImportedDossierResult = withContext(Dispatchers.IO) {
        try {
            val response = withTimeout(60_000L) {
                geminiStructuredImportModel.generateContent(
                    "Extract all verbatim statements, posts, commits, or behavioral observations from the following OSINT input for subject ${activeSubject.handle}:\n\n$rawText"
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty structured response from Gemini")
            val json = JSONObject(jsonText)
            val statementsArr = json.optJSONArray("extractedStatements") ?: JSONArray()
            val extractedItems = mutableListOf<ScrapedArtifactItem>()
            for (i in 0 until statementsArr.length()) {
                val item = statementsArr.optJSONObject(i) ?: continue
                val text = item.optString("verbatimText").trim()
                if (text.isNotBlank()) {
                    extractedItems.add(
                        StylometryEngine.createTimestampedArtifact(
                            id = "art-gemini-imp-$i-${System.nanoTime()}",
                            platform = item.optString("platform", "GEMINI_IMPORT").uppercase(Locale.US),
                            timestampIso = item.optString("timestampIso", ""),
                            verbatimText = text,
                            contextLabel = "Gemini Structured Import"
                        )
                    )
                }
            }
            if (extractedItems.isNotEmpty()) {
                val mergedProfile = recomputeProfileWithNewArtifacts(activeSubject, extractedItems, custody)
                return@withContext ImportedDossierResult(
                    updatedProfile = mergedProfile,
                    importedStatementsCount = extractedItems.size,
                    formatDetected = "GEMINI_STRUCTURED_JSON_SCHEMA",
                    summaryMessage = "GEMINI AI EXTRACTED ${extractedItems.size} TIMESTAMPED STATEMENTS & RECOMPUTED LIWC PROFILE FOR [${mergedProfile.handle}]"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini structured import fallback to deterministic parser", e)
        }
        // Fallback to deterministic multi-format parser if offline or App Check pending
        parseAndImportData(rawText, activeSubject)
    }

    // ========================================================================
    // MULTI-FORMAT EXPORT BUILDERS (ROOM JSON, CSV, CLAUDE XML, GEMINI, PERPLEXITY)
    // ========================================================================

    fun buildExportPayload(
        format: DataExportFormat,
        subject: SubjectProfile,
        astPatches: List<AstPatchEntity>,
        rawEntities: List<RawSocialMediaEntity>,
        lingEntities: List<LinguisticPersonalityProfileEntity>,
        hierarchyEntities: List<MindMapHierarchyEntity>,
        customDirective: String = ""
    ): String = when (format) {
        DataExportFormat.AGENT_HANDOFF -> buildAgentHandoffBundleJson(
            subject = subject,
            astPatches = astPatches,
            rawEntities = rawEntities,
            lingEntities = lingEntities,
            hierarchyEntities = hierarchyEntities,
            customDirective = customDirective
        )

        DataExportFormat.PKZIP_STIX -> ExportEngine.buildStix21BundleJson(subject, astPatches)

        DataExportFormat.ROOM_JSON -> buildRoomDossierJson(subject, rawEntities, lingEntities, hierarchyEntities)

        DataExportFormat.DISCOURSE_CSV -> buildDiscourseCsv(subject)

        DataExportFormat.CLAUDE_XML -> buildClaudeXmlBundle(subject, customDirective)

        DataExportFormat.GEMINI_PROMPT -> buildGeminiMarkdownContext(subject, customDirective)

        DataExportFormat.PERPLEXITY_QUERY -> buildPerplexitySearchBundle(subject, customDirective)
    }

    /**
     * Builds a comprehensive, self-contained Multi-Agent Handoff JSON bundle combining:
     * - Agent Handoff Specification & Architecture manifest
     * - Full 4-table Room Database Relational Dossier
     * - Sovereign-Aegis VERDAD Manipulation Risk, 6 Emotional Bait Lexicons, DISARM Red/Blue, Situational Watchpoints & Reema Post-Quantum KeyBinding
     * - OASIS STIX 2.1 Provenance Bundle & Self-Healing AST Patches
     */
    fun buildAgentHandoffBundleJson(
        subject: SubjectProfile,
        astPatches: List<AstPatchEntity>,
        rawEntities: List<RawSocialMediaEntity>,
        lingEntities: List<LinguisticPersonalityProfileEntity>,
        hierarchyEntities: List<MindMapHierarchyEntity>,
        customDirective: String = ""
    ): String {
        val verdad = VerdadEngine.evaluateSovereignVerdad(subject)
        val roomDossier = JSONObject(buildRoomDossierJson(subject, rawEntities, lingEntities, hierarchyEntities))
        val stixBundle = JSONObject(ExportEngine.buildStix21BundleJson(subject, astPatches))

        return JSONObject().apply {
            put("handoff_protocol", "AEGIS_BLACKVAULT_SOVEREIGN_AGENT_HANDOFF_V1")
            put("application_id", "com.aistudio.aegisblackvault.osint")
            put("exported_at_iso", currentIsoUtc())
            put("w3c_prov_o_root_sha256", subject.provSha256)
            put(
                "agent_instructions",
                JSONObject().apply {
                    put(
                        "role",
                        "Downstream Autonomous OSINT, Psychometric & Cognitive Defense Agent"
                    )
                    put(
                        "directive",
                        customDirective.ifBlank {
                            "Ingest the 4-table Room relational dossier, Sovereign-Aegis VERDAD assessment, DISARM Red/Blue kill-chain mappings, and STIX 2.1 bundle for [${subject.handle}]. Preserve the load-bearing invariant that manipulationGate derives strictly from deterministic manipulationRisk."
                        }
                    )
                    put(
                        "supported_models",
                        JSONObject().apply {
                            put("gemini", JSONArray(AiProviderEngine.GEMINI.presetModels.map { it.modelId }))
                            put("claude", JSONArray(AiProviderEngine.CLAUDE.presetModels.map { it.modelId }))
                            put("perplexity", JSONArray(AiProviderEngine.PERPLEXITY.presetModels.map { it.modelId }))
                        }
                    )
                }
            )
            put("sovereign_aegis_verdad", verdad.toJsonObject())
            put("room_relational_dossier", roomDossier)
            put("oasis_stix_2_1_bundle", stixBundle)
        }.toString(2)
    }

    /**
     * Exports a complete, round-trip importable JSON snapshot of the subject's Room database tables:
     * `subject_profiles`, `raw_social_media_entities`, `linguistic_personality_profiles`, and `mind_map_hierarchy_edges`.
     */
    fun buildRoomDossierJson(
        subject: SubjectProfile,
        rawEntities: List<RawSocialMediaEntity>,
        lingEntities: List<LinguisticPersonalityProfileEntity>,
        hierarchyEntities: List<MindMapHierarchyEntity>
    ): String {
        val subjEntity = subject.toEntity()
        val subjectRaw = rawEntities.filter { it.subjectId == subject.id }
            .ifEmpty { subject.toRawSocialMediaEntities() }
        val subjectLing = lingEntities.find { it.subjectId == subject.id }
            ?: subject.toLinguisticProfileEntity()
        val subjectHierarchy = hierarchyEntities.filter { it.rootSubjectId == subject.id }
            .ifEmpty { MindMapEngine.buildMindMapHierarchyEntities(subject) }

        return JSONObject().apply {
            put("aegis_schema_version", "3.5")
            put("exported_at_iso", currentIsoUtc())
            put("prov_sha256", subject.provSha256)
            put(
                "subject_profile",
                JSONObject().apply {
                    put("id", subjEntity.id)
                    put("stixId", subjEntity.stixId)
                    put("handle", subjEntity.handle)
                    put("codeId", subjEntity.codeId)
                    put("alias", subjEntity.alias)
                    put("cluster", subjEntity.cluster)
                    put("loc", subjEntity.loc)
                    put("lat", subjEntity.lat)
                    put("lon", subjEntity.lon)
                    put("utcOffset", subjEntity.utcOffset)
                    put("troughWindowLabel", subjEntity.troughWindowLabel)
                    put("burrowsDelta", subjEntity.burrowsDelta)
                    put("styloMatch", subjEntity.styloMatch)
                    put("mattrScore", subjEntity.mattrScore)
                    put("ocean", JSONObject(subjEntity.oceanJson))
                    put("darkTriad", JSONObject(subjEntity.darkTriadJson))
                    put("threatMetrics", JSONObject(subjEntity.threatMetricsJson))
                }
            )
            put("linguistic_personality_profile", subject.linguisticProfile.toJsonObject())
            put(
                "raw_social_media_entities",
                JSONArray().apply {
                    subjectRaw.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("entityId", r.entityId)
                                put("subjectId", r.subjectId)
                                put("subjectHandle", r.subjectHandle)
                                put("platform", r.platform)
                                put("sourceUri", r.sourceUri)
                                put("postedTimestampIso", r.postedTimestampIso)
                                put("utcHour", r.utcHour)
                                put("rawPayloadText", r.rawPayloadText)
                                put("lexicalMarkersCsv", r.lexicalMarkersCsv)
                                put("sentimentScore", r.sentimentScore)
                            }
                        )
                    }
                }
            )
            put(
                "mind_map_hierarchy_edges",
                JSONArray().apply {
                    subjectHierarchy.forEach { h ->
                        put(
                            JSONObject().apply {
                                put("hierarchyId", h.hierarchyId)
                                put("parentNodeId", h.parentNodeId)
                                put("childNodeId", h.childNodeId)
                                put("childLabel", h.childLabel)
                                put("nodeCategory", h.nodeCategory)
                                put("hierarchyDepth", h.hierarchyDepth)
                                put("relationshipPredicate", h.relationshipPredicate)
                                put("confidenceWeight", h.confidenceWeight.toDouble())
                            }
                        )
                    }
                }
            )
        }.toString(2)
    }

    /**
     * Exports RFC-4180 compliant CSV of all timestamped scraped statements & LIWC markers.
     */
    fun buildDiscourseCsv(subject: SubjectProfile): String {
        val sb = StringBuilder()
        sb.appendLine("entity_id,subject_handle,platform,posted_timestamp_iso,utc_hour,sentiment_score,lexical_markers,verbatim_text")
        subject.scrapedArtifacts.forEach { art ->
            val escapedText = art.verbatimText.replace("\"", "\"\"")
            val escapedMarkers = art.lexicalMarkers.joinToString(";").replace("\"", "\"\"")
            sb.appendLine(
                "\"${art.id}\",\"${subject.handle}\",\"${art.platform}\",\"${art.timestampIso}\",${art.utcHour},${art.sentimentScore},\"$escapedMarkers\",\"$escapedText\""
            )
        }
        return sb.toString()
    }

    /**
     * Builds an Anthropic Claude 3.7 XML-structured prompt bundle (`<aegis_osint_dossier>`).
     */
    fun buildClaudeXmlBundle(subject: SubjectProfile, customDirective: String = ""): String {
        val lp = subject.linguisticProfile
        val directive = customDirective.trim().ifBlank {
            if (subject.restrictedOutputsUnlocked) {
                "Analyze this subject's psycholinguistic profile, identify motivational drivers, and suggest 3 rapport and negotiation approaches."
            } else {
                "Summarize, factually and with sources, the public material collected for ${subject.handle}: what was collected, from where, when, and how reliable the scores are given the data-quality label."
            }
        }
        return buildString {
            appendLine("<!-- ${consentPreamble(subject)} -->")
            appendLine(
                "<aegis_osint_dossier schema=\"3.6\" target=\"${subject.handle}\" consent=\"${subject.consentStatus.name}\" data_quality=\"${subject.dataQualityLabel}\">"
            )
            appendLine("  <identity>")
            appendLine("    <handle>${subject.handle}</handle>")
            appendLine("    <alias>${subject.alias}</alias>")
            appendLine("    <location utc_offset=\"${subject.utcOffset}\">${subject.loc}</location>")
            appendLine("    <diurnal_sleep_trough>${subject.troughWindowLabel}</diurnal_sleep_trough>")
            appendLine(
                "    <stylometry burrows_delta=\"${subject.burrowsDelta}\" match_pct=\"${subject.styloMatch}\" mattr=\"${subject.mattrScore}\" />"
            )
            appendLine("  </identity>")
            appendLine("  <psychometric_profile archetype=\"${lp.archetypeTitle}\">")
            appendLine(
                "    <ocean O=\"${subject.ocean.O}\" C=\"${subject.ocean.C}\" E=\"${subject.ocean.E}\" A=\"${subject.ocean.A}\" N=\"${subject.ocean.N}\" />"
            )
            appendLine(
                "    <dark_triad machiavellianism=\"${subject.darkTriad.machiavellianism}\" narcissism=\"${subject.darkTriad.narcissism}\" psychopathy=\"${subject.darkTriad.psychopathy}\" />"
            )
            appendLine(
                "    <pennebaker_liwc ati=\"${lp.analyticalThinkingIndex}\" clout=\"${lp.cloutDominanceIndex}\" authenticity=\"${lp.authenticityIndex}\" tone=\"${lp.emotionalToneIndex}\" i_rate=\"${lp.iPronounRatePct}\" we_rate=\"${lp.wePronounRatePct}\" />"
            )
            appendLine("    <what_makes_them_tick>")
            lp.whatMakesThemTick.forEach { appendLine("      <driver>$it</driver>") }
            appendLine("    </what_makes_them_tick>")
            appendLine("    <how_to_talk_to_them>")
            lp.howToTalkToThem.forEach { appendLine("      <protocol>$it</protocol>") }
            appendLine("    </how_to_talk_to_them>")
            appendLine("    <persuasion_and_leverage>")
            lp.persuasionAndLeverageHooks.forEach { appendLine("      <hook>$it</hook>") }
            appendLine("    </persuasion_and_leverage>")
            appendLine("  </psychometric_profile>")
            appendLine("  <timestamped_evidence count=\"${subject.scrapedArtifacts.size}\">")
            subject.scrapedArtifacts.take(12).forEach { art ->
                appendLine(
                    "    <statement id=\"${art.id}\" platform=\"${art.platform}\" timestamp=\"${art.timestampIso}\" tone=\"${art.sentimentScore}\">${art.verbatimText}</statement>"
                )
            }
            appendLine("  </timestamped_evidence>")
            appendLine("  <analyst_directive>$directive</analyst_directive>")
            appendLine("</aegis_osint_dossier>")
        }
    }

    /**
     * Builds a Google Gemini Markdown Context & Briefing Prompt.
     */
    fun buildGeminiMarkdownContext(subject: SubjectProfile, customDirective: String = ""): String {
        val lp = subject.linguisticProfile
        val directive = customDirective.trim().ifBlank {
            if (subject.restrictedOutputsUnlocked) {
                "Provide an actionable psycholinguistic briefing for ${subject.handle}: (1) Psychological Core & Ego Drivers, (2) High-LSM Communication Scripts, (3) Persuasion & Negotiation Leverage, and (4) Cross-Platform Stylometric Verification."
            } else {
                "Summarize, factually and with sources, the public material collected for ${subject.handle}: what was collected, from where, when, and how reliable the scores are given the data-quality label."
            }
        }
        return buildString {
            appendLine("> ${consentPreamble(subject)}")
            appendLine()
            appendLine("# AEGIS BLACKVAULT // GEMINI AI DOSSIER CONTEXT")
            appendLine("- **Subject**: `${subject.handle}` (${subject.alias})")
            appendLine("- **Archetype**: ${lp.archetypeTitle} — ${lp.archetypeSummary}")
            appendLine("- **Chronolocation**: ${subject.loc} | Sleep Trough: ${subject.troughWindowLabel}")
            appendLine(
                "- **Stylometry**: 50-Word Calibrated Burrows' Delta Δ=${subject.burrowsDelta} (${subject.styloMatch}% Match) | MATTR=${subject.mattrScore}"
            )
            appendLine(
                "- **Big Five OCEAN**: O=${subject.ocean.O}%, C=${subject.ocean.C}%, E=${subject.ocean.E}%, A=${subject.ocean.A}%, N=${subject.ocean.N}%"
            )
            appendLine(
                "- **Dark Triad**: Machiavellianism=${subject.darkTriad.machiavellianism}%, Narcissism=${subject.darkTriad.narcissism}%, Psychopathy=${subject.darkTriad.psychopathy}%"
            )
            appendLine(
                "- **Pennebaker LIWC**: ATI=${lp.analyticalThinkingIndex}%, Clout=${lp.cloutDominanceIndex}%, Authenticity=${lp.authenticityIndex}%, Tone=${lp.emotionalToneIndex}%"
            )
            appendLine()
            appendLine("## Timestamped Scraped Discourse")
            subject.scrapedArtifacts.take(10).forEach { art ->
                appendLine("- `[${art.timestampIso}]` **${art.platform}** (${art.contextLabel}): \"${art.verbatimText}\"")
            }
            appendLine()
            appendLine("## Analyst Directive")
            appendLine(directive)
        }
    }

    /**
     * Builds a Perplexity Sonar Deep Web-Grounded OSINT Search & Corroboration Query.
     */
    fun buildPerplexitySearchBundle(subject: SubjectProfile, customDirective: String = ""): String {
        val repoNames = subject.githubRepos.take(4).joinToString(", ") { it.fullName }
        val hfNames = subject.huggingFaceAssets.take(4).joinToString(", ") { it.id }
        val directive = customDirective.trim().ifBlank {
            "Verify, with citations, the public technical footprint listed below (repositories, models, publications). Do not search for personal information beyond it."
        }
        return buildString {
            appendLine(consentPreamble(subject))
            appendLine()
            appendLine("OSINT TARGET CORROBORATION & WEB GROUNDING QUERY:")
            appendLine("Target Handle / Alias: ${subject.handle} (${subject.alias})")
            appendLine("Known GitHub Repositories: $repoNames")
            appendLine("Known Hugging Face Assets: $hfNames")
            appendLine("Inferred Chronolocation: ${subject.loc} (${subject.troughWindowLabel})")
            appendLine(
                "Psycholinguistic Signature: ${subject.linguisticProfile.archetypeTitle} (ATI=${subject.linguisticProfile.analyticalThinkingIndex}%, Clout=${subject.linguisticProfile.cloutDominanceIndex}%)"
            )
            appendLine()
            appendLine("Task: $directive")
            appendLine("1. Corroborate recent public commits, releases, papers, or forum discussions tied to these repositories/models.")
            appendLine("2. Identify key technical collaborators, domain expertise clusters, and public communication patterns.")
            appendLine("3. Provide verified source URLs and timestamps.")
        }
    }

    // ========================================================================
    // UNIVERSAL MULTI-FORMAT DATA IMPORTER (JSON, STIX 2.1, CSV, XML, RAW TEXT)
    // ========================================================================

    /**
     * Parses imported data from a file or pasted payload (Aegis Room JSON, OASIS STIX 2.1 JSON,
     * CSV, Claude XML, or raw timestamped lines), merges the extracted statements into the active
     * subject dossier, and recomputes the full Pennebaker LIWC + Burrows' Delta + Mind Map hierarchy.
     */
    fun parseAndImportData(rawPayload: String, activeSubject: SubjectProfile, custody: ImportCustody? = null): ImportedDossierResult {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) {
            return ImportedDossierResult(
                updatedProfile = activeSubject,
                importedStatementsCount = 0,
                formatDetected = "EMPTY",
                summaryMessage = "No content found to import."
            )
        }

        val newArtifacts = mutableListOf<ScrapedArtifactItem>()
        var detectedFormat = "RAW_TRANSCRIPT_LINES"

        try {
            when {
                // 1. JSON Object (Aegis Room Dossier JSON or OASIS STIX 2.1 Bundle)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("raw_social_media_entities") -> {
                            detectedFormat = "AEGIS_ROOM_DOSSIER_JSON"
                            val arr = obj.getJSONArray("raw_social_media_entities")
                            for (i in 0 until arr.length()) {
                                val r = arr.getJSONObject(i)
                                val text = r.optString("rawPayloadText", r.optString("verbatimText", "")).trim()
                                if (text.isNotBlank()) {
                                    newArtifacts.add(
                                        StylometryEngine.createTimestampedArtifact(
                                            id = r.optString("entityId", "art-imp-json-$i-${System.nanoTime()}"),
                                            platform = r.optString("platform", "IMPORTED_JSON"),
                                            timestampIso = r.optString("postedTimestampIso", ""),
                                            verbatimText = text,
                                            contextLabel = r.optString("sourceUri", "Room JSON Import")
                                        )
                                    )
                                }
                            }
                        }

                        obj.optString("type") == "bundle" && obj.has("objects") -> {
                            detectedFormat = "OASIS_STIX_2.1_BUNDLE"
                            val objs = obj.getJSONArray("objects")
                            for (i in 0 until objs.length()) {
                                val stixObj = objs.getJSONObject(i)
                                val stixType = stixObj.optString("type")
                                if (stixType == "observed-data" || stixType == "note" || stixType == "indicator") {
                                    val text = stixObj.optString(
                                        "x_verbatim_text",
                                        stixObj.optString("description", stixObj.optString("name", ""))
                                    ).trim()
                                    val ts = stixObj.optString("first_observed", stixObj.optString("created", ""))
                                    val plat = stixObj.optString("x_platform", "STIX_${stixType.uppercase(Locale.US)}")
                                    if (text.isNotBlank()) {
                                        newArtifacts.add(
                                            StylometryEngine.createTimestampedArtifact(
                                                id = "art-stix-$i-${System.nanoTime()}",
                                                platform = plat,
                                                timestampIso = ts,
                                                verbatimText = text,
                                                contextLabel = stixObj.optString("id", "STIX 2.1 Import")
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            detectedFormat = "GENERIC_JSON_OBJECT"
                            val text = obj.optString("text", obj.optString("content", obj.optString("body", trimmed.take(360))))
                            newArtifacts.add(
                                StylometryEngine.createTimestampedArtifact(
                                    id = "art-json-single-${System.nanoTime()}",
                                    platform = obj.optString("platform", "JSON_IMPORT"),
                                    timestampIso = obj.optString("timestamp", ""),
                                    verbatimText = text,
                                    contextLabel = "JSON Object Import"
                                )
                            )
                        }
                    }
                }

                // 2. JSON Array of social posts / messages
                trimmed.startsWith("[") -> {
                    detectedFormat = "JSON_ARRAY_FEED"
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        if (item != null) {
                            val text = item.optString(
                                "verbatimText",
                                item.optString("rawPayloadText", item.optString("text", item.optString("body", "")))
                            ).trim()
                            val ts = item.optString(
                                "timestampIso",
                                item.optString("postedTimestampIso", item.optString("created_at", ""))
                            )
                            val plat = item.optString("platform", "JSON_ARRAY").uppercase(Locale.US)
                            if (text.isNotBlank()) {
                                newArtifacts.add(
                                    StylometryEngine.createTimestampedArtifact(
                                        id = "art-arr-$i-${System.nanoTime()}",
                                        platform = plat,
                                        timestampIso = ts,
                                        verbatimText = text,
                                        contextLabel = "JSON Array Import"
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Claude XML Bundle (<statement ...>...</statement>)
                trimmed.contains("<statement", ignoreCase = true) || trimmed.contains("<aegis_osint_dossier", ignoreCase = true) -> {
                    detectedFormat = "CLAUDE_XML_BUNDLE"
                    val stmtRegex = Regex(
                        """<statement[^>]*platform=["']([^"']+)["'][^>]*timestamp=["']([^"']+)["'][^>]*>(.*?)</statement>""",
                        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                    )
                    stmtRegex.findAll(trimmed).forEachIndexed { idx, match ->
                        val plat = match.groupValues[1].trim()
                        val ts = match.groupValues[2].trim()
                        val text = match.groupValues[3].trim()
                        if (text.isNotBlank()) {
                            newArtifacts.add(
                                StylometryEngine.createTimestampedArtifact(
                                    id = "art-xml-$idx-${System.nanoTime()}",
                                    platform = plat,
                                    timestampIso = ts,
                                    verbatimText = text,
                                    contextLabel = "Claude XML Import"
                                )
                            )
                        }
                    }
                }

                // 4. CSV Table (RFC-4180 or simple comma-separated rows)
                trimmed.lines().firstOrNull()?.contains(",") == true && trimmed.lines().size >= 2 -> {
                    detectedFormat = "CSV_DISCOURSE_TABLE"
                    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val startIdx = if (lines.first().lowercase(Locale.US).contains("verbatim_text") ||
                        lines.first().lowercase(Locale.US).contains("timestamp")
                    ) {
                        1
                    } else {
                        0
                    }

                    for (i in startIdx until lines.size) {
                        val cols = parseCsvLine(lines[i])
                        if (cols.size >= 8) {
                            // Aegis 8-column CSV format: entity_id,subject_handle,platform,posted_timestamp_iso,utc_hour,sentiment_score,lexical_markers,verbatim_text
                            val plat = cols[2].ifBlank { "CSV_IMPORT" }
                            val ts = cols[3]
                            val text = cols[7].trim()
                            if (text.isNotBlank()) {
                                newArtifacts.add(
                                    StylometryEngine.createTimestampedArtifact(
                                        id = "art-csv-$i-${System.nanoTime()}",
                                        platform = plat,
                                        timestampIso = ts,
                                        verbatimText = text,
                                        contextLabel = "CSV Row #$i"
                                    )
                                )
                            }
                        } else if (cols.size >= 3) {
                            // 3-column CSV: timestamp,platform,text
                            val ts = cols[0]
                            val plat = cols[1].ifBlank { "CSV_IMPORT" }
                            val text = cols.subList(2, cols.size).joinToString(", ").trim()
                            if (text.isNotBlank()) {
                                newArtifacts.add(
                                    StylometryEngine.createTimestampedArtifact(
                                        id = "art-csv3-$i-${System.nanoTime()}",
                                        platform = plat.uppercase(Locale.US),
                                        timestampIso = ts,
                                        verbatimText = text,
                                        contextLabel = "CSV Row #$i"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Structured parse fallback to line parser: ${e.message}")
        }

        // 5. Fallback: Parse raw paragraphs / lines (e.g. pasted Claude, Gemini, or Perplexity output or chat transcript)
        if (newArtifacts.isEmpty()) {
            detectedFormat = "RAW_TEXT_TRANSCRIPT"
            val paragraphs = trimmed.split(Regex("\\n+"))
                .map { it.trim() }
                .filter { it.length >= 12 }
                .take(15)
            paragraphs.forEachIndexed { idx, para ->
                newArtifacts.add(
                    StylometryEngine.createTimestampedArtifact(
                        id = "art-raw-$idx-${System.nanoTime()}",
                        platform = "IMPORTED_TRANSCRIPT",
                        timestampIso = "", // plain text carries no dates; none is invented
                        verbatimText = para.take(360),
                        contextLabel = "Line #${idx + 1}"
                    )
                )
            }
        }

        val updatedProfile = recomputeProfileWithNewArtifacts(activeSubject, newArtifacts, custody)
        return ImportedDossierResult(
            updatedProfile = updatedProfile,
            importedStatementsCount = newArtifacts.size,
            formatDetected = detectedFormat,
            summaryMessage = "IMPORTED ${newArtifacts.size} RECORDS ($detectedFormat) -> ROOM DB & RECOMPUTED LIWC PROFILE FOR [${updatedProfile.handle}]"
        )
    }

    private fun recomputeProfileWithNewArtifacts(
        activeSubject: SubjectProfile,
        imported: List<ScrapedArtifactItem>,
        custody: ImportCustody?
    ): SubjectProfile {
        if (imported.isEmpty()) return activeSubject
        val newArtifacts = if (custody != null) imported.map(custody::stamp) else imported
        val mergedArtifacts = (newArtifacts + activeSubject.scrapedArtifacts)
            .distinctBy { "${it.platform}:${it.verbatimText.take(80)}" }
            .sortedByDescending { it.timestampIso }
            .take(28)

        val combinedCorpus = mergedArtifacts.joinToString(" ") { it.verbatimText }
        val hours = mergedArtifacts.filter { it.hasKnownHour }.map { it.utcHour }

        val eval = StylometryEngine.evaluateCorpus(
            rawText = combinedCorpus,
            repoCount = activeSubject.githubRepos.size,
            hfCount = activeSubject.huggingFaceAssets.size,
            followerCount = 0 // follower counts are not stored; none is assumed
        )
        val chrono = ChronoGeoEngine.evaluateChronoAndGeo(
            utcHours = hours,
            declaredLocation = activeSubject.loc.substringBefore("(").trim()
        )
        val lingProfile = StylometryEngine.buildLinguisticPersonalityProfile(
            handle = activeSubject.handle,
            eval = eval,
            chrono = chrono,
            artifactsCount = mergedArtifacts.size
        )
        val insights = PlaybookEngine.synthesizeActionableInsights(
            handle = activeSubject.handle,
            eval = eval,
            chrono = chrono,
            repos = activeSubject.githubRepos,
            hfAssets = activeSubject.huggingFaceAssets,
            healedPatchId = activeSubject.healedPatchId,
            lingProfile = lingProfile
        )
        val newHash = ForensicCommon.sha256(
            "${activeSubject.handle}:${mergedArtifacts.size}:${eval.burrowsDelta}:${System.currentTimeMillis()}"
        )

        return activeSubject.copy(
            burrowsDelta = eval.burrowsDelta,
            styloMatch = eval.styloMatchPct,
            mattrScore = eval.mattr,
            ocean = eval.ocean,
            darkTriad = eval.darkTriad,
            threatMetrics = eval.threatMetrics,
            zScores = eval.zScores30,
            hourlyBins = chrono.hourlyBins,
            troughWindowLabel = chrono.windowLabel,
            backStageQuote = "[${newArtifacts.first().platform} @ ${newArtifacts.first().timestampIso}]: \"${newArtifacts.first().verbatimText.take(
                150
            )}\"",
            scrapedArtifacts = mergedArtifacts,
            linguisticProfile = lingProfile,
            actionableInsights = insights,
            provSha256 = newHash,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }

                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun currentIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
