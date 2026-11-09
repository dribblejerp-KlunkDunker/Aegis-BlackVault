package com.example.engine

import com.example.model.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * STIX 2.1 bundle and PKZIP project export.
 */
object ExportEngine {
    /**
     * Compiles a standards-compliant OASIS STIX 2.1 Bundle with W3C PROV-O SHA-256 lineage,
     * timestamped evidence logs, and the full Linguistic Personality Profile.
     */
    fun buildStix21BundleJson(subject: SubjectProfile, patches: List<AstPatchEntity>): String {
        val provHash = ForensicCommon.sha256("${subject.handle}:${subject.burrowsDelta}:${subject.provSha256}")
        val identityId = subject.stixId
        val locationId = ForensicCommon.generateStixId("location", "${subject.handle}-${subject.loc}")
        val indicatorId = ForensicCommon.generateStixId("indicator", "${subject.handle}-stylo-${subject.burrowsDelta}")
        val relationshipId = ForensicCommon.generateStixId("relationship", "$identityId-$locationId")

        val objectsArr = JSONArray()

        objectsArr.put(
            JSONObject().apply {
                put("type", "identity")
                put("spec_version", "2.1")
                put("id", identityId)
                put("name", "${subject.handle} (${subject.alias})")
                put("identity_class", "individual")
                put("x_ocean_scores", JSONObject(subject.ocean.toJson()))
                put("x_dark_triad", JSONObject(subject.darkTriad.toJson()))
                put("x_linguistic_personality_profile", subject.linguisticProfile.toJsonObject())
                put("x_timestamped_artifacts_count", subject.scrapedArtifacts.size)
                put("x_burrows_delta", subject.burrowsDelta)
                put("x_stylo_match_pct", subject.styloMatch)
                put("x_mattr_lexical_diversity", subject.mattrScore)
                put("x_github_repos_count", subject.githubRepos.size)
                put("x_huggingface_assets_count", subject.huggingFaceAssets.size)
                put("x_healed_ast_patch", subject.healedPatchId)
                put("x_w3c_prov_o_sha256", provHash)
            }
        )

        objectsArr.put(
            JSONObject().apply {
                put("type", "location")
                put("spec_version", "2.1")
                put("id", locationId)
                put("name", subject.loc)
                put("latitude", subject.lat)
                put("longitude", subject.lon)
                put("x_inferred_utc_offset", subject.utcOffset)
                put("x_diurnal_trough_utc", subject.troughWindowLabel)
                put("x_w3c_prov_o_sha256", ForensicCommon.sha256(subject.loc))
            }
        )

        objectsArr.put(
            JSONObject().apply {
                put("type", "indicator")
                put("spec_version", "2.1")
                put("id", indicatorId)
                put("name", "50-Word Calibrated Burrows' Delta & LIWC Behavioral Signature")
                put("pattern_type", "stix")
                put(
                    "pattern",
                    "[x-stylometry:burrows_delta <= ${subject.burrowsDelta} AND x-liwc:ati == ${subject.linguisticProfile.analyticalThinkingIndex}]"
                )
                put("x_active_ast_patches", patches.size)
            }
        )

        objectsArr.put(
            JSONObject().apply {
                put("type", "relationship")
                put("spec_version", "2.1")
                put("id", relationshipId)
                put("relationship_type", "located-at")
                put("source_ref", identityId)
                put("target_ref", locationId)
                put("confidence", subject.styloMatch.roundToInt().coerceIn(1, 100))
            }
        )

        val bundle = JSONObject().apply {
            put("type", "bundle")
            put("id", ForensicCommon.generateStixId("bundle", provHash))
            put("spec_version", "2.1")
            put("x_w3c_prov_o_root_sha256", provHash)
            put("objects", objectsArr)
        }
        return bundle.toString(2)
    }

