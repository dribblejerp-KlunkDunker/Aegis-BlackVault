package com.example.connectors

/**
 * Files or text you import yourself (dossier JSON, STIX, CSV, transcripts, or your own account's data
 * export). LOCAL: it contacts nothing. It is driven by the import screen rather than by a collection
 * target, so it never applies to one; it is registered so it appears with the other sources and so
 * imported artifacts carry a run id like every other connector's.
 *
 * Every imported artifact is stamped with the source (file name or "pasted text"), the time it was
 * received, and the SHA-256 of the exact bytes imported (see ImportCustody).
 */
object ManualImportConnector : Connector {
    override val spec = ConnectorSpec(
        id = "manual_import",
        displayName = "Manual import",
        kind = ConnectorKind.LOCAL,
        requiresOptIn = false,
        maxRequestsPerMinute = 0,
        requestsPerRun = 0,
        endpoints = "Files or text you import (Insights tab); nothing is sent",
        termsNote = "You choose what to import. The optional Gemini parser sends the text to Google; the " +
            "plain parsers keep it on the device."
    )

    override fun appliesTo(target: CollectionTarget): Boolean = false

    override suspend fun collect(
        target: CollectionTarget,
        acc: EvidenceAccumulator,
        tools: ConnectorToolkit,
        runId: String
    ): Pair<ConnectorRunStatus, String> = ConnectorRunStatus.NOTHING_FOUND to "imports run from the Insights tab"
}
