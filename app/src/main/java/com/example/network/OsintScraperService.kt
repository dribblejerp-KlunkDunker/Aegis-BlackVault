package com.example.network

import com.example.engine.ForensicMathEngine
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Live OkHttp Multi-Vector OSINT Scraper Service & Intelligence Combiner:
 * 1. Scrapes real GitHub REST API (/users/{handle}, /users/{handle}/repos, /users/{handle}/events/public, /search/repositories)
 *    preserving exact ISO-8601 timestamps when every commit, issue, PR comment, or repo update occurred.
 * 2. Scrapes real Hugging Face Hub API (/api/models, /api/datasets) with model/dataset timestamps and tags.
 * 3. Scrapes arbitrary HTTP/HTTPS URLs using OkHttp with User-Agent rotation, extracting:
 *    - JSON-LD (application/ld+json) structured metadata & datePublished timestamps
 *    - Next.js / React Hydration trees (__NEXT_DATA__)
 *    - OpenGraph (<meta property="og:*">, article:published_time)
 *    - Semantic <time datetime="..."> and <article>/<p>/<blockquote> discourse segments
 * 4. Records per-request HttpEndpointProbe diagnostics (status code, latency ms, payload bytes, server headers)
 *    and computes a full Pennebaker LIWC + 50-word Burrows' Delta Linguistic Personality Profile ready for Room @Transaction insert.
 */
