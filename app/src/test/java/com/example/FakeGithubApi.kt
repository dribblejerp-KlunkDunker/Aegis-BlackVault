package com.example

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Canned GitHub REST responses for hermetic scraper tests. Nothing here touches the network.
 * Any request it doesn't recognise gets a 404, so a test notices if the scraper starts calling
 * endpoints it shouldn't (for example the removed keyword-search fallback).
 */
object FakeGithubApi {
    const val LOGIN = "octo-test"

    /** Every URL the fake client was asked for, in order. */
    val requestedUrls = mutableListOf<String>()

    private val user = """
        {"login":"$LOGIN","name":"Octo Test","location":"Berlin","bio":"Builds lock-free ring buffers and writes about latency.",
         "company":"Example Labs","followers":10,"updated_at":"2026-11-01T10:00:00Z"}
    """.trimIndent()

    private val repos = """
        [{"name":"ringbuf","full_name":"$LOGIN/ringbuf","description":"Lock-free ring buffer with reproducible latency benchmarks",
          "language":"Rust","stargazers_count":5,"forks_count":1,"html_url":"https://github.com/$LOGIN/ringbuf",
          "pushed_at":"2026-11-02T14:00:00Z","topics":["ring-buffer"]}]
    """.trimIndent()

    private val events = """
        [{"type":"PushEvent","created_at":"2026-11-03T09:15:00Z","repo":{"name":"$LOGIN/ringbuf"},
          "payload":{"commits":[{"message":"Add benchmark harness for the consumer path"},{"message":"Fix off-by-one in wraparound"}]}},
         {"type":"IssueCommentEvent","created_at":"2026-11-04T16:40:00Z","repo":{"name":"$LOGIN/ringbuf"},
          "payload":{"comment":{"body":"Thanks for the report. Could you share the exact CPU and the trace?"}}}]
    """.trimIndent()

    /** A small website for the web-page connector: robots.txt plus one dated article. */
    const val SITE = "https://pages.example"
    const val ARTICLE_URL = "$SITE/notes/ring-buffers"
    const val PRIVATE_URL = "$SITE/private/draft"

    private val robots = """
        # Example site
        User-agent: *
        Disallow: /private/
        Allow: /private/public-note
    """.trimIndent()

    private val article = """
        <html><head><title>Ring buffer notes</title>
        <script type="application/ld+json">{"@type":"BlogPosting","headline":"Ring buffer notes",
          "datePublished":"2026-10-20T08:30:00Z",
          "articleBody":"Measuring tail latency needs a quiet machine and many runs."}</script>
        </head><body><article><time datetime="2026-10-21T19:05:00Z">Oct 21</time>
        <p>Pinning the consumer thread cut the p99 by a third.</p></article></body></html>
    """.trimIndent()

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                val url = request.url
                requestedUrls.add(url.toString())
                val body: String? = when {
                    url.host == "api.github.com" && url.encodedPath == "/users/$LOGIN" -> user
                    url.host == "api.github.com" && url.encodedPath == "/users/$LOGIN/repos" -> repos
                    url.host == "api.github.com" && url.encodedPath == "/users/$LOGIN/events/public" -> events
                    url.host == "pages.example" && url.encodedPath == "/robots.txt" -> robots
                    url.host == "pages.example" && url.encodedPath.startsWith("/notes/") -> article
                    url.host == "pages.example" && url.encodedPath.startsWith("/private/") -> article
                    else -> null
                }
                val type = if (url.host == "pages.example") "text/html" else "application/json"
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (body != null) 200 else 404)
                    .message(if (body != null) "OK" else "Not Found")
                    .body((body ?: """{"message":"Not Found"}""").toResponseBody(type.toMediaType()))
                    .build()
            }
        )
        .build()

    /** A client whose every request fails, to simulate being offline or blocked. */
    fun offlineClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                requestedUrls.add(chain.request().url.toString())
                throw IOException("simulated offline")
            }
        )
        .build()
}
