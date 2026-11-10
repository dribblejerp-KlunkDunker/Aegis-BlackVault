package com.example.connectors

import java.net.URI
import java.util.Locale

/**
 * What every ACTIVE web connector (page, feed) must do before contacting a site:
 * refuse platforms whose terms prohibit automated collection, refuse anything that isn't an http(s)
 * URL, and obey robots.txt (RFC 9309; an unreachable file means disallow).
 */
object WebFetchPolicy {
    /**
     * Platforms whose terms prohibit automated collection. Material from them comes in through their
     * own data-export tools (your account) and manual import, never through a web connector.
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

    /** Refusal decided without sending anything: platform terms, then URL shape. */
    fun refusal(url: String): Pair<ConnectorRunStatus, String>? {
        prohibitedPlatform(url)?.let { platform ->
            return ConnectorRunStatus.BLOCKED_PLATFORM_TERMS to
                "$platform's terms prohibit automated collection; import your own account export instead"
        }
        val uri = runCatching { URI(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.US)
        val isWebUrl = (scheme == "http" || scheme == "https") && !uri?.host.isNullOrBlank()
        return if (isWebUrl) null else ConnectorRunStatus.FAILED to "not an http(s) URL"
    }

    /** Reads the site's robots.txt (one request) and returns a block, or null when [url] may be fetched. */
    fun robotsBlock(url: String, acc: EvidenceAccumulator, tools: ConnectorToolkit): Pair<ConnectorRunStatus, String>? {
        val uri = URI(url) // refusal() has already rejected anything that isn't a valid http(s) URL
        val scheme = uri.scheme.lowercase(Locale.US)
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
}
