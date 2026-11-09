package com.example

import com.example.connectors.ConnectorKind
import com.example.connectors.ConnectorRateLimiter
import com.example.connectors.ConnectorRegistry
import com.example.connectors.ConnectorRunStatus
import com.example.connectors.DirectUrlConnector
import com.example.connectors.RobotsTxt
import com.example.model.ScraperPlatformMode
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

/** Roadmap Phase 3: collection through declared connectors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectorTest {

    private val directUrl = setOf(DirectUrlConnector.spec.id)

    @Before
    fun setUp() = FakeGithubApi.requestedUrls.clear()

    // ---- the contract ----

    @Test
    fun `every active connector requires opt-in and every passive one does not`() {
        ConnectorRegistry.all.forEach { c ->
            assertEquals(c.spec.id, c.spec.kind == ConnectorKind.ACTIVE, c.spec.requiresOptIn)
            assertTrue(c.spec.requestsPerRun <= c.spec.maxRequestsPerMinute)
            assertTrue(c.spec.termsNote.isNotBlank() && c.spec.endpoints.isNotBlank())
        }
        assertEquals(ConnectorRegistry.all.size, ConnectorRegistry.all.map { it.spec.id }.toSet().size)
    }

    // ---- robots.txt (RFC 9309) ----

    @Test
    fun `robots rules - longest match wins, allow wins ties, wildcards and end anchors`() {
        val r = RobotsTxt.parse(
            """
            User-agent: *
            Disallow: /private/
            Allow: /private/public-note
            Disallow: /*.pdf$
            Disallow:
            """.trimIndent()
        )
        assertTrue(r.isAllowed("/"))
        assertTrue(r.isAllowed("/notes/a"))
        assertFalse(r.isAllowed("/private/draft"))
        assertTrue(r.isAllowed("/private/public-note"))
        assertFalse(r.isAllowed("/papers/x.pdf"))
        assertTrue(r.isAllowed("/papers/x.pdf?download=1"))
        val tie = RobotsTxt.parse("User-agent: *\nDisallow: /a\nAllow: /a")
        assertTrue(tie.isAllowed("/a"))
    }

    @Test
    fun `robots - our own group beats the star group, and fetch failures are handled per RFC 9309`() {
        val r = RobotsTxt.parse(
            """
            User-agent: *
            Disallow: /

            User-agent: AegisBlackvault
            Allow: /
            """.trimIndent()
        )
        assertTrue(r.isAllowed("/anything"))
        assertTrue("4xx means no restrictions", RobotsTxt.forFetch(404, null).isAllowed("/x"))
        assertFalse("5xx means treat as disallowed", RobotsTxt.forFetch(503, null).isAllowed("/x"))
        assertFalse("network failure means treat as disallowed", RobotsTxt.forFetch(0, null).isAllowed("/x"))
    }

    // ---- rate limits ----

    @Test
    fun `rate limiter keeps a per-connector one-minute budget`() {
        var now = 0L
        val limiter = ConnectorRateLimiter { now }
        assertTrue(limiter.tryAcquire("github", maxPerMinute = 6, cost = 3))
        assertTrue(limiter.tryAcquire("github", 6, 3))
        assertFalse(limiter.tryAcquire("github", 6, 3))
        assertTrue("budgets are per connector", limiter.tryAcquire("huggingface", 6, 3))
        now += 60_000
        assertTrue(limiter.tryAcquire("github", 6, 3))
    }

    @Test
    fun `a connector over its budget is skipped without any request`() = runTest {
        val limiter = ConnectorRateLimiter { 0L }
        val scraper = OsintScraperService(customClient = FakeGithubApi.client(), rateLimiter = limiter)
        repeat(10) { scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB) }
        FakeGithubApi.requestedUrls.clear()

        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB)
        val run = result.connectorRuns.single { it.connectorId == "github" }
        assertEquals(ConnectorRunStatus.SKIPPED_RATE_LIMITED, run.status)
        assertEquals(0, run.requests)
        assertTrue(FakeGithubApi.requestedUrls.isEmpty())
    }

    // ---- the web-page connector (ACTIVE) ----

    @Test
    fun `the web-page connector does nothing without opt-in`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.ARTICLE_URL, ScraperPlatformMode.DIRECT_URL)

        val run = result.connectorRuns.single { it.connectorId == DirectUrlConnector.spec.id }
        assertEquals(ConnectorRunStatus.SKIPPED_NOT_OPTED_IN, run.status)
        assertTrue("no request reached the site", FakeGithubApi.requestedUrls.none { it.contains("pages.example") })
        assertEquals("NO_DATA", result.profile.dataProvenance)
    }

    @Test
    fun `with opt-in it reads robots txt first, then the page, and stamps custody with its run id`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.ARTICLE_URL, ScraperPlatformMode.DIRECT_URL, directUrl)

        val run = result.connectorRuns.single { it.connectorId == DirectUrlConnector.spec.id }
        assertEquals(ConnectorRunStatus.COLLECTED, run.status)
        assertEquals(2, run.requests)
        val siteRequests = FakeGithubApi.requestedUrls.filter { it.contains("pages.example") }
        assertEquals(listOf("${FakeGithubApi.SITE}/robots.txt", FakeGithubApi.ARTICLE_URL), siteRequests)

        val arts = result.profile.scrapedArtifacts
        assertTrue(arts.isNotEmpty())
        assertTrue(arts.all { it.connectorRunId == run.runId && it.sourceUrl == FakeGithubApi.ARTICLE_URL })
        assertTrue(arts.all { it.rawResponseSha256.length == 64 && it.httpStatus == 200 })
        assertTrue(run.runId.endsWith("-${DirectUrlConnector.spec.id}"))
    }

    @Test
    fun `robots txt disallow stops the page fetch`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.PRIVATE_URL, ScraperPlatformMode.DIRECT_URL, directUrl)

        val run = result.connectorRuns.single { it.connectorId == DirectUrlConnector.spec.id }
        assertEquals(ConnectorRunStatus.BLOCKED_ROBOTS_TXT, run.status)
        assertFalse("the disallowed page was never requested", FakeGithubApi.requestedUrls.contains(FakeGithubApi.PRIVATE_URL))
        assertTrue(result.profile.scrapedArtifacts.isEmpty())
    }

    @Test
    fun `an unreachable robots txt counts as disallow`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.offlineClient())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.ARTICLE_URL, ScraperPlatformMode.DIRECT_URL, directUrl)
        assertEquals(
            ConnectorRunStatus.BLOCKED_ROBOTS_TXT,
            result.connectorRuns.single { it.connectorId == DirectUrlConnector.spec.id }.status
        )
        assertEquals(listOf("${FakeGithubApi.SITE}/robots.txt"), FakeGithubApi.requestedUrls)
    }

    @Test
    fun `platforms whose terms prohibit collection are refused before any request, even with opt-in`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        listOf(
            "https://www.tiktok.com/@someone",
            "https://instagram.com/someone",
            "https://m.facebook.com/someone",
            "https://x.com/someone",
            "https://old.reddit.com/user/someone"
        ).forEach { url ->
            FakeGithubApi.requestedUrls.clear()
            val result = scraper.scrapeAndBuildProfile(url, ScraperPlatformMode.DIRECT_URL, directUrl)
            val run = result.connectorRuns.single { it.connectorId == DirectUrlConnector.spec.id }
            assertEquals(url, ConnectorRunStatus.BLOCKED_PLATFORM_TERMS, run.status)
            assertTrue(url, FakeGithubApi.requestedUrls.isEmpty())
        }
        assertNull(DirectUrlConnector.prohibitedPlatform("https://pages.example/notes"))
        assertNull("look-alike hosts are not matched", DirectUrlConnector.prohibitedPlatform("https://notx.com/a"))
    }

    // ---- passive connectors ----

    @Test
    fun `a handle runs only the passive connectors, each with its own run id`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val result = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.ALL, directUrl)

        assertEquals(listOf("github", "huggingface"), result.connectorRuns.map { it.connectorId })
        assertTrue(result.connectorRuns.all { it.kind == ConnectorKind.PASSIVE })
        val github = result.connectorRuns.first { it.connectorId == "github" }
        assertEquals(ConnectorRunStatus.COLLECTED, github.status)
        assertEquals(3, github.requests)
        assertTrue(result.profile.scrapedArtifacts.all { it.connectorRunId == github.runId })
    }
}