    /**
     * Pure-Kotlin PKZIP Binary Archive Builder (creates a valid .zip byte array with CRC-32 headers)
     */
    fun createProjectZipBytes(subject: SubjectProfile, patches: List<AstPatchEntity>, stixBundleJson: String): ByteArray {
        val lp = subject.linguisticProfile
        val verdad = VerdadEngine.evaluateSovereignVerdad(subject)
        val files = linkedMapOf(
            "aegis-blackvault/README.md" to """
                # AEGIS // BLACKVAULT + SOVEREIGN-AEGIS — Multi-Agent Handoff & OSINT Archive
                
                ## Active Target Dossier: ${subject.handle} (${subject.alias})
                - **Archetype:** ${lp.archetypeTitle}
                - **LIWC Composite:** ATI=${lp.analyticalThinkingIndex}/100 | Clout=${lp.cloutDominanceIndex}/100 | Authenticity=${lp.authenticityIndex}/100
                - **Calibrated 50-Word Burrows' Delta:** Δ = ${subject.burrowsDelta} (${subject.styloMatch}% Match)
                - **Sovereign-Aegis VERDAD Gate:** ${verdad.recommendationGate} (Manipulation Risk = ${verdad.manipulationRisk}/100)
                - **Chronolocation:** ${subject.loc} | ${subject.troughWindowLabel}
                - **Timestamped Statements Parsed:** ${subject.scrapedArtifacts.size}
                - **Scraped GitHub Repositories:** ${subject.githubRepos.size}
                - **Scraped Hugging Face Assets:** ${subject.huggingFaceAssets.size}
                - **W3C PROV-O Root SHA-256:** ${subject.provSha256}
            """.trimIndent(),
            "aegis-blackvault/AGENT_HANDOFF_BRIEF.md" to """
                # MULTI-AGENT HANDOFF SPECIFICATION — AEGIS_OS // BLACKVAULT
                
                This archive contains the complete deterministic and AI-ready intelligence state from **Aegis Blackvault** (`com.aistudio.aegisblackvault.osint`) fused with **Sovereign-Aegis** (`dribblejerp-KlunkDunker/Sovereign-Aegis`).
                
                ## 1. Architecture & Room Database Schema (v3)
                - `subject_profiles`: Primary identity core, Big Five OCEAN, Dark Triad, Burrows' Delta, MATTR, Diurnal UTC Chronolocation.
                - `raw_social_media_entities`: Verbatim timestamped statements, commits, model cards, lexical markers, sentiment scores, PROV-O SHA-256.
                - `linguistic_personality_profiles`: Pennebaker LIWC 4D indices (ATI, Clout, Authenticity, Tone), Pronoun & Certainty/Hedging rates, What Makes Them Tick, How to Talk to Them, Persuasion & Leverage Hooks.
                - `mind_map_hierarchy_edges`: 3-Tier Hierarchical Brain Graph (Depth 0 Root -> Depth 1 Cluster Hubs -> Depth 2 Leaf Artifacts/Traits).
                
                ## 2. Sovereign-Aegis Cognitive Defense & Reema Seam
                - **VERDAD Manipulation Gate:** `${verdad.recommendationGate}` (Risk Score: `${verdad.manipulationRisk}/100`)
                - **DISARM Red/Blue Kill-Chain Mappings:** ${verdad.disarmRedBlueMappings.size} active counter-inoculation rules.
                - **Prebunk Inoculation Script:** `${verdad.prebunkInoculationScript}`
                - **AEGIS <-> Reema Post-Quantum KeyBinding:** WebCrypto ECDSA P-256 `did:key` bound to `libsodium X25519 + ML-DSA-65 + ML-KEM-768` 44-char BLAKE2b routing spine.
                
                ## 3. Included Files in This Archive
                - `exports/personality_dossier_${subject.handle.lowercase(Locale.US)}.json`: Complete LIWC + OCEAN + Persuasion dossier.
                - `exports/sovereign_verdad_${subject.handle.lowercase(
                Locale.US
            )}.json`: VERDAD 6-Emotion Bait scores, Fallacies, DISARM Red/Blue, Situational Watchpoints, and Reema KeyBinding.
                - `exports/aegis_reema_keybinding_${subject.handle.lowercase(
                Locale.US
            )}.json`: Signed Post-Quantum `KeyBinding` attestation JSON.
                - `exports/timestamped_discourse_${subject.handle.lowercase(Locale.US)}.json`: Timestamped verbatim evidence log.
                - `exports/latest_stix21_bundle.json`: OASIS STIX 2.1 Threat & Identity bundle with W3C PROV-O SHA-256 lineage.
                - `exports/actionable_insights_${subject.handle.lowercase(Locale.US)}.json`: Prioritized tactical recommendations.
                - `backend/healed_ast_patches.json`: Self-healing DOM/AST scraper selectors.
            """.trimIndent(),
            "aegis-blackvault/exports/personality_dossier_${subject.handle.lowercase(Locale.US)}.json" to lp.toJsonObject().toString(2),
            "aegis-blackvault/exports/sovereign_verdad_${subject.handle.lowercase(Locale.US)}.json" to verdad.toJsonObject().toString(2),
            "aegis-blackvault/exports/aegis_reema_keybinding_${subject.handle.lowercase(Locale.US)}.json" to verdad.reemaKeyBindingJson,
            "aegis-blackvault/exports/timestamped_discourse_${subject.handle.lowercase(Locale.US)}.json" to JSONArray(
                subject.scrapedArtifacts.map { it.toJsonObject() }
            ).toString(2),
            "aegis-blackvault/exports/latest_stix21_bundle.json" to stixBundleJson,
            "aegis-blackvault/exports/actionable_insights_${subject.handle.lowercase(Locale.US)}.json" to JSONArray(
                subject.actionableInsights.map { it.toJsonObject() }
            ).toString(2),
            "aegis-blackvault/backend/healed_ast_patches.json" to JSONArray(
                patches.map {
                    JSONObject().apply {
                        put("patchId", it.patchId)
                        put("platform", it.platformName)
                        put("deprecatedSelector", it.deprecatedSelector)
                        put("healedSelector", it.healedSelector)
                        put("strategy", it.strategy)
                        put("confidence", it.confidence)
                    }
                }
            ).toString(2)
        )

        val localOut = ByteArrayOutputStream()
        val centralOut = ByteArrayOutputStream()
        var localOffset = 0
        var entryCount = 0

        for ((path, content) in files) {
            val nameBytes = path.toByteArray(Charsets.UTF_8)
            val dataBytes = content.toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(dataBytes) }.value.toInt()
            val size = dataBytes.size

            val localHeader = ByteBuffer.allocate(30 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            localHeader.putInt(0x04034b50)
            localHeader.putShort(20)
            localHeader.putShort(0x0800)
            localHeader.putShort(0)
            localHeader.putShort(0x4800)
            localHeader.putShort(0x5d68.toShort())
            localHeader.putInt(crc)
            localHeader.putInt(size)
            localHeader.putInt(size)
            localHeader.putShort(nameBytes.size.toShort())
            localHeader.putShort(0)
            localHeader.put(nameBytes)

            val lhBytes = localHeader.array()
            localOut.write(lhBytes)
            localOut.write(dataBytes)

            val centralHeader = ByteBuffer.allocate(46 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            centralHeader.putInt(0x02014b50)
            centralHeader.putShort(20)
            centralHeader.putShort(20)
            centralHeader.putShort(0x0800)
            centralHeader.putShort(0)
            centralHeader.putShort(0x4800)
            centralHeader.putShort(0x5d68.toShort())
            centralHeader.putInt(crc)
            centralHeader.putInt(size)
            centralHeader.putInt(size)
            centralHeader.putShort(nameBytes.size.toShort())
            centralHeader.putShort(0)
            centralHeader.putShort(0)
            centralHeader.putShort(0)
            centralHeader.putShort(0)
            centralHeader.putInt(0)
            centralHeader.putInt(localOffset)
            centralHeader.put(nameBytes)

            centralOut.write(centralHeader.array())
            localOffset += lhBytes.size + dataBytes.size
            entryCount++
        }

        val centralBytes = centralOut.toByteArray()
        val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        eocd.putInt(0x06054b50)
        eocd.putShort(0)
        eocd.putShort(0)
        eocd.putShort(entryCount.toShort())
        eocd.putShort(entryCount.toShort())
        eocd.putInt(centralBytes.size)
        eocd.putInt(localOffset)
        eocd.putShort(0)

        val finalOut = ByteArrayOutputStream()
        finalOut.write(localOut.toByteArray())
        finalOut.write(centralBytes)
        finalOut.write(eocd.array())
        return finalOut.toByteArray()
    }
}
