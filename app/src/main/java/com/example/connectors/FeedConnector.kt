package com.example.connectors

import android.util.Xml
import com.example.engine.StylometryEngine
import com.example.model.ScraperPlatformMode
import java.io.StringReader
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/**
 * Reads one RSS 2.0 or Atom feed and turns each entry into a dated statement. ACTIVE (it contacts the
 * site that publishes the feed): opt-in, robots.txt first, prohibited platforms refused.
 * Entries without a parseable date stay undated rather than taking the fetch time.
 */
object FeedConnector : Connector {
    override val spec = ConnectorSpec(
        id = "feed",
        displayName = "RSS / Atom feed",
        kind = ConnectorKind.ACTIVE,
        requiresOptIn = true,
        maxRequestsPerMinute = 6,
        requestsPerRun = 2,
        endpoints = "The feed URL you enter, plus that site's /robots.txt",
        termsNote = "Only the one feed you name; robots.txt respected; the platforms the web-page " +
            "connector refuses are refused here too."
    )

    private const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8"
    private const val MAX_ITEMS = 20
    private const val MAX_TEXT = 360
    private val FEED_PATH = Regex("""(\.(rss|atom|xml)$)|(/(feed|rss|atom)(/|\.xml|$))""", RegexOption.IGNORE_CASE)

    /** A feed is either chosen explicitly (FEED mode) or recognisable from its URL. */
    fun isFeedTarget(target: CollectionTarget): Boolean {
        val url = target.explicitUrl.orEmpty()
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
        return url.isNotEmpty() &&
            (
                target.mode == ScraperPlatformMode.FEED ||
                    FEED_PATH.containsMatchIn("/$path") ||
                    url.contains("format=rss", ignoreCase = true)
                )
    }

    override fun appliesTo(target: CollectionTarget): Boolean = isFeedTarget(target)

    override fun refusal(target: CollectionTarget): Pair<ConnectorRunStatus, String>? = WebFetchPolicy.refusal(target.explicitUrl.orEmpty())

    override suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        val url = target.explicitUrl.orEmpty()
        return refusal(target) ?: WebFetchPolicy.robotsBlock(url, acc, tools) ?: fetchFeed(url, acc, tools, runId)
    }

    private fun fetchFeed(url: String, acc: EvidenceAccumulator, tools: ConnectorToolkit, runId: String): Pair<ConnectorRunStatus, String> {
        val resp = tools.executeHttpGet(url, "RSS_ATOM_FEED", ACCEPT)
        acc.requestsMade++
        val entries = resp.body?.let { body -> runCatching { parse(body) }.getOrDefault(emptyList()) }.orEmpty()
        val mark = acc.scrapedArtifacts.size
        entries.take(MAX_ITEMS).forEachIndexed { i, entry ->
            val art = StylometryEngine.createTimestampedArtifact(
                id = "art-feed-$i-${System.nanoTime()}",
                platform = "FEED_ENTRY",
                timestampIso = entry.publishedUtc.orEmpty(),
                verbatimText = entry.text.take(MAX_TEXT),
                contextLabel = entry.link.ifBlank { url }
            )
            acc.scrapedArtifacts.add(art)
            if (art.hasKnownHour) acc.utcHours.add(art.utcHour)
            acc.corpusBuilder.append(entry.text).append(". ")
        }
        val added = acc.scrapedArtifacts.size - mark
        if (added > 0) acc.liveSources.add("RSS_ATOM_FEED")
        stampCustody(acc.scrapedArtifacts, mark, resp, runId)
        acc.httpProbes.add(resp.toProbe(added))
        return when {
            added > 0 -> ConnectorRunStatus.COLLECTED to "$added entr${if (added == 1) "y" else "ies"}"
            resp.statusCode !in 200..299 -> ConnectorRunStatus.FAILED to "HTTP ${resp.statusCode}"
            resp.body?.let(::declaresDtd) == true -> ConnectorRunStatus.FAILED to "refused: the feed declares a DTD or entities"
            else -> ConnectorRunStatus.NOTHING_FOUND to "not an RSS or Atom feed, or it has no entries"
        }
    }

    data class FeedEntry(val title: String, val body: String, val link: String, val publishedUtc: String?) {
        val text: String get() = listOf(title, body).filter { it.isNotBlank() }.joinToString(": ")
    }

    /** Feeds never need a DTD; declaring one is how entity-expansion ("billion laughs") attacks work. */
    private val DTD_DECLARATION = Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)

    /** True if [xml] declares a DOCTYPE or ENTITY; such feeds are refused without being parsed. */
    fun declaresDtd(xml: String): Boolean = DTD_DECLARATION.containsMatchIn(xml)

    /**
     * RSS `<item>` and Atom `<entry>` elements, in document order. Untrusted input: any DOCTYPE or ENTITY
     * declaration is refused before parsing, and DTD processing is switched off on the parser as well
     * (Android's Xml.newPullParser() turns it on by default).
     */
    fun parse(xml: String): List<FeedEntry> {
        if (declaresDtd(xml)) return emptyList()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val entries = mutableListOf<FeedEntry>()
        var fields: MutableMap<String, String>? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.US)
            when {
                event == XmlPullParser.START_TAG && (name == "item" || name == "entry") -> fields = mutableMapOf()

                event == XmlPullParser.END_TAG && (name == "item" || name == "entry") -> {
                    fields?.let { entries += toEntry(it) }
                    fields = null
                }

                event == XmlPullParser.START_TAG && fields != null && name != null -> readField(parser, name, fields)
            }
            event = parser.next()
        }
        return entries
    }

    private val FIELDS = setOf(
        "title",
        "description",
        "summary",
        "content",
        "content:encoded",
        "pubdate",
        "published",
        "updated",
        "dc:date",
        "link"
    )

    private fun readField(parser: XmlPullParser, name: String, fields: MutableMap<String, String>) {
        if (name !in FIELDS) return
        if (name == "link") {
            // Atom: <link href="..."/>; RSS: <link>...</link>
            parser.getAttributeValue(null, "href")?.let { fields.putIfAbsent("link", it) }
        }
        val text = runCatching { parser.nextText() }.getOrDefault("").trim()
        if (text.isNotEmpty()) fields.putIfAbsent(name, text)
    }

    private fun toEntry(f: Map<String, String>): FeedEntry {
        val body = f["content:encoded"] ?: f["content"] ?: f["description"] ?: f["summary"] ?: ""
        val date = sequenceOf("published", "pubdate", "dc:date", "updated")
            .mapNotNull { f[it] }
            .firstNotNullOfOrNull { Timestamps.anyToUtc(it) }
        return FeedEntry(
            title = plainText(f["title"].orEmpty()),
            body = plainText(body),
            link = f["link"].orEmpty(),
            publishedUtc = date
        )
    }

    private val TAGS = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")

    /** Feed bodies are often HTML; keep the words only. */
    fun plainText(html: String): String = html
        .replace(TAGS, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(SPACES, " ")
        .trim()
}
