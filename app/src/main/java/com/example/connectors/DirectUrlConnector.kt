package com.example.connectors

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

    /** Kept for callers and tests; the list lives in [WebFetchPolicy]. */
    fun prohibitedPlatform(url: String): String? = WebFetchPolicy.prohibitedPlatform(url)

    /** Any explicit URL that isn't a feed and isn't covered by a platform's public API connector. */
    override fun appliesTo(target: CollectionTarget): Boolean = target.explicitUrl != null &&
        !FeedConnector.isFeedTarget(target) &&
        !GithubConnector.appliesTo(target) &&
        !HuggingFaceConnector.appliesTo(target)

    override suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        val targetUrl = target.explicitUrl.orEmpty()
        // The orchestrator already applied refusal(); checked again so a direct call can never bypass it.
        return refusal(target) ?: WebFetchPolicy.robotsBlock(targetUrl, acc, tools) ?: fetchPage(targetUrl, acc, tools, runId)
    }

    override fun refusal(target: CollectionTarget): Pair<ConnectorRunStatus, String>? = WebFetchPolicy.refusal(target.explicitUrl.orEmpty())

    private fun fetchPage(
        targetUrl: String,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        val platform = "DIRECT_URL_DOM"
        val htmlResp = tools.executeHttpGet(targetUrl, platform, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        acc.requestsMade++
        // Statements without a published date stay undated (empty timestamp, no hour); the fetch time is
        // recorded in custody, never passed off as when the author wrote it.
        val parsed = htmlResp.body?.takeIf { it.isNotBlank() }
            ?.let { tools.parseHtmlAndHydrationArtifacts(targetUrl, it, platform, fallbackTimestampIso = "") }
            .orEmpty()
        parsed.forEach { webArt ->
            acc.scrapedArtifacts.add(0, webArt)
            if (webArt.hasKnownHour) acc.utcHours.add(webArt.utcHour)
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
