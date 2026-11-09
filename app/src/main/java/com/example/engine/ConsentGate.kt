package com.example.engine

import com.example.model.*
import kotlin.math.*

/**
 * The consent gate: restricted inferences are stripped from any subject without recorded consent
 * (Self, Consented or Demo) before it is stored, shown, exported or sent to an AI provider.
 */
object ConsentGate {
    const val RESTRICTED_LABEL = "WITHHELD (CONSENT REQUIRED)"

    /** Insight categories that are restricted inferences about a person. */
    val RESTRICTED_INSIGHT_CATEGORIES = setOf(
        "WHAT_MAKES_THEM_TICK",
        "HOW_TO_TALK",
        "INFLUENCE_LEVERAGE",
        "OPSEC_CHRONO"
    )

    /**
     * Consent gate. For subjects whose consent is not SELF / CONSENTED / DEMO, strips restricted
     * inferences before the profile is stored, shown, exported or sent to an AI provider:
     * persuasion/leverage playbooks, exploitable biases, Dark Triad, violence pathway,
     * activity-window (sleep) and inferred location. Collected artifacts and non-restricted
     * descriptive metrics are kept. Unlocking later recomputes from stored artifacts.
     */
    fun applyConsentGate(profile: SubjectProfile): SubjectProfile {
        if (profile.restrictedOutputsUnlocked) return profile
        val lp = profile.linguisticProfile
        return profile.copy(
            darkTriad = DarkTriadScores(machiavellianism = 0, narcissism = 0, psychopathy = 0),
            threatMetrics = profile.threatMetrics.copy(pathwayToViolence = 0),
            troughWindowLabel = RESTRICTED_LABEL,
            hourlyBins = List(24) { 0 },
            utcOffset = 0,
            lat = 0.0,
            lon = 0.0,
            loc = RESTRICTED_LABEL,
            backStageQuote = if (profile.backStageQuote.contains("trough", ignoreCase = true)) {
                "[RESTRICTED]: activity-window inference withheld until consent is recorded."
            } else {
                profile.backStageQuote
            },
            linguisticProfile = lp.copy(
                whatMakesThemTick = emptyList(),
                egoCravingsAndRewards = emptyList(),
                fearAndFrictionTriggers = emptyList(),
                howToTalkToThem = emptyList(),
                exactPhrasesToUse = emptyList(),
                phrasesToAvoid = emptyList(),
                persuasionAndLeverageHooks = emptyList(),
                cognitiveBiasesToExploit = emptyList(),
                negotiationTactics = emptyList()
            ),
            actionableInsights = profile.actionableInsights.filterNot { it.category in RESTRICTED_INSIGHT_CATEGORIES }
        )
    }
}
