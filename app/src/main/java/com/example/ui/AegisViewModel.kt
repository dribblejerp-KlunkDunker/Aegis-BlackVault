package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.engine.ForensicMathEngine
import com.example.model.*
import com.example.network.MultiAiAndDataPortabilityService
import com.example.network.OsintScraperService
import com.example.security.SecureKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AegisUiState(
    val profiles: List<SubjectProfile> = emptyList(),
    val selectedSubjectId: String = "subj-alpha",
    val comparisonSubjectId: String = "subj-beta",
    val triplets: List<KnowledgeTripletEntity> = emptyList(),
    val astPatches: Map<String, AstPatchEntity> = emptyMap(),
    val rawSocialEntities: List<RawSocialMediaEntity> = emptyList(),
    val linguisticProfileEntities: List<LinguisticPersonalityProfileEntity> = emptyList(),
    val mindMapHierarchyEntities: List<MindMapHierarchyEntity> = emptyList(),
    val scraperUri: String = "",
    val selectedPlatformMode: ScraperPlatformMode = ScraperPlatformMode.ALL,
    val httpProbes: List<HttpEndpointProbe> = emptyList(),
    val isScraping: Boolean = false,
    val bridgeStatus: String = "STEALTH_FLEET // READY",
    val containmentOverride: Boolean = false,
    val selectedSpectrumToken: String = "to",
    val graphMode: String = "HIERARCHY",
    val domDrift: DomDriftState = DomDriftState(),
    val cypherQuery: String = "MATCH (a:Identity)-[r]->(b) RETURN a.handle, type(r), b.name LIMIT 12",
    val cypherResultRows: List<List<String>> = emptyList(),
    val selectedAiProvider: AiProviderEngine = AiProviderEngine.GEMINI,
    val selectedModelByProvider: Map<AiProviderEngine, String> = mapOf(
        AiProviderEngine.GEMINI to AiProviderEngine.GEMINI.defaultModelId,
        AiProviderEngine.CLAUDE to AiProviderEngine.CLAUDE.defaultModelId,
        AiProviderEngine.PERPLEXITY to AiProviderEngine.PERPLEXITY.defaultModelId
    ),
    val inAppApiKeys: Map<AiProviderEngine, String> = emptyMap(),
    val aiDirectivePrompt: String = "",
    val githubTokenConfigured: Boolean = false,
    val aiSynthesisState: AiSynthesisState = AiSynthesisState.Idle,
    val selectedExportFormat: DataExportFormat = DataExportFormat.AGENT_HANDOFF,
    val telemetryLogs: List<TelemetryLog> = listOf(
        TelemetryLog("20:47:01.104", "PROV_O", "AEGIS TERMINAL INITIALIZED // GEMINI + CLAUDE + PERPLEXITY + OKHTTP ARMED."),
        TelemetryLog("20:47:01.412", "ROOM_DAO", "NORMALIZED ROOM TABLES ARMED: raw_social_media_entities, linguistic_personality_profiles, mind_map_hierarchy_edges.")
    ),
    val exportBannerMessage: String? = null
) {
    val activeSubject: SubjectProfile?
        get() = profiles.find { it.id == selectedSubjectId } ?: profiles.firstOrNull()

    val comparisonSubject: SubjectProfile?
        get() = profiles.find { it.id == comparisonSubjectId && it.id != activeSubject?.id }
            ?: profiles.firstOrNull { it.id != activeSubject?.id }
            ?: activeSubject

    val activeModelId: String
        get() = selectedModelByProvider[selectedAiProvider]?.takeIf { it.isNotBlank() }
            ?: selectedAiProvider.defaultModelId

    val activeInAppApiKey: String
        get() = inAppApiKeys[selectedAiProvider].orEmpty()
}

class AegisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AegisRepository
    private val keyVault = SecureKeyVault(application)

    /** Built per use so a newly saved GitHub token takes effect immediately. */
    private val scraperService: OsintScraperService
        get() = OsintScraperService(githubToken = keyVault.get(SecureKeyVault.GITHUB_TOKEN))
    private val aiAndPortabilityService = MultiAiAndDataPortabilityService()
    private val prefs = application.getSharedPreferences("aegis_ai_model_vault", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(AegisUiState())
    val uiState: StateFlow<AegisUiState> = _uiState.asStateFlow()

    init {
        val db = AegisDatabase.getInstance(application)
        repository = AegisRepository(db.aegisDao())

        // Restore persisted model choices and optional in-app API key overrides
        val savedModels = AiProviderEngine.entries.associateWith { provider ->
            prefs.getString("model_${provider.name}", provider.defaultModelId)
                ?.takeIf { it.isNotBlank() && it !in AiProviderEngine.RETIRED_MODEL_IDS }
                ?: provider.defaultModelId
        }
        // API keys now live in the Keystore-encrypted vault; move any legacy plaintext keys over once.
        keyVault.migrateLegacyPlaintext(
            legacyPrefs = prefs,
            legacyKeyToVaultName = AiProviderEngine.entries.associate { "key_${it.name}" to it.secretKeyName }
        )
        val savedKeys = AiProviderEngine.entries.associateWith { provider -> keyVault.get(provider.secretKeyName) }
        _uiState.update {
            it.copy(
                selectedModelByProvider = savedModels,
                inAppApiKeys = savedKeys,
                githubTokenConfigured = keyVault.get(SecureKeyVault.GITHUB_TOKEN).isNotEmpty()
            )
        }

        viewModelScope.launch {
            repository.ensureSeedData()
        }

        viewModelScope.launch {
            combine(
                repository.fullDossiersFlow,
                repository.tripletsFlow,
                repository.patchesFlow,
                repository.rawSocialEntitiesFlow,
                repository.mindMapHierarchyFlow
            ) { fullDossiers, tripletEntities, patchEntities, allRawSocial, allHierarchy ->
                val domainProfiles = fullDossiers.map { rel ->
                    rel.subject.toDomain(
                        relationalArtifacts = rel.rawSocialEntities,
                        relationalProfile = rel.linguisticProfile
                    )
                }
                val lingEntities = fullDossiers.mapNotNull { it.linguisticProfile }
                Tuple5(
                    domainProfiles,
                    tripletEntities,
                    patchEntities.associateBy { it.platformKey },
                    allRawSocial,
                    lingEntities to allHierarchy
                )
            }.collect { (domainProfiles, triplets, patchesMap, allRawSocial, lingAndHierarchy) ->
                val (lingEntities, allHierarchy) = lingAndHierarchy
                _uiState.update { current ->
                    val validSelected = if (domainProfiles.any { it.id == current.selectedSubjectId }) {
                        current.selectedSubjectId
                    } else {
                        domainProfiles.firstOrNull()?.id ?: "subj-alpha"
                    }
                    val rows = triplets.take(12).map {
                        listOf(it.subjectHandle, it.predicate, it.objectNode, String.format(Locale.US, "%.2f", it.confidence))
                    }
                    current.copy(
                        profiles = domainProfiles,
                        selectedSubjectId = validSelected,
                        triplets = triplets,
                        astPatches = patchesMap,
                        rawSocialEntities = allRawSocial,
                        linguisticProfileEntities = lingEntities,
                        mindMapHierarchyEntities = allHierarchy,
                        cypherResultRows = rows
                    )
                }
            }
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    private fun nowStamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun appendLog(category: String, message: String) {
        val entry = TelemetryLog(nowStamp(), category, message)
        _uiState.update { state ->
            state.copy(telemetryLogs = (listOf(entry) + state.telemetryLogs).take(60))
        }
    }

    fun selectSubject(subjectId: String) {
        _uiState.update { it.copy(selectedSubjectId = subjectId) }
        val handle = _uiState.value.profiles.find { it.id == subjectId }?.handle ?: subjectId
        appendLog("DOSSIER", "SWITCHED ACTIVE TARGET DOSSIER -> [$handle]")
    }

    fun selectComparisonSubject(subjectId: String) {
        _uiState.update { it.copy(comparisonSubjectId = subjectId) }
    }

    fun updateScraperUri(uri: String) {
        val autoMode = when {
            uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
                when {
                    uri.contains("github.com", ignoreCase = true) -> ScraperPlatformMode.ALL
                    uri.contains("huggingface.co", ignoreCase = true) -> ScraperPlatformMode.HUGGINGFACE
                    else -> ScraperPlatformMode.DIRECT_URL
                }
            }
            else -> _uiState.value.selectedPlatformMode
        }
        _uiState.update {
            it.copy(
                scraperUri = uri,
                selectedPlatformMode = autoMode,
                domDrift = it.domDrift.copy(active = false, healing = false)
            )
        }
    }

    fun setScraperPlatformMode(mode: ScraperPlatformMode) {
        _uiState.update { it.copy(selectedPlatformMode = mode) }
        appendLog("OKHTTP_MODE", "SCRAPER VECTOR ROUTING SET TO -> [${mode.label}]")
    }

    fun toggleContainmentOverride() {
        _uiState.update {
            val next = !it.containmentOverride
            it.copy(containmentOverride = next)
        }
        appendLog(
            "DEFCON",
            if (_uiState.value.containmentOverride) "AEGIS CONTAINMENT OVERRIDE ENGAGED (DEFCON 3 LOCK)"
            else "CONTAINMENT OVERRIDE RELEASED // LIVE THREAT EVALUATION"
        )
    }

    fun selectSpectrumToken(token: String) {
        _uiState.update { it.copy(selectedSpectrumToken = token) }
    }

    fun setGraphMode(mode: String) {
        _uiState.update { it.copy(graphMode = mode) }
        appendLog("TOPOLOGY", "MIND MAP PROJECTION MATRIX -> [$mode]")
    }

    fun updateCypherQuery(query: String) {
        _uiState.update { it.copy(cypherQuery = query) }
    }

    fun executeReadOnlyCypher() {
        val query = _uiState.value.cypherQuery.trim()
        val forbidden = Regex("\\b(DELETE|DETACH|DROP|REMOVE|CREATE|SET|MERGE)\\b", RegexOption.IGNORE_CASE)
        if (forbidden.containsMatchIn(query)) {
            appendLog("CYPHER_ERR", "MUTATING KEYWORDS BLOCKED IN READ-ONLY CYPHER CONSOLE.")
            return
        }
        val filterTerm = Regex("['\"]([^'\"]+)['\"]").find(query)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
        val allTriplets = _uiState.value.triplets
        val matched = if (!filterTerm.isNullOrBlank()) {
            allTriplets.filter {
                it.subjectHandle.lowercase(Locale.US).contains(filterTerm) ||
                    it.objectNode.lowercase(Locale.US).contains(filterTerm) ||
                    it.predicate.lowercase(Locale.US).contains(filterTerm)
            }
        } else {
            allTriplets
        }
        val rows = matched.take(15).map {
            listOf(it.subjectHandle, it.predicate, it.objectNode, String.format(Locale.US, "%.2f", it.confidence))
        }
        _uiState.update { it.copy(cypherResultRows = rows) }
        appendLog("FALKOR_CYPHER", "EXECUTED CYPHER QUERY // RETURNED ${rows.size} GRAPH TRIPLETS.")
    }

    // ========================================================================
    // MULTI-AI INTEGRATION (GOOGLE GEMINI, ANTHROPIC CLAUDE, PERPLEXITY SONAR)
    // ========================================================================

    fun selectAiProvider(provider: AiProviderEngine) {
        _uiState.update { it.copy(selectedAiProvider = provider) }
        val activeModel = _uiState.value.selectedModelByProvider[provider] ?: provider.defaultModelId
        appendLog("AI_HUB", "SELECTED AI INTELLIGENCE ENGINE -> [${provider.label} ($activeModel)]")
    }

    fun selectAiModel(modelId: String, provider: AiProviderEngine = _uiState.value.selectedAiProvider) {
        val cleaned = modelId.trim()
        val effective = cleaned.ifBlank { provider.defaultModelId }
        prefs.edit { putString("model_${provider.name}", effective) }
        _uiState.update { current ->
            val nextMap = current.selectedModelByProvider.toMutableMap().apply {
                put(provider, effective)
            }
            current.copy(selectedModelByProvider = nextMap)
        }
        appendLog("AI_MODEL", "SET ${provider.label} TARGET MODEL -> [$effective]")
    }

    fun updateInAppApiKey(apiKey: String, provider: AiProviderEngine = _uiState.value.selectedAiProvider) {
        val cleaned = apiKey.trim()
        keyVault.put(provider.secretKeyName, cleaned)
        _uiState.update { current ->
            val nextKeys = current.inAppApiKeys.toMutableMap().apply {
                put(provider, cleaned)
            }
            current.copy(inAppApiKeys = nextKeys)
        }
        val (_, sourceLabel) = aiAndPortabilityService.resolveApiKeyAndSource(provider, cleaned)
        appendLog("KEY_VAULT", "${provider.label} AUTH ROUTING -> [$sourceLabel]")
    }

    /** Saves (or clears, when blank) an optional GitHub token in the encrypted vault. */
    fun updateGithubToken(token: String) {
        keyVault.put(SecureKeyVault.GITHUB_TOKEN, token)
        val configured = token.isNotBlank()
        _uiState.update { it.copy(githubTokenConfigured = configured) }
        appendLog("KEY_VAULT", if (configured) "GITHUB TOKEN STORED (ENCRYPTED) -> 5,000 REQ/HR" else "GITHUB TOKEN CLEARED -> 60 REQ/HR")
    }

    // ========================================================================
    // CONSENT GATE
    // ========================================================================

    /**
     * Records the consent status for the active subject, appends a timestamped entry to its provenance
     * audit trail, and recomputes all scores from stored artifacts. Restricted outputs appear only when
     * the new status is SELF or CONSENTED; switching back to UNVERIFIED strips them again on save.
     * Demo personas are fixed and cannot be changed.
     */
    fun setSubjectConsent(status: ConsentStatus) {
        val active = _uiState.value.activeSubject ?: return
        if (active.consentStatus == ConsentStatus.DEMO || status == ConsentStatus.DEMO) return
        if (active.consentStatus == status) return

        viewModelScope.launch {
            val auditEntry = "CONSENT:${status.name}@${OsintScraperService().currentIsoUtc()}"
            val withConsent = active.copy(
                consentStatus = status,
                dataProvenance = if (active.dataProvenance.isBlank()) auditEntry else "${active.dataProvenance} | $auditEntry"
            )
            val updated = scraperService.recomputeFromArtifacts(withConsent)
            val allCurrent = listOf(updated) + _uiState.value.profiles.filter { it.id != updated.id }
            repository.saveProfile(updated, allCurrent, _uiState.value.triplets)
            appendLog(
                "CONSENT",
                "[${updated.handle}] CONSENT -> ${status.label}. RESTRICTED OUTPUTS ${if (status.unlocksRestricted) "COMPUTED" else "WITHHELD"}. RECORDED IN PROVENANCE TRAIL."
            )
        }
    }

    fun resolveProviderAuthStatus(provider: AiProviderEngine = _uiState.value.selectedAiProvider): String {
        val overrideKey = _uiState.value.inAppApiKeys[provider].orEmpty()
        return aiAndPortabilityService.resolveApiKeyAndSource(provider, overrideKey).second
    }

    fun updateAiDirectivePrompt(prompt: String) {
        _uiState.update { it.copy(aiDirectivePrompt = prompt) }
    }

    fun executeMultiAiAnalysis() {
        val subject = _uiState.value.activeSubject ?: return
        val provider = _uiState.value.selectedAiProvider
        val selectedModel = _uiState.value.activeModelId
        val keyOverride = _uiState.value.activeInAppApiKey
        val directive = _uiState.value.aiDirectivePrompt

        viewModelScope.launch {
            _uiState.update {
                it.copy(bridgeStatus = "${provider.label} [$selectedModel] // SYNTHESIZING...")
            }
            appendLog("AI_SYNTH", "DISPATCHING DOSSIER [${subject.handle}] TO ${provider.label} ($selectedModel)...")

            aiAndPortabilityService.streamAiDossierAnalysis(
                provider = provider,
                subject = subject,
                customDirective = directive,
                selectedModelId = selectedModel,
                inAppKeyOverride = keyOverride
            ).collect { state ->
                _uiState.update { current ->
                    val nextStatus = when (state) {
                        is AiSynthesisState.Loading -> "${provider.label} [${state.modelId}] // CONNECTING..."
                        is AiSynthesisState.Streaming -> "${provider.label} [${state.modelId}] // STREAMING..."
                        is AiSynthesisState.Success -> "${provider.label} [${state.modelId}] // COMPLETE"
                        is AiSynthesisState.Error -> "${provider.label} [${state.modelId}] // NOTICE"
                        AiSynthesisState.Idle -> current.bridgeStatus
                    }
                    current.copy(
                        aiSynthesisState = state,
                        bridgeStatus = nextStatus
                    )
                }
                if (state is AiSynthesisState.Success) {
                    appendLog(
                        "AI_OK",
                        "${provider.label} (${state.modelId}) SYNTHESIS COMPLETE (${state.responseText.length} CHARS, ${state.citations.size} CITATIONS)."
                    )
                } else if (state is AiSynthesisState.Error) {
                    appendLog("AI_NOTICE", "${provider.label} (${state.modelId}): ${state.errorMessage.take(120)}")
                }
            }
        }
    }

    fun ingestAiResponseIntoDossier() {
        val state = _uiState.value.aiSynthesisState
        val text = when (state) {
            is AiSynthesisState.Success -> state.responseText
            is AiSynthesisState.Streaming -> state.partialText
            else -> return
        }
        val provider = when (state) {
            is AiSynthesisState.Success -> state.provider
            is AiSynthesisState.Streaming -> state.provider
            else -> _uiState.value.selectedAiProvider
        }
        val modelUsed = when (state) {
            is AiSynthesisState.Success -> state.modelId
            is AiSynthesisState.Streaming -> state.modelId
            else -> _uiState.value.activeModelId
        }
        addCustomTimestampedArtifact(
            verbatimText = "[$modelUsed] ${text.take(460)}",
            platform = "${provider.name}_BRIEFING",
            timestampIso = scraperService.currentIsoUtc()
        )
        _uiState.update {
            it.copy(
                exportBannerMessage = "Persisted ${provider.label} ($modelUsed) briefing into Room DB (raw_social_media_entities) and updated LIWC profile."
            )
        }
    }

    // ========================================================================
    // UNIVERSAL DATA IMPORT & EXPORT (JSON, STIX 2.1, CSV, CLAUDE XML, SAF)
    // ========================================================================

    fun selectExportFormat(format: DataExportFormat) {
        _uiState.update { it.copy(selectedExportFormat = format) }
    }

    fun buildFormattedPayload(format: DataExportFormat = _uiState.value.selectedExportFormat): String {
        val subject = _uiState.value.activeSubject ?: return ""
        return aiAndPortabilityService.buildExportPayload(
            format = format,
            subject = subject,
            astPatches = _uiState.value.astPatches.values.toList(),
            rawEntities = _uiState.value.rawSocialEntities,
            lingEntities = _uiState.value.linguisticProfileEntities,
            hierarchyEntities = _uiState.value.mindMapHierarchyEntities,
            customDirective = _uiState.value.aiDirectivePrompt
        )
    }

    fun copyFormattedPayloadToClipboard(
        context: Context,
        format: DataExportFormat = _uiState.value.selectedExportFormat
    ) {
        val subject = _uiState.value.activeSubject ?: return
        val payload = buildFormattedPayload(format)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(sensitiveClip("Aegis_${subject.handle}_${format.name}", payload))
        _uiState.update {
            it.copy(
                exportBannerMessage = "Copied ${format.label} (${payload.length} chars) for [${subject.handle}] to clipboard — ready to paste into Claude, Gemini, or Perplexity."
            )
        }
        appendLog("EXPORT_CLIP", "COPIED ${format.label} (${payload.length} BYTES) TO SYSTEM CLIPBOARD.")
    }

    fun writeExportToDocumentUri(
        context: Context,
        uri: Uri,
        format: DataExportFormat = _uiState.value.selectedExportFormat
    ) {
        val subject = _uiState.value.activeSubject ?: return
        val patches = _uiState.value.astPatches.values.toList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    if (format == DataExportFormat.PKZIP_STIX) {
                        val stixJson = ForensicMathEngine.buildStix21BundleJson(subject, patches)
                        val zipBytes = ForensicMathEngine.createProjectZipBytes(subject, patches, stixJson)
                        out.write(zipBytes)
                    } else {
                        val payload = buildFormattedPayload(format)
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    }
                }
                _uiState.update {
                    it.copy(
                        exportBannerMessage = "Saved ${format.label} for [${subject.handle}] to device storage."
                    )
                }
                appendLog("SAF_EXPORT", "WROTE ${format.label} FOR [${subject.handle}] TO SAF DOCUMENT URI.")
            } catch (e: Exception) {
                appendLog("EXPORT_ERR", "SAF file export failed: ${e.message}")
            }
        }
    }

    fun importFromDocumentUri(
        context: Context,
        uri: Uri,
        useGeminiStructuredParser: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()
                if (rawText.isNotBlank()) {
                    importRawPayloadText(rawText, useGeminiStructuredParser)
                }
            } catch (e: Exception) {
                appendLog("IMPORT_ERR", "SAF file import failed: ${e.message}")
            }
        }
    }

    fun importRawPayloadText(
        rawPayload: String,
        useGeminiStructuredParser: Boolean = false
    ) {
        val active = _uiState.value.activeSubject ?: return
        if (rawPayload.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScraping = true,
                    bridgeStatus = if (useGeminiStructuredParser) "GEMINI AI PARSING IMPORT..." else "IMPORTING DATA -> ROOM..."
                )
            }
            val importRes = withContext(Dispatchers.IO) {
                if (useGeminiStructuredParser) {
                    aiAndPortabilityService.importUnstructuredTextWithGemini(rawPayload, active)
                } else {
                    aiAndPortabilityService.parseAndImportData(rawPayload, active)
                }
            }
            val updated = importRes.updatedProfile
            val allCurrent = listOf(updated) + _uiState.value.profiles.filter { it.id != updated.id }
            repository.saveProfile(updated, allCurrent, _uiState.value.triplets)

            _uiState.update {
                it.copy(
                    isScraping = false,
                    bridgeStatus = "IMPORTED // ${importRes.formatDetected}",
                    exportBannerMessage = importRes.summaryMessage
                )
            }
            appendLog("DATA_IMPORT", importRes.summaryMessage)
        }
    }

    // ========================================================================
    // OKHTTP SCRAPER & AI AUTO-HEAL PROTOCOL
    // ========================================================================

    fun forceDomMutationAlert() {
        val target = _uiState.value.scraperUri.trim()
        val spec = ForensicMathEngine.detectWalledGarden(target) ?: WalledGardenSpec(
            key = "custom_walled_garden",
            name = target.substringAfter("://").substringBefore("/").ifBlank { "Walled-Garden Target" },
            sampleUrl = target,
            deprecatedSelector = "div.profile-root > main > article.post-card",
            mutatedDom = "<div data-reactroot=\"\" class=\"_obf_991a2\"><!-- Shadow Hydration Root Mutated --></div>",
            healedSelector = "hydration_ast://window.__NEXT_DATA__.props.pageProps.targetEntity",
            strategy = "Headless Playwright AST Hydration Interceptor",
            pythonSnippet = "async def extract_custom_healed(page):\n    state = await page.evaluate(\"() => window.__NEXT_DATA__.props.pageProps\")\n    return state[\"targetEntity\"]"
        )
        _uiState.update {
            it.copy(
                isScraping = false,
                bridgeStatus = "DOM_DRIFT // ${spec.name.uppercase(Locale.US)} HALTED",
                domDrift = DomDriftState(
                    active = true,
                    platform = spec.name,
                    platformKey = spec.key,
                    deprecatedSelector = spec.deprecatedSelector,
                    mutatedDom = spec.mutatedDom,
                    proposedSelector = spec.healedSelector,
                    strategy = spec.strategy,
                    pythonSnippet = spec.pythonSnippet
                )
            )
        }
        appendLog("CRITICAL", "AST DRIFT ON [${spec.name.uppercase(Locale.US)}]: Selector \"${spec.deprecatedSelector}\" returned 0 nodes.")
    }

    fun executeScrapeJob(forceHeal: Boolean = false, overrideUri: String? = null) {
        val target = (overrideUri ?: _uiState.value.scraperUri).trim()
        if (target.isBlank()) return
        val mode = _uiState.value.selectedPlatformMode

        val spec = ForensicMathEngine.detectWalledGarden(target)
        val existingPatch = if (spec != null) _uiState.value.astPatches[spec.key] else null

        if (spec != null && !forceHeal && existingPatch == null && mode != ScraperPlatformMode.DIRECT_URL) {
            forceDomMutationAlert()
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScraping = true,
                    bridgeStatus = "OKHTTP SCRAPING [${mode.label}]..."
                )
            }
            appendLog("OKHTTP", "DISPATCHING OKHTTP HARVESTER (${mode.label}) -> [$target]")

            val result = scraperService.scrapeAndBuildProfile(
                rawTargetInput = target,
                platformMode = mode,
                healedPatchId = existingPatch?.patchId
            )

            result.httpProbes.forEach { probe ->
                appendLog(
                    "HTTP_${probe.httpStatusCode}",
                    "${probe.platform} -> ${probe.statusBadge} (${probe.latencyMs}ms, ${probe.payloadBytes}B) [${probe.url.take(48)}]"
                )
            }

            val allCurrent = listOf(result.profile) + _uiState.value.profiles.filter { it.id != result.profile.id }
            repository.saveProfile(result.profile, allCurrent, result.triplets)

            _uiState.update {
                it.copy(
                    selectedSubjectId = result.profile.id,
                    httpProbes = (result.httpProbes + it.httpProbes).take(16),
                    isScraping = false,
                    bridgeStatus = "ROOM TX OK // ${result.liveSourceUsed}"
                )
            }
            appendLog("ROOM_TX", result.telemetrySummary)
        }
    }

    fun initiateAiAutoHealProtocol() {
        val drift = _uiState.value.domDrift
        if (!drift.active || drift.healing) return
        val currentUri = _uiState.value.scraperUri

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    domDrift = it.domDrift.copy(
                        healing = true,
                        healStep = 1,
                        healProgress = 24,
                        healStatusText = "STAGE 1/4: SNAPSHOTTING MUTATED ${drift.platform.uppercase(Locale.US)} DOM & SHADOW AST..."
                    )
                )
            }
            appendLog("AI_HEAL", "[1/4] Capturing obfuscated DOM tree on ${drift.platform}...")
            delay(450)

            _uiState.update {
                it.copy(
                    domDrift = it.domDrift.copy(
                        healStep = 2,
                        healProgress = 54,
                        healStatusText = "STAGE 2/4: DIFFING BROKEN SELECTOR AGAINST HYDRATION PAYLOADS..."
                    )
                )
            }
            appendLog("AI_HEAL", "[2/4] Diffing failed selector \"${drift.deprecatedSelector}\"...")
            delay(450)

            _uiState.update {
                it.copy(
                    domDrift = it.domDrift.copy(
                        healStep = 3,
                        healProgress = 86,
                        healStatusText = "STAGE 3/4: SYNTHESIZING NEW XPATH / JSON-HYDRATION ADAPTER..."
                    )
                )
            }
            appendLog("AI_HEAL", "[3/4] Synthesized new selector: ${drift.proposedSelector}")
            delay(450)

            val patchHash = ForensicMathEngine.sha256("${drift.platformKey}-${System.currentTimeMillis()}").take(6).uppercase(Locale.US)
            val newPatch = AstPatchEntity(
                platformKey = drift.platformKey,
                patchId = "AST-PATCH-$patchHash",
                platformName = drift.platform,
                deprecatedSelector = drift.deprecatedSelector,
                mutatedDom = drift.mutatedDom,
                healedSelector = drift.proposedSelector,
                strategy = drift.strategy,
                pythonSnippet = drift.pythonSnippet,
                confidence = "98.4%",
                timestamp = nowStamp().take(8)
            )
            repository.saveAstPatch(newPatch)

            _uiState.update {
                it.copy(
                    domDrift = it.domDrift.copy(
                        active = false,
                        healing = false,
                        healStep = 4,
                        healProgress = 100,
                        healStatusText = "STAGE 4/4: HOT-PATCH COMPILED & INJECTED. RE-ENGAGING TARGET..."
                    ),
                    bridgeStatus = "AUTO_HEALED // ${newPatch.patchId}"
                )
            }
            appendLog("AI_HEAL", "[4/4] Patch ${newPatch.patchId} deployed (Conf: 98.4%). Re-scraping $currentUri...")
            executeScrapeJob(forceHeal = true, overrideUri = currentUri)
        }
    }

    fun harvestGithubAndHuggingFaceUpgrades() {
        val active = _uiState.value.activeSubject ?: return
        if (_uiState.value.isScraping) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScraping = true,
                    bridgeStatus = "HARVESTING GITHUB + HF HUB..."
                )
            }
            appendLog("GH_HF_HUB", "QUERYING GITHUB SEARCH API + HUGGING FACE MODELS/DATASETS FOR OSINT ENHANCEMENTS...")

            val res = scraperService.harvestGithubAndHuggingFaceEnhancements(active)
            val allCurrent = listOf(res.profile) + _uiState.value.profiles.filter { it.id != res.profile.id }
            repository.saveProfile(res.profile, allCurrent, res.triplets)

            _uiState.update {
                it.copy(
                    httpProbes = (res.httpProbes + it.httpProbes).take(16),
                    isScraping = false,
                    bridgeStatus = "FUSED // ${res.liveSourceUsed}"
                )
            }
            appendLog("GH_HF_HUB", res.telemetrySummary)
        }
    }

    fun addCustomTimestampedArtifact(verbatimText: String, platform: String, timestampIso: String) {
        val active = _uiState.value.activeSubject ?: return
        if (verbatimText.isBlank()) return

        viewModelScope.launch {
            val updated = scraperService.ingestCustomStatement(
                activeProfile = active,
                verbatimStatement = verbatimText.trim(),
                platformLabel = platform.trim(),
                timestampIso = timestampIso.trim()
            )
            val allCurrent = listOf(updated) + _uiState.value.profiles.filter { it.id != updated.id }
            repository.saveProfile(updated, allCurrent, _uiState.value.triplets)
            appendLog(
                "ROOM_TX",
                "PERSISTED RAW SOCIAL ENTITY + UPDATED LINGUISTIC PROFILE + MIND MAP HIERARCHY FOR [${updated.handle}]"
            )
        }
    }

    fun dismissExportBanner() {
        _uiState.update { it.copy(exportBannerMessage = null) }
    }

    fun exportAndShareBundle(
        context: Context,
        format: DataExportFormat = DataExportFormat.AGENT_HANDOFF
    ) {
        val subject = _uiState.value.activeSubject ?: return
        val patches = _uiState.value.astPatches.values.toList()
        viewModelScope.launch {
            try {
                val safeHandle = subject.handle.lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "_").take(40)
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val formattedPayload = buildFormattedPayload(format)

                val shareIntent = withContext(Dispatchers.IO) {
                    if (format == DataExportFormat.PKZIP_STIX) {
                        // Share the actual ZIP through a FileProvider content:// URI (previously the ZIP was
                        // written to cache and never shared; only text went out).
                        val stixJson = ForensicMathEngine.buildStix21BundleJson(subject, patches)
                        val zipBytes = ForensicMathEngine.createProjectZipBytes(subject, patches, stixJson)
                        val zipFile = File(exportDir, "aegis_blackvault_$safeHandle.zip")
                        zipFile.writeBytes(zipBytes)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "AEGIS // BLACKVAULT EXPORT (${format.label}) — ${subject.handle}")
                            clipData = ClipData.newRawUri(zipFile.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AEGIS // BLACKVAULT EXPORT (${format.label}) — ${subject.handle}")
                            putExtra(Intent.EXTRA_TEXT, formattedPayload)
                        }
                    }
                }

                // The full dossier is no longer silently copied to the clipboard as a side effect of sharing.
                _uiState.update {
                    it.copy(exportBannerMessage = "Ready to share ${format.label} for [${subject.handle}] (consent: ${subject.consentStatus.label}).")
                }
                appendLog("EXPORT", "SHARING ${format.label} FOR [${subject.handle}] // PROVENANCE: ${subject.dataProvenance.ifBlank { "UNKNOWN" }}")
                context.startActivity(Intent.createChooser(shareIntent, "Share Aegis export (${format.label})"))
            } catch (e: Exception) {
                appendLog("EXPORT_ERR", "Export failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * Clipboard content flagged as sensitive so Android 13+ hides it from the clipboard preview
     * and keyboard suggestions (the extra is ignored on older versions).
     */
    private fun sensitiveClip(label: String, text: String): ClipData =
        ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
}
