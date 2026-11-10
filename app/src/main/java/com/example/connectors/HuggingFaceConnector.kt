package com.example.connectors

import com.example.model.ScraperPlatformMode
import java.net.URLEncoder

/**
 * Hugging Face Hub's public API: models published under the handle. Passive.
 */
object HuggingFaceConnector : Connector {
    override val spec = ConnectorSpec(
        id = "huggingface",
        displayName = "Hugging Face Hub API",
        kind = ConnectorKind.PASSIVE,
        requiresOptIn = false,
        maxRequestsPerMinute = 30,
        requestsPerRun = 1,
        endpoints = "huggingface.co: /api/models?author={handle}",
        termsNote = "Hugging Face Hub terms; public model metadata only."
    )

    /** A bare handle in ALL or HUGGINGFACE mode, or a huggingface.co URL (see [GithubConnector.appliesTo]). */
    override fun appliesTo(target: CollectionTarget): Boolean = target.handle.isNotBlank() &&
        if (target.explicitUrl != null) {
            target.raw.contains("huggingface.co", ignoreCase = true)
        } else {
            target.mode == ScraperPlatformMode.ALL || target.mode == ScraperPlatformMode.HUGGINGFACE
        }

    override suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> {
        // The old popularity-search fallback (models?search=<language>) stays removed: it attached
        // unrelated top-downloaded models to the subject.
        val resp = tools.executeHttpGet(
            url = "https://huggingface.co/api/models?author=${URLEncoder.encode(target.handle, "UTF-8")}&limit=5",
            platform = "HF_AUTHOR_API"
        )
        acc.requestsMade++
        val assetsBefore = acc.hfAssets.size
        val mark = acc.scrapedArtifacts.size
        resp.body?.let { tools.parseHuggingFaceModels(it, acc.hfAssets, acc.scrapedArtifacts, acc.corpusBuilder) }
        val found = acc.hfAssets.size - assetsBefore
        if (found > 0) acc.liveSources.add("HF_AUTHOR_API")
        stampCustody(acc.scrapedArtifacts, mark, resp, runId)
        acc.httpProbes.add(resp.toProbe(found))
        return when {
            found > 0 -> ConnectorRunStatus.COLLECTED to "$found model(s)"
            resp.statusCode !in 200..299 -> ConnectorRunStatus.FAILED to "HTTP ${resp.statusCode}"
            else -> ConnectorRunStatus.NOTHING_FOUND to "no public models"
        }
    }
}
