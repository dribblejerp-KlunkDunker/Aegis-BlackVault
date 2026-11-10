package com.example

import com.example.connectors.CollectionTarget
import com.example.connectors.ConnectorRunStatus
import com.example.connectors.FeedConnector
import com.example.connectors.ManualImportConnector
import com.example.connectors.Timestamps
import com.example.engine.StylometryEngine
import com.example.model.ScraperPlatformMode
import com.example.network.MultiAiAndDataPortabilityService
import com.example.network.MultiAiAndDataPortabilityService.ImportCustody
import com.example.network.OsintScraperService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 3b: feeds, manual import custody, and timestamps that are never invented. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedAndImportTest {

    private val feedOptIn = setOf(FeedConnector.spec.id)

    @Before
    fun setUp() = FakeGithubApi.requestedUrls.clear()

    // ---- timestamps ----

    @Test
    fun `offsets are converted to UTC and dates without times get no hour`() {
        assertEquals("2026-10-20T06:30:00Z", Timestamps.isoToUtc("2026-10-20T08:30:00+02:00"))
        assertEquals("2026-10-21T23:05:00Z", Timestamps.isoToUtc("2026-10-21T19:05:00-0400"))
        assertEquals("2026-10-20T08:30:00Z", Timestamps.isoToUtc("2026-10-20T08:30:00.123Z"))
        assertEquals("2026-10-20", Timestamps.isoToUtc("2026-10-20"))
        assertNull(Timestamps.isoToUtc("yesterday"))
        assertEquals("2026-10-20T06:30:00Z", Timestamps.rfc822ToUtc("Tue, 20 Oct 2026 08:30:00 +0200"))
        assertEquals("2026-10-20T08:30:00Z", Timestamps.rfc822ToUtc("Tue, 20 Oct 2026 08:30:00 GMT"))
        assertNull(Timestamps.anyToUtc(""))
    }

    @Test
    fun `an artifact without a real timestamp has no activity hour`() {
        val undated = StylometryEngine.createTimestampedArtifact("a", "X", "", "words", "ctx")
        assertFalse(undated.hasKnownHour)
        val dateOnly = StylometryEngine.createTimestampedArtifact("b", "X", "2026-10-20", "words", "ctx")
        assertFalse(dateOnly.hasKnownHour)
        val dated = StylometryEngine.createTimestampedArtifact("c", "X", "2026-10-20T06:30:00Z", "words", "ctx")
        assertEquals(6, dated.utcHour)
    }

    // ---- feeds ----

    @Test
    fun `RSS and Atom entries parse with plain text and UTC dates`() {
        val rss = FeedConnector.parse(FakeGithubApi.rss)
        assertEquals(2, rss.size)
        assertEquals("Ring buffers: Measuring tail latency needs a quiet machine.", rss[0].text)
        assertEquals("2026-10-20T06:30:00Z", rss[0].publishedUtc)
        assertNull("an undated entry stays undated", rss[1].publishedUtc)

        val atom = FeedConnector.parse(FakeGithubApi.atom)
        assertEquals(1, atom.size)
        assertEquals("${FakeGithubApi.SITE}/notes/pinning", atom[0].link)
        assertEquals("2026-10-21T23:05:00Z", atom[0].publishedUtc)
    }

    @Test
    fun `feed URLs go to the feed connector and pages to the web-page connector`() {
        fun target(url: String, mode: ScraperPlatformMode = ScraperPlatformMode.ALL) = CollectionTarget(url, "", url, mode)
        assertTrue(FeedConnector.isFeedTarget(target(FakeGithubApi.RSS_URL)))
        assertTrue(FeedConnector.isFeedTarget(target(FakeGithubApi.ATOM_URL)))
        assertTrue(FeedConnector.isFeedTarget(target("https://blog.example/feed/")))
        assertTrue(FeedConnector.isFeedTarget(target("https://blog.example/posts", ScraperPlatformMode.FEED)))
        assertFalse(FeedConnector.isFeedTarget(target(FakeGithubApi.ARTICLE_URL)))
        assertFalse(FeedConnector.isFeedTarget(target("https://blog.example/feedback")))
    }

    @Test
    fun `the feed connector needs opt-in, reads robots first, and dates entries honestly`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())

        val skipped = scraper.scrapeAndBuildProfile(FakeGithubApi.RSS_URL, ScraperPlatformMode.ALL)
        // A feed URL in ALL mode runs only the feed connector (no handle lookups on other sites).
        assertEquals(listOf("feed"), skipped.connectorRuns.map { it.connectorId })
        assertEquals(ConnectorRunStatus.SKIPPED_NOT_OPTED_IN, skipped.connectorRuns.single().status)
        assertTrue(FakeGithubApi.requestedUrls.none { it.contains("pages.example") })

        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.RSS_URL, ScraperPlatformMode.ALL, feedOptIn)
        val run = result.connectorRuns.single()
        assertEquals(ConnectorRunStatus.COLLECTED, run.status)
        assertEquals(listOf("${FakeGithubApi.SITE}/robots.txt", FakeGithubApi.RSS_URL), FakeGithubApi.requestedUrls)
        val arts = result.profile.scrapedArtifacts
        assertEquals(2, arts.size)
        assertTrue(arts.all { it.connectorRunId == run.runId && it.sourceUrl == FakeGithubApi.RSS_URL })
        assertEquals(1, arts.count { it.hasKnownHour })
        assertTrue(arts.any { it.timestampIso.isEmpty() })
    }

    @Test
    fun `feeds on prohibited platforms are refused before any request`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile("https://www.reddit.com/r/test/.rss", ScraperPlatformMode.FEED, feedOptIn)
        assertEquals(ConnectorRunStatus.BLOCKED_PLATFORM_TERMS, result.connectorRuns.single().status)
        assertTrue(FakeGithubApi.requestedUrls.isEmpty())
    }

    // ---- manual import ----

    @Test
    fun `a URL for another site never triggers GitHub or Hugging Face lookups`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.ARTICLE_URL, ScraperPlatformMode.ALL)
        assertEquals(listOf("direct_url"), result.connectorRuns.map { it.connectorId })
        assertTrue(FakeGithubApi.requestedUrls.none { "github.com" in it || "huggingface.co" in it })
    }

    @Test
    fun `imported statements carry custody and plain text gets no invented dates`() = runTest {
        val service = MultiAiAndDataPortabilityService()
        // An empty, honest NO_DATA profile to import into.
        val subject = OsintScraperService(customClient = FakeGithubApi.offlineClient())
            .scrapeAndBuildProfile("import-test", ScraperPlatformMode.GITHUB).profile
        val payload = "First paragraph of my own notes, long enough.\n\nSecond paragraph of the same notes here."
        val custody = ImportCustody.of(payload, "file:notes.txt", nowMillis = 1_700_000_000_000L)

        val res = service.parseAndImportData(payload, subject, custody)

        val imported = res.updatedProfile.scrapedArtifacts
        assertEquals(2, imported.size)
        assertTrue(imported.all { it.connectorRunId == custody.runId && it.sourceUrl == "file:notes.txt" })
        assertTrue(imported.all { it.rawResponseSha256 == custody.rawSha256 && it.rawResponseSha256.length == 64 })
        assertTrue(imported.all { it.fetchedAtMillis == 1_700_000_000_000L })
        assertTrue("plain text has no dates", imported.none { it.hasKnownHour })
        assertTrue(custody.runId.endsWith("-${ManualImportConnector.spec.id}"))
    }
}
