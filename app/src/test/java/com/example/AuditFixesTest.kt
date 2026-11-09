package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.engine.ForensicMathEngine
import com.example.model.ConsentStatus
import com.example.model.DataSufficiency
import com.example.model.ScraperPlatformMode
import com.example.network.MultiAiAndDataPortabilityService
import com.example.network.OsintScraperService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards for the v3.6 audit fixes: no fabricated or misattributed data, the consent gate,
 * and data-sufficiency limits. See CHANGES.md and AGENT_HANDOFF_PACKET.md invariants 4 to 6.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditFixesTest {

    private lateinit var db: AegisDatabase
    private lateinit var repository: AegisRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AegisRepository(db.aegisDao())
        FakeGithubApi.requestedUrls.clear()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `offline scrape produces an empty NO_DATA profile with nothing invented`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.offlineClient())
        val result = scraper.scrapeAndBuildProfile("someone-unknown", ScraperPlatformMode.ALL)
        val profile = result.profile

        assertEquals("NO_DATA", profile.dataProvenance)
        assertEquals(0, profile.corpusWordCount)
        assertTrue("no repos may be invented", profile.githubRepos.isEmpty())
        assertTrue("no models may be invented", profile.huggingFaceAssets.isEmpty())
        assertTrue("no quotes may be invented", profile.scrapedArtifacts.isEmpty())
        assertTrue("no activity hours may be invented", profile.hourlyBins.all { it == 0 })
        assertEquals(0.0, profile.burrowsDelta, 0.0)
        assertTrue(result.httpProbes.all { it.statusBadge.contains("NETWORK ERROR") })
    }

    @Test
    fun `scrape never calls the removed keyword-search fallbacks`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.ALL)

        assertFalse(FakeGithubApi.requestedUrls.any { it.contains("/search/repositories") })
        assertFalse(FakeGithubApi.requestedUrls.any { it.contains("models?search=") })
    }

    @Test
    fun `scraped repos all belong to the subject`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val profile = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile

        assertTrue(profile.githubRepos.isNotEmpty())
        assertTrue(profile.githubRepos.all { it.fullName.startsWith("${FakeGithubApi.LOGIN}/") })
        assertTrue(profile.scrapedArtifacts.isNotEmpty())
        assertTrue(profile.corpusWordCount > 0)
    }

    @Test
    fun `blank input makes no requests and does not default to a real account`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile("   ", ScraperPlatformMode.ALL)

        assertTrue(FakeGithubApi.requestedUrls.isEmpty())
        assertTrue(result.httpProbes.isEmpty())
        assertEquals("NO_DATA", result.profile.dataProvenance)
        assertEquals("", scraper.extractCleanHandle(""))
    }

    @Test
    fun `unverified subjects never store restricted inferences`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB)
        assertEquals(ConsentStatus.UNVERIFIED, result.profile.consentStatus)

        repository.saveProfile(result.profile, listOf(result.profile), result.triplets)
        val stored = db.aegisDao().observeFullDossierById(result.profile.id).first()
        assertNotNull(stored)

        val ling = stored!!.linguisticProfile!!
        assertEquals(0, JSONArray(ling.persuasionHooksJson).length())
        assertEquals(0, JSONArray(ling.whatMakesThemTickJson).length())
        assertEquals(0, JSONArray(ling.howToTalkJson).length())

        val domain = stored.subject.toDomain(stored.rawSocialEntities, stored.linguisticProfile)
        assertEquals(0, domain.darkTriad.machiavellianism)
        assertEquals(0, domain.darkTriad.narcissism)
        assertEquals(0, domain.darkTriad.psychopathy)
        assertEquals(0, domain.threatMetrics.pathwayToViolence)
        assertEquals(ForensicMathEngine.RESTRICTED_LABEL, domain.troughWindowLabel)
        assertEquals(ForensicMathEngine.RESTRICTED_LABEL, domain.loc)
        assertTrue(domain.actionableInsights.none { it.category in ForensicMathEngine.RESTRICTED_INSIGHT_CATEGORIES })
    }

    @Test
    fun `recording consent recomputes restricted outputs from stored artifacts`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val gated = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
        assertTrue(gated.linguisticProfile.persuasionAndLeverageHooks.isEmpty())

        val unlocked = scraper.recomputeFromArtifacts(gated.copy(consentStatus = ConsentStatus.SELF))
        assertTrue(unlocked.restrictedOutputsUnlocked)
        assertTrue(unlocked.linguisticProfile.persuasionAndLeverageHooks.isNotEmpty())

        // Switching back to UNVERIFIED strips them again.
        val relocked = ForensicMathEngine.applyConsentGate(unlocked.copy(consentStatus = ConsentStatus.UNVERIFIED))
        assertTrue(relocked.linguisticProfile.persuasionAndLeverageHooks.isEmpty())
    }

    @Test
    fun `demo personas stay unlocked and are labeled as demo data`() = runTest {
        repository.ensureSeedData()
        val alpha = db.aegisDao().observeFullDossierById("subj-alpha").first()!!.subject
        assertEquals(ConsentStatus.DEMO.name, alpha.consentStatus)
        assertTrue(alpha.dataProvenance.startsWith("DEMO_SEED"))
    }

    @Test
    fun `stylometry is withheld below the minimum corpus and scores have no artificial floors`() {
        val shortText = "We must verify the benchmark before merging. The trace looks clean."
        val eval = ForensicMathEngine.evaluateCorpus(shortText, repoCount = 0, hfCount = 0, followerCount = 0)

        assertTrue(eval.tokenCount < DataSufficiency.MIN_WORDS_FOR_STYLOMETRY)
        assertEquals(0.0, eval.burrowsDelta, 0.0)
        assertEquals(0.0, eval.styloMatchPct, 0.0)
        listOf(
            eval.darkTriad.machiavellianism, eval.darkTriad.narcissism, eval.darkTriad.psychopathy,
            eval.threatMetrics.pathwayToViolence
        ).forEach { assertTrue(it in 0..100) }

        val empty = ForensicMathEngine.evaluateCorpus("", repoCount = 0, hfCount = 0, followerCount = 0)
        assertEquals(0, empty.tokenCount)
    }

    @Test
    fun `activity window needs real timestamps and never invents coordinates`() {
        val tooFew = ForensicMathEngine.evaluateChronoAndGeo(listOf(9, 10, 11), null)
        assertTrue(tooFew.windowLabel.startsWith("INSUFFICIENT TIMESTAMPS"))
        assertEquals(0.0, tooFew.lat, 0.0)
        assertEquals(0.0, tooFew.lon, 0.0)
        assertEquals(3, tooFew.hourlyBins.sum())

        val enough = ForensicMathEngine.evaluateChronoAndGeo((8..21).toList(), null)
        assertEquals(0.0, enough.lat, 0.0)
    }

    @Test
    fun `ai prompts for unverified subjects carry the consent policy`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val gated = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
        val ai = MultiAiAndDataPortabilityService()

        listOf(
            ai.buildClaudeXmlBundle(gated),
            ai.buildGeminiMarkdownContext(gated),
            ai.buildPerplexitySearchBundle(gated)
        ).forEach { bundle ->
            assertTrue(bundle.contains("consent=UNVERIFIED"))
            assertTrue(bundle.contains("Do NOT produce persuasion"))
        }
    }
}
