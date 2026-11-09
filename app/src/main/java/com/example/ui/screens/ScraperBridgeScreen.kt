package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun ScraperBridgeScreen(
    activeSubject: SubjectProfile,
    scraperUri: String,
    selectedPlatformMode: ScraperPlatformMode = ScraperPlatformMode.ALL,
    httpProbes: List<HttpEndpointProbe> = emptyList(),
    rawSocialEntityCount: Int = 0,
    linguisticProfileCount: Int = 0,
    mindMapHierarchyCount: Int = 0,
    isScraping: Boolean,
    domDrift: DomDriftState,
    astPatches: Map<String, AstPatchEntity>,
    telemetryLogs: List<TelemetryLog>,
    onUpdateScraperUri: (String) -> Unit,
    onSelectPlatformMode: (ScraperPlatformMode) -> Unit = {},
    onExecuteScrape: (Boolean) -> Unit,
    onForceDomMutation: () -> Unit,
    onInitiateAutoHeal: () -> Unit,
    onHarvestEnhancements: () -> Unit,
    onAddCustomArtifact: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPatchCode by remember { mutableStateOf(false) }
    var showAddArtifactForm by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var customPlatform by remember { mutableStateOf("REDDIT_POST") }
    var customTimestamp by remember { mutableStateOf("2026-11-08T18:30:00Z") }

    val currentSpec = remember(scraperUri) { ForensicMathEngine.detectWalledGarden(scraperUri) }
    val activePatch = remember(currentSpec, astPatches) {
        if (currentSpec != null && astPatches.containsKey(currentSpec.key)) {
            astPatches[currentSpec.key]
        } else {
            astPatches.values.firstOrNull()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Live OkHttp Scraper Input, Platform Vector Selector, Presets & Auto-Upgrade Button
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OKHTTP OSINT SCRAPER & VIEWMODEL -> ROOM PIPELINE",
                        color = PhosphorGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .testTag("force_dom_mutation_btn")
                            .background(AlarmRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, AlarmRed, RoundedCornerShape(4.dp))
                            .clickable { onForceDomMutation() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FORCE DOM MUTATION",
                            color = AlarmRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // OkHttp Vector Routing Mode Chips
                Text(
                    text = "OKHTTP INGESTION VECTOR MODE:",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScraperPlatformMode.entries.forEach { mode ->
                        val isSelected = selectedPlatformMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_chip_${mode.name.lowercase(Locale.US)}")
                                .background(
                                    if (isSelected) CyanTelemetry.copy(alpha = 0.22f) else ObsidianBg,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CyanTelemetry else PhosphorGreenDim,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectPlatformMode(mode) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) CyanTelemetry else TextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Target Presets: GitHub, Hugging Face, Direct URLs & Walled Gardens
                Text(
                    text = "QUICK-SELECT TARGETS (GITHUB, HUGGING FACE, DIRECT URLS, WALLED GARDENS):",
                    color = TextSecondary,
                    fontSize = 9.sp
                )

                val quickTargets = listOf(
                    Triple("GH: torvalds", "torvalds", null),
                    Triple("GH: karpathy", "karpathy", null),
                    Triple("HF: thomwolf", "https://huggingface.co/thomwolf", null),
                    Triple("URL: github.com/torvalds", "https://github.com/torvalds", null),
                    Triple("TikTok", ForensicMathEngine.WALLED_GARDEN_CATALOG["tiktok"]!!.sampleUrl, "tiktok"),
                    Triple("Instagram", ForensicMathEngine.WALLED_GARDEN_CATALOG["instagram"]!!.sampleUrl, "instagram"),
                    Triple("YouTube", ForensicMathEngine.WALLED_GARDEN_CATALOG["youtube"]!!.sampleUrl, "youtube"),
                    Triple("Reddit", ForensicMathEngine.WALLED_GARDEN_CATALOG["reddit"]!!.sampleUrl, "reddit"),
                    Triple("Facebook", ForensicMathEngine.WALLED_GARDEN_CATALOG["facebook"]!!.sampleUrl, "facebook"),
                    Triple("OnlyFans", ForensicMathEngine.WALLED_GARDEN_CATALOG["onlyfans"]!!.sampleUrl, "onlyfans")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickTargets) { (label, uri, wgKey) ->
                        val isHealed = wgKey != null && astPatches.containsKey(wgKey)
                        val isSelected = scraperUri.equals(uri, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) AmberSignal.copy(alpha = 0.22f) else ObsidianBg,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AmberSignal else PhosphorGreenDim,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onUpdateScraperUri(uri) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AmberSignal else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isHealed) {
                                    Text(
                                        text = "[HEALED]",
                                        color = PhosphorGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Target URI / Handle Input Field
                OutlinedTextField(
                    value = scraperUri,
                    onValueChange = onUpdateScraperUri,
                    enabled = !domDrift.healing,
                    singleLine = true,
                    label = {
                        Text(
                            "Target URL (https://...) or Handle (@user / GitHub / Hugging Face)",
                            fontSize = 10.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PhosphorGreen,
                        unfocusedBorderColor = PhosphorGreenDim,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = PhosphorGreen,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scraper_uri_input")
                )

                // Primary Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onExecuteScrape(false) },
                        enabled = !isScraping && !domDrift.healing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberSignal,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scrape_execute_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Scrape",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isScraping) "SCRAPING..." else "SCRAPE -> ROOM DB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onHarvestEnhancements,
                        enabled = !isScraping && !domDrift.healing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VioletHf,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("harvest_gh_hf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Harvest GitHub & HF",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SCRAPE GH + HF UPGRADES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Live OkHttp Endpoint Probe Diagnostics & Room Database Transaction Matrix
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, CyanTelemetry.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Room Database & OkHttp Telemetry",
                            tint = CyanTelemetry,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "OKHTTP ENDPOINT PROBES & ROOM @TRANSACTION PERSISTENCE",
                            color = CyanTelemetry,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${httpProbes.size} PROBES",
                        color = PhosphorGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Room Entity Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoomCounterBadge(
                        label = "RAW SOCIAL ENTITIES",
                        count = rawSocialEntityCount.coerceAtLeast(activeSubject.scrapedArtifacts.size),
                        color = CyanTelemetry,
                        modifier = Modifier.weight(1f)
                    )
                    RoomCounterBadge(
                        label = "LIWC PROFILES",
                        count = linguisticProfileCount.coerceAtLeast(1),
                        color = VioletHf,
                        modifier = Modifier.weight(1f)
                    )
                    RoomCounterBadge(
                        label = "MIND MAP EDGES",
                        count = mindMapHierarchyCount.coerceAtLeast(12),
                        color = PhosphorGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (httpProbes.isNotEmpty()) {
                    httpProbes.take(6).forEach { probe ->
                        val isOk = probe.httpStatusCode in 200..299
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianBg, RoundedCornerShape(4.dp))
                                .border(
                                    1.dp,
                                    if (isOk) PhosphorGreenDim else AmberSignal.copy(alpha = 0.6f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = probe.platform,
                                        color = CyanTelemetry,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = probe.statusBadge,
                                        color = if (isOk) PhosphorGreen else AmberSignal,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = probe.url.take(56),
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${probe.latencyMs} ms",
                                    color = TextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${probe.payloadBytes / 1024.0f.coerceAtLeast(1f).toInt()} KB",
                                    color = TextSecondary,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "READY: Enter any target URL or handle above and tap SCRAPE -> ROOM DB to inspect live OkHttp probes (status code, latency ms, payload bytes, and extracted items).",
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // 3. DOM Drift Alert & 4-Stage Visual AI Auto-Heal State Machine
        if (domDrift.active) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlarmRed.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                        .border(1.5.dp, AlarmRed, RoundedCornerShape(6.dp))
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "DOM Drift",
                                tint = AlarmRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "DOM DRIFT DETECTED // ${domDrift.platform.uppercase(Locale.US)} SCHEMA MUTATED",
                                color = AlarmRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "AST_FAIL",
                            color = AlarmRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                            .border(1.dp, AlarmRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DEPRECATED SELECTOR (RETURNED 0 NODES):",
                            color = AlarmRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = domDrift.deprecatedSelector,
                            color = TextPrimary,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "MUTATED DOM / HYDRATION SIGNATURE DETECTED:",
                            color = AmberSignal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = domDrift.mutatedDom,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    if (domDrift.healing) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = domDrift.healStatusText,
                                    color = AmberSignal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${domDrift.healProgress}%",
                                    color = PhosphorGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { domDrift.healProgress / 100f },
                                color = AmberSignal,
                                trackColor = ObsidianBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onInitiateAutoHeal,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorGreen,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("initiate_auto_heal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Initiate Auto-Heal",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "INITIATE AI AUTO-HEAL PROTOCOL (SYNTHESIZE AST PATCH)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Active Hot-Patched AST Architecture Card
        if (!domDrift.active && activePatch != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PhosphorGreen.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .border(1.dp, PhosphorGreen, RoundedCornerShape(6.dp))
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
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Healed",
                                tint = PhosphorGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AI AUTO-HEAL PATCH ACTIVE // ${activePatch.patchId} (${activePatch.platformName})",
                                color = PhosphorGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(ObsidianBg, RoundedCornerShape(4.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                                .clickable { showPatchCode = !showPatchCode }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Code",
                                    tint = AmberSignal,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (showPatchCode) "HIDE AI CODE" else "VIEW AI CODE",
                                    color = AmberSignal,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "SYNTHESIZED SELECTOR: ${activePatch.healedSelector}",
                        color = TextPrimary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "BYPASS STRATEGY: ${activePatch.strategy} (CONFIDENCE: ${activePatch.confidence})",
                        color = AmberSignal,
                        fontSize = 10.sp
                    )

                    if (showPatchCode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianBg, RoundedCornerShape(4.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = activePatch.pythonSnippet,
                                color = PhosphorGreen,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 5. Timestamped Discourse & Verbatim Evidence Feed (With ISO-8601 Timestamps + Custom Statement Injector)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, CyanTelemetry.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Timestamped Evidence",
                            tint = CyanTelemetry,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "TIMESTAMPED SCRAPED STATEMENTS (${activeSubject.scrapedArtifacts.size})",
                            color = CyanTelemetry,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .testTag("toggle_add_statement_btn")
                            .background(CyanTelemetry.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, CyanTelemetry, RoundedCornerShape(4.dp))
                            .clickable { showAddArtifactForm = !showAddArtifactForm }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = "Add Statement",
                                tint = CyanTelemetry,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (showAddArtifactForm) "CLOSE FORM" else "+ INJECT STATEMENT",
                                color = CyanTelemetry,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (showAddArtifactForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                            .border(1.dp, CyanTelemetry, RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "INJECT VERBATIM STATEMENT / CHAT LOG WITH TIMESTAMP (AUTO-RECOMPUTES LIWC PROFILE):",
                            color = AmberSignal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = customPlatform,
                                onValueChange = { customPlatform = it },
                                label = { Text("Source / Platform", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customTimestamp,
                                onValueChange = { customTimestamp = it },
                                label = { Text("Timestamp (ISO-8601)", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("Verbatim quote, commit message, email, or social post...", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onAddCustomArtifact(customText, customPlatform, customTimestamp)
                                    customText = ""
                                    showAddArtifactForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanTelemetry,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PARSE STATEMENT & UPDATE PERSONALITY DOSSIER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                activeSubject.scrapedArtifacts.take(10).forEach { art ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg, RoundedCornerShape(4.dp))
                            .border(1.dp, PhosphorGreenDim.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(PhosphorGreen.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = art.platform,
                                        color = PhosphorGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = art.contextLabel.take(26),
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                            Text(
                                text = "SAID/POSTED: ${art.timestampIso}",
                                color = AmberSignal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "\"${art.verbatimText}\"",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                art.lexicalMarkers.forEach { marker ->
                                    Text(
                                        text = "#$marker",
                                        color = VioletHf,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "TONE: ${art.sentimentScore}/100 | UTC HR: ${String.format(Locale.US, "%02d:00", art.utcHour)}",
                                color = CyanTelemetry,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // 6. Live W3C PROV-O Cryptographic Audit & Telemetry Stream
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "W3C PROV-O LIVE TELEMETRY STREAM",
                    color = PhosphorGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                telemetryLogs.take(10).forEachIndexed { idx, log ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ">",
                            color = PhosphorGreenDim,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "[${log.category}]",
                            color = AmberSignal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "[${log.ts}] ${log.message}",
                            color = if (idx == 0) TextPrimary else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCounterBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ObsidianBg, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
