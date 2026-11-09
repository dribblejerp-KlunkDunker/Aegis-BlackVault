package com.example

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.example.security.AppLock
import com.example.ui.AegisViewModel
import com.example.ui.components.InteractiveMindMapSurface
import com.example.ui.screens.CommandDeckScreen
import com.example.ui.screens.InsightsAndExportScreen
import com.example.ui.screens.PsychometricScreen
import com.example.ui.screens.ScraperBridgeScreen
import com.example.ui.theme.*

private data class NavTabItem(
    val index: Int,
    val label: String,
    val shortcutHint: String,
    val icon: ImageVector,
    val tag: String
)

/**
 * FragmentActivity (a ComponentActivity) so the optional app lock can use BiometricPrompt.
 */
class MainActivity : FragmentActivity() {

    /** True while the optional app lock is enabled and the user has not yet authenticated. */
    private var locked by mutableStateOf(false)
    private var prompting = false
    /** Prompt automatically once per lock; after a cancel the user taps UNLOCK (avoids a re-prompt loop). */
    private var autoPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        locked = AppLock.isEnabled(this)
        setContent {
            MyApplicationTheme {
                if (locked) {
                    AppLockScreen(onUnlock = ::requestUnlock)
                } else {
                    AegisBlackvaultApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (locked && !autoPrompted) {
            autoPrompted = true
            requestUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the app leaves the foreground (not while our own unlock prompt is up).
        if (!prompting && AppLock.isEnabled(this)) {
            locked = true
            autoPrompted = false
        }
    }

    private fun requestUnlock() {
        if (prompting || !locked) return
        if (!AppLock.isAvailable(this)) {
            // The device lock was removed after enabling: fail open rather than lock the user out,
            // and switch the setting off so it is visible in Governance.
            AppLock.setEnabled(this, false)
            locked = false
            return
        }
        prompting = true
        AppLock.prompt(
            activity = this,
            onUnlocked = {
                prompting = false
                locked = false
                autoPrompted = false
            },
            onFailed = { prompting = false }
        )
    }
}

@Composable
private fun AppLockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.Security, contentDescription = "Locked", tint = PhosphorGreen, modifier = Modifier.size(48.dp))
            Text("AEGIS_OS // LOCKED", color = PhosphorGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Unlock with your fingerprint, face or screen lock.", color = TextSecondary, fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .border(1.dp, PhosphorGreen, RoundedCornerShape(6.dp))
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("app_unlock_btn")
            ) {
                Text("UNLOCK", color = PhosphorGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AegisBlackvaultApp(
    viewModel: AegisViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Detect if running on a Google Chromebook (ARC / ARCVM PC feature)
    val isChromeOsHardware = remember(context) {
        context.packageManager.hasSystemFeature("org.chromium.arc") ||
            context.packageManager.hasSystemFeature("org.chromium.arc.device_management") ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PC)
    }

    // Allows user to toggle Chromebook Dual-Pane Workstation Dock on or off at any time
    var chromebookSplitDockOverride by remember { mutableStateOf<Boolean?>(null) }

    // Return to Command Deck when back is pressed on secondary tabs
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val tabs = remember {
        listOf(
            NavTabItem(0, "COMMAND", "Alt+1", Icons.Default.Dashboard, "nav_tab_command"),
            NavTabItem(1, "MIND MAP", "Alt+2", Icons.Default.Hub, "nav_tab_mindmap"),
            NavTabItem(2, "PSYCH", "Alt+3", Icons.Default.Psychology, "nav_tab_psych"),
            NavTabItem(3, "SCRAPER", "Alt+4", Icons.Default.CloudSync, "nav_tab_scraper"),
            NavTabItem(4, "INSIGHTS", "Alt+5", Icons.Default.Lightbulb, "nav_tab_insights")
        )
    }

    val activeSubject = uiState.activeSubject
    val comparisonSubject = uiState.comparisonSubject

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val isMod = event.isAltPressed || event.isCtrlPressed
                    if (isMod) {
                        when (event.key) {
                            Key.One, Key.NumPad1 -> {
                                selectedTab = 0
                                true
                            }
                            Key.Two, Key.NumPad2 -> {
                                selectedTab = 1
                                true
                            }
                            Key.Three, Key.NumPad3 -> {
                                selectedTab = 2
                                true
                            }
                            Key.Four, Key.NumPad4 -> {
                                selectedTab = 3
                                true
                            }
                            Key.Five, Key.NumPad5 -> {
                                selectedTab = 4
                                true
                            }
                            Key.B -> {
                                chromebookSplitDockOverride = !(chromebookSplitDockOverride ?: false)
                                true
                            }
                            Key.E -> {
                                viewModel.exportAndShareBundle(context, uiState.selectedExportFormat)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
    ) {
        val windowWidthDp = maxWidth.value.toInt()
        val isExpanded = maxWidth >= 720.dp
        val isUltraWide = maxWidth >= 980.dp
        val showChromebookSplitDock = chromebookSplitDockOverride ?: (isUltraWide || (isChromeOsHardware && isExpanded))

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Column {
                    AegisHardwareHeaderBar(
                        bridgeStatus = uiState.bridgeStatus,
                        containmentOverride = uiState.containmentOverride,
                        isChromebookSplitActive = showChromebookSplitDock,
                        isExpandedScreen = isExpanded,
                        onToggleContainment = viewModel::toggleContainmentOverride,
                        onToggleChromebookSplit = {
                            chromebookSplitDockOverride = !showChromebookSplitDock
                        },
                        onExportZip = { viewModel.exportAndShareBundle(context) }
                    )
                    if (isExpanded || showChromebookSplitDock) {
                        ChromebookWorkstationStatusBar(
                            isChromeOsHardware = isChromeOsHardware,
                            showSplitDock = showChromebookSplitDock,
                            activeHandle = activeSubject?.handle ?: "BOOTING",
                            windowWidthDp = windowWidthDp
                        )
                    }
                }
            },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar(
                        containerColor = ObsidianPanel,
                        contentColor = PhosphorGreen,
                        tonalElevation = 0.dp,
                        modifier = Modifier.border(width = 1.dp, color = PhosphorGreenDim)
                    ) {
                        tabs.forEach { tab ->
                            val isSelected = selectedTab == tab.index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab.index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianBg,
                                    selectedTextColor = PhosphorGreen,
                                    indicatorColor = PhosphorGreen,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (activeSubject == null || comparisonSubject == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PhosphorGreen)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpanded) {
                        NavigationRail(
                            containerColor = ObsidianPanel,
                            contentColor = PhosphorGreen,
                            modifier = Modifier
                                .fillMaxHeight()
                                .border(1.dp, PhosphorGreenDim)
                        ) {
                            tabs.forEach { tab ->
                                val isSelected = selectedTab == tab.index
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab.index },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = tab.label,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = tab.shortcutHint,
                                                fontSize = 7.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ObsidianBg,
                                        selectedTextColor = PhosphorGreen,
                                        indicatorColor = PhosphorGreen,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    // Primary Workspace Pane
                    Box(
                        modifier = Modifier
                            .weight(if (showChromebookSplitDock && selectedTab != 1) 0.56f else 1f)
                            .fillMaxHeight()
                    ) {
                        when (selectedTab) {
                            0 -> CommandDeckScreen(
                                activeSubject = activeSubject,
                                allSubjects = uiState.profiles,
                                selectedSpectrumToken = uiState.selectedSpectrumToken,
                                containmentOverride = uiState.containmentOverride,
                                onSelectSubject = viewModel::selectSubject,
                                onSelectSpectrumToken = viewModel::selectSpectrumToken,
                                onNavigateToTab = { selectedTab = it }
                            )

                            1 -> InteractiveMindMapSurface(
                                activeSubject = activeSubject,
                                allSubjects = uiState.profiles,
                                triplets = uiState.triplets,
                                mindMapHierarchyEntities = uiState.mindMapHierarchyEntities,
                                linguisticProfileEntities = uiState.linguisticProfileEntities,
                                graphMode = uiState.graphMode,
                                onGraphModeChange = viewModel::setGraphMode,
                                onSelectSubject = viewModel::selectSubject,
                                onNavigateToPsychTab = { selectedTab = 2 }
                            )

                            2 -> PsychometricScreen(
                                activeSubject = activeSubject,
                                comparisonSubject = comparisonSubject,
                                allSubjects = uiState.profiles,
                                selectedSpectrumToken = uiState.selectedSpectrumToken,
                                onSelectSpectrumToken = viewModel::selectSpectrumToken,
                                onSelectComparisonSubject = viewModel::selectComparisonSubject,
                                onRecordConsent = { status, method, days -> viewModel.setSubjectConsent(status, method, days) },
                                auditEntries = uiState.auditEntries,
                                auditChain = uiState.auditChain,
                                retentionDays = uiState.retentionDays,
                                appLockEnabled = uiState.appLockEnabled,
                                appLockAvailable = uiState.appLockAvailable,
                                onDeleteSubject = { viewModel.deleteActiveSubject(context) },
                                onSetRetentionDays = viewModel::setRetentionDays,
                                onSetAppLock = { enabled -> viewModel.setAppLockEnabled(context, enabled) },
                                onVerifyAuditChain = viewModel::refreshAuditChain
                            )

                            3 -> ScraperBridgeScreen(
                                activeSubject = activeSubject,
                                scraperUri = uiState.scraperUri,
                                selectedPlatformMode = uiState.selectedPlatformMode,
                                httpProbes = uiState.httpProbes,
                                rawSocialEntityCount = uiState.rawSocialEntities.size,
                                linguisticProfileCount = uiState.linguisticProfileEntities.size,
                                mindMapHierarchyCount = uiState.mindMapHierarchyEntities.size,
                                isScraping = uiState.isScraping,
                                domDrift = uiState.domDrift,
                                astPatches = uiState.astPatches,
                                telemetryLogs = uiState.telemetryLogs,
                                onUpdateScraperUri = viewModel::updateScraperUri,
                                onSelectPlatformMode = viewModel::setScraperPlatformMode,
                                onExecuteScrape = viewModel::executeScrapeJob,
                                onForceDomMutation = viewModel::forceDomMutationAlert,
                                onInitiateAutoHeal = viewModel::initiateAiAutoHealProtocol,
                                onHarvestEnhancements = viewModel::harvestGithubAndHuggingFaceUpgrades,
                                onAddCustomArtifact = viewModel::addCustomTimestampedArtifact
                            )

                            4 -> {
                                val formattedPreview = remember(
                                    activeSubject,
                                    uiState.selectedExportFormat,
                                    uiState.aiDirectivePrompt,
                                    uiState.rawSocialEntities.size,
                                    uiState.mindMapHierarchyEntities.size
                                ) {
                                    viewModel.buildFormattedPayload(uiState.selectedExportFormat)
                                }
                                val authStatusLabel = remember(
                                    uiState.selectedAiProvider,
                                    uiState.inAppApiKeys
                                ) {
                                    viewModel.resolveProviderAuthStatus(uiState.selectedAiProvider)
                                }
                                InsightsAndExportScreen(
                                    activeSubject = activeSubject,
                                    astPatches = uiState.astPatches.values.toList(),
                                    rawSocialEntities = uiState.rawSocialEntities,
                                    linguisticProfileEntities = uiState.linguisticProfileEntities,
                                    mindMapHierarchyEntities = uiState.mindMapHierarchyEntities,
                                    cypherQuery = uiState.cypherQuery,
                                    cypherRows = uiState.cypherResultRows,
                                    selectedAiProvider = uiState.selectedAiProvider,
                                    selectedModelByProvider = uiState.selectedModelByProvider,
                                    inAppApiKeys = uiState.inAppApiKeys,
                                    activeAuthStatusLabel = authStatusLabel,
                                    aiDirectivePrompt = uiState.aiDirectivePrompt,
                                    aiSynthesisState = uiState.aiSynthesisState,
                                    selectedExportFormat = uiState.selectedExportFormat,
                                    formattedExportPreview = formattedPreview,
                                    exportBannerMessage = uiState.exportBannerMessage,
                                    onUpdateCypherQuery = viewModel::updateCypherQuery,
                                    onExecuteCypher = viewModel::executeReadOnlyCypher,
                                    onSelectAiProvider = viewModel::selectAiProvider,
                                    onSelectAiModel = { modelId ->
                                        viewModel.selectAiModel(modelId, uiState.selectedAiProvider)
                                    },
                                    onUpdateInAppApiKey = { keyText ->
                                        viewModel.updateInAppApiKey(keyText, uiState.selectedAiProvider)
                                    },
                                    githubTokenConfigured = uiState.githubTokenConfigured,
                                    onUpdateGithubToken = viewModel::updateGithubToken,
                                    onUpdateAiDirective = viewModel::updateAiDirectivePrompt,
                                    onExecuteAiSynthesis = viewModel::executeMultiAiAnalysis,
                                    onIngestAiResponse = viewModel::ingestAiResponseIntoDossier,
                                    onSelectExportFormat = viewModel::selectExportFormat,
                                    onCopyFormattedPayload = {
                                        viewModel.copyFormattedPayloadToClipboard(
                                            context,
                                            uiState.selectedExportFormat
                                        )
                                    },
                                    onSaveExportToUri = { uri ->
                                        viewModel.writeExportToDocumentUri(
                                            context,
                                            uri,
                                            uiState.selectedExportFormat
                                        )
                                    },
                                    onImportFromUri = { uri, useGeminiSchema ->
                                        viewModel.importFromDocumentUri(
                                            context = context,
                                            uri = uri,
                                            useGeminiStructuredParser = useGeminiSchema
                                        )
                                    },
                                    onImportRawPayload = { text, useGeminiSchema ->
                                        viewModel.importRawPayloadText(
                                            rawPayload = text,
                                            useGeminiStructuredParser = useGeminiSchema
                                        )
                                    },
                                    onExportAndShare = {
                                        viewModel.exportAndShareBundle(
                                            context,
                                            uiState.selectedExportFormat
                                        )
                                    },
                                    onDismissExportBanner = viewModel::dismissExportBanner
                                )
                            }
                        }
                    }

                    // Docked Right-Hand Chromebook Workstation Mind Map & Telemetry Pane
                    if (showChromebookSplitDock && selectedTab != 1) {
                        Box(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .border(1.dp, PhosphorGreenDim)
                        ) {
                            InteractiveMindMapSurface(
                                activeSubject = activeSubject,
                                allSubjects = uiState.profiles,
                                triplets = uiState.triplets,
                                mindMapHierarchyEntities = uiState.mindMapHierarchyEntities,
                                linguisticProfileEntities = uiState.linguisticProfileEntities,
                                graphMode = uiState.graphMode,
                                onGraphModeChange = viewModel::setGraphMode,
                                onSelectSubject = viewModel::selectSubject,
                                onNavigateToPsychTab = { selectedTab = 2 }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromebookWorkstationStatusBar(
    isChromeOsHardware: Boolean,
    showSplitDock: Boolean,
    activeHandle: String,
    windowWidthDp: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF060C09))
            .border(1.dp, PhosphorGreenDim.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isChromeOsHardware) {
                "CHROMEOS DESKTOP ARCVM // FREEFORM WINDOW (${windowWidthDp}dp) // TARGET: $activeHandle"
            } else {
                "ADAPTIVE WORKSTATION (${windowWidthDp}dp) // PHONE + CHROMEBOOK UNIFIED APK // TARGET: $activeHandle"
            },
            color = CyanTelemetry,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "HOTKEYS: [ALT+1..5] TABS  [CTRL+B] ${if (showSplitDock) "HIDE" else "SHOW"} MIND-MAP DOCK  [CTRL+E] EXPORT  [SCROLL] ZOOM",
            color = TextSecondary,
            fontSize = 8.sp
        )
    }
}

@Composable
private fun AegisHardwareHeaderBar(
    bridgeStatus: String,
    containmentOverride: Boolean,
    isChromebookSplitActive: Boolean,
    isExpandedScreen: Boolean,
    onToggleContainment: () -> Unit,
    onToggleChromebookSplit: () -> Unit,
    onExportZip: () -> Unit
) {
    Surface(
        color = ObsidianPanel,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(1.dp, PhosphorGreenDim)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = stringResource(R.string.desc_shield_icon),
                    tint = RustAccent,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.header_title),
                        color = RustAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = bridgeStatus,
                        color = PhosphorGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Phone vs. Chromebook Split Workstation Toggle
                Box(
                    modifier = Modifier
                        .testTag("header_form_factor_btn")
                        .defaultMinSize(minHeight = 32.dp)
                        .background(
                            if (isChromebookSplitActive) CyanTelemetry.copy(alpha = 0.18f) else ObsidianSurface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (isChromebookSplitActive) CyanTelemetry else PhosphorGreenDim,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(onClick = onToggleChromebookSplit)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isChromebookSplitActive || isExpandedScreen) {
                                Icons.Default.LaptopChromebook
                            } else {
                                Icons.Default.PhoneAndroid
                            },
                            contentDescription = "Toggle Phone or Chromebook Split Mode",
                            tint = if (isChromebookSplitActive) CyanTelemetry else PhosphorGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isChromebookSplitActive) "CB SPLIT" else "PHONE/CB",
                            color = if (isChromebookSplitActive) CyanTelemetry else PhosphorGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .testTag("header_containment_btn")
                        .defaultMinSize(minHeight = 32.dp)
                        .background(
                            if (containmentOverride) AmberSignal.copy(alpha = 0.2f) else AlarmRed.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (containmentOverride) AmberSignal else AlarmRed,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(onClick = onToggleContainment)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (containmentOverride) "CONTAINED" else "LIVE MODE",
                        color = if (containmentOverride) AmberSignal else AlarmRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("header_export_zip_btn")
                        .defaultMinSize(minHeight = 32.dp)
                        .background(PhosphorGreen, RoundedCornerShape(4.dp))
                        .clickable(onClick = onExportZip)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.desc_export_button),
                            tint = ObsidianBg,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = ".ZIP",
                            color = ObsidianBg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
