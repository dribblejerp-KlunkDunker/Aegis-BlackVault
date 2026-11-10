package com.example.connectors

import com.example.engine.StylometryEngine
import com.example.model.GithubRepoItem
import com.example.model.ScraperPlatformMode
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * GitHub's public REST API: profile, recently pushed repositories and public events (commit messages,
 * issue and PR comments). Passive: it reads GitHub's API, never a server the subject runs.
 * An optional read-only token raises GitHub's limit from 60 to 5,000 requests an hour.
 *
 * The old keyword-search fallback (search/repositories?q=<handle>) stays removed: it attributed other
 * people's repositories to the subject. An account with no public repositories simply has none.
 */
object GithubConnector : Connector {
    override val spec = ConnectorSpec(
        id = "github",
        displayName = "GitHub public API",
        kind = ConnectorKind.PASSIVE,
        requiresOptIn = false,
        maxRequestsPerMinute = 30,
        requestsPerRun = 3,
        endpoints = "api.github.com: /users/{handle}, /repos, /events/public",
        termsNote = "GitHub API terms; public data only; honest User-Agent; optional read-only token."
    )

    private const val ACCEPT = "application/vnd.github+json"
    private const val MAX_COMMITS = 12
    private const val MAX_ARTIFACTS_BEFORE_COMMENTS = 22

    /**
     * A bare handle in ALL or GITHUB mode, or a github.com URL. A URL for any other site never makes this
     * connector look up a "handle" taken from that URL's path: that could pull in an unrelated account.
     */
    override fun appliesTo(target: CollectionTarget): Boolean = target.handle.isNotBlank() &&
        if (target.explicitUrl != null) {
            target.raw.contains("github.com", ignoreCase = true)
        } else {
            target.mode == ScraperPlatformMode.ALL || target.mode == ScraperPlatformMode.GITHUB
        }

    override suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        val handle = target.handle
        val base = "https://api.github.com/users/" + URLEncoder.encode(handle, "UTF-8")
        val artifactsBefore = acc.scrapedArtifacts.size
        val probesBefore = acc.httpProbes.size

        fetchStep(acc, tools, runId, base, "GITHUB_USER_API") { parseUser(JSONObject(it), acc, tools, handle) }
        fetchStep(acc, tools, runId, "$base/repos?sort=updated&per_page=8", "GITHUB_REPOS_API") {
            parseRepos(JSONArray(it), acc, tools, handle)
        }
        fetchStep(acc, tools, runId, "$base/events/public?per_page=30", "GITHUB_EVENTS_API") {
            parseEvents(JSONArray(it), acc, tools, handle)
        }

