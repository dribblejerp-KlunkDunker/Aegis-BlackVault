package com.example.connectors

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Timestamp parsing that never invents anything:
 * - an explicit offset is converted, so the hour is the real UTC hour;
 * - a date without a time stays a date ("yyyy-MM-dd"), so no hour reaches the activity histogram;
 * - anything unparseable returns null and the caller keeps the statement undated.
 */
object Timestamps {
    private val ISO_DATE_TIME = Regex("""(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?""")
    private val ISO_DATE_ONLY = Regex("""\d{4}-\d{2}-\d{2}""")

    /** RSS 2.0 dates (RFC 822 with four-digit years, as used in practice). */
    private val RFC822_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm Z",
        "EEE, dd MMM yyyy HH:mm zzz"
    )

    private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** ISO 8601 / RFC 3339 to UTC. A value without an offset is taken as UTC (the convention of web APIs). */
    fun isoToUtc(raw: String): String? = raw.trim().let { dateTimeToUtc(it) ?: dateOnly(it) }

    private fun dateTimeToUtc(value: String): String? {
        val g = ISO_DATE_TIME.matchEntire(value)?.groupValues ?: return null
        val base = "${g[1]}T${g[2]}:${g[3]}:${g[4]}Z"
        val offset = g[5]
        val fmt = utcFormat()
        return when {
            offset.isEmpty() || offset == "Z" -> base
            else -> runCatching { fmt.parse(base) }.getOrNull()?.let { fmt.format(Date(it.time - offsetMinutes(offset) * 60_000L)) }
        }
    }

    /** "+02:00", "-0500" to signed minutes. */
    private fun offsetMinutes(offset: String): Int {
        val sign = if (offset[0] == '-') -1 else 1
        val digits = offset.drop(1).replace(":", "")
        return sign * (digits.take(2).toInt() * 60 + digits.drop(2).toInt())
    }

    /** "yyyy-MM-dd" (optionally followed by something that isn't a time) stays a date: no hour is invented. */
    private fun dateOnly(value: String): String? {
        val isDateOnly = value.length >= 10 && ISO_DATE_ONLY.matches(value.take(10)) &&
            (value.length == 10 || !value[10].isDigit())
        return if (isDateOnly) value.take(10) else null
    }

    /** RFC 822 (RSS pubDate) to UTC. */
    fun rfc822ToUtc(raw: String): String? {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        for (pattern in RFC822_PATTERNS) {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val parsed = runCatching { fmt.parse(trimmed) }.getOrNull()
            if (parsed != null) return utcFormat().format(parsed)
        }
        return null
    }

    /** Whichever format [raw] is in, or null. */
    fun anyToUtc(raw: String): String? = if (raw.isBlank()) null else isoToUtc(raw) ?: rfc822ToUtc(raw)
}