class OsintScraperService(
    customClient: OkHttpClient? = null,
    /** Optional GitHub token (fine-grained, read-only public access is enough). Raises the limit from 60 to 5,000 req/hr. */
    private val githubToken: String = ""
) {

    companion object {
        /** Identifies the app honestly. Browser User-Agent spoofing/rotation was removed. */
        const val USER_AGENT = "AegisBlackvault/3.6 (Android; research workbench)"

        /** Hard cap on bytes read from any single response, to avoid OOM on large or hostile pages. */
        const val MAX_BODY_BYTES = 2L * 1024 * 1024
    }

    private val telemetryInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
        if (original.url.host == "api.github.com") {
            builder.header("X-GitHub-Api-Version", "2022-11-28")
            if (githubToken.isNotBlank()) {
                builder.header("Authorization", "Bearer ${githubToken.trim()}")
            }
        }
        chain.proceed(builder.build())
    }

    private val client: OkHttpClient = customClient ?: OkHttpClient.Builder()
        .addInterceptor(telemetryInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class RawHttpResponse(
        val url: String,
        val platform: String,
        val statusCode: Int,
        val body: String?,
        val latencyMs: Long,
        val payloadBytes: Int,
        val serverHeader: String,
        val timestampHeader: String
    ) {
        fun toProbe(extractedItemsCount: Int): HttpEndpointProbe {
            val badge = when {
                statusCode in 200..299 && extractedItemsCount > 0 -> "$statusCode OK ($extractedItemsCount ITEMS)"
                statusCode in 200..299 -> "$statusCode OK"
                statusCode == 403 || statusCode == 429 -> "$statusCode RATE_LIMIT/WAF"
                statusCode > 0 -> "HTTP $statusCode"
                else -> "NETWORK ERROR (NO DATA)"
            }
            return HttpEndpointProbe(
                id = "probe-${System.nanoTime()}",
                url = url,
                platform = platform,
                httpStatusCode = statusCode,
                latencyMs = latencyMs,
                payloadBytes = payloadBytes,
                extractedItemsCount = extractedItemsCount,
                serverHeader = serverHeader,
                timestampHeader = timestampHeader,
                statusBadge = badge
            )
        }
    }

    data class ScrapeIngestionResult(
        val profile: SubjectProfile,
        val triplets: List<KnowledgeTripletEntity>,
        val httpProbes: List<HttpEndpointProbe>,
        val telemetrySummary: String,
        val liveSourceUsed: String
    )

    fun currentIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Executes a real OkHttp GET request and captures HTTP status code, latency in ms,
     * payload size in bytes, and server/date headers.
     */
    private fun executeHttpGet(
        url: String,
        platform: String,
        acceptHeader: String = "application/json"
    ): RawHttpResponse {
        val startMs = System.currentTimeMillis()
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", acceptHeader)
                .build()
            client.newCall(req).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                // Bounded read: never pull more than MAX_BODY_BYTES into memory.
                val rawBody = resp.body?.source()?.let { src ->
                    src.request(MAX_BODY_BYTES)
                    src.buffer.readUtf8(minOf(src.buffer.size, MAX_BODY_BYTES))
                }
                val bytes = rawBody?.toByteArray(Charsets.UTF_8)?.size ?: 0
                val server = resp.header("Server")
                    ?: resp.header("x-github-request-id")?.let { "GitHub.com ($it)" }
                    ?: resp.header("cf-ray")?.let { "Cloudflare ($it)" }
                    ?: "edge-gateway"
                val dateHdr = resp.header("Last-Modified")
                    ?: resp.header("Date")
                    ?: currentIsoUtc()
                RawHttpResponse(
                    url = url,
                    platform = platform,
                    statusCode = resp.code,
                    body = if (resp.isSuccessful) rawBody else null,
                    latencyMs = elapsed,
                    payloadBytes = bytes,
                    serverHeader = server,
                    timestampHeader = dateHdr
                )
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            RawHttpResponse(
                url = url,
                platform = platform,
                statusCode = 0,
                body = null,
                latencyMs = elapsed,
                payloadBytes = 0,
                serverHeader = "NETWORK_ERROR: ${e.javaClass.simpleName}: ${e.message?.take(120).orEmpty()}",
                timestampHeader = currentIsoUtc()
            )
        }
    }

    /**
     * Parses a user input (GitHub handle, GitHub URL, Hugging Face URL/handle, or arbitrary HTTP/HTTPS URL),
     * captures every scraped statement/commit/model/post with its timestamp, records OkHttp endpoint probes,
     * and computes the full scientific Linguistic Personality Profile.
     */
    suspend fun scrapeAndBuildProfile(
        rawTargetInput: String,
        platformMode: ScraperPlatformMode = ScraperPlatformMode.ALL,
        healedPatchId: String? = null
    ): ScrapeIngestionResult = withContext(Dispatchers.IO) {
        val trimmed = rawTargetInput.trim()
        val isExplicitUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        val cleanHandle = extractCleanHandle(trimmed)
        val displayHandle = cleanHandle.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_]"), "_")
            .take(18)
            .ifBlank { "TARGET_NODE" }

        val corpusBuilder = StringBuilder()
        val utcHours = mutableListOf<Int>()
        val scrapedArtifacts = mutableListOf<ScrapedArtifactItem>()
        val httpProbes = mutableListOf<HttpEndpointProbe>()
        var declaredLocation: String? = null
        var bioText = ""
        var displayName = cleanHandle
        var followerCount = 0
        val repos = mutableListOf<GithubRepoItem>()
        val hfAssets = mutableListOf<HuggingFaceAsset>()
        val liveSources = mutableListOf<String>()
        val commitMessages = mutableListOf<String>()

        val encodedHandle = URLEncoder.encode(cleanHandle, "UTF-8")

        // A blank handle no longer silently falls back to a default account.
        val hasHandle = cleanHandle.isNotBlank()

        val shouldScrapeGithub = hasHandle && (platformMode == ScraperPlatformMode.ALL ||
            platformMode == ScraperPlatformMode.GITHUB ||
            trimmed.contains("github.com", ignoreCase = true))

        val shouldScrapeHf = hasHandle && (platformMode == ScraperPlatformMode.ALL ||
            platformMode == ScraperPlatformMode.HUGGINGFACE ||
            trimmed.contains("huggingface.co", ignoreCase = true))

        // Direct page fetches touch the target's site, so (as in OpenOSINT) they require an explicit
        // URL or explicit DIRECT_URL mode; handles alone never trigger them.
        val shouldScrapeDirectUrl = isExplicitUrl

        // 1. Live GitHub User + Repos + Public Events Scrape (with ISO-8601 Timestamps)
        if (shouldScrapeGithub) {
            val ghUserResp = executeHttpGet(
                url = "https://api.github.com/users/$encodedHandle",
                platform = "GITHUB_USER_API",
                acceptHeader = "application/vnd.github+json"
            )
            var ghUserExtracted = 0
            if (ghUserResp.body != null) {
                try {
                    val u = JSONObject(ghUserResp.body)
                    displayName = u.optString("name", cleanHandle).takeIf { it.isNotBlank() && it != "null" } ?: cleanHandle
                    declaredLocation = u.optString("location", "").takeIf { it.isNotBlank() && it != "null" }
                    bioText = u.optString("bio", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
                    followerCount = u.optInt("followers", 0)
                    val updatedAt = u.optString("updated_at", currentIsoUtc()).takeIf { it.isNotBlank() } ?: currentIsoUtc()
                    val company = u.optString("company", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
                    if (bioText.isNotBlank()) {
                        scrapedArtifacts.add(
                            ForensicMathEngine.createTimestampedArtifact(
                                id = "art-gh-bio-${System.nanoTime()}",
                                platform = "GITHUB_BIO",
                                timestampIso = updatedAt,
                                verbatimText = "$bioText ${if (company.isNotBlank()) "($company)" else ""}".trim(),
                                contextLabel = "github.com/$cleanHandle"
                            )
                        )
                        ghUserExtracted++
                    }
                    corpusBuilder.append(bioText).append(". ").append(company).append(". ")
                    liveSources.add("GITHUB_API")
                } catch (_: Exception) {}
            }
            httpProbes.add(ghUserResp.toProbe(ghUserExtracted))

            val ghReposResp = executeHttpGet(
                url = "https://api.github.com/users/$encodedHandle/repos?sort=updated&per_page=8",
                platform = "GITHUB_REPOS_API",
                acceptHeader = "application/vnd.github+json"
            )
            var ghReposExtracted = 0
            if (ghReposResp.body != null) {
                try {
                    val arr = JSONArray(ghReposResp.body)
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val name = r.optString("name", "repo-$i")
                        val fullName = r.optString("full_name", "$cleanHandle/$name")
                        val desc = r.optString("description", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
                        val lang = r.optString("language", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
                        val stars = r.optInt("stargazers_count", 0)
                        val forks = r.optInt("forks_count", 0)
                        val htmlUrl = r.optString("html_url", "https://github.com/$fullName")
                        val pushedAt = r.optString("pushed_at", r.optString("updated_at", currentIsoUtc()))
                        val topicsArr = r.optJSONArray("topics") ?: JSONArray()
                        val topics = mutableListOf<String>()
                        for (t in 0 until topicsArr.length()) topics.add(topicsArr.optString(t))

                        if (pushedAt.length >= 13 && pushedAt[10] == 'T') {
                            pushedAt.substring(11, 13).toIntOrNull()?.let { utcHours.add(it) }
                        }

                        repos.add(GithubRepoItem(name, fullName, desc, lang, stars, forks, htmlUrl, pushedAt, topics))
                        scrapedArtifacts.add(
                            ForensicMathEngine.createTimestampedArtifact(
                                id = "art-gh-repo-$i-${System.nanoTime()}",
                                platform = "GITHUB_REPO",
                                timestampIso = pushedAt,
                                verbatimText = "[$fullName ($lang, ★$stars)]: $desc",
                                contextLabel = fullName
                            )
                        )
                        ghReposExtracted++
                        if (desc.isNotBlank()) corpusBuilder.append(desc).append(". ")
                    }
                } catch (_: Exception) {}
            }
            httpProbes.add(ghReposResp.toProbe(ghReposExtracted))

            // Extract commit messages, issue comments, PR descriptions & UTC timestamps from GitHub public events
            val ghEventsResp = executeHttpGet(
                url = "https://api.github.com/users/$encodedHandle/events/public?per_page=30",
                platform = "GITHUB_EVENTS_API",
                acceptHeader = "application/vnd.github+json"
            )
            var ghEventsExtracted = 0
            if (ghEventsResp.body != null) {
                try {
                    val events = JSONArray(ghEventsResp.body)
                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val evType = ev.optString("type", "PushEvent")
                        val createdAt = ev.optString("created_at", currentIsoUtc())
                        val repoName = ev.optJSONObject("repo")?.optString("name", cleanHandle) ?: cleanHandle
                        if (createdAt.length >= 13 && createdAt[10] == 'T') {
                            createdAt.substring(11, 13).toIntOrNull()?.let { utcHours.add(it) }
                        }
                        val payload = ev.optJSONObject("payload")

                        // Commits
                        val commits = payload?.optJSONArray("commits")
                        if (commits != null) {
                            for (c in 0 until commits.length()) {
                                val msg = commits.getJSONObject(c).optString("message", "").trim()
                                if (msg.isNotBlank() && commitMessages.size < 12) {
                                    commitMessages.add(msg)
                                    corpusBuilder.append(msg).append(". ")
                                    scrapedArtifacts.add(
                                        ForensicMathEngine.createTimestampedArtifact(
                                            id = "art-commit-$i-$c-${System.nanoTime()}",
                                            platform = "GITHUB_COMMIT",
                                            timestampIso = createdAt,
                                            verbatimText = msg,
                                            contextLabel = repoName
                                        )
                                    )
                                    ghEventsExtracted++
                                }
                            }
                        }

                        // Issue / PR comments
                        val commentBody = payload?.optJSONObject("comment")?.optString("body", "")?.trim() ?: ""
                        if (commentBody.isNotBlank() && scrapedArtifacts.size < 22) {
                            corpusBuilder.append(commentBody.take(400)).append(". ")
                            scrapedArtifacts.add(
                                ForensicMathEngine.createTimestampedArtifact(
                                    id = "art-comment-$i-${System.nanoTime()}",
                                    platform = "GITHUB_${evType.uppercase(Locale.US).take(10)}",
                                    timestampIso = createdAt,
                                    verbatimText = commentBody.take(280),
                                    contextLabel = repoName
                                )
                            )
                            ghEventsExtracted++
                        }
                    }
                } catch (_: Exception) {}
            }
            httpProbes.add(ghEventsResp.toProbe(ghEventsExtracted))

            // NOTE: the old keyword-search fallback (search/repositories?q=<handle>) was removed. It attributed
            // other people's repositories to the subject. A user with no public repos now simply has none.
        }

        // 2. Live Hugging Face Hub API Scrape (Author models + Semantic search models with timestamps)
        if (shouldScrapeHf) {
            val hfAuthorResp = executeHttpGet(
                url = "https://huggingface.co/api/models?author=$encodedHandle&limit=5",
                platform = "HF_AUTHOR_API"
            )
            val beforeAuthorCount = hfAssets.size
            if (hfAuthorResp.body != null) {
                parseHuggingFaceModels(hfAuthorResp.body, hfAssets, scrapedArtifacts, corpusBuilder)
                if (hfAssets.isNotEmpty()) liveSources.add("HF_AUTHOR_API")
            }
            httpProbes.add(hfAuthorResp.toProbe(hfAssets.size - beforeAuthorCount))

            // NOTE: the old popularity-search fallback (models?search=<language>) was removed. It attached
            // unrelated top-downloaded models to the subject.
        }

        // 3. Direct Target URL / Walled-Garden HTML, JSON-LD, __NEXT_DATA__ Hydration & <time> Scraper
        if (shouldScrapeDirectUrl) {
            val targetUrl = trimmed
            val detectedPlatform = ForensicMathEngine.detectWalledGarden(targetUrl)?.name?.uppercase(Locale.US)
                ?: "DIRECT_URL_DOM"
            val htmlResp = executeHttpGet(
                url = targetUrl,
                platform = detectedPlatform,
                acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            var domExtractedCount = 0
            if (!htmlResp.body.isNullOrBlank()) {
                val fetchTimestamp = currentIsoUtc()
                val parsedWebArtifacts = parseHtmlAndHydrationArtifacts(
                    url = targetUrl,
                    html = htmlResp.body,
                    platformLabel = detectedPlatform,
                    fallbackTimestampIso = fetchTimestamp
                )
                parsedWebArtifacts.forEach { webArt ->
                    scrapedArtifacts.add(0, webArt)
                    // Artifacts without a real published timestamp carry the fetch time; exclude them
                    // from the activity histogram so "now" is not mistaken for the author's activity.
                    if (webArt.timestampIso != fetchTimestamp) utcHours.add(webArt.utcHour)
                    corpusBuilder.append(webArt.verbatimText).append(". ")
                    domExtractedCount++
                }
                if (domExtractedCount > 0) {
                    liveSources.add("HTML_HYDRATION_PARSER")
                }
            }
            httpProbes.add(htmlResp.toProbe(domExtractedCount))
        }

        // No fabricated fallbacks: if nothing was collected, the profile says so (dataProvenance = NO_DATA,
        // corpusWordCount = 0) instead of inventing repos, models, quotes or activity hours.
        val combinedCorpus = corpusBuilder.toString().trim().ifBlank {
            scrapedArtifacts.joinToString(" ") { it.verbatimText }
        }

        val eval = ForensicMathEngine.evaluateCorpus(
            rawText = combinedCorpus,
            repoCount = repos.size,
            hfCount = hfAssets.size,
            followerCount = followerCount
        )
        val chrono = ForensicMathEngine.evaluateChronoAndGeo(utcHours, declaredLocation)
        val patchBadge = healedPatchId ?: if (liveSources.isNotEmpty()) liveSources.joinToString("+") else "DIRECT_AST_V3.5"

        val sortedArtifacts = scrapedArtifacts.sortedByDescending { it.timestampIso }.take(20)

        val lingProfile = ForensicMathEngine.buildLinguisticPersonalityProfile(
            handle = displayHandle,
            eval = eval,
            chrono = chrono,
            artifactsCount = sortedArtifacts.size
        )

        val insights = ForensicMathEngine.synthesizeActionableInsights(
            handle = displayHandle,
            eval = eval,
            chrono = chrono,
            repos = repos,
            hfAssets = hfAssets,
            healedPatchId = patchBadge,
            lingProfile = lingProfile
        )

        val frontStage = when {
            bioText.isNotBlank() -> "[PUBLIC BIO // $displayName]: $bioText"
            sortedArtifacts.isNotEmpty() -> "[${sortedArtifacts.first().platform} @ ${sortedArtifacts.first().timestampIso}]: ${sortedArtifacts.first().verbatimText}"
            else -> "[NO DATA COLLECTED]: every source returned nothing or failed; see the HTTP probe log."
        }

        val backStage = when {
            commitMessages.isNotEmpty() -> "[COMMIT TRACE]: \"${commitMessages.first().take(150)}\""
            sortedArtifacts.size >= 2 -> "[${sortedArtifacts[1].platform} @ ${sortedArtifacts[1].timestampIso}]: \"${sortedArtifacts[1].verbatimText.take(150)}\""
            else -> "[NO SECOND STATEMENT]: fewer than two artifacts collected."
        }

        val provHash = ForensicMathEngine.sha256("$displayHandle:$trimmed:${eval.burrowsDelta}:${System.currentTimeMillis()}")
        val profile = SubjectProfile(
            id = "subj-${provHash.take(8)}",
            stixId = ForensicMathEngine.generateStixId("identity", displayHandle),
            handle = displayHandle,
            codeId = "#${displayHandle.take(2)}-${provHash.take(4).uppercase(Locale.US)}",
            alias = if (eval.tokenCount >= DataSufficiency.MIN_WORDS_FOR_TRAITS) {
                "$displayName // ${lingProfile.archetypeTitle.take(26)}"
            } else {
                "$displayName // INSUFFICIENT DATA"
            },
            cluster = "Cluster: ${repos.firstOrNull()?.language?.ifBlank { null } ?: "Unknown"} + LIWC heuristics",
            loc = chrono.locationLabel,
            lat = chrono.lat,
            lon = chrono.lon,
            utcOffset = chrono.inferredUtcOffset,
            troughWindowLabel = chrono.windowLabel,
            burrowsDelta = eval.burrowsDelta,
            styloMatch = eval.styloMatchPct,
            mattrScore = eval.mattr,
            ocean = eval.ocean,
            darkTriad = eval.darkTriad,
            threatMetrics = eval.threatMetrics,
            zScores = eval.zScores30,
            hourlyBins = chrono.hourlyBins,
            frontStageQuote = frontStage,
            backStageQuote = backStage,
            githubRepos = repos,
            huggingFaceAssets = hfAssets,
            actionableInsights = insights,
            scrapedArtifacts = sortedArtifacts,
            linguisticProfile = lingProfile,
            healedPatchId = patchBadge,
            provSha256 = provHash,
            updatedAt = System.currentTimeMillis(),
            consentStatus = ConsentStatus.UNVERIFIED,
            corpusWordCount = eval.tokenCount,
            dataProvenance = if (liveSources.isNotEmpty()) "LIVE: ${liveSources.joinToString(" + ")}" else "NO_DATA"
        ).let { ForensicMathEngine.applyConsentGate(it) }

        val now = System.currentTimeMillis()
        val triplets = mutableListOf<KnowledgeTripletEntity>()
        repos.take(3).forEachIndexed { idx, repo ->
            triplets.add(
                KnowledgeTripletEntity(
                    id = "tr-gh-$displayHandle-$idx-$now",
                    subjectHandle = displayHandle,
                    predicate = "MAINTAINS_REPO (★${repo.stars})",
                    objectNode = repo.fullName,
                    nodeCategory = NodeCategory.GITHUB_REPO.name,
                    confidence = 0.98f,
                    provSha256 = provHash,
                    timestamp = now - idx * 100
                )
            )
        }
        hfAssets.take(3).forEachIndexed { idx, hf ->
            triplets.add(
                KnowledgeTripletEntity(
                    id = "tr-hf-$displayHandle-$idx-$now",
                    subjectHandle = displayHandle,
                    predicate = "HF_${hf.kind} (${hf.pipelineTag})",
                    objectNode = hf.id,
                    nodeCategory = NodeCategory.HF_MODEL.name,
                    confidence = 0.95f,
                    provSha256 = provHash,
                    timestamp = now - 500 - idx * 100
                )
            )
        }

        val sourceLabel = if (liveSources.isNotEmpty()) liveSources.joinToString(" + ") else "NO_DATA"
        val totalLatencyMs = httpProbes.sumOf { it.latencyMs }
        ScrapeIngestionResult(
            profile = profile,
            triplets = triplets,
            httpProbes = httpProbes,
            telemetrySummary = "OKHTTP INGESTED [$displayHandle] VIA $sourceLabel (${httpProbes.size} PROBES, ${totalLatencyMs}ms) // ${sortedArtifacts.size} LOGS, Δ=${eval.burrowsDelta}, ATI=${lingProfile.analyticalThinkingIndex}%",
            liveSourceUsed = sourceLabel
        )
    }

    /**
     * Multi-Strategy HTML / JSON-LD / Hydration (__NEXT_DATA__) / OpenGraph / Semantic <time> Extractor.
     * Parses raw HTML into timestamped ScrapedArtifactItems.
     */
    fun parseHtmlAndHydrationArtifacts(
        url: String,
        html: String,
        platformLabel: String = "DIRECT_URL_DOM",
        fallbackTimestampIso: String = currentIsoUtc()
    ): List<ScrapedArtifactItem> {
        val extracted = mutableListOf<ScrapedArtifactItem>()
        val contextHost = url.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").take(44)

        // Strategy A: Extract meta / OpenGraph timestamps and descriptions
        val metaTimeRegex = Regex(
            """<meta[^>]+(?:property|name)=["'](?:article:published_time|article:modified_time|og:updated_time|pubdate|date|DC\.date\.issued)["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val metaTimestamp = metaTimeRegex.find(html)?.groupValues?.getOrNull(1)?.let { normalizeIsoTimestamp(it, fallbackTimestampIso) }
            ?: fallbackTimestampIso

        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.getOrNull(1)?.let { cleanHtmlEntities(it) } ?: ""

        val ogDescMatch = Regex(
            """<meta[^>]+(?:property|name)=["'](?:og:description|description|twitter:description)["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.let { cleanHtmlEntities(it) } ?: ""

        if (titleMatch.isNotBlank() || ogDescMatch.isNotBlank()) {
            val combinedMeta = listOf(titleMatch, ogDescMatch).filter { it.isNotBlank() }.joinToString(" — ")
            if (combinedMeta.length >= 12) {
                extracted.add(
                    ForensicMathEngine.createTimestampedArtifact(
                        id = "art-meta-${System.nanoTime()}",
                        platform = "${platformLabel}_META",
                        timestampIso = metaTimestamp,
                        verbatimText = combinedMeta.take(320),
                        contextLabel = contextHost
                    )
                )
            }
        }

        // Strategy B: Extract <script type="application/ld+json"> structured data
        val jsonLdRegex = Regex(
            """<script[^>]+type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        jsonLdRegex.findAll(html).take(3).forEachIndexed { idx, match ->
            val rawJson = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (rawJson.startsWith("{")) {
                try {
                    val obj = JSONObject(rawJson)
                    val headline = obj.optString("headline", obj.optString("name", "")).trim()
                    val body = obj.optString("articleBody", obj.optString("description", "")).trim()
                    val datePub = normalizeIsoTimestamp(
                        obj.optString("datePublished", obj.optString("dateCreated", obj.optString("dateModified", ""))),
                        metaTimestamp
                    )
                    val text = listOf(headline, body).filter { it.isNotBlank() }.joinToString(": ").take(340)
                    if (text.length >= 15) {
                        extracted.add(
                            ForensicMathEngine.createTimestampedArtifact(
                                id = "art-jsonld-$idx-${System.nanoTime()}",
                                platform = "${platformLabel}_JSONLD",
                                timestampIso = datePub,
                                verbatimText = cleanHtmlEntities(text),
                                contextLabel = contextHost
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // Strategy C: Extract Next.js / React Hydration <script id="__NEXT_DATA__">
        val nextDataRegex = Regex(
            """<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val nextDataMatch = nextDataRegex.find(html)?.groupValues?.getOrNull(1)?.trim()
        if (!nextDataMatch.isNullOrBlank()) {
            try {
                val textValues = Regex(""""(?:text|body|content|description|title|bio|summary)"\s*:\s*"([^"]{20,320})"""")
                    .findAll(nextDataMatch)
                    .map { it.groupValues[1] }
                    .distinct()
                    .take(3)
                    .toList()
                textValues.forEachIndexed { idx, snippet ->
                    extracted.add(
                        ForensicMathEngine.createTimestampedArtifact(
                            id = "art-nextdata-$idx-${System.nanoTime()}",
                            platform = "${platformLabel}_HYDRATION",
                            timestampIso = metaTimestamp,
                            verbatimText = cleanHtmlEntities(snippet),
                            contextLabel = "$contextHost // __NEXT_DATA__"
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // Strategy D: Semantic <time datetime="..."> and <article>/<p>/<blockquote> blocks
        val timeTags = Regex("""<time[^>]+datetime=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { normalizeIsoTimestamp(it.groupValues[1], metaTimestamp) }
            .toList()

        val paragraphRegex = Regex(
            """<(?:p|blockquote|article|li)[^>]*>(.*?)</(?:p|blockquote|article|li)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        var pIndex = 0
        for (match in paragraphRegex.findAll(html)) {
            if (extracted.size >= 8) break
            val inner = extractCleanTextFromHtml(match.groupValues[1])
            if (inner.length in 35..500) {
                val ts = timeTags.getOrNull(pIndex) ?: metaTimestamp
                extracted.add(
                    ForensicMathEngine.createTimestampedArtifact(
                        id = "art-dom-$pIndex-${System.nanoTime()}",
                        platform = platformLabel,
                        timestampIso = ts,
                        verbatimText = inner.take(320),
                        contextLabel = contextHost
                    )
                )
                pIndex++
            }
        }

        // Strategy E: Fallback full-body cleaned text if no structured paragraphs matched
        if (extracted.isEmpty()) {
            val cleaned = extractCleanTextFromHtml(html)
            if (cleaned.isNotBlank()) {
                extracted.add(
                    ForensicMathEngine.createTimestampedArtifact(
                        id = "art-body-${System.nanoTime()}",
                        platform = platformLabel,
                        timestampIso = metaTimestamp,
                        verbatimText = cleaned.take(340),
                        contextLabel = contextHost
                    )
                )
            }
        }

        return extracted
    }

    private fun normalizeIsoTimestamp(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 19 && trimmed[4] == '-' && trimmed[7] == '-') {
            val base = trimmed.take(19).replace(' ', 'T')
            return "${base}Z"
        }
        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            return "${trimmed.take(10)}T12:00:00Z"
        }
        return fallback
    }

    private fun cleanHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("\\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Allows the user to inject/append custom timestamped statements, transcripts, or scraped posts
     * directly into the active subject profile and dynamically recomputes the full Linguistic Personality Profile!
     */
    suspend fun ingestCustomStatement(
        activeProfile: SubjectProfile,
        verbatimStatement: String,
        platformLabel: String,
        timestampIso: String
    ): SubjectProfile = withContext(Dispatchers.Default) {
        val cleanTs = timestampIso.trim().ifBlank { currentIsoUtc() }
        val newArtifact = ForensicMathEngine.createTimestampedArtifact(
            id = "art-custom-${System.currentTimeMillis()}",
            platform = platformLabel.uppercase(Locale.US).ifBlank { "CUSTOM_INTEL" },
            timestampIso = cleanTs,
            verbatimText = verbatimStatement,
            contextLabel = "Manual Timestamped Ingest"
        )
        val updatedArtifacts = listOf(newArtifact) + activeProfile.scrapedArtifacts
        val combinedCorpus = updatedArtifacts.joinToString(" ") { it.verbatimText }
        val hours = updatedArtifacts.map { it.utcHour }

        val eval = ForensicMathEngine.evaluateCorpus(
            rawText = combinedCorpus,
            repoCount = activeProfile.githubRepos.size,
            hfCount = activeProfile.huggingFaceAssets.size,
            followerCount = 0
        )
        val chrono = ForensicMathEngine.evaluateChronoAndGeo(hours, declaredLocationOf(activeProfile))
        val lingProfile = ForensicMathEngine.buildLinguisticPersonalityProfile(
            handle = activeProfile.handle,
            eval = eval,
            chrono = chrono,
            artifactsCount = updatedArtifacts.size
        )
        val insights = ForensicMathEngine.synthesizeActionableInsights(
            handle = activeProfile.handle,
            eval = eval,
            chrono = chrono,
            repos = activeProfile.githubRepos,
            hfAssets = activeProfile.huggingFaceAssets,
            healedPatchId = activeProfile.healedPatchId,
            lingProfile = lingProfile
        )
        val newHash = ForensicMathEngine.sha256("${activeProfile.handle}:${updatedArtifacts.size}:${eval.burrowsDelta}")

        activeProfile.copy(
            burrowsDelta = eval.burrowsDelta,
            styloMatch = eval.styloMatchPct,
            mattrScore = eval.mattr,
            ocean = eval.ocean,
            darkTriad = eval.darkTriad,
            threatMetrics = eval.threatMetrics,
            zScores = eval.zScores30,
            hourlyBins = chrono.hourlyBins,
            troughWindowLabel = chrono.windowLabel,
            backStageQuote = "[${newArtifact.platform} @ ${newArtifact.timestampIso}]: \"${newArtifact.verbatimText.take(150)}\"",
            scrapedArtifacts = updatedArtifacts,
            linguisticProfile = lingProfile,
            actionableInsights = insights,
            provSha256 = newHash,
            updatedAt = System.currentTimeMillis(),
            corpusWordCount = eval.tokenCount,
            dataProvenance = appendProvenance(activeProfile.dataProvenance, "MANUAL")
        ).let { ForensicMathEngine.applyConsentGate(it) }
    }

    /**
     * Recomputes every derived score from the artifacts already stored for a subject (no network).
     * Used when consent status changes: restricted outputs were never stored for UNVERIFIED subjects,
     * so unlocking them means recomputing from the collected evidence, then re-applying the gate.
     */
    suspend fun recomputeFromArtifacts(profile: SubjectProfile): SubjectProfile = withContext(Dispatchers.Default) {
        val artifacts = profile.scrapedArtifacts
        val corpus = artifacts.joinToString(" ") { it.verbatimText }
        val eval = ForensicMathEngine.evaluateCorpus(
            rawText = corpus,
            repoCount = profile.githubRepos.size,
            hfCount = profile.huggingFaceAssets.size,
            followerCount = 0
        )
        val chrono = ForensicMathEngine.evaluateChronoAndGeo(artifacts.map { it.utcHour }, declaredLocationOf(profile))
        val lingProfile = ForensicMathEngine.buildLinguisticPersonalityProfile(
            handle = profile.handle,
            eval = eval,
            chrono = chrono,
            artifactsCount = artifacts.size
        )
        val insights = ForensicMathEngine.synthesizeActionableInsights(
            handle = profile.handle,
            eval = eval,
            chrono = chrono,
            repos = profile.githubRepos,
            hfAssets = profile.huggingFaceAssets,
            healedPatchId = profile.healedPatchId,
            lingProfile = lingProfile
        )
        profile.copy(
            burrowsDelta = eval.burrowsDelta,
            styloMatch = eval.styloMatchPct,
            mattrScore = eval.mattr,
            ocean = eval.ocean,
            darkTriad = eval.darkTriad,
            threatMetrics = eval.threatMetrics,
            zScores = eval.zScores30,
            hourlyBins = chrono.hourlyBins,
            troughWindowLabel = chrono.windowLabel,
            loc = chrono.locationLabel,
            lat = chrono.lat,
            lon = chrono.lon,
            utcOffset = chrono.inferredUtcOffset,
            linguisticProfile = lingProfile,
            actionableInsights = insights,
            corpusWordCount = eval.tokenCount,
            updatedAt = System.currentTimeMillis()
        ).let { ForensicMathEngine.applyConsentGate(it) }
    }

    /** The location the subject publicly declared, if known (never a restricted/inferred label). */
    private fun declaredLocationOf(profile: SubjectProfile): String? {
        val raw = profile.loc
        return when {
            raw.isBlank() -> null
            raw.startsWith(ForensicMathEngine.RESTRICTED_LABEL) -> null
            raw.startsWith("DECLARED: ") -> raw.removePrefix("DECLARED: ").trim()
            raw.startsWith("UNKNOWN") || raw.startsWith("MERIDIAN") -> null
            else -> raw.substringBefore("(").substringBefore("//").trim().ifBlank { null }
        }
    }

    private fun appendProvenance(existing: String, tag: String): String =
        if (existing.isBlank()) tag else if (existing.contains(tag)) existing else "$existing + $tag"

    /**
     * Looks up reference tools on GitHub and Hugging Face and links them in the knowledge graph as
     * REFERENCE_TOOL nodes. These results are NOT attributed to the subject: they are not added to the
     * subject's repos, models, artifacts or corpus, and the subject's scores are not recomputed from them.
     * (The previous version merged strangers' repo descriptions into the subject's text and re-scored
     * their personality from it.)
     */
    suspend fun harvestGithubAndHuggingFaceEnhancements(
        activeProfile: SubjectProfile
    ): ScrapeIngestionResult = withContext(Dispatchers.IO) {
        val httpProbes = mutableListOf<HttpEndpointProbe>()
        val toolRepos = mutableListOf<GithubRepoItem>()
        val toolModels = mutableListOf<HuggingFaceAsset>()
        val liveHitSources = mutableListOf<String>()

        val ghQuery = URLEncoder.encode("stylometry OR psycholinguistics OR disinformation", "UTF-8")
        val ghSearchResp = executeHttpGet(
            url = "https://api.github.com/search/repositories?q=$ghQuery&sort=stars&per_page=5",
            platform = "GITHUB_TOOL_SEARCH",
            acceptHeader = "application/vnd.github+json"
        )
        if (ghSearchResp.body != null) {
            try {
                val items = JSONObject(ghSearchResp.body).optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val r = items.getJSONObject(i)
                    val fullName = r.optString("full_name", "")
                    if (fullName.isBlank()) continue
                    toolRepos.add(
                        GithubRepoItem(
                            name = r.optString("name", fullName),
                            fullName = fullName,
                            description = r.optString("description", "").takeIf { it != "null" }.orEmpty(),
                            language = r.optString("language", "").takeIf { it != "null" }.orEmpty(),
                            stars = r.optInt("stargazers_count", 0),
                            forks = r.optInt("forks_count", 0),
                            url = r.optString("html_url", "https://github.com/$fullName"),
                            updatedAtIso = r.optString("updated_at", currentIsoUtc())
                        )
                    )
                }
                liveHitSources.add("GITHUB_TOOL_SEARCH")
            } catch (_: Exception) {}
        }
        httpProbes.add(ghSearchResp.toProbe(toolRepos.size))

        val hfResp = executeHttpGet(
            url = "https://huggingface.co/api/models?search=propaganda&sort=downloads&direction=-1&limit=5",
            platform = "HF_TOOL_SEARCH"
        )
        if (hfResp.body != null) {
            // Parse into throwaway artifact/corpus buffers so nothing reaches the subject's corpus.
            parseHuggingFaceModels(hfResp.body, toolModels, mutableListOf(), StringBuilder())
            if (toolModels.isNotEmpty()) liveHitSources.add("HF_TOOL_SEARCH")
        }
        httpProbes.add(hfResp.toProbe(toolModels.size))

        val now = System.currentTimeMillis()
        val refHash = ForensicMathEngine.sha256("${activeProfile.handle}:REFERENCE_TOOLS:$now")
        val newTriplets = mutableListOf<KnowledgeTripletEntity>()
        toolRepos.take(3).forEachIndexed { idx, r ->
            newTriplets.add(
                KnowledgeTripletEntity(
                    id = "tr-ref-gh-$idx-$now",
                    subjectHandle = activeProfile.handle,
                    predicate = "REFERENCE_TOOL (not attributed to subject, ★${r.stars})",
                    objectNode = r.fullName,
                    nodeCategory = NodeCategory.OSINT_TOOL.name,
                    confidence = 1.0f,
                    provSha256 = refHash,
                    timestamp = now - idx * 50
                )
            )
        }
        toolModels.take(3).forEachIndexed { idx, hf ->
            newTriplets.add(
                KnowledgeTripletEntity(
                    id = "tr-ref-hf-$idx-$now",
                    subjectHandle = activeProfile.handle,
                    predicate = "REFERENCE_MODEL (not attributed to subject)",
                    objectNode = hf.id,
                    nodeCategory = NodeCategory.HF_MODEL.name,
                    confidence = 1.0f,
                    provSha256 = refHash,
                    timestamp = now - 200 - idx * 50
                )
            )
        }

        val srcSummary = if (liveHitSources.isNotEmpty()) liveHitSources.joinToString("+") else "NO_DATA"
        ScrapeIngestionResult(
            profile = activeProfile,
            triplets = newTriplets,
            httpProbes = httpProbes,
            telemetrySummary = "REFERENCE TOOL LOOKUP ($srcSummary) // ${toolRepos.size} REPOS, ${toolModels.size} HF MODELS LINKED AS REFERENCES (SUBJECT PROFILE UNCHANGED)",
            liveSourceUsed = srcSummary
        )
    }

    private fun parseHuggingFaceModels(
        rawJson: String,
        targetList: MutableList<HuggingFaceAsset>,
        artifactsList: MutableList<ScrapedArtifactItem>,
        corpusBuilder: StringBuilder
    ) {
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val id = m.optString("id", m.optString("modelId", "hf/model-$i"))
                val tag = m.optString("pipeline_tag", "text-classification").ifBlank { "transformers" }
                val downloads = m.optInt("downloads", 0)
                val likes = m.optInt("likes", 0)
                val lastMod = m.optString("lastModified", m.optString("createdAt", currentIsoUtc())).ifBlank { currentIsoUtc() }
                val tagsArr = m.optJSONArray("tags") ?: JSONArray()
                val tags = mutableListOf<String>()
                for (t in 0 until minOf(6, tagsArr.length())) {
                    tags.add(tagsArr.optString(t))
                }
                val summary = "Hugging Face Model [$id] ($tag, ⬇$downloads) — Tags: ${tags.joinToString(", ")}"
                if (targetList.none { it.id.equals(id, ignoreCase = true) }) {
                    targetList.add(
                        HuggingFaceAsset(
                            id = id,
                            kind = "MODEL",
                            pipelineTag = tag,
                            downloads = downloads,
                            likes = likes,
                            summary = summary,
                            lastModifiedIso = lastMod,
                            tags = tags
                        )
                    )
                    artifactsList.add(
                        ForensicMathEngine.createTimestampedArtifact(
                            id = "art-hf-$i-${System.nanoTime()}",
                            platform = "HUGGING_FACE",
                            timestampIso = lastMod,
                            verbatimText = summary,
                            contextLabel = "huggingface.co/$id"
                        )
                    )
                    corpusBuilder.append(summary).append(". ")
                }
            }
        } catch (_: Exception) {}
    }

    fun extractCleanHandle(raw: String): String {
        val noProto = raw.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").trimEnd('/')
        val segments = noProto.split("/").filter { it.isNotBlank() }
        val candidate = when {
            segments.size >= 2 && (segments[0].contains("github.com") || segments[0].contains("huggingface.co")) -> segments[1]
            segments.size >= 2 -> segments.last()
            segments.size == 1 -> segments[0].substringBefore(".")
            else -> raw
        }
        return candidate.removePrefix("@").substringBefore("?").substringBefore("#").trim()
    }

    fun extractCleanTextFromHtml(html: String): String {
        val noScripts = html.replace(
            Regex("<(script|style|svg|noscript)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            " "
        )
        val noTags = noScripts.replace(Regex("<[^>]+>"), " ")
        return cleanHtmlEntities(noTags)
    }
}
