package com.example.engine

import com.example.model.*
import java.util.Locale
import kotlin.math.*

/**
 * Actionable insight synthesis (the playbook). Restricted categories are removed by [ConsentGate]
 * for subjects without recorded consent.
 */
object PlaybookEngine {
    /**
     * Synthesizes concrete Actionable Insights from the combined GitHub, Hugging Face, Stylometry,
     * Timestamped Discourse, and Behavioral Persuasion Profile.
     */
    fun synthesizeActionableInsights(
        handle: String,
        eval: StylometryEvaluation,
        chrono: ChronoGeoEvaluation,
        repos: List<GithubRepoItem>,
        hfAssets: List<HuggingFaceAsset>,
        healedPatchId: String,
        lingProfile: LinguisticPersonalityProfile? = null
    ): List<ActionableInsight> {
        val lp = lingProfile ?: StylometryEngine.buildLinguisticPersonalityProfile(handle, eval, chrono, repos.size + hfAssets.size + 4)
        val insights = mutableListOf<ActionableInsight>()

        // 1. What Makes Them Tick
        insights.add(
            ActionableInsight(
                id = "ins-tick-$handle",
                category = "WHAT_MAKES_THEM_TICK",
                title = "Core Driver: ${lp.archetypeTitle}",
                priority = "CRITICAL",
                summary = lp.whatMakesThemTick.firstOrNull() ?: lp.archetypeSummary,
                recommendation = "Ego Reward Trigger: ${lp.egoCravingsAndRewards.firstOrNull() ?: "Recognize first-principles architectural ownership."} Avoid: ${lp.fearAndFrictionTriggers.firstOrNull() ?: "Micromanagement."}",
                confidencePct = min(98, max(82, eval.styloMatchPct.roundToInt()))
            )
        )

        // 2. How to Talk to Them
        val peakHourUtc = (chrono.troughEndUtc + 3) % 24
        val peakEndUtc = (peakHourUtc + 5) % 24
        insights.add(
            ActionableInsight(
                id = "ins-talk-$handle",
                category = "HOW_TO_TALK",
                title = "Communication Protocol & %02d:00–%02d:00 UTC Window".format(Locale.US, peakHourUtc, peakEndUtc),
                priority = "HIGH",
                summary = lp.howToTalkToThem.firstOrNull() ?: "Use high-ATI deductive framing with zero hedging.",
                recommendation = "Use Opener: ${lp.exactPhrasesToUse.firstOrNull() ?: "Lead with raw benchmarks and grant technical veto."} Never Use: ${lp.phrasesToAvoid.firstOrNull() ?: "Vague alignment calls."}",
                confidencePct = 93
            )
        )

        // 3. Persuasion, Influence & Leverage Dynamics
        insights.add(
            ActionableInsight(
                id = "ins-lev-$handle",
                category = "INFLUENCE_LEVERAGE",
                title = "Psychological Leverage & Negotiation Hook (Clout=${lp.cloutDominanceIndex}%)",
                priority = "CRITICAL",
                summary = lp.persuasionAndLeverageHooks.firstOrNull() ?: "Deploy Ego-Syntonic Challenge + Commitment Consistency locks.",
                recommendation = "Bias Vector: ${lp.cognitiveBiasesToExploit.firstOrNull() ?: "Anchor with exact metrics."} Tactic: ${lp.negotiationTactics.firstOrNull() ?: "Offer two high-agency technical options."}",
                confidencePct = 91
            )
        )

        // 4. Combined GitHub + Hugging Face + Chronolocation OPSEC Audit
        val topLangs = repos.map {
            it.language
        }.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ").ifBlank { "Python, TypeScript, Rust" }
        val topHf = hfAssets.firstOrNull()?.id ?: "Minej/bert-base-personality"
        insights.add(
            ActionableInsight(
                id = "ins-stack-$handle",
                category = "OPSEC_CHRONO",
                title = "Fused Footprint: ${repos.size} GitHub Repos + ${hfAssets.size} HF Assets ($healedPatchId)",
                priority = "TACTICAL",
                summary = "Stack [$topLangs] + HF [$topHf]. Burrows' Delta Δ=${eval.burrowsDelta} (${eval.styloMatchPct}% authorship match) | Sleep trough: ${chrono.windowLabel}.",
                recommendation = "Cross-link timestamped commits with social posts in the Mind Map; Δ < 0.38 confirms multi-handle persona linkage.",
                confidencePct = 95
            )
        )

        return insights
    }
}
