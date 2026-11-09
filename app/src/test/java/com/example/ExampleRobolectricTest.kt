package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.model.ScraperPlatformMode
import com.example.network.MultiAiAndDataPortabilityService
import com.example.network.OsintScraperService
import com.example.ui.components.InteractiveMindMapSurface
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AegisDatabase
    private lateinit var repository: AegisRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AegisRepository(db.aegisDao())
        kotlinx.coroutines.runBlocking { repository.ensureDefaultProject() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aegis Blackvault", appName)
    }

    @Test
    fun `room database schema stores raw social entities, linguistic profiles, and 3-tier mind map hierarchy`() = runTest {
        repository.ensureSeedData()

        val dao = db.aegisDao()
        val fullDossier = dao.observeFullDossierById("subj-alpha").first()
        assertNotNull(fullDossier)
        assertEquals("NULL_ROUTER", fullDossier!!.subject.handle)

        // Verify Table: raw_social_media_entities
        val rawEntities = dao.observeRawSocialEntitiesForSubject("subj-alpha").first()
        assertTrue("Expected raw social media entities to be persisted", rawEntities.size >= 5)
        assertTrue(rawEntities.any { it.platform == "GITHUB_COMMIT" && it.postedTimestampIso.contains("2026-") })

        // Verify Table: linguistic_personality_profiles
        val lingProfile = dao.observeLinguisticProfileForSubject("subj-alpha").first()
        assertNotNull("Expected parsed linguistic personality profile to be persisted", lingProfile)
        assertTrue(lingProfile!!.analyticalThinkingIndex > 0)
        assertTrue(lingProfile.whatMakesThemTickJson.contains("Intellectual"))
        assertTrue(lingProfile.howToTalkJson.isNotEmpty())
        assertTrue(lingProfile.persuasionHooksJson.isNotEmpty())

        // Verify Table: mind_map_hierarchy_edges (Depth 0 Root -> Depth 1 Hubs -> Depth 2 Leaves)
        val hierarchyEdges = dao.observeMindMapHierarchyForSubject("subj-alpha").first()
        assertTrue("Expected hierarchical mind map nodes and edges", hierarchyEdges.size >= 10)
        val rootNodes = dao.observeMindMapNodesByDepth("subj-alpha", 0).first()
        val depth1Hubs = dao.observeMindMapNodesByDepth("subj-alpha", 1).first()
        val depth2Leaves = dao.observeMindMapNodesByDepth("subj-alpha", 2).first()
        assertEquals(1, rootNodes.size)
        assertEquals("NULL_ROUTER", rootNodes.first().childLabel)
        assertTrue("Expected Depth-1 Cluster Hubs", depth1Hubs.isNotEmpty())
        assertTrue("Expected Depth-2 Leaf Artifacts/Traits", depth2Leaves.isNotEmpty())
    }

    @Test
    fun `compose canvas mind map renders room hierarchy and tapping node shows psychometric details`() = runTest {
        repository.ensureSeedData()
        val fullDossiers = repository.fullDossiersFlow.first()
        val alphaRelation = fullDossiers.first { it.subject.id == "subj-alpha" }
        val activeSubject = alphaRelation.subject.toDomain(
            relationalArtifacts = alphaRelation.rawSocialEntities,
            relationalProfile = alphaRelation.linguisticProfile
        )
        val allSubjects = fullDossiers.map {
            it.subject.toDomain(it.rawSocialEntities, it.linguisticProfile)
        }
        val hierarchyEntities = alphaRelation.mindMapHierarchy
        val lingEntities = fullDossiers.mapNotNull { it.linguisticProfile }

        composeTestRule.setContent {
            MyApplicationTheme {
                InteractiveMindMapSurface(
                    activeSubject = activeSubject,
                    allSubjects = allSubjects,
                    triplets = emptyList(),
                    mindMapHierarchyEntities = hierarchyEntities,
                    linguisticProfileEntities = lingEntities,
                    graphMode = "HIERARCHY",
                    onGraphModeChange = {},
                    onSelectSubject = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("interactive_mind_map_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mindmap_psychometric_inspector").assertIsDisplayed()

        // Tap the Psychometric Core Hub node chip to inspect its psychometric details
        val psychHub = hierarchyEntities.first { it.childNodeId.startsWith("hub-psych-") }
        composeTestRule.onNodeWithTag("hierarchy_node_chip_${psychHub.childNodeId}").performClick()

        // Switch between the Psychometric Inspector tabs (OCEAN/LIWC, What Makes Them Tick, How to Talk & Leverage)
        composeTestRule.onNodeWithTag("inspector_tab_1").performClick()
        composeTestRule.onNodeWithTag("inspector_tab_2").performClick()
        composeTestRule.onNodeWithTag("inspector_tab_0").performClick()
    }

    @Test
    fun `okhttp scraper service parses html hydration and persists scraped dossier into room`() = runTest {
        // Hermetic: canned GitHub responses instead of the live network (the old version only passed
        // offline because the scraper fabricated data when requests failed).
        val scraperService = OsintScraperService(customClient = FakeGithubApi.client())
        val sampleHtml = """
            <html>
              <head>
                <title>Kernel Architecture Notes — Linus Torvalds</title>
                <meta property="article:published_time" content="2026-11-08T09:42:15Z" />
                <meta property="og:description" content="Deterministic memory barriers and zero-copy VFS scheduling benchmarks." />
                <script type="application/ld+json">
                  {"headline":"Zero-Copy Scheduler Patch","articleBody":"We must verify every lockless ring buffer transition with empirical latency traces.","datePublished":"2026-11-08T11:15:00Z"}
                </script>
                <script id="__NEXT_DATA__" type="application/json">
                  {"props":{"pageProps":{"summary":"Strict code review standards require reproducible kernel telemetry before merge."}}}
                </script>
              </head>
              <body>
                <time datetime="2026-11-08T13:20:00Z">Nov 8</time>
                <article>Talk is cheap. Show me the patch and the exact cycle-accurate profiler numbers.</article>
              </body>
            </html>
        """.trimIndent()

        val parsedArtifacts = scraperService.parseHtmlAndHydrationArtifacts(
            url = "https://example.org/kernel-notes",
            html = sampleHtml,
            platformLabel = "DIRECT_URL_DOM"
        )
        assertTrue("Expected multi-strategy HTML/JSON-LD/Hydration parser to extract >= 3 artifacts", parsedArtifacts.size >= 3)
        assertTrue(parsedArtifacts.any { it.timestampIso.startsWith("2026-11-08") })

        // Verify full scrapeAndBuildProfile + Room persistence pipeline
        val result = scraperService.scrapeAndBuildProfile(
            rawTargetInput = FakeGithubApi.LOGIN,
            platformMode = ScraperPlatformMode.GITHUB
        )
        assertTrue(result.httpProbes.isNotEmpty())
        assertTrue(result.profile.dataProvenance.startsWith("LIVE"))
        repository.saveProfile(result.profile, listOf(result.profile), result.triplets)

        val dao = db.aegisDao()
        val persistedDossier = dao.observeFullDossierById(result.profile.id).first()
        assertNotNull(persistedDossier)
        assertEquals(FakeGithubApi.LOGIN.uppercase().replace("-", "_"), persistedDossier!!.subject.handle)
        assertTrue(persistedDossier.rawSocialEntities.isNotEmpty())
        assertNotNull(persistedDossier.linguisticProfile)
        assertTrue(persistedDossier.mindMapHierarchy.isNotEmpty())
    }

    @Test
    fun `multi ai and data portability service exports and imports room json, csv, and claude xml`() = runTest {
        repository.ensureSeedData()
        val fullDossiers = repository.fullDossiersFlow.first()
        val alphaRelation = fullDossiers.first { it.subject.id == "subj-alpha" }
        val activeSubject = alphaRelation.subject.toDomain(
            relationalArtifacts = alphaRelation.rawSocialEntities,
            relationalProfile = alphaRelation.linguisticProfile
        )

        val portabilityService = MultiAiAndDataPortabilityService()

        // 1. Verify export generators (Room JSON, CSV, Claude XML, Gemini Markdown, Perplexity Query)
        val roomJson = portabilityService.buildRoomDossierJson(
            subject = activeSubject,
            rawEntities = alphaRelation.rawSocialEntities,
            lingEntities = listOfNotNull(alphaRelation.linguisticProfile),
            hierarchyEntities = alphaRelation.mindMapHierarchy
        )
        assertTrue(roomJson.contains("aegis_schema_version"))
        assertTrue(roomJson.contains("NULL_ROUTER"))

        val claudeXml = portabilityService.buildClaudeXmlBundle(
            subject = activeSubject,
            customDirective = "Synthesize negotiation leverage."
        )
        assertTrue(claudeXml.contains("<aegis_osint_dossier"))
        assertTrue(claudeXml.contains("<analyst_directive>"))

        val geminiMd = portabilityService.buildGeminiMarkdownContext(
            subject = activeSubject,
            customDirective = "Build 30-day rapport strategy."
        )
        assertTrue(geminiMd.contains("# AEGIS BLACKVAULT"))

        val perplexityBundle = portabilityService.buildPerplexitySearchBundle(
            subject = activeSubject,
            customDirective = "Verify recent commits."
        )
        assertTrue(perplexityBundle.contains("OSINT TARGET CORROBORATION"))

        // 2. Verify Universal Importer with CSV discourse payload -> Room DB
        val csvPayload = """
            entity_id,subject_handle,platform,posted_timestamp_iso,utc_hour,sentiment_score,lexical_markers,verbatim_text
            "imp-1","NULL_ROUTER","GITHUB_PR","2026-11-08T10:15:00Z",10,42,"therefore;architecture","We need deterministic benchmarks and zero-copy ring buffers before merging this architecture."
            "imp-2","NULL_ROUTER","HF_DISCUSSION","2026-11-08T12:30:00Z",12,35,"verify;telemetry","Never trust unverified weights without reproducible evaluation harness telemetry."
        """.trimIndent()

        val importResult = portabilityService.parseAndImportData(
            rawPayload = csvPayload,
            activeSubject = activeSubject
        )
        assertTrue(importResult.formatDetected.contains("CSV"))
        assertTrue(importResult.importedStatementsCount >= 2)

        repository.saveProfile(
            importResult.updatedProfile,
            listOf(importResult.updatedProfile),
            emptyList()
        )

        val persistedImport = db.aegisDao().observeFullDossierById(importResult.updatedProfile.id).first()
        assertNotNull(persistedImport)
        assertTrue(persistedImport!!.rawSocialEntities.size >= alphaRelation.rawSocialEntities.size + 2)
        assertNotNull(persistedImport.linguisticProfile)
        assertTrue(persistedImport.mindMapHierarchy.isNotEmpty())

        // 3. Verify Provider Model Catalogs (Gemini, Claude, Perplexity) & Key Resolution
        assertTrue(com.example.model.AiProviderEngine.GEMINI.presetModels.any { it.modelId == "gemini-3.8-flash" })
        assertTrue(com.example.model.AiProviderEngine.CLAUDE.presetModels.any { it.modelId == "claude-sonnet-5-5" })
        // No retired model IDs may be offered as presets.
        com.example.model.AiProviderEngine.entries.forEach { provider ->
            provider.presetModels.forEach { option ->
                assertFalse(option.modelId in com.example.model.AiProviderEngine.RETIRED_MODEL_IDS)
            }
        }
        assertTrue(com.example.model.AiProviderEngine.PERPLEXITY.presetModels.any { it.modelId == "sonar-pro" })

        val (customClaudeKey, claudeSource) = portabilityService.resolveApiKeyAndSource(
            provider = com.example.model.AiProviderEngine.CLAUDE,
            inAppKeyOverride = "sk-ant-api03-test-override"
        )
        assertEquals("sk-ant-api03-test-override", customClaudeKey)
        assertEquals("IN-APP KEY VAULT", claudeSource)

        val (customGeminiKey, geminiSource) = portabilityService.resolveApiKeyAndSource(
            provider = com.example.model.AiProviderEngine.GEMINI,
            inAppKeyOverride = "AIzaSyTestGeminiKeyOverride"
        )
        assertEquals("AIzaSyTestGeminiKeyOverride", customGeminiKey)
        assertEquals("IN-APP KEY VAULT", geminiSource)
    }

    @Test
    fun `sovereign aegis verdad engine evaluates manipulation risk, disarm techniques, watchpoints, and reema keybinding`() = runTest {
        repository.ensureSeedData()
        val fullDossiers = repository.fullDossiersFlow.first()
        val alphaRelation = fullDossiers.first { it.subject.id == "subj-alpha" }
        val activeSubject = alphaRelation.subject.toDomain(
            relationalArtifacts = alphaRelation.rawSocialEntities,
            relationalProfile = alphaRelation.linguisticProfile
        )

        // 1. Corpus-level Sovereign VERDAD assessment
        val corpusAssessment = com.example.engine.ForensicMathEngine.evaluateSovereignVerdad(activeSubject)
        assertTrue(corpusAssessment.manipulationRisk in 0..100)
        assertTrue(
            corpusAssessment.recommendationGate.startsWith("ALLOW") ||
                corpusAssessment.recommendationGate.startsWith("CAUTION") ||
                corpusAssessment.recommendationGate.startsWith("BLOCK")
        )
        assertEquals(6, corpusAssessment.emotionalBaitScores.size)
        assertTrue(corpusAssessment.disarmRedBlueMappings.isNotEmpty())
        assertTrue(corpusAssessment.situationalWatchpoints.isNotEmpty())
        assertTrue(corpusAssessment.reemaKeyBindingJson.contains("\"type\": \"KeyBinding\""))
        assertTrue(corpusAssessment.reemaKeyBindingJson.contains("ML-DSA-65"))
        // The KeyBinding is a placeholder and must say so.
        assertTrue(corpusAssessment.reemaKeyBindingJson.contains("\"simulated\": true"))

        // 2. High-bait draft sandbox evaluation (verdad-service.shouldShare)
        val highBaitDraft = "BREAKING: Share before deleted!! Wake up sheep, they don't want you to know the secret agenda coverup! Everyone knows either we act now or we will be destroyed!"
        val sandboxAssessment = com.example.engine.ForensicMathEngine.evaluateSovereignVerdad(
            subject = activeSubject,
            customDraftText = highBaitDraft
        )
        assertTrue("Expected elevated manipulationRisk on high-bait draft", sandboxAssessment.manipulationRisk >= 40)
        assertTrue(
            sandboxAssessment.recommendationGate.startsWith("CAUTION") ||
                sandboxAssessment.recommendationGate.startsWith("BLOCK")
        )
        assertTrue(sandboxAssessment.detectedFallacies.isNotEmpty())
    }
}

