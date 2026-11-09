package com.example.connectors

import java.net.URI
import java.util.Locale

/**
 * Fetches one explicit page and extracts its statements (JSON-LD, hydration payloads, OpenGraph,
 * semantic `<time>`). ACTIVE: it contacts the target's own site, so it is off until the user opts in,
 * it checks robots.txt first, and it refuses platforms whose terms prohibit automated collection.
 */
object DirectUrlConnector : Connector {
    override val spec = ConnectorSpec(
        id = "direct_url",
        displayName = "Explicit web page",
        kind = ConnectorKind.ACTIVE,
        requiresOptIn = true,
        maxRequestsPerMinute = 6,
        requestsPerRun = 2,
        endpoints = "The URL you enter, plus that site's /robots.txt",
        termsNote = "Only the one page you name; robots.txt respected; social platforms that prohibit " +
            "automated collection are refused. Use their own data export of your account instead."
    )

    /**
     * Platforms whose terms prohibit automated collection. Collection from them goes through their own
     * data-export tools (your account) and manual import, never through this connector.
     */
    val PROHIBITED_HOSTS: Map<String, String> = mapOf(
        "tiktok.com" to "TikTok",
        "instagram.com" to "Instagram",
        "facebook.com" to "Facebook",
        "fb.com" to "Facebook",
        "threads.net" to "Threads",
        "x.com" to "X",
        "twitter.com" to "X",
        "linkedin.com" to "LinkedIn",
        "snapchat.com" to "Snapchat",
        "onlyfans.com" to "OnlyFans",
        "youtube.com" to "YouTube",
        "reddit.com" to "Reddit",
        "discord.com" to "Discord"
    )

    /** The platform name if [url]'s host is (a subdomain of) a prohibited platform. */
    fun prohibitedPlatform(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        return PROHIBITED_HOSTS.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value
    }

    override fun appliesTo(target: CollectionTarget): Boolean = target.explicitUrl != null

    override suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        val targetUrl = target.explicitUrl.orEmpty()
        val refusal = refuseByTerms(targetUrl) ?: refuseByRobots(targetUrl, acc, tools)
        return refusal ?: fetchPage(targetUrl, acc, tools, runId)
    }

    private fun refuseByTerms(targetUrl: String): Pair<ConnectorRunStatus, String>? = prohibitedPlatform(targetUrl)?.let { platform ->
        ConnectorRunStatus.BLOCKED_PLATFORM_TERMS to
            "$platform's terms prohibit automated collection; import your own account export instead"
    }

    /** robots.txt first (RFC 9309); an unreachable file counts as "disallow everything". */
    private fun refuseByRobots(targetUrl: String, acc: EvidenceAccumulator, tools: ConnectorToolkit): Pair<ConnectorRunStatus, String>? {
        val uri = runCatching { URI(targetUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.US)
        val isWebUrl = scheme == "http" || scheme == "https"
        if (uri == null || uri.host.isNullOrBlank() || !isWebUrl) return ConnectorRunStatus.FAILED to "not an http(s) URL"

        val robotsResp = tools.executeHttpGet("$scheme://${uri.rawAuthority}/robots.txt", "ROBOTS_TXT", "text/plain")
        acc.requestsMade++
        acc.httpProbes.add(robotsResp.toProbe(0))
        val robots = RobotsTxt.forFetch(robotsResp.statusCode, robotsResp.body)
        val pathAndQuery = uri.rawPath.orEmpty().ifBlank { "/" } + uri.rawQuery?.let { "?$it" }.orEmpty()
        return when {
            robots.isAllowed(pathAndQuery) -> null
            robots.unreachable -> ConnectorRunStatus.BLOCKED_ROBOTS_TXT to "robots.txt could not be read (HTTP ${robotsResp.statusCode})"
            else -> ConnectorRunStatus.BLOCKED_ROBOTS_TXT to "robots.txt disallows $pathAndQuery"
        }
    }

    private fun fetchPage(
        targetUrl: String,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        val platform = "DIRECT_URL_DOM"
        val htmlResp = tools.executeHttpGet(targetUrl, platform, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        acc.requestsMade++
        val fetchTimestamp = tools.currentIsoUtc()
        val parsed = htmlResp.body?.takeIf { it.isNotBlank() }
            ?.let { tools.parseHtmlAndHydrationArtifacts(targetUrl, it, platform, fetchTimestamp) }
            .orEmpty()
        parsed.forEach { webArt ->
            acc.scrapedArtifacts.add(0, webArt)
            // Artifacts without a real published timestamp carry the fetch time; exclude them from the
            // activity histogram so "now" is not mistaken for the author's activity.
            if (webArt.timestampIso != fetchTimestamp) acc.utcHours.add(webArt.utcHour)
            acc.corpusBuilder.append(webArt.verbatimText).append(". ")
        }
        if (parsed.isNotEmpty()) acc.liveSources.add("HTML_HYDRATION_PARSER")
        stampCustody(acc.scrapedArtifacts, 0, htmlResp, runId, toIndex = parsed.size)
        acc.httpProbes.add(htmlResp.toProbe(parsed.size))
        return when {
            parsed.isNotEmpty() -> ConnectorRunStatus.COLLECTED to "${parsed.size} statement(s)"
            htmlResp.statusCode !in 200..299 -> ConnectorRunStatus.FAILED to "HTTP ${htmlResp.statusCode}"
            else -> ConnectorRunStatus.NOTHING_FOUND to "no dated statements on the page"
        }
    }
}
