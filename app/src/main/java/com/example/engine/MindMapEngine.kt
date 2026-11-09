package com.example.engine

import com.example.model.*
import kotlin.math.*

/**
 * Mind-map topology and the persisted hierarchy edges. Cross-subject links are only built from the
 * subjects the caller passes in, which the app limits to the active project (and opted-in projects).
 */
object MindMapEngine {
    /**
     * Builds the Interactive Mind Map (Brain / Knowledge Graph) nodes & edges for the active subject,
     * including Identity Core, GitHub Repos, Hugging Face Models, Psychometric & Influence Traits,
     * Timestamped Discourse Clusters, and Cross-Subject Links.
     */
    fun buildMindMapTopology(
        subject: SubjectProfile,
        allSubjects: List<SubjectProfile>,
        triplets: List<KnowledgeTripletEntity>
    ): Pair<List<MindMapNode>, List<MindMapEdge>> {
        val nodes = mutableListOf<MindMapNode>()
        val edges = mutableListOf<MindMapEdge>()

        val lp = subject.linguisticProfile

        // 1. Central Identity Core Node
        val centerId = "node-core-${subject.handle}"
        nodes.add(
            MindMapNode(
                id = centerId,
                label = subject.handle,
                subtitle = "${lp.archetypeTitle.take(24)} // Δ=${subject.burrowsDelta}",
                category = NodeCategory.IDENTITY,
                weight = 1.65f,
                x3d = 0f, y3d = 0f, z3d = 0f,
                umapX = 0f, umapY = 0f, umapZ = 0f,
                details = "Primary Identity Core: ${subject.alias}\nArchetype: ${lp.archetypeTitle}\nLocation: ${subject.loc}\nLIWC: ATI=${lp.analyticalThinkingIndex} | Clout=${lp.cloutDominanceIndex} | Auth=${lp.authenticityIndex}\nTimestamped Statements: ${subject.scrapedArtifacts.size}",
                subjectId = subject.id
            )
        )

        // 2. GitHub Repository Nodes (Orbit Ring 1)
        subject.githubRepos.take(4).forEachIndexed { i, repo ->
            val angle = (i * (2 * Math.PI / max(1, min(4, subject.githubRepos.size)))).toFloat()
            val radius = 112f
            val nodeId = "node-gh-${repo.name}"
            nodes.add(
                MindMapNode(
                    id = nodeId,
                    label = repo.name.take(18),
                    subtitle = "${repo.language} ★${repo.stars} (${repo.updatedAtIso.take(10)})",
                    category = NodeCategory.GITHUB_REPO,
                    weight = 1.1f,
                    x3d = cos(angle) * radius,
                    y3d = sin(angle) * radius,
                    z3d = (i - 1.5f) * 28f,
                    umapX = -85f + (i * 20f),
                    umapY = -65f + (i * 15f),
                    umapZ = 20f,
                    details = "GitHub Repo: ${repo.fullName}\nLast Updated: ${repo.updatedAtIso}\nLanguage: ${repo.language} | Stars: ${repo.stars} | Forks: ${repo.forks}\n${repo.description}"
                )
            )
            edges.add(
                MindMapEdge(
                    id = "edge-gh-$i",
                    sourceId = centerId,
                    targetId = nodeId,
                    predicate = "AUTHORED_REPO",
                    confidence = 0.98f
                )
            )
        }

        // 3. Hugging Face Model / Dataset Nodes (Orbit Ring 2)
        subject.huggingFaceAssets.take(4).forEachIndexed { i, hf ->
            val angle = ((i + 0.5) * (2 * Math.PI / max(1, min(4, subject.huggingFaceAssets.size)))).toFloat()
            val radius = 132f
            val shortName = hf.id.substringAfterLast("/").take(18)
            val nodeId = "node-hf-$i-$shortName"
            nodes.add(
                MindMapNode(
                    id = nodeId,
                    label = shortName,
                    subtitle = "${hf.kind} ⬇${hf.downloads}",
                    category = NodeCategory.HF_MODEL,
                    weight = 1.15f,
                    x3d = cos(angle) * radius,
                    y3d = sin(angle) * radius,
                    z3d = (1.5f - i) * 32f,
                    umapX = 85f - (i * 18f),
                    umapY = -60f + (i * 22f),
                    umapZ = -20f,
                    details = "Hugging Face ${hf.kind}: ${hf.id}\nTimestamp: ${hf.lastModifiedIso}\nTask: ${hf.pipelineTag} | Downloads: ${hf.downloads} | Likes: ${hf.likes}\n${hf.summary}"
                )
            )
            edges.add(
                MindMapEdge(
                    id = "edge-hf-$i",
                    sourceId = centerId,
                    targetId = nodeId,
                    predicate = "HF_${hf.kind}",
                    confidence = 0.95f
                )
            )
        }

        // 4. Deep Psychometric, Motivation & Persuasion Nodes
        val psychNodes = listOf(
            Triple(
                "TICK: ${lp.archetypeTitle.take(16)}",
                "ATI=${lp.analyticalThinkingIndex}% Clout=${lp.cloutDominanceIndex}%",
                "WHAT MAKES THEM TICK:\n• ${lp.whatMakesThemTick.joinToString("\n• ")}"
            ),
            Triple(
                "TALK: LSM Mirroring",
                "Use Deductive / Zero-Hedge",
                "HOW TO TALK TO THEM:\n• ${lp.howToTalkToThem.joinToString(
                    "\n• "
                )}\n\nEXACT OPENER:\n${lp.exactPhrasesToUse.firstOrNull() ?: ""}"
            ),
            Triple(
                "LEVERAGE: Cialdini + Bias",
                "M=${subject.darkTriad.machiavellianism}% O=${subject.ocean.O}% C=${subject.ocean.C}%",
                "PERSUASION & LEVERAGE HOOKS:\n• ${lp.persuasionAndLeverageHooks.joinToString(
                    "\n• "
                )}\n\nCOGNITIVE BIASES:\n• ${lp.cognitiveBiasesToExploit.joinToString("\n• ")}"
            )
        )
        psychNodes.forEachIndexed { idx, (label, sub, detail) ->
            val angle = (idx * 2.1f) + 0.9f
            val nodeId = "node-psych-$idx"
            nodes.add(
                MindMapNode(
                    id = nodeId,
                    label = label,
                    subtitle = sub,
                    category = if (idx == 2) NodeCategory.OSINT_TOOL else NodeCategory.PSYCH_TRAIT,
                    weight = 1.15f,
                    x3d = cos(angle) * 86f,
                    y3d = sin(angle) * 86f,
                    z3d = 58f - idx * 35f,
                    umapX = -75f + idx * 45f,
                    umapY = 75f,
                    umapZ = 35f,
                    details = detail
                )
            )
            edges.add(
                MindMapEdge(
                    id = "edge-psych-$idx",
                    sourceId = centerId,
                    targetId = nodeId,
                    predicate = when (idx) {
                        0 -> "CORE_MOTIVATOR"
                        1 -> "RAPPORT_PROTOCOL"
                        else -> "LEVERAGE_VECTOR"
                    },
                    confidence = (subject.styloMatch / 100.0).toFloat().coerceIn(0.75f, 0.99f)
                )
            )
        }

        // 5. Chronolocation & Geolocation Node
        val geoNodeId = "node-geo-${subject.handle}"
        nodes.add(
            MindMapNode(
                id = geoNodeId,
                label = subject.loc.substringBefore("(").trim().take(18),
                subtitle = "UTC${if (subject.utcOffset >= 0) "+${subject.utcOffset}" else subject.utcOffset} // ${subject.scrapedArtifacts.size} Logs",
                category = NodeCategory.GEO_CHRONO,
                weight = 1.05f,
                x3d = -110f,
                y3d = 75f,
                z3d = -45f,
                umapX = 75f,
                umapY = 75f,
                umapZ = -30f,
                details = "Geolocation & Chronolocation Node\nCoordinates: ${subject.lat}, ${subject.lon}\nDiurnal Rest Window: ${subject.troughWindowLabel}\nDerived from ${subject.scrapedArtifacts.size} timestamped statements."
            )
        )
        edges.add(
            MindMapEdge(
                id = "edge-geo",
                sourceId = centerId,
                targetId = geoNodeId,
                predicate = "CHRONO_LOCATED",
                confidence = 0.92f
            )
        )

        // 6. Cross-Subject Sockpuppet / Dyadic Links
        allSubjects.filter { it.id != subject.id }.take(4).forEachIndexed { idx, other ->
            val (crossDelta, lsmPct) = StylometryEngine.computeDyadicLsm(subject, other)
            val otherNodeId = "node-subj-${other.handle}"
            val angle = (idx * 1.57f) + 0.4f
            nodes.add(
                MindMapNode(
                    id = otherNodeId,
                    label = other.handle,
                    subtitle = "LSM $lsmPct% // Δ=$crossDelta",
                    category = NodeCategory.WALLED_GARDEN,
                    weight = 1.25f,
                    x3d = cos(angle) * 155f,
                    y3d = sin(angle) * 155f,
                    z3d = -60f + idx * 35f,
                    umapX = (idx - 1.5f) * 55f,
                    umapY = -105f,
                    umapZ = 0f,
                    details = "Linked Dossier: ${other.handle} (${other.alias})\nCross-Handle Burrows' Delta: $crossDelta\nPennebaker Dyadic LSM Cohesion: $lsmPct%\nTap 'Switch Target' to inspect full dossier.",
                    subjectId = other.id
                )
            )
            edges.add(
                MindMapEdge(
                    id = "edge-dyad-$idx",
                    sourceId = centerId,
                    targetId = otherNodeId,
                    predicate = if (crossDelta < 0.45) "SOCKPUPPET_LINK (Δ=$crossDelta)" else "DYAD_LSM ($lsmPct%)",
                    confidence = (lsmPct / 100.0).toFloat().coerceIn(0.6f, 0.98f)
                )
            )
        }

        // 7. Extra GraphRAG Triplets
        triplets.filter { it.subjectHandle.equals(subject.handle, ignoreCase = true) }.take(3).forEachIndexed { idx, tr ->
            val existing = nodes.any { it.label.equals(tr.objectNode, ignoreCase = true) }
            if (!existing) {
                val trNodeId = "node-tr-${tr.id}"
                val angle = (idx * 1.3f) + 2.2f
                val cat = try {
                    NodeCategory.valueOf(tr.nodeCategory)
                } catch (_: Exception) {
                    NodeCategory.OSINT_TOOL
                }
                nodes.add(
                    MindMapNode(
                        id = trNodeId,
                        label = tr.objectNode.take(18),
                        subtitle = tr.predicate.take(18),
                        category = cat,
                        weight = 0.95f,
                        x3d = cos(angle) * 140f,
                        y3d = sin(angle) * 140f,
                        z3d = 65f,
                        umapX = 95f,
                        umapY = (idx - 1.5f) * 40f,
                        umapZ = 40f,
                        details = "FalkorDB GraphRAG Triplet:\n(${tr.subjectHandle}) -[${tr.predicate}]-> (${tr.objectNode})\nConfidence: ${(tr.confidence * 100).roundToInt()}%\nPROV-O SHA-256: ${tr.provSha256.take(
                            16
                        )}..."
                    )
                )
                edges.add(
                    MindMapEdge(
                        id = "edge-tr-${tr.id}",
                        sourceId = centerId,
                        targetId = trNodeId,
                        predicate = tr.predicate,
                        confidence = tr.confidence
                    )
                )
            }
        }

        return nodes to edges
    }

    /**
     * Builds normalized hierarchical Room entities (`MindMapHierarchyEntity`) for the `mind_map_hierarchy_edges` table:
     * - Depth 0: Root Identity Node
     * - Depth 1: Category Cluster Branch Nodes (GitHub Cluster, Hugging Face Cluster, Psychometric & Leverage Cluster, Chrono/Geo Cluster)
     * - Depth 2: Leaf Artifact, Trait, Repo, Model, and Dyadic Persona Nodes linked to their parent cluster
     */
    fun buildMindMapHierarchyEntities(
        subject: SubjectProfile,
        allSubjects: List<SubjectProfile> = emptyList(),
        triplets: List<KnowledgeTripletEntity> = emptyList()
    ): List<MindMapHierarchyEntity> {
        val (nodes, edges) = buildMindMapTopology(subject, allSubjects, triplets)
        val edgeByTarget = edges.associateBy { it.targetId }
        val now = System.currentTimeMillis()
        val centerId = "node-core-${subject.handle}"
        val lp = subject.linguisticProfile

        // Define Depth-1 intermediate cluster hubs parented to the Depth-0 Root Identity node
        data class HubSpec(
            val hubId: String,
            val label: String,
            val subtitle: String,
            val category: NodeCategory,
            val predicate: String,
            val angleRad: Float,
            val details: String
        )

        val hubs = mapOf(
            NodeCategory.PSYCH_TRAIT to HubSpec(
                hubId = "hub-psych-${subject.id}",
                label = "PSYCHOMETRIC CORE",
                subtitle = "ATI=${lp.analyticalThinkingIndex}% | Clout=${lp.cloutDominanceIndex}%",
                category = NodeCategory.PSYCH_TRAIT,
                predicate = "HAS_PSYCH_PROFILE",
                angleRad = 0.45f,
                details = "Pennebaker LIWC & OCEAN Psychometric Hub\nArchetype: ${lp.archetypeTitle}\nBig Five: O=${subject.ocean.O}% C=${subject.ocean.C}% E=${subject.ocean.E}% A=${subject.ocean.A}% N=${subject.ocean.N}%\nDark Triad: Mach=${subject.darkTriad.machiavellianism}% Narc=${subject.darkTriad.narcissism}% Psych=${subject.darkTriad.psychopathy}%"
            ),
            NodeCategory.OSINT_TOOL to HubSpec(
                hubId = "hub-lev-${subject.id}",
                label = "LEVERAGE & RAPPORT",
                subtitle = "${lp.persuasionAndLeverageHooks.size} Hooks | Δ=${subject.burrowsDelta}",
                category = NodeCategory.OSINT_TOOL,
                predicate = "SUSCEPTIBLE_TO",
                angleRad = 1.65f,
                details = "Behavioral Influence & Persuasion Hub\nTick: ${lp.whatMakesThemTick.firstOrNull() ?: ""}\nTalk: ${lp.howToTalkToThem.firstOrNull() ?: ""}\nLeverage: ${lp.persuasionAndLeverageHooks.firstOrNull() ?: ""}"
            ),
            NodeCategory.GITHUB_REPO to HubSpec(
                hubId = "hub-gh-${subject.id}",
                label = "GITHUB CODE HUB",
                subtitle = "${subject.githubRepos.size} Repositories",
                category = NodeCategory.GITHUB_REPO,
                predicate = "MAINTAINS_CODEBASE",
                angleRad = 2.85f,
                details = "GitHub Engineering Footprint Hub\nRepositories: ${subject.githubRepos.joinToString {
                    it.name
                }}\nPrimary Stack: ${subject.githubRepos.firstOrNull()?.language ?: "Python"}"
            ),
            NodeCategory.HF_MODEL to HubSpec(
                hubId = "hub-hf-${subject.id}",
                label = "HUGGING FACE HUB",
                subtitle = "${subject.huggingFaceAssets.size} AI Models/Assets",
                category = NodeCategory.HF_MODEL,
                predicate = "DEPLOYS_AI_ASSETS",
                angleRad = 4.05f,
                details = "Hugging Face AI & NLP Hub\nModels: ${subject.huggingFaceAssets.joinToString { it.id }}"
            ),
            NodeCategory.WALLED_GARDEN to HubSpec(
                hubId = "hub-wg-${subject.id}",
                label = "DYADIC PERSONA HUB",
                subtitle = "LSM & Cross-Handle Links",
                category = NodeCategory.WALLED_GARDEN,
                predicate = "LINKED_ALIASES",
                angleRad = 5.25f,
                details = "Cross-Handle Stylometry & Dyadic LSM Cluster\nEvaluates sockpuppet probability and Language Style Matching across target personas."
            )
        )

        val result = mutableListOf<MindMapHierarchyEntity>()

        // 1. Insert Depth-0 Root Node
        val rootNode = nodes.firstOrNull { it.category == NodeCategory.IDENTITY }
        if (rootNode != null) {
            result.add(
                MindMapHierarchyEntity(
                    hierarchyId = "mmh-${subject.id}-root-${rootNode.id}",
                    rootSubjectId = subject.id,
                    parentNodeId = "ROOT",
                    childNodeId = rootNode.id,
                    childLabel = rootNode.label,
                    childSubtitle = rootNode.subtitle,
                    nodeCategory = rootNode.category.name,
                    hierarchyDepth = 0,
                    relationshipPredicate = "IDENTITY_ROOT",
                    confidenceWeight = 1.0f,
                    nodeWeight = rootNode.weight,
                    x3d = 0f,
                    y3d = 0f,
                    z3d = 0f,
                    umapX = 0f,
                    umapY = 0f,
                    umapZ = 0f,
                    nodeDetails = rootNode.details,
                    linkedSubjectId = rootNode.subjectId,
                    provSha256 = subject.provSha256,
                    updatedAtMillis = now
                )
            )
        }

        // 2. Insert Depth-1 Intermediate Hub Nodes (for categories that have children)
        val activeCategories = nodes.map { it.category }.toSet()
        hubs.forEach { (cat, spec) ->
            if (cat in activeCategories) {
                val r1 = 68f
                val hx = cos(spec.angleRad) * r1
                val hy = sin(spec.angleRad) * r1
                val hz = sin(spec.angleRad * 2f) * 24f
                result.add(
                    MindMapHierarchyEntity(
                        hierarchyId = "mmh-${subject.id}-hub-${spec.hubId}",
                        rootSubjectId = subject.id,
                        parentNodeId = centerId,
                        childNodeId = spec.hubId,
                        childLabel = spec.label,
                        childSubtitle = spec.subtitle,
                        nodeCategory = spec.category.name,
                        hierarchyDepth = 1,
                        relationshipPredicate = spec.predicate,
                        confidenceWeight = 0.97f,
                        nodeWeight = 1.32f,
                        x3d = hx,
                        y3d = hy,
                        z3d = hz,
                        umapX = hx * 0.9f,
                        umapY = hy * 0.9f,
                        umapZ = hz,
                        nodeDetails = spec.details,
                        linkedSubjectId = subject.id,
                        provSha256 = subject.provSha256,
                        updatedAtMillis = now
                    )
                )
            }
        }

        // 3. Insert Depth-1 Direct Nodes (e.g. GEO_CHRONO) & Depth-2 Leaf Nodes linked to their Depth-1 Hub
        nodes.filter { it.category != NodeCategory.IDENTITY }.forEachIndexed { idx, node ->
            val matchedEdge = edgeByTarget[node.id]
            val hubSpec = hubs[node.category]
            val isDirectDepth1 = hubSpec == null || node.category == NodeCategory.GEO_CHRONO
            val depth = if (isDirectDepth1) 1 else 2
            val parentId = if (isDirectDepth1) centerId else hubSpec!!.hubId
            val predicate = matchedEdge?.predicate ?: "CONTAINS_NODE"
            val conf = matchedEdge?.confidence ?: 0.92f

            result.add(
                MindMapHierarchyEntity(
                    hierarchyId = "mmh-${subject.id}-leaf-$idx-${node.id}",
                    rootSubjectId = subject.id,
                    parentNodeId = parentId,
                    childNodeId = node.id,
                    childLabel = node.label,
                    childSubtitle = node.subtitle,
                    nodeCategory = node.category.name,
                    hierarchyDepth = depth,
                    relationshipPredicate = predicate,
                    confidenceWeight = conf,
                    nodeWeight = node.weight,
                    x3d = node.x3d,
                    y3d = node.y3d,
                    z3d = node.z3d,
                    umapX = node.umapX,
                    umapY = node.umapY,
                    umapZ = node.umapZ,
                    nodeDetails = node.details,
                    linkedSubjectId = node.subjectId ?: subject.id,
                    provSha256 = subject.provSha256,
                    updatedAtMillis = now
                )
            )
        }

        return result
    }
}
