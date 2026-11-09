package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ForensicMathEngine
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun InsightsAndExportScreen(
    activeSubject: SubjectProfile,
    astPatches: List<AstPatchEntity>,
    rawSocialEntities: List<RawSocialMediaEntity>,
    linguisticProfileEntities: List<LinguisticPersonalityProfileEntity>,
    mindMapHierarchyEntities: List<MindMapHierarchyEntity>,
    cypherQuery: String,
    cypherRows: List<List<String>>,
    selectedAiProvider: AiProviderEngine = AiProviderEngine.GEMINI,
    selectedModelByProvider: Map<AiProviderEngine, String> = mapOf(
        AiProviderEngine.GEMINI to AiProviderEngine.GEMINI.defaultModelId,
        AiProviderEngine.CLAUDE to AiProviderEngine.CLAUDE.defaultModelId,
        AiProviderEngine.PERPLEXITY to AiProviderEngine.PERPLEXITY.defaultModelId
    ),
    inAppApiKeys: Map<AiProviderEngine, String> = emptyMap(),
    activeAuthStatusLabel: String = "FIREBASE AI LOGIC (ZERO-KEY)",
    aiDirectivePrompt: String = "",
    aiSynthesisState: AiSynthesisState = AiSynthesisState.Idle,
    selectedExportFormat: DataExportFormat = DataExportFormat.ROOM_JSON,
    formattedExportPreview: String = "",
    exportBannerMessage: String?,
    onSelectAiProvider: (AiProviderEngine) -> Unit = {},
    onSelectAiModel: (String) -> Unit = {},
    onUpdateInAppApiKey: (String) -> Unit = {},
    githubTokenConfigured: Boolean = false,
    onUpdateGithubToken: (String) -> Unit = {},
    onUpdateAiDirective: (String) -> Unit = {},
    onExecuteAiSynthesis: () -> Unit = {},
    onIngestAiResponse: () -> Unit = {},
    onSelectExportFormat: (DataExportFormat) -> Unit = {},
    onCopyFormattedPayload: () -> Unit = {},
    onSaveExportToUri: (Uri) -> Unit = {},
    onImportFromUri: (Uri, Boolean) -> Unit = { _, _ -> },
    onImportRawPayload: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateCypherQuery: (String) -> Unit,
    onExecuteCypher: () -> Unit,
    onExportAndShare: () -> Unit,
    onDismissExportBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf("AI_PORTABILITY") }
    var importPasteBuffer by remember { mutableStateOf("") }
    var useGeminiForFileImport by remember { mutableStateOf(false) }
    var showApiKeyVault by remember { mutableStateOf(false) }
    var githubTokenDraft by remember { mutableStateOf("") }

    val activeModelId = selectedModelByProvider[selectedAiProvider]?.takeIf { it.isNotBlank() }
        ?: selectedAiProvider.defaultModelId
    val activeInAppKey = inAppApiKeys[selectedAiProvider].orEmpty()

    val stixBundleJson = remember(activeSubject, astPatches) {
        ForensicMathEngine.buildStix21BundleJson(activeSubject, astPatches)
    }

    val subjectRawEntities = remember(rawSocialEntities, activeSubject.id) {
        rawSocialEntities.filter { it.subjectId == activeSubject.id }
    }
    val subjectHierarchy = remember(mindMapHierarchyEntities, activeSubject.id) {
        mindMapHierarchyEntities.filter { it.rootSubjectId == activeSubject.id }
    }

    // Zero-permission Storage Access Framework (SAF) Launchers for File Export & Import
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedExportFormat.mimeType)
    ) { uri: Uri? ->
        if (uri != null) {
            onSaveExportToUri(uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportFromUri(uri, useGeminiForFileImport)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (exportBannerMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_status_banner")
                    .background(PhosphorGreen.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreen, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exportBannerMessage,
                    color = PhosphorGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissExportBanner) {
                    Text("OK", color = AmberSignal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section Switcher (Including AI + IMPORT/EXPORT Hub)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "AI_PORTABILITY" to "AI & I/O",
                "INSIGHTS" to "INSIGHTS",
                "GH_HF_STACK" to "GH + HF",
                "ROOM_SCHEMA" to "ROOM DB",
                "CYPHER_STIX" to "STIX 2.1"
            ).forEach { (key, label) ->
                val isSel = activeSection == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("insights_subtab_$key")
                        .background(
                            if (isSel) PhosphorGreen else ObsidianBg,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (isSel) PhosphorGreen else PhosphorGreenDim,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { activeSection = key }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSel) ObsidianBg else PhosphorGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // One-Tap Full Dossier + STIX 2.1 + PKZIP Archive Export Button
        Button(
            onClick = onExportAndShare,
            colors = ButtonDefaults.buttonColors(
                containerColor = PhosphorGreen,
                contentColor = ObsidianBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_zip_stix_button")
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Export Project ZIP and STIX 2.1",
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "EXPORT FULL DOSSIER .ZIP + STIX 2.1 BUNDLE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        when (activeSection) {
            "AI_PORTABILITY" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Multi-AI Intelligence Bridge: Google Gemini, Anthropic Claude, Perplexity Sonar
                    item {
                        val providerColor = when (selectedAiProvider) {
                            AiProviderEngine.GEMINI -> CyanTelemetry
                            AiProviderEngine.CLAUDE -> AmberSignal
                            AiProviderEngine.PERPLEXITY -> VioletHf
                        }
                        val activePresetMeta = selectedAiProvider.presetModels.find { it.modelId == activeModelId }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, providerColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Multi-AI Bridge",
                                        tint = providerColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "MULTI-AI INTELLIGENCE BRIDGE & MODEL SELECTOR",
                                            color = providerColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "AUTH: $activeAuthStatusLabel",
                                            color = PhosphorGreen,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .testTag("toggle_api_key_vault_btn")
                                        .background(
                                            if (showApiKeyVault) providerColor.copy(alpha = 0.22f) else ObsidianBg,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(1.dp, providerColor, RoundedCornerShape(4.dp))
                                        .clickable { showApiKeyVault = !showApiKeyVault }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (showApiKeyVault) "HIDE KEY VAULT" else "API KEY VAULT",
                                        color = providerColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 1A. AI Provider Selector Chips (Google Gemini, Anthropic Claude, Perplexity Sonar)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AiProviderEngine.entries.forEach { provider ->
                                    val isSel = selectedAiProvider == provider
                                    val chipColor = when (provider) {
                                        AiProviderEngine.GEMINI -> CyanTelemetry
                                        AiProviderEngine.CLAUDE -> AmberSignal
                                        AiProviderEngine.PERPLEXITY -> VioletHf
                                    }
                                    val chosenModelForProvider = selectedModelByProvider[provider] ?: provider.defaultModelId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ai_provider_chip_${provider.name.lowercase(Locale.US)}")
                                            .background(
                                                if (isSel) chipColor.copy(alpha = 0.22f) else ObsidianBg,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSel) chipColor else PhosphorGreenDim,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onSelectAiProvider(provider) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = provider.label,
                                                color = if (isSel) chipColor else TextPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = chosenModelForProvider.take(20),
                                                color = if (isSel) TextPrimary else TextSecondary,
                                                fontSize = 7.5.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 1B. Full Model Picker for Active Provider (Preset Catalog + Custom Model ID Entry)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(5.dp))
                                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(5.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SELECT ${selectedAiProvider.label} MODEL (${selectedAiProvider.presetModels.size} PRESETS + CUSTOM):",
                                        color = providerColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "DEFAULT",
                                        color = AmberSignal,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onSelectAiModel(selectedAiProvider.defaultModelId) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(selectedAiProvider.presetModels, key = { it.modelId }) { modelOpt ->
                                        val isModelSelected = activeModelId == modelOpt.modelId
                                        Box(
                                            modifier = Modifier
                                                .testTag("ai_model_chip_${modelOpt.modelId}")
                                                .background(
                                                    if (isModelSelected) providerColor.copy(alpha = 0.22f) else ObsidianPanel,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isModelSelected) providerColor else PhosphorGreenDim,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { onSelectAiModel(modelOpt.modelId) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = modelOpt.displayName,
                                                        color = if (isModelSelected) providerColor else TextPrimary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "[${modelOpt.tierBadge}]",
                                                        color = PhosphorGreen,
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = modelOpt.modelId,
                                                    color = TextSecondary,
                                                    fontSize = 7.5.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                if (activePresetMeta != null) {
                                    Text(
                                        text = activePresetMeta.summary,
                                        color = TextSecondary,
                                        fontSize = 8.5.sp
                                    )
                                }

                                // Custom / Exact Model ID Input Field (works for any provider)
                                OutlinedTextField(
                                    value = activeModelId,
                                    onValueChange = onSelectAiModel,
                                    singleLine = true,
                                    label = {
                                        Text(
                                            "Active / Custom Model ID for ${selectedAiProvider.label} (type any model snapshot)",
                                            fontSize = 8.5.sp
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = providerColor,
                                        unfocusedBorderColor = PhosphorGreenDim,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_custom_model_input")
                                )
                            }

                            // 1C. Collapsible API Key Vault (supports GEMINI_API_KEY, ANTHROPIC_API_KEY, PERPLEXITY_API_KEY)
                            if (showApiKeyVault) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF070D0A), RoundedCornerShape(5.dp))
                                        .border(1.dp, providerColor, RoundedCornerShape(5.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "API KEY VAULT // ${selectedAiProvider.label} (${selectedAiProvider.secretKeyName})",
                                        color = providerColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (selectedAiProvider) {
                                            AiProviderEngine.GEMINI -> "Google Gemini works automatically via Firebase AI Logic (zero-key), OR you can add GEMINI_API_KEY in the AI Studio Secrets panel or paste your Gemini API key below to call Google's Generative Language REST API directly with any model."
                                            AiProviderEngine.CLAUDE -> "Reads ANTHROPIC_API_KEY from the AI Studio Secrets panel automatically, or you can paste/override your Anthropic API key (sk-ant-...) directly below."
                                            AiProviderEngine.PERPLEXITY -> "Optional: Add PERPLEXITY_API_KEY in the AI Studio Secrets panel or paste it below when available. Without a Perplexity key, use Google Gemini or Anthropic Claude for live synthesis, or copy the Perplexity OSINT query below."
                                        },
                                        color = TextSecondary,
                                        fontSize = 8.5.sp,
                                        lineHeight = 12.5.sp
                                    )
                                    OutlinedTextField(
                                        value = activeInAppKey,
                                        onValueChange = onUpdateInAppApiKey,
                                        singleLine = true,
                                        // Keys are masked on screen (they are stored encrypted with Android Keystore).
                                        visualTransformation = PasswordVisualTransformation(),
                                        label = {
                                            Text(
                                                "Optional In-App ${selectedAiProvider.secretKeyName} Override (leave blank to use AI Studio Secrets)",
                                                fontSize = 8.5.sp
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = providerColor,
                                            unfocusedBorderColor = PhosphorGreenDim,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ai_in_app_key_input")
                                    )

                                    // Optional GitHub token: raises the GitHub API limit from 60 to 5,000 requests/hour.
                                    OutlinedTextField(
                                        value = githubTokenDraft,
                                        onValueChange = { githubTokenDraft = it },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        label = {
                                            Text(
                                                if (githubTokenConfigured) {
                                                    "GitHub token: stored (encrypted). Enter a new one to replace it, or save blank to clear"
                                                } else {
                                                    "Optional GitHub token (read-only, public access): 60 -> 5,000 requests/hr"
                                                },
                                                fontSize = 8.5.sp
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = providerColor,
                                            unfocusedBorderColor = PhosphorGreenDim,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("github_token_input")
                                    )
                                    Box(
                                        modifier = Modifier
                                            .testTag("save_github_token_btn")
                                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                                            .border(1.dp, providerColor, RoundedCornerShape(4.dp))
                                            .clickable {
                                                onUpdateGithubToken(githubTokenDraft)
                                                githubTokenDraft = ""
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (githubTokenDraft.isBlank() && githubTokenConfigured) "CLEAR GITHUB TOKEN" else "SAVE GITHUB TOKEN",
                                            color = providerColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = aiDirectivePrompt,
                                onValueChange = onUpdateAiDirective,
                                label = {
                                    Text(
                                        "Analyst Prompt / Directive for ${selectedAiProvider.label} ($activeModelId)",
                                        fontSize = 9.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = providerColor,
                                    unfocusedBorderColor = PhosphorGreenDim,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_directive_input")
                            )

                            Button(
                                onClick = onExecuteAiSynthesis,
                                enabled = aiSynthesisState !is AiSynthesisState.Loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = providerColor,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("execute_ai_synthesis_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Execute AI Synthesis",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (aiSynthesisState is AiSynthesisState.Loading) {
                                        "SYNTHESIZING WITH ${selectedAiProvider.label} ($activeModelId)..."
                                    } else {
                                        "ANALYZE [${activeSubject.handle}] WITH ${selectedAiProvider.label} ($activeModelId)"
                                    },
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // AI Synthesis Output Card
                            when (val state = aiSynthesisState) {
                                is AiSynthesisState.Loading -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = providerColor,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = state.statusText,
                                            color = providerColor,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                is AiSynthesisState.Streaming -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                                            .border(1.dp, CyanTelemetry, RoundedCornerShape(4.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "LIVE STREAM // ${state.provider.label} [${state.modelId}]:",
                                            color = CyanTelemetry,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = state.partialText,
                                            color = TextPrimary,
                                            fontSize = 10.5.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                is AiSynthesisState.Success -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                                            .border(1.dp, PhosphorGreen, RoundedCornerShape(4.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${state.provider.label} [${state.modelId}] SYNTHESIS:",
                                                    color = PhosphorGreen,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (state.authSourceLabel.isNotBlank()) {
                                                    Text(
                                                        text = "VIA ${state.authSourceLabel}",
                                                        color = TextSecondary,
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(PhosphorGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .border(1.dp, PhosphorGreen, RoundedCornerShape(4.dp))
                                                    .clickable { onIngestAiResponse() }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "+ SAVE TO ROOM DB",
                                                    color = PhosphorGreen,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = state.responseText,
                                            color = TextPrimary,
                                            fontSize = 10.5.sp,
                                            lineHeight = 15.sp
                                        )
                                        if (state.citations.isNotEmpty()) {
                                            Text(
                                                text = "PERPLEXITY WEB CITATIONS (${state.citations.size}):",
                                                color = VioletHf,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            state.citations.forEachIndexed { idx, url ->
                                                Text(
                                                    text = "[${idx + 1}] $url",
                                                    color = CyanTelemetry,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                is AiSynthesisState.Error -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(AmberSignal.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                            .border(1.dp, AmberSignal, RoundedCornerShape(4.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${state.provider.label} [${state.modelId}] STATUS:",
                                            color = AmberSignal,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = state.errorMessage,
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }

                                AiSynthesisState.Idle -> {
                                    Text(
                                        text = "TIP: Google Gemini uses Firebase AI Logic (App Check attested). For direct Anthropic Claude or Perplexity Sonar API calls, add ANTHROPIC_API_KEY and PERPLEXITY_API_KEY in the AI Studio Secrets panel, or export their formatted bundles below.",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2. Multi-Format Export Hub (Room JSON, CSV, Claude XML, Gemini MD, Perplexity, STIX ZIP)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreen, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SaveAlt,
                                    contentDescription = "Export Hub",
                                    tint = PhosphorGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "MULTI-FORMAT EXPORT HUB (JSON • CSV • CLAUDE XML • GEMINI • PERPLEXITY)",
                                    color = PhosphorGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DataExportFormat.entries) { fmt ->
                                    val isSel = selectedExportFormat == fmt
                                    Box(
                                        modifier = Modifier
                                            .testTag("export_format_chip_${fmt.name.lowercase(Locale.US)}")
                                            .background(
                                                if (isSel) PhosphorGreen.copy(alpha = 0.22f) else ObsidianBg,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSel) PhosphorGreen else PhosphorGreenDim,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onSelectExportFormat(fmt) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = fmt.label,
                                            color = if (isSel) PhosphorGreen else TextPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Action Row: Copy to Clipboard, Save File (SAF), Share Sheet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = onCopyFormattedPayload,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberSignal,
                                        contentColor = ObsidianBg
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("copy_export_payload_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("COPY", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val fileName = "aegis_${activeSubject.handle.lowercase(Locale.US)}.${selectedExportFormat.extension}"
                                        createDocumentLauncher.launch(fileName)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanTelemetry,
                                        contentColor = ObsidianBg
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_file_saf_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SaveAlt,
                                        contentDescription = "Save File",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("SAVE FILE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = onExportAndShare,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PhosphorGreen,
                                        contentColor = ObsidianBg
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_formatted_payload_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("SHARE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Live Preview of Selected Export Format
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp, max = 160.dp)
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = formattedExportPreview.take(1800),
                                    color = PhosphorGreen,
                                    fontSize = 9.5.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }

                    // 3. Universal Data Import Hub (SAF File Picker + Paste JSON/STIX/CSV/Claude XML/Raw Text)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, AmberSignal.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Import Hub",
                                    tint = AmberSignal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "UNIVERSAL DATA IMPORT HUB (JSON • STIX 2.1 • CSV • CLAUDE XML • RAW TEXT)",
                                    color = AmberSignal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "Import external OSINT files (.json, .csv, .xml, .txt) from device storage or paste raw JSON / CSV / Claude / Gemini / Perplexity outputs below to merge into Room DB and recompute the LIWC & Mind Map profile:",
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        useGeminiForFileImport = false
                                        openDocumentLauncher.launch(arrayOf("*/*"))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberSignal,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_file_saf_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileOpen,
                                        contentDescription = "Import File",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("IMPORT FILE (.JSON/.CSV/.XML)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        importPasteBuffer = """
                                            entity_id,subject_handle,platform,posted_timestamp_iso,utc_hour,sentiment_score,lexical_markers,verbatim_text
                                            "art-sample-1","${activeSubject.handle}","CLAUDE_ANALYST_NOTE","2026-11-08T16:10:00Z",16,68,"analytical;certainty","We must verify every cryptographic state transition with deterministic benchmarks before shipping."
                                            "art-sample-2","${activeSubject.handle}","PERPLEXITY_OSINT","2026-11-08T17:45:00Z",17,64,"cognitive;social","Cross-repository commit timestamps confirm consistent UTC+1 diurnal activity and high technical autonomy."
                                        """.trimIndent()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VioletHf,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .testTag("load_sample_import_btn")
                                ) {
                                    Text("LOAD SAMPLE CSV", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = importPasteBuffer,
                                onValueChange = { importPasteBuffer = it },
                                label = {
                                    Text(
                                        "Paste Aegis JSON, STIX 2.1, CSV rows, Claude XML, or unstructured transcript...",
                                        fontSize = 9.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberSignal,
                                    unfocusedBorderColor = PhosphorGreenDim,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_paste_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (importPasteBuffer.isNotBlank()) {
                                            onImportRawPayload(importPasteBuffer, false)
                                            importPasteBuffer = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PhosphorGreen,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_raw_payload_btn")
                                ) {
                                    Text("IMPORT -> ROOM DB", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (importPasteBuffer.isNotBlank()) {
                                            onImportRawPayload(importPasteBuffer, true)
                                            importPasteBuffer = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanTelemetry,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_gemini_schema_btn")
                                ) {
                                    Text("GEMINI AI SCHEMA IMPORT", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            "INSIGHTS" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeSubject.actionableInsights, key = { it.id }) { insight ->
                        val badgeColor = when (insight.priority) {
                            "CRITICAL" -> AlarmRed
                            "HIGH" -> AmberSignal
                            else -> CyanTelemetry
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "Insight",
                                        tint = badgeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = insight.category,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${insight.priority} // CONF: ${insight.confidencePct}%",
                                    color = PhosphorGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = insight.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = insight.summary,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, PhosphorGreenDim.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "TACTICAL ACTION / PLAYBOOK RECOMMENDATION:",
                                    color = AmberSignal,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = insight.recommendation,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            "GH_HF_STACK" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, CyanTelemetry.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SCRAPED GITHUB REPOSITORIES (${activeSubject.githubRepos.size})",
                                color = CyanTelemetry,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            activeSubject.githubRepos.forEach { repo ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                                        .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = repo.fullName,
                                            color = CyanTelemetry,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${repo.language} • ★${repo.stars} • ⑂${repo.forks}",
                                            color = AmberSignal,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = repo.description,
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "LAST PUSH TIMESTAMP: ${repo.updatedAtIso}",
                                        color = TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, VioletHf.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SCRAPED HUGGING FACE MODELS & DATASETS (${activeSubject.huggingFaceAssets.size})",
                                color = VioletHf,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            activeSubject.huggingFaceAssets.forEach { hf ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                                        .border(1.dp, VioletHf.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = hf.id,
                                            color = VioletHf,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${hf.kind} • ⬇${hf.downloads} • ♥${hf.likes}",
                                            color = AmberSignal,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = hf.summary,
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "PIPELINE: ${hf.pipelineTag} | TIMESTAMP: ${hf.lastModifiedIso}",
                                        color = TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "ROOM_SCHEMA" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Overview of Normalized Room Tables
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreen, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = "Room Schema",
                                    tint = PhosphorGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ROOM SQLITE RELATIONAL SCHEMA (v3) // LIVE DAO METRICS",
                                    color = PhosphorGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("raw_social_media_entities", color = CyanTelemetry, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${rawSocialEntities.size} Total (${subjectRawEntities.size} Active)", color = TextPrimary, fontSize = 11.sp)
                                }
                                Column {
                                    Text("linguistic_personality_profiles", color = VioletHf, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${linguisticProfileEntities.size} Profiles", color = TextPrimary, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("mind_map_hierarchy_edges", color = AmberSignal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("${mindMapHierarchyEntities.size} Edges (${subjectHierarchy.size} Active)", color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Table 1: linguistic_personality_profiles rows
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, VioletHf.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TABLE: linguistic_personality_profiles (FK -> subject_profiles.id)",
                                color = VioletHf,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            linguisticProfileEntities.forEach { lpp ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "PK: ${lpp.profileId} | HANDLE: ${lpp.subjectHandle} | LOGS: ${lpp.evaluatedArtifactCount}",
                                        color = AmberSignal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ARCHETYPE: ${lpp.archetypeTitle} | Δ=${lpp.burrowsDelta} (${lpp.styloMatchPct}%)",
                                        color = TextPrimary,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "LIWC: ATI=${lpp.analyticalThinkingIndex} Clout=${lpp.cloutDominanceIndex} Auth=${lpp.authenticityIndex} | OCEAN: O=${lpp.openness} C=${lpp.conscientiousness} E=${lpp.extraversion} A=${lpp.agreeableness} N=${lpp.neuroticism}",
                                        color = TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    // Table 2: mind_map_hierarchy_edges rows for active subject
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, AmberSignal.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TABLE: mind_map_hierarchy_edges (${subjectHierarchy.size} HIERARCHICAL NODES FOR ${activeSubject.handle})",
                                color = AmberSignal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            subjectHierarchy.take(10).forEach { edge ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "[DEPTH ${edge.hierarchyDepth}] ${edge.parentNodeId} -[${edge.relationshipPredicate}]-> ${edge.childLabel}",
                                            color = TextPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "CAT: ${edge.nodeCategory} | 3D: (${edge.x3d.toInt()}, ${edge.y3d.toInt()}, ${edge.z3d.toInt()}) | UMAP: (${edge.umapX.toInt()}, ${edge.umapY.toInt()})",
                                            color = TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%.0f%%", edge.confidenceWeight * 100),
                                        color = PhosphorGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Table 3: raw_social_media_entities rows for active subject
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, CyanTelemetry.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TABLE: raw_social_media_entities (${subjectRawEntities.size} TIMESTAMPED ENTITIES FOR ${activeSubject.handle})",
                                color = CyanTelemetry,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            subjectRawEntities.take(8).forEach { raw ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianBg, RoundedCornerShape(4.dp))
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${raw.platform} // ${raw.sourceUri.take(24)}",
                                            color = CyanTelemetry,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = raw.postedTimestampIso,
                                            color = AmberSignal,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = raw.rawPayloadText,
                                        color = TextPrimary,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "MARKERS: [${raw.lexicalMarkersCsv}] | TONE: ${raw.sentimentScore} | SHA256: ${raw.provSha256.take(16)}...",
                                        color = TextSecondary,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "CYPHER_STIX" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Cypher",
                                    tint = PhosphorGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "FALKORDB GRAPHRAG READ-ONLY CYPHER CONSOLE",
                                    color = PhosphorGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = cypherQuery,
                                    onValueChange = onUpdateCypherQuery,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = onExecuteCypher,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberSignal,
                                        contentColor = ObsidianBg
                                    )
                                ) {
                                    Text("RUN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                cypherRows.forEach { row ->
                                    Text(
                                        text = "(${row.getOrElse(0) { "" }}) -[${row.getOrElse(1) { "" }}]-> (${row.getOrElse(2) { "" }}) [conf=${row.getOrElse(3) { "" }}]",
                                        color = TextPrimary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "STIX 2.1",
                                    tint = AmberSignal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "OASIS STIX 2.1 & W3C PROV-O SHA-256 BUNDLE",
                                    color = AmberSignal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "ROOT SHA-256: ${activeSubject.provSha256}",
                                color = PhosphorGreen,
                                fontSize = 9.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = stixBundleJson,
                                    color = PhosphorGreen,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
