package com.example.connectors

import java.util.Locale

/**
 * Minimal robots.txt evaluator following RFC 9309 (https://www.rfc-editor.org/rfc/rfc9309):
 * - picks the group whose user-agent matches our product token, else the `*` group;
 * - the most specific (longest) matching Allow/Disallow rule wins, Allow on a tie;
 * - `*` matches any run of characters and a trailing `$` anchors the end;
 * - an empty Disallow allows everything.
 * Fetch outcomes ([forFetch]): 2xx parses the file, 4xx means no restrictions, anything else
 * (5xx, network failure) means the site is treated as fully disallowed.
 */
class RobotsTxt private constructor(private val rules: List<Rule>, val unreachable: Boolean) {

    private data class Rule(val allow: Boolean, val pattern: String) {
        private val regex: Regex by lazy {
            val anchored = pattern.endsWith("$")
            val body = if (anchored) pattern.dropLast(1) else pattern
            val escaped = body.split("*").joinToString(".*") { Regex.escape(it) }
            Regex("^" + escaped + if (anchored) "$" else "")
        }
        fun matches(path: String) = regex.containsMatchIn(path)
    }

    /** True if [pathAndQuery] (for example "/blog/post?id=1") may be fetched. */
    fun isAllowed(pathAndQuery: String): Boolean {
        if (unreachable) return false
        val path = pathAndQuery.ifBlank { "/" }
        val best = rules.filter { it.matches(path) }
            .maxWithOrNull(compareBy<Rule> { it.pattern.length }.thenBy { if (it.allow) 1 else 0 })
        return best?.allow ?: true
    }

    companion object {
        /** Product token sent in the User-Agent and matched against robots.txt groups. */
        const val PRODUCT_TOKEN = "AegisBlackvault"

        val ALLOW_ALL = RobotsTxt(emptyList(), unreachable = false)
        val DISALLOW_ALL = RobotsTxt(emptyList(), unreachable = true)

        fun forFetch(statusCode: Int, body: String?): RobotsTxt = when (statusCode) {
            in 200..299 -> parse(body.orEmpty())
            in 400..499 -> ALLOW_ALL
            else -> DISALLOW_ALL
        }

        fun parse(text: String, productToken: String = PRODUCT_TOKEN): RobotsTxt {
            val groups = mutableListOf<Group>()
            var lastWasAgent = false
            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.contains(':') }
                .forEach { line ->
                    val key = line.substringBefore(':').trim().lowercase(Locale.US)
                    val value = line.substringAfter(':').trim()
                    lastWasAgent = addLine(groups, key, value, lastWasAgent)
                }
            val token = productToken.lowercase(Locale.US)
            val ours = groups.filter { g -> g.agents.any { it.isNotEmpty() && it != "*" && token.startsWith(it) } }
            val chosen = ours.ifEmpty { groups.filter { g -> "*" in g.agents } }
            return RobotsTxt(chosen.flatMap { it.rules }, unreachable = false)
        }

        private class Group(val agents: MutableList<String> = mutableListOf(), val rules: MutableList<Rule> = mutableListOf())

        /** Applies one "key: value" line; returns whether it was a user-agent line. */
        private fun addLine(groups: MutableList<Group>, key: String, value: String, lastWasAgent: Boolean): Boolean {
            when (key) {
                "user-agent" -> {
                    // Consecutive user-agent lines share one group; one after a rule starts a new group.
                    if (groups.isEmpty() || !lastWasAgent) groups += Group()
                    groups.last().agents += value.lowercase(Locale.US)
                    return true
                }

                "allow", "disallow" -> {
                    // A rule before any user-agent line belongs to no group; "Disallow:" with no path allows all.
                    if (groups.isNotEmpty() && value.isNotEmpty()) groups.last().rules += Rule(allow = key == "allow", pattern = value)
                }
            }
            return false
        }
    }
}
