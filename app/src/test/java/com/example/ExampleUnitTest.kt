package com.example

import com.example.engine.ForensicMathEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun forensicMathAndPersonalityProfile_isAccurate() {
        val sampleText = """
            Therefore the deterministic consensus architecture must enforce SHA-256 verification across all distributed nodes.
            We will analyze the benchmark latency and deploy the updated graph schema before 04:00 UTC.
        """.trimIndent()

        val eval = ForensicMathEngine.evaluateCorpus(sampleText, repoCount = 6, hfCount = 3, followerCount = 400)
        // A ~30-word sample is far below the stylometry minimum, so Burrows' Delta is withheld (0.0)
        // rather than reported as a finding.
        assertTrue(eval.tokenCount < com.example.model.DataSufficiency.MIN_WORDS_FOR_STYLOMETRY)
        assertEquals(0.0, eval.burrowsDelta, 0.0)
        assertEquals(30, eval.zScores30.size)
        // Scores are clamped to their real 0..100 range now (the old artificial 24..97 band was removed).
        assertTrue(eval.ocean.C in 0..100)

        val chrono = ForensicMathEngine.evaluateChronoAndGeo(listOf(8, 9, 10, 11, 12, 13, 14, 15, 16), "Zürich, CH")
        assertEquals(47.3769, chrono.lat, 0.01)

        val artifact = ForensicMathEngine.createTimestampedArtifact(
            id = "test-art-1",
            platform = "GITHUB_COMMIT",
            timestampIso = "2026-11-08T14:22:10Z",
            verbatimText = sampleText,
            contextLabel = "repo/main"
        )
        assertEquals(14, artifact.utcHour)
        assertTrue(artifact.lexicalMarkers.isNotEmpty())

        val profile = ForensicMathEngine.buildLinguisticPersonalityProfile("TEST_HANDLE", eval, chrono, 5)
        assertTrue(profile.whatMakesThemTick.isNotEmpty())
        assertTrue(profile.howToTalkToThem.isNotEmpty())
        assertTrue(profile.persuasionAndLeverageHooks.isNotEmpty())
    }
}
