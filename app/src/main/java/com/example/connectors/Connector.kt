package com.example.connectors

import com.example.model.GithubRepoItem
import com.example.model.HttpEndpointProbe
import com.example.model.HuggingFaceAsset
import com.example.model.ScrapedArtifactItem
import com.example.model.ScraperPlatformMode
import com.example.network.OsintScraperService.RawHttpResponse

/**
 * Collection is split into small, declared connectors (roadmap Phase 3, pattern from OpenOSINT).
 * Each one says what it touches, how often it may run and whether it needs an explicit opt-in.
 * Every artifact a connector produces carries its run id, source URL, fetch time, HTTP status and
 * the SHA-256 of the raw response (chain of custody, Phase 2).
 */
enum class ConnectorKind(val label: String, val description: String) {
    PASSIVE(
        "Passive",
        "Reads a platform's public API. Never contacts servers the subject runs."
    ),
    ACTIVE(
        "Active",
        "Contacts the target's own site. Off until you opt in, and always respects robots.txt."
    )
}

data class ConnectorSpec(
    val id: String,
    val displayName: String,
    val kind: ConnectorKind,
    /** Active connectors always require it; passive ones never do. */
    val requiresOptIn: Boolean,
    /** Requests this connector may make per minute, across runs. */
    val maxRequestsPerMinute: Int,
    /** Requests one run makes (used to check the budget before starting). */
    val requestsPerRun: Int,
    val endpoints: String,
    val termsNote: String
)

enum class ConnectorRunStatus(val label: String) {
    COLLECTED("Collected"),
    NOTHING_FOUND("Nothing found"),
    SKIPPED_NOT_OPTED_IN("Skipped: needs opt-in"),
    SKIPPED_RATE_LIMITED("Skipped: rate limit"),
    BLOCKED_ROBOTS_TXT("Blocked by robots.txt"),
    BLOCKED_PLATFORM_TERMS("Refused: platform terms"),
    FAILED("Failed")
}

/** One line of the run log the UI shows and the audit log summarises. */
data class ConnectorRunRecord(
    val runId: String,
    val connectorId: String,
    val displayName: String,
    val kind: ConnectorKind,
    val status: ConnectorRunStatus,
    val artifacts: Int,
    val requests: Int,
    val detail: String = ""
)

/** What the user asked to collect. */
data class CollectionTarget(
    val raw: String,
    /** Handle extracted from the input (blank when none). */
    val handle: String,
    /** The input when it is an explicit http(s) URL, else null. */
    val explicitUrl: String?,
    val mode: ScraperPlatformMode
)

/** Mutable evidence that connectors add to; the scraper turns it into a profile afterwards. */
class EvidenceAccumulator(initialDisplayName: String) {
    val corpusBuilder = StringBuilder()
    val utcHours = mutableListOf<Int>()
    val scrapedArtifacts = mutableListOf<ScrapedArtifactItem>()
    val httpProbes = mutableListOf<HttpEndpointProbe>()
    var declaredLocation: String? = null
    var bioText = ""
    var displayName = initialDisplayName
    var followerCount = 0
    val repos = mutableListOf<GithubRepoItem>()
    val hfAssets = mutableListOf<HuggingFaceAsset>()
    val liveSources = mutableListOf<String>()
    val commitMessages = mutableListOf<String>()
    var requestsMade = 0
}

/** The scraper's HTTP and parsing helpers, as connectors see them. */
interface ConnectorToolkit {
    fun executeHttpGet(url: String, platform: String, acceptHeader: String = "application/json"): RawHttpResponse
    fun currentIsoUtc(): String
    fun parseHuggingFaceModels(
        rawJson: String,
        targetList: MutableList<HuggingFaceAsset>,
        artifactsList: MutableList<ScrapedArtifactItem>,
        corpusBuilder: StringBuilder
    )
    fun parseHtmlAndHydrationArtifacts(
        url: String,
        html: String,
        platformLabel: String = "DIRECT_URL_DOM",
        fallbackTimestampIso: String = currentIsoUtc()
    ): List<ScrapedArtifactItem>
}

interface Connector {
    val spec: ConnectorSpec

    /** Whether this connector has anything to do for [target] (input shape and selected mode). */
    fun appliesTo(target: CollectionTarget): Boolean

    /**
     * Collects into [acc]. Returns the run's status (COLLECTED, NOTHING_FOUND, a BLOCKED_* status or
     * FAILED) and a short detail line. Must stamp custody on every artifact it adds, with [runId].
     */
    suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String>
}

/**
 * Chain of custody: stamps every artifact added since [fromIndex] (up to [toIndex]) with the response
 * it came from, so each stored statement traces to a URL, a fetch time, a status, a hash of the exact
 * bytes read, and the connector run.
 */
fun stampCustody(
    artifacts: MutableList<ScrapedArtifactItem>,
    fromIndex: Int,
    resp: RawHttpResponse,
    runId: String,
    toIndex: Int = artifacts.size
) {
    for (i in fromIndex until toIndex) {
        artifacts[i] = artifacts[i].copy(
            sourceUrl = resp.url,
            fetchedAtMillis = resp.fetchedAtMillis,
            httpStatus = resp.statusCode,
            rawResponseSha256 = resp.rawSha256,
            connectorRunId = runId
        )
    }
}

/**
 * Per-connector sliding one-minute request budget, shared by every run in the process.
 * [nowMillis] is injectable for tests.
 */
class ConnectorRateLimiter(private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val sent = mutableMapOf<String, ArrayDeque<Long>>()

    /** Reserves [cost] requests for [connectorId] if the budget allows; returns false otherwise. */
    @Synchronized
    fun tryAcquire(connectorId: String, maxPerMinute: Int, cost: Int): Boolean {
        val now = nowMillis()
        val window = sent.getOrPut(connectorId) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() >= 60_000L) window.removeFirst()
        if (window.size + cost > maxPerMinute) return false
        repeat(cost) { window.addLast(now) }
        return true
    }
}

/** Every connector the app ships, in the order they run. */
object ConnectorRegistry {
    val all: List<Connector> = listOf(GithubConnector, HuggingFaceConnector, DirectUrlConnector)

    fun byId(id: String): Connector? = all.find { it.spec.id == id }
}
