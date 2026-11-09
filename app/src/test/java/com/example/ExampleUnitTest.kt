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
        assertTrue(eval.burrowsDelta > 0.0)
        assertEquals(30, eval.zScores30.size)
        assertTrue(eval.ocean.C in 20..99)

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
