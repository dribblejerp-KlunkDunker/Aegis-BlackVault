package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ForensicMathEngine
import com.example.model.SubjectProfile
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun PsychometricScreen(
    activeSubject: SubjectProfile,
    comparisonSubject: SubjectProfile,
    allSubjects: List<SubjectProfile>,
    selectedSpectrumToken: String,
    onSelectSpectrumToken: (String) -> Unit,
    onSelectComparisonSubject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf("PLAYBOOK") }
    val lp = activeSubject.linguisticProfile

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sub-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "PLAYBOOK" to "TICK / TALK / LEVERAGE",
                "OCEAN_LIWC" to "OCEAN & 50-WORD Δ",
                "DYAD_LSM" to "DYADIC LSM",
                "VERDAD" to "SOVEREIGN VERDAD"
            ).forEach { (key, title) ->
                val isSel = activeSubTab == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("psych_subtab_$key")
                        .background(
                            if (isSel) AmberSignal else ObsidianBg,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (isSel) AmberSignal else PhosphorGreenDim,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { activeSubTab = key }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSel) ObsidianBg else PhosphorGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (activeSubTab) {
            "PLAYBOOK" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Scientific LIWC Composite Telemetry Header
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, VioletHf.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "LIWC",
                                    tint = VioletHf,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "PENNEBAKER LIWC & COGNITIVE ARCHETYPE // ${activeSubject.handle}",
                                    color = VioletHf,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = lp.archetypeTitle,
                                color = AmberSignal,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = lp.archetypeSummary,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            // 5 LIWC Composite Bars
                            TraitProgressRow("Analytical Thinking Index (ATI)", lp.analyticalThinkingIndex, CyanTelemetry)
                            TraitProgressRow("Clout & Social Hierarchy Dominance", lp.cloutDominanceIndex, AmberSignal)
                            TraitProgressRow("Integrative Cognitive Complexity", lp.cognitiveComplexityIndex, VioletHf)
                            TraitProgressRow("Discourse Authenticity (Unfiltered)", lp.authenticityIndex, PhosphorGreen)
                            TraitProgressRow("Affective Emotional Tone", lp.emotionalToneIndex, RustAccent)

                            // Function-Word Pronoun & Certainty Ratios
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("I-Focus: ${lp.iPronounRatePct}%", color = TextPrimary, fontSize = 10.sp)
                                Text("We-Focus: ${lp.wePronounRatePct}%", color = PhosphorGreen, fontSize = 10.sp)
                                Text("You-Direct: ${lp.youPronounRatePct}%", color = AmberSignal, fontSize = 10.sp)
                                Text("Cert: ${lp.certaintyMarkerPct}%", color = CyanTelemetry, fontSize = 10.sp)
                                Text("Hedge: ${lp.hedgingMarkerPct}%", color = AlarmRed, fontSize = 10.sp)
                            }
                        }
                    }

                    // 2. WHAT MAKES THEM TICK (Core Psychological Drivers, Ego Rewards, Fear/Friction Triggers)
                    item {
                        PlaybookSectionCard(
                            headerTitle = "1. WHAT MAKES ${activeSubject.handle} TICK (CORE DRIVERS)",
                            accentColor = PhosphorGreen,
                            sections = listOf(
                                "INTRINSIC PSYCHOLOGICAL DRIVERS" to lp.whatMakesThemTick,
                                "EGO CRAVINGS & DOPAMINE REWARDS" to lp.egoCravingsAndRewards,
                                "REACTANCE & FRICTION TRIGGERS (WHAT ANTAGONIZES THEM)" to lp.fearAndFrictionTriggers
                            )
                        )
                    }

                    // 3. HOW TO TALK TO THEM (Communication Protocol, Exact Phrases to Use vs Avoid)
                    item {
                        PlaybookSectionCard(
                            headerTitle = "2. HOW TO TALK TO ${activeSubject.handle} (RAPPORT PROTOCOL)",
                            accentColor = CyanTelemetry,
                            sections = listOf(
                                "LINGUISTIC MIRRORING & CADENCE RULES" to lp.howToTalkToThem,
                                "HIGH-TRUST EXACT PHRASES TO USE" to lp.exactPhrasesToUse,
                                "DEALBREAKER PHRASES TO AVOID" to lp.phrasesToAvoid
                            )
                        )
                    }

                    // 4. PERSUASION, INFLUENCE & LEVERAGE DYNAMICS (Cialdini Vectors, Biases, Negotiation)
                    item {
                        PlaybookSectionCard(
                            headerTitle = "3. PERSUASION, INFLUENCE & LEVERAGE MATRIX",
                            accentColor = AmberSignal,
                            sections = listOf(
                                "PSYCHOLOGICAL LEVERAGE & INFLUENCE HOOKS" to lp.persuasionAndLeverageHooks,
                                "COGNITIVE BIASES & DECISION BLIND SPOTS" to lp.cognitiveBiasesToExploit,
                                "NEGOTIATION & COUNTER-MANIPULATION TACTICS" to lp.negotiationTactics
                            )
                        )
                    }
                }
            }

            "OCEAN_LIWC" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Big Five OCEAN
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "BIG FIVE (OCEAN) BFI-44 PSYCHOMETRIC BASELINE",
                                color = PhosphorGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            activeSubject.ocean.toMap().forEach { (trait, score) ->
                                TraitProgressRow(trait, score, PhosphorGreen)
                            }
                        }
                    }

                    // Dark Triad Composite
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, AlarmRed.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DARK TRIAD COMPOSITE (STRATEGIC AGENCY & ANTAGONISM)",
                                color = AlarmRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TraitProgressRow("Machiavellianism (Strategic Calculation)", activeSubject.darkTriad.machiavellianism, AmberSignal)
                            TraitProgressRow("Narcissism (Ego Centrality & Status Drive)", activeSubject.darkTriad.narcissism, RustAccent)
                            TraitProgressRow("Psychopathy (Callousness / Unfiltered Hostility)", activeSubject.darkTriad.psychopathy, AlarmRed)
                        }
                    }

                    // Calibrated Function-Word Z-Score Grid
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CALIBRATED BURROWS' DELTA Z-SCORES",
                                    color = PhosphorGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Δ=${activeSubject.burrowsDelta} | MATTR=${activeSubject.mattrScore}",
                                    color = AmberSignal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val entries = activeSubject.zScores.entries.take(15).chunked(5)
                            entries.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowItems.forEach { (word, z) ->
                                        val isSel = word == selectedSpectrumToken
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isSel) AmberSignal.copy(alpha = 0.2f) else ObsidianBg,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSel) AmberSignal else PhosphorGreenDim,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { onSelectSpectrumToken(word) }
                                                .padding(vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "\"${word.uppercase(Locale.US)}\"",
                                                color = PhosphorGreen,
                                                fontSize = 9.sp
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%+.2fσ", z),
                                                color = AmberSignal,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Front-Stage vs Back-Stage Discourse Divergence
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Discourse",
                                    tint = CyanTelemetry,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "FRONT-STAGE VS. BACK-STAGE PERSONA DIVERGENCE",
                                    color = CyanTelemetry,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "FRONT-STAGE (PUBLIC BIO / PR PERSONA):",
                                color = PhosphorGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "\"${activeSubject.frontStageQuote}\"",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic
                            )
                            HorizontalDivider(color = PhosphorGreenDim.copy(alpha = 0.4f))
                            Text(
                                text = "BACK-STAGE (RAW COMMIT / UNFILTERED STREAM):",
                                color = AmberSignal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "\"${activeSubject.backStageQuote}\"",
                                color = AmberSignal,
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            "DYAD_LSM" -> {
                val (crossDelta, lsmPct) = remember(activeSubject, comparisonSubject) {
                    ForensicMathEngine.computeDyadicLsm(activeSubject, comparisonSubject)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, AmberSignal.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = "Dyadic LSM",
                                    tint = AmberSignal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "DYADIC SOCKPUPPET & PENNEBAKER LSM COMPARATOR",
                                    color = AmberSignal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "Select Secondary Subject to Compare Against [${activeSubject.handle}]:",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(allSubjects) { candidate ->
                                    val isSel = candidate.id == comparisonSubject.id
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSel) AmberSignal else ObsidianBg,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(1.dp, AmberSignal, RoundedCornerShape(4.dp))
                                            .clickable { onSelectComparisonSubject(candidate.id) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = candidate.handle,
                                            color = if (isSel) ObsidianBg else AmberSignal,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("CROSS-HANDLE Δ", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        text = "$crossDelta",
                                        color = if (crossDelta < 0.40) AlarmRed else AmberSignal,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (crossDelta < 0.40) "SOCKPUPPET / SAME AUTHOR LIKELY" else "DISTINCT AUTHORSHIP SIGNATURE",
                                        color = PhosphorGreen,
                                        fontSize = 9.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("PENNEBAKER LSM COHESION", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        text = "$lsmPct%",
                                        color = CyanTelemetry,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (lsmPct >= 85.0) "HIGH DYADIC RAPPORT / ACCOMMODATION" else "MODERATE STYLE ALIGNMENT",
                                        color = AmberSignal,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    // Trait-by-Trait Side-by-Side Comparison
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SIDE-BY-SIDE TRAIT & LIWC ALIGNMENT (${activeSubject.handle} vs ${comparisonSubject.handle})",
                                color = PhosphorGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            DyadComparisonBar("Openness (O)", activeSubject.ocean.O, comparisonSubject.ocean.O)
                            DyadComparisonBar("Conscientiousness (C)", activeSubject.ocean.C, comparisonSubject.ocean.C)
                            DyadComparisonBar("Extraversion (E)", activeSubject.ocean.E, comparisonSubject.ocean.E)
                            DyadComparisonBar("Agreeableness (A)", activeSubject.ocean.A, comparisonSubject.ocean.A)
                            DyadComparisonBar("Neuroticism (N)", activeSubject.ocean.N, comparisonSubject.ocean.N)
                            DyadComparisonBar(
                                "Analytical Thinking (ATI)",
                                activeSubject.linguisticProfile.analyticalThinkingIndex,
                                comparisonSubject.linguisticProfile.analyticalThinkingIndex
                            )
                            DyadComparisonBar(
                                "Social Clout / Dominance",
                                activeSubject.linguisticProfile.cloutDominanceIndex,
                                comparisonSubject.linguisticProfile.cloutDominanceIndex
                            )
                            DyadComparisonBar(
                                "Machiavellianism",
                                activeSubject.darkTriad.machiavellianism,
                                comparisonSubject.darkTriad.machiavellianism
                            )
                        }
                    }
                }
            }

            "VERDAD" -> {
                var customVerdadDraft by remember { mutableStateOf("") }
                val verdadAssessment = remember(activeSubject, customVerdadDraft) {
                    ForensicMathEngine.evaluateSovereignVerdad(activeSubject, customVerdadDraft)
                }
                val gateColor = when {
                    verdadAssessment.manipulationRisk >= 70 -> AlarmRed
                    verdadAssessment.manipulationRisk >= 40 -> AmberSignal
                    else -> PhosphorGreen
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Sovereign-Aegis VERDAD Pre-Share & Manipulation Gate Card
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, gateColor, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SOVEREIGN-AEGIS // VERDAD EPISTEMIC GATE",
                                    color = CyanTelemetry,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .background(gateColor.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                                        .border(1.dp, gateColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${verdadAssessment.recommendationGate} • ${verdadAssessment.manipulationRisk}/100",
                                        color = gateColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = verdadAssessment.gateRationale,
                                color = TextPrimary,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                            Text(
                                text = "INVARIANT (verdad-service.js): Gate derives strictly from deterministic manipulationRisk (Block >= 70, Caution >= 40) so prompt injection cannot override a BLOCK.",
                                color = TextSecondary,
                                fontSize = 8.5.sp
                            )

                            OutlinedTextField(
                                value = customVerdadDraft,
                                onValueChange = { customVerdadDraft = it },
                                singleLine = false,
                                label = {
                                    Text(
                                        "Live verdad-service.shouldShare(draft) Sandbox (leave blank to audit ${activeSubject.handle}'s corpus)",
                                        fontSize = 8.5.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanTelemetry,
                                    unfocusedBorderColor = PhosphorGreenDim,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("verdad_draft_sandbox_input")
                            )
                        }
                    }

                    // 2. 6 Emotional Bait Lexicons (from Sovereign-Aegis data/verdad_rules.json)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "VERDAD 6-VECTOR EMOTIONAL BAIT LEXICONS (verdad_rules.json)",
                                color = AmberSignal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            verdadAssessment.emotionalBaitScores.forEach { (category, score) ->
                                val barCol = when {
                                    score >= 60 -> AlarmRed
                                    score >= 35 -> AmberSignal
                                    else -> PhosphorGreen
                                }
                                TraitProgressRow(
                                    label = "$category Bait Density",
                                    score = score,
                                    color = barCol
                                )
                            }
                        }
                    }

                    // 3. Epistemic Hygiene Markers, Rhetorical Fallacies & Prebunk Script
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, VioletHf.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "RHETORICAL FALLACIES, CERTAINTY INFLATION & PREBUNK SCRIPT",
                                color = VioletHf,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "CERTAINTY INFLATION MARKERS: ${
                                    verdadAssessment.certaintyInflationHits.joinToString(", ").ifBlank { "None detected" }
                                }",
                                color = AmberSignal,
                                fontSize = 9.5.sp
                            )
                            Text(
                                text = "HEDGING / PASSIVE ATTRIBUTION: ${
                                    (verdadAssessment.hedgingMarkerHits + verdadAssessment.passiveAttributionHits)
                                        .joinToString(", ")
                                        .ifBlank { "None detected" }
                                }",
                                color = CyanTelemetry,
                                fontSize = 9.5.sp
                            )
                            if (verdadAssessment.detectedFallacies.isNotEmpty()) {
                                verdadAssessment.detectedFallacies.forEach { fallacy ->
                                    Text(
                                        text = "• FALLACY TRIGGERED: $fallacy",
                                        color = AlarmRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "• No overt rhetorical fallacy regexes triggered in current window.",
                                    color = PhosphorGreen,
                                    fontSize = 10.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = verdadAssessment.prebunkInoculationScript,
                                    color = PhosphorGreen,
                                    fontSize = 10.sp,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // 4. DISARM Red/Blue Influence Framework & Personal Defense Watchpoints
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, CyanTelemetry.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DISARM RED/BLUE KILL-CHAIN & SITUATIONAL WATCHPOINTS (profile.js)",
                                color = CyanTelemetry,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            verdadAssessment.disarmRedBlueMappings.forEach { mapping ->
                                Text(
                                    text = "• $mapping",
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            HorizontalDivider(color = PhosphorGreenDim)
                            verdadAssessment.situationalWatchpoints.forEach { wp ->
                                Text(
                                    text = "▸ $wp",
                                    color = AmberSignal,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // 5. AEGIS (P-256 did:key) <-> REEMA (Post-Quantum ML-DSA-65 + X25519) KeyBinding Attestation
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianPanel, RoundedCornerShape(6.dp))
                                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "AEGIS <-> REEMA POST-QUANTUM KEYBINDING SEAM (DESIGN-aegis-reema-seam.md)",
                                color = PhosphorGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = verdadAssessment.reemaKeyBindingJson,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybookSectionCard(
    headerTitle: String,
    accentColor: Color,
    sections: List<Pair<String, List<String>>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianPanel, RoundedCornerShape(6.dp))
            .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = headerTitle,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = headerTitle,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        sections.forEach { (subTitle, bullets) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBg, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = subTitle,
                    color = AmberSignal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                bullets.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("▸", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = item,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TraitProgressRow(
    label: String,
    score: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = TextPrimary, fontSize = 11.sp)
            Text(text = "$score%", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(ObsidianBg, RoundedCornerShape(2.dp))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((score / 100f).coerceIn(0.02f, 1f))
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun DyadComparisonBar(
    trait: String,
    scoreA: Int,
    scoreB: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "$scoreA%", color = PhosphorGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = trait, color = TextPrimary, fontSize = 10.sp)
            Text(text = "$scoreB%", color = AmberSignal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(ObsidianBg, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((scoreA / 100f).coerceIn(0.02f, 1f))
                        .background(PhosphorGreen, RoundedCornerShape(2.dp))
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(ObsidianBg, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((scoreB / 100f).coerceIn(0.02f, 1f))
                        .background(AmberSignal, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