        val added = acc.scrapedArtifacts.size - artifactsBefore
        val succeeded = acc.httpProbes.drop(probesBefore).count { it.httpStatusCode in 200..299 }
        return when {
            added > 0 || acc.repos.isNotEmpty() -> ConnectorRunStatus.COLLECTED to "$added artifact(s), ${acc.repos.size} repo(s)"
            succeeded == 0 -> ConnectorRunStatus.FAILED to "no request succeeded (see the HTTP probe log)"
            else -> ConnectorRunStatus.NOTHING_FOUND to "account has no public text"
        }
    }

    /** One request: fetch, parse into [acc], stamp custody on what was added, log the probe. */
    private inline fun fetchStep(
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String,
        url: String,
        platform: String,
        parse: (String) -> Int
    ) {
        val resp = tools.executeHttpGet(url, platform, ACCEPT)
        acc.requestsMade++
        val mark = acc.scrapedArtifacts.size
        val extracted = resp.body?.let { body ->
            try {
                parse(body)
            } catch (_: JSONException) {
                0 // Malformed or unexpected JSON: nothing is extracted, the probe still records the response.
            }
        } ?: 0
        stampCustody(acc.scrapedArtifacts, mark, resp, runId)
        acc.httpProbes.add(resp.toProbe(extracted))
    }

    private fun JSONObject.text(key: String): String = optString(key, "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()

    private fun utcHourOf(iso: String): Int? = if (iso.length >= 13 && iso[10] == 'T') iso.substring(11, 13).toIntOrNull() else null

    private fun parseUser(u: JSONObject, acc: EvidenceAccumulator, tools: ConnectorToolkit, handle: String): Int {
        acc.displayName = u.text("name").ifBlank { handle }
        acc.declaredLocation = u.text("location").ifBlank { null }
        acc.bioText = u.text("bio")
        acc.followerCount = u.optInt("followers", 0)
        val updatedAt = u.optString("updated_at", "").ifBlank { tools.currentIsoUtc() }
        val company = u.text("company")
        var extracted = 0
        if (acc.bioText.isNotBlank()) {
            acc.scrapedArtifacts.add(
                StylometryEngine.createTimestampedArtifact(
                    id = "art-gh-bio-${System.nanoTime()}",
                    platform = "GITHUB_BIO",
                    timestampIso = updatedAt,
                    verbatimText = "${acc.bioText} ${if (company.isNotBlank()) "($company)" else ""}".trim(),
                    contextLabel = "github.com/$handle"
                )
            )
            extracted++
        }
        acc.corpusBuilder.append(acc.bioText).append(". ").append(company).append(". ")
        acc.liveSources.add("GITHUB_API")
        return extracted
    }

    private fun parseRepos(arr: JSONArray, acc: EvidenceAccumulator, tools: ConnectorToolkit, handle: String): Int {
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val name = r.optString("name", "repo-$i")
            val fullName = r.optString("full_name", "$handle/$name")
            val desc = r.text("description")
            val lang = r.text("language")
            val stars = r.optInt("stargazers_count", 0)
            val pushedAt = r.optString("pushed_at", r.optString("updated_at", tools.currentIsoUtc()))
            val topicsArr = r.optJSONArray("topics") ?: JSONArray()
            val topics = (0 until topicsArr.length()).map { topicsArr.optString(it) }
            utcHourOf(pushedAt)?.let { acc.utcHours.add(it) }

            acc.repos.add(
                GithubRepoItem(
                    name,
                    fullName,
                    desc,
                    lang,
                    stars,
                    r.optInt("forks_count", 0),
                    r.optString("html_url", "https://github.com/$fullName"),
                    pushedAt,
                    topics
                )
            )
            acc.scrapedArtifacts.add(
                StylometryEngine.createTimestampedArtifact(
                    id = "art-gh-repo-$i-${System.nanoTime()}",
                    platform = "GITHUB_REPO",
                    timestampIso = pushedAt,
                    verbatimText = "[$fullName ($lang, ★$stars)]: $desc",
                    contextLabel = fullName
                )
            )
            if (desc.isNotBlank()) acc.corpusBuilder.append(desc).append(". ")
        }
        return arr.length()
    }

    /** Commit messages and issue/PR comments from public events, with their UTC timestamps. */
    private fun parseEvents(events: JSONArray, acc: EvidenceAccumulator, tools: ConnectorToolkit, handle: String): Int {
        var extracted = 0
        for (i in 0 until events.length()) {
            val ev = events.getJSONObject(i)
            val createdAt = ev.optString("created_at", tools.currentIsoUtc())
            val repoName = ev.optJSONObject("repo")?.optString("name", handle) ?: handle
            utcHourOf(createdAt)?.let { acc.utcHours.add(it) }
            val payload = ev.optJSONObject("payload")
            extracted += addCommits(payload?.optJSONArray("commits"), i, createdAt, repoName, acc)
            extracted += addComment(payload, ev.optString("type", "PushEvent"), i, createdAt, repoName, acc)
        }
        return extracted
    }

    private fun addCommits(commits: JSONArray?, eventIndex: Int, createdAt: String, repoName: String, acc: EvidenceAccumulator): Int {
        if (commits == null) return 0
        var added = 0
        for (c in 0 until commits.length()) {
            val msg = commits.getJSONObject(c).optString("message", "").trim()
            if (msg.isBlank() || acc.commitMessages.size >= MAX_COMMITS) continue
            acc.commitMessages.add(msg)
            acc.corpusBuilder.append(msg).append(". ")
            acc.scrapedArtifacts.add(
                StylometryEngine.createTimestampedArtifact(
                    id = "art-commit-$eventIndex-$c-${System.nanoTime()}",
                    platform = "GITHUB_COMMIT",
                    timestampIso = createdAt,
                    verbatimText = msg,
                    contextLabel = repoName
                )
            )
            added++
        }
        return added
    }

    private fun addComment(
        payload: JSONObject?,
        eventType: String,
        eventIndex: Int,
        createdAt: String,
        repoName: String,
        acc: EvidenceAccumulator
    ): Int {
        val body = payload?.optJSONObject("comment")?.optString("body", "")?.trim().orEmpty()
        if (body.isBlank() || acc.scrapedArtifacts.size >= MAX_ARTIFACTS_BEFORE_COMMENTS) return 0
        acc.corpusBuilder.append(body.take(400)).append(". ")
        acc.scrapedArtifacts.add(
            StylometryEngine.createTimestampedArtifact(
                id = "art-comment-$eventIndex-${System.nanoTime()}",
                platform = "GITHUB_${eventType.uppercase(Locale.US).take(10)}",
                timestampIso = createdAt,
                verbatimText = body.take(280),
                contextLabel = repoName
            )
        )
        return 1
    }
}
