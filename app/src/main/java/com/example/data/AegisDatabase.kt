package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.engine.ForensicMathEngine
import com.example.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AegisDao {
    // ------------------------------------------------------------------------
    // 1. Core Subject Dossiers & @Transaction Relational Joins
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM subject_profiles ORDER BY updatedAt DESC")
    fun observeAllProfiles(): Flow<List<SubjectProfileEntity>>

    @Transaction
    @Query("SELECT * FROM subject_profiles ORDER BY updatedAt DESC")
    fun observeAllFullIntelligenceDossiers(): Flow<List<SubjectIntelligenceDossierRelation>>

    @Transaction
    @Query("SELECT * FROM subject_profiles WHERE id = :subjectId LIMIT 1")
    fun observeFullDossierById(subjectId: String): Flow<SubjectIntelligenceDossierRelation?>

    @Query("SELECT COUNT(*) FROM subject_profiles")
    suspend fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: SubjectProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<SubjectProfileEntity>)

    // ------------------------------------------------------------------------
    // 2. Raw Social Media Entities Table (`raw_social_media_entities`)
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM raw_social_media_entities WHERE subjectId = :subjectId ORDER BY postedTimestampIso DESC")
    fun observeRawSocialEntitiesForSubject(subjectId: String): Flow<List<RawSocialMediaEntity>>

    @Query("SELECT * FROM raw_social_media_entities WHERE platform = :platform ORDER BY postedTimestampIso DESC")
    fun observeRawSocialEntitiesByPlatform(platform: String): Flow<List<RawSocialMediaEntity>>

    @Query("SELECT * FROM raw_social_media_entities ORDER BY postedTimestampIso DESC LIMIT 100")
    fun observeAllRawSocialEntities(): Flow<List<RawSocialMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRawSocialEntity(entity: RawSocialMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRawSocialEntities(entities: List<RawSocialMediaEntity>)

    @Query("DELETE FROM raw_social_media_entities WHERE subjectId = :subjectId")
    suspend fun deleteRawSocialEntitiesForSubject(subjectId: String)

    // ------------------------------------------------------------------------
    // 3. Parsed Linguistic Personality Profiles Table (`linguistic_personality_profiles`)
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM linguistic_personality_profiles WHERE subjectId = :subjectId LIMIT 1")
    fun observeLinguisticProfileForSubject(subjectId: String): Flow<LinguisticPersonalityProfileEntity?>

    @Query("SELECT * FROM linguistic_personality_profiles ORDER BY updatedAtMillis DESC")
    fun observeAllLinguisticProfiles(): Flow<List<LinguisticPersonalityProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinguisticProfile(profile: LinguisticPersonalityProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinguisticProfiles(profiles: List<LinguisticPersonalityProfileEntity>)

    // ------------------------------------------------------------------------
    // 4. Mind-Map Hierarchical Relationships Table (`mind_map_hierarchy_edges`)
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM mind_map_hierarchy_edges WHERE rootSubjectId = :rootSubjectId ORDER BY hierarchyDepth ASC, confidenceWeight DESC")
    fun observeMindMapHierarchyForSubject(rootSubjectId: String): Flow<List<MindMapHierarchyEntity>>

    @Query("SELECT * FROM mind_map_hierarchy_edges WHERE rootSubjectId = :rootSubjectId AND parentNodeId = :parentNodeId ORDER BY confidenceWeight DESC")
    fun observeMindMapChildrenByParent(rootSubjectId: String, parentNodeId: String): Flow<List<MindMapHierarchyEntity>>

    @Query("SELECT * FROM mind_map_hierarchy_edges WHERE rootSubjectId = :rootSubjectId AND hierarchyDepth = :depth ORDER BY confidenceWeight DESC")
    fun observeMindMapNodesByDepth(rootSubjectId: String, depth: Int): Flow<List<MindMapHierarchyEntity>>

    @Query("SELECT * FROM mind_map_hierarchy_edges ORDER BY hierarchyDepth ASC, updatedAtMillis DESC LIMIT 150")
    fun observeAllMindMapHierarchyEdges(): Flow<List<MindMapHierarchyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMindMapHierarchyEdges(edges: List<MindMapHierarchyEntity>)

    @Query("DELETE FROM mind_map_hierarchy_edges WHERE rootSubjectId = :rootSubjectId")
    suspend fun deleteMindMapHierarchyForSubject(rootSubjectId: String)

    // ------------------------------------------------------------------------
    // 5. GraphRAG Triplets & Walled-Garden AST Patches
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM knowledge_triplets ORDER BY timestamp DESC LIMIT 60")
    fun observeTriplets(): Flow<List<KnowledgeTripletEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriplet(triplet: KnowledgeTripletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriplets(triplets: List<KnowledgeTripletEntity>)

    @Query("SELECT * FROM ast_patches ORDER BY timestamp DESC")
    fun observeAstPatches(): Flow<List<AstPatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAstPatch(patch: AstPatchEntity)

    // ------------------------------------------------------------------------
    // 6. Atomic Multi-Table Intelligence Ingestion Transaction
    // ------------------------------------------------------------------------
    @Transaction
    suspend fun persistCompleteIntelligenceDossier(
        subjectEntity: SubjectProfileEntity,
        rawSocialEntities: List<RawSocialMediaEntity>,
        linguisticProfileEntity: LinguisticPersonalityProfileEntity,
        mindMapHierarchyEntities: List<MindMapHierarchyEntity>,
        triplets: List<KnowledgeTripletEntity> = emptyList()
    ) {
        upsertProfile(subjectEntity)
        upsertRawSocialEntities(rawSocialEntities)
        upsertLinguisticProfile(linguisticProfileEntity)
        deleteMindMapHierarchyForSubject(subjectEntity.id)
        upsertMindMapHierarchyEdges(mindMapHierarchyEntities)
        if (triplets.isNotEmpty()) {
            insertTriplets(triplets)
        }
    }
}

@Database(
    entities = [
        SubjectProfileEntity::class,
        RawSocialMediaEntity::class,
        LinguisticPersonalityProfileEntity::class,
        MindMapHierarchyEntity::class,
        KnowledgeTripletEntity::class,
        AstPatchEntity::class
    ],
    version = 4,
    // Schema JSON is exported to app/schemas (see room.schemaLocation in app/build.gradle.kts)
    // so future migrations can be written and tested against real schemas.
    exportSchema = true
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        @Volatile
        private var INSTANCE: AegisDatabase? = null

        /**
         * v3 -> v4: adds consent status, collected word count and data provenance to subject_profiles.
         * Existing dossiers keep their data and start as UNVERIFIED with unknown provenance.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN consentStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN corpusWordCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN dataProvenance TEXT NOT NULL DEFAULT 'UNKNOWN (pre-v4)'")
            }
        }

        fun getInstance(context: Context): AegisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AegisDatabase::class.java,
                    "aegis_blackvault.db"
                )
                    // v3 data is migrated, never wiped. (v1/v2 were pre-release AI Studio builds only.)
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AegisRepository(private val dao: AegisDao) {
    val profilesFlow: Flow<List<SubjectProfileEntity>> = dao.observeAllProfiles()
    val fullDossiersFlow: Flow<List<SubjectIntelligenceDossierRelation>> = dao.observeAllFullIntelligenceDossiers()
    val rawSocialEntitiesFlow: Flow<List<RawSocialMediaEntity>> = dao.observeAllRawSocialEntities()
    val linguisticProfilesFlow: Flow<List<LinguisticPersonalityProfileEntity>> = dao.observeAllLinguisticProfiles()
    val mindMapHierarchyFlow: Flow<List<MindMapHierarchyEntity>> = dao.observeAllMindMapHierarchyEdges()
    val tripletsFlow: Flow<List<KnowledgeTripletEntity>> = dao.observeTriplets()
    val patchesFlow: Flow<List<AstPatchEntity>> = dao.observeAstPatches()

    fun observeRawEntitiesForSubject(subjectId: String): Flow<List<RawSocialMediaEntity>> =
        dao.observeRawSocialEntitiesForSubject(subjectId)

    fun observeLinguisticProfileForSubject(subjectId: String): Flow<LinguisticPersonalityProfileEntity?> =
        dao.observeLinguisticProfileForSubject(subjectId)

    fun observeMindMapHierarchyForSubject(rootSubjectId: String): Flow<List<MindMapHierarchyEntity>> =
        dao.observeMindMapHierarchyForSubject(rootSubjectId)

    suspend fun saveProfile(
        profile: SubjectProfile,
        allProfiles: List<SubjectProfile> = emptyList(),
        triplets: List<KnowledgeTripletEntity> = emptyList()
    ) {
        // Single persistence choke point: restricted inferences are never stored for subjects
        // without recorded consent, no matter which screen or service produced the profile.
        @Suppress("NAME_SHADOWING")
        val profile = ForensicMathEngine.applyConsentGate(profile)
        val subjectEntity = profile.toEntity()
        val rawEntities = profile.toRawSocialMediaEntities()
        val lingEntity = profile.toLinguisticProfileEntity()
        val hierarchyEntities = ForensicMathEngine.buildMindMapHierarchyEntities(
            subject = profile,
            allSubjects = allProfiles,
            triplets = triplets
        )
        dao.persistCompleteIntelligenceDossier(
            subjectEntity = subjectEntity,
            rawSocialEntities = rawEntities,
            linguisticProfileEntity = lingEntity,
            mindMapHierarchyEntities = hierarchyEntities,
            triplets = triplets
        )
    }

    suspend fun saveTriplet(triplet: KnowledgeTripletEntity) {
        dao.insertTriplet(triplet)
    }

    suspend fun saveTriplets(triplets: List<KnowledgeTripletEntity>) {
        dao.insertTriplets(triplets)
    }

    suspend fun saveAstPatch(patch: AstPatchEntity) {
        dao.upsertAstPatch(patch)
    }

    suspend fun ensureSeedData() {
        if (dao.getProfileCount() > 0) return

        val now = System.currentTimeMillis()

        // Seed 1: NULL_ROUTER
        val nullArtifacts = listOf(
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-null-1",
                platform = "GITHUB_COMMIT",
                timestampIso = "2026-11-08T08:14:22Z",
                verbatimText = "Enforce deterministic SHA-256 provenance verification across all distributed FalkorDB graph nodes before key rotation.",
                contextLabel = "null-router/falkordb-osint-graphrag@993afx1"
            ),
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-null-2",
                platform = "GITHUB_PR_REVIEW",
                timestampIso = "2026-11-07T11:42:09Z",
                verbatimText = "We are committed to inclusive governance where every contributor is empowered across our distributed consensus architecture.",
                contextLabel = "PR #44: Governance & Consensus Spec"
            ),
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-null-3",
                platform = "REDDIT_SHADOW_DOM",
                timestampIso = "2026-11-06T16:05:48Z",
                verbatimText = "These idiots actually bought the PR. Lock down the repo keys and purge anyone asking about the commits or shadow hydration leaks.",
                contextLabel = "r/netsec // u/CipherVane_Public"
            ),
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-null-4",
                platform = "HUGGING_FACE_CARD",
                timestampIso = "2026-11-04T14:29:31Z",
                verbatimText = "Calibrated 50-word Burrows' Delta variance matrix paired with 384-d MiniLM embeddings for authorship attribution.",
                contextLabel = "hf.co/Minej/bert-base-personality"
            ),
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-null-5",
                platform = "TIKTOK_REHYDRATION",
                timestampIso = "2026-11-02T19:51:10Z",
                verbatimText = "Never trust default DOM selectors; intercept the hydration state tree directly from the script payload.",
                contextLabel = "@Ghost_Protocol_99 // Video #8821"
            )
        )
        val nullCorpus = nullArtifacts.joinToString(" ") { it.verbatimText }
        val nullEval = ForensicMathEngine.evaluateCorpus(nullCorpus, repoCount = 14, hfCount = 5, followerCount = 840)
        val nullChrono = ForensicMathEngine.evaluateChronoAndGeo(
            listOf(7, 8, 8, 9, 10, 11, 11, 12, 13, 14, 15, 16, 16, 17, 18, 19, 20, 21),
            "Zürich, Switzerland"
        )
        val nullRepos = listOf(
            GithubRepoItem(
                name = "falkordb-osint-graphrag",
                fullName = "null-router/falkordb-osint-graphrag",
                description = "High-throughput Cypher knowledge graph & calibrated 50-word Burrows' Delta stylometry engine",
                language = "Python",
                stars = 1420,
                forks = 218,
                url = "https://github.com/FalkorDB/FalkorDB",
                updatedAtIso = "2026-11-08T08:14:22Z",
                topics = listOf("osint", "graphrag", "falkordb", "stylometry")
            ),
            GithubRepoItem(
                name = "stealth-ast-healer",
                fullName = "null-router/stealth-ast-healer",
                description = "Playwright DOM drift detector & neural hydration selector synthesizer for walled gardens",
                language = "TypeScript",
                stars = 890,
                forks = 134,
                url = "https://github.com/microsoft/playwright",
                updatedAtIso = "2026-11-06T15:20:00Z",
                topics = listOf("scraper", "ast", "playwright", "osint")
            ),
            GithubRepoItem(
                name = "stix21-prov-ledger",
                fullName = "null-router/stix21-prov-ledger",
                description = "RFC 4122 UUIDv5 + FIPS 180-4 SHA-256 W3C PROV-O threat intelligence bundler",
                language = "Rust",
                stars = 610,
                forks = 74,
                url = "https://github.com/oasis-open/cti-stix-validator",
                updatedAtIso = "2026-11-03T10:11:45Z",
                topics = listOf("stix2", "cryptography", "threat-intel")
            )
        )
        val nullHf = listOf(
            HuggingFaceAsset(
                id = "Minej/bert-base-personality",
                kind = "MODEL",
                pipelineTag = "text-classification",
                downloads = 48200,
                likes = 195,
                summary = "Big Five (OCEAN) psycholinguistic personality trait classifier trained on multi-platform text corpora.",
                lastModifiedIso = "2026-11-04T14:29:31Z",
                tags = listOf("psychometrics", "ocean", "bert", "personality")
            ),
            HuggingFaceAsset(
                id = "sentence-transformers/all-MiniLM-L6-v2",
                kind = "MODEL",
                pipelineTag = "sentence-similarity",
                downloads = 64500000,
                likes = 2890,
                summary = "384-dimensional dense vector embedding model used for UMAP semantic clustering and mind-map topology.",
                lastModifiedIso = "2026-10-29T18:00:00Z",
                tags = listOf("embeddings", "umap", "semantic-search")
            ),
            HuggingFaceAsset(
                id = "dslim/bert-base-NER",
                kind = "MODEL",
                pipelineTag = "token-classification",
                downloads = 8900000,
                likes = 940,
                summary = "Named Entity Recognition for extracting organizations, personas, and locations from OSINT text.",
                lastModifiedIso = "2026-10-21T09:15:00Z",
                tags = listOf("ner", "osint", "entity-extraction")
            )
        )
        val nullHash = ForensicMathEngine.sha256("NULL_ROUTER:${nullEval.burrowsDelta}")
        val nullLingProfile = ForensicMathEngine.buildLinguisticPersonalityProfile(
            "NULL_ROUTER", nullEval, nullChrono, nullArtifacts.size
        )
        val nullInsights = ForensicMathEngine.synthesizeActionableInsights(
            "NULL_ROUTER", nullEval, nullChrono, nullRepos, nullHf, "AST-PATCH-993AFX", nullLingProfile
        )
        val subjAlpha = SubjectProfile(
            id = "subj-alpha",
            stixId = ForensicMathEngine.generateStixId("identity", "NULL_ROUTER"),
            handle = "NULL_ROUTER",
            codeId = "#993A-FX",
            alias = "CipherVane // Null_Architect_99",
            cluster = "Cluster-01: Zero-Trust Sabotage",
            loc = nullChrono.locationLabel,
            lat = nullChrono.lat,
            lon = nullChrono.lon,
            utcOffset = nullChrono.inferredUtcOffset,
            troughWindowLabel = nullChrono.windowLabel,
            burrowsDelta = 0.312,
            styloMatch = 94.2,
            mattrScore = nullEval.mattr,
            ocean = OceanScores(O = 78, C = 86, E = 66, A = 22, N = 64),
            darkTriad = DarkTriadScores(machiavellianism = 89, narcissism = 78, psychopathy = 64),
            threatMetrics = ThreatMetrics(pathwayToViolence = 87, memeticVirality = 94, contextCollapse = 82, networkCentrality = 99),
            zScores = nullEval.zScores30,
            hourlyBins = nullChrono.hourlyBins,
            frontStageQuote = "We are committed to inclusive governance where every contributor is empowered across our distributed consensus architecture.",
            backStageQuote = "These idiots actually bought the PR. Lock down the repo keys and purge anyone asking about the commits.",
            githubRepos = nullRepos,
            huggingFaceAssets = nullHf,
            actionableInsights = nullInsights,
            scrapedArtifacts = nullArtifacts,
            linguisticProfile = nullLingProfile,
            healedPatchId = "AST-PATCH-993AFX",
            provSha256 = nullHash,
            updatedAt = now,
            consentStatus = ConsentStatus.DEMO,
            corpusWordCount = nullEval.tokenCount,
            dataProvenance = "DEMO_SEED (fictional persona)"
        )

        // Seed 2: V_KOVACS
        val kovacsArtifacts = listOf(
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-kov-1",
                platform = "GITHUB_COMMIT",
                timestampIso = "2026-11-08T15:30:12Z",
                verbatimText = "Calibrate sliding 6-hour minimum activity window over 24-hour UTC commit histograms to detect physical timezone drift.",
                contextLabel = "v-kovacs/chronolocate-diurnal@771czx"
            ),
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-kov-2",
                platform = "GITHUB_ISSUE",
                timestampIso = "2026-11-07T18:12:44Z",
                verbatimText = "Excited to attend the regional transit infrastructure symposium and collaborate with the open-source security community!",
                contextLabel = "Issue #12: Boston Meetup & Transit Telemetry"
            ),
            ForensicMathEngine.createTimestampedArtifact(
                id = "art-kov-3",
                platform = "TELEGRAM_BRIDGE",
                timestampIso = "2026-11-06T01:48:05Z",
                verbatimText = "Drop the hardware near the Kendall locker before 04:00 UTC. Ensure the relay firmware overrides the default telemetry check. Do not message me on clearnet again.",
                contextLabel = "Relay Channel #RedLine_Op"
            )
        )
        val kovacsCorpus = kovacsArtifacts.joinToString(" ") { it.verbatimText }
        val kovacsEval = ForensicMathEngine.evaluateCorpus(kovacsCorpus, repoCount = 8, hfCount = 3, followerCount = 310)
        val kovacsChrono = ForensicMathEngine.evaluateChronoAndGeo(
            listOf(12, 13, 14, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 0, 1, 2),
            "Cambridge, MA"
        )
        val kovacsRepos = listOf(
            GithubRepoItem(
                name = "chronolocate-diurnal",
                fullName = "v-kovacs/chronolocate-diurnal",
                description = "Sliding 6-hour UTC commit trough analyzer for physical timezone shift detection",
                language = "Python",
                stars = 530,
                forks = 88,
                url = "https://github.com/v-kovacs/chronolocate-diurnal",
                updatedAtIso = "2026-11-08T15:30:12Z",
                topics = listOf("chronolocation", "osint", "git-forensics")
            ),
            GithubRepoItem(
                name = "rf-mesh-telemetry",
                fullName = "v-kovacs/rf-mesh-telemetry",
                description = "Low-power packet dissector and equirectangular radar projection daemon",
                language = "C++",
                stars = 412,
                forks = 61,
                url = "https://github.com/v-kovacs/rf-mesh-telemetry",
                updatedAtIso = "2026-11-05T21:14:00Z",
                topics = listOf("sigint", "telemetry", "radar")
            )
        )
        val kovacsHf = listOf(
            HuggingFaceAsset(
                id = "cardiffnlp/twitter-roberta-base-sentiment-latest",
                kind = "MODEL",
                pipelineTag = "text-classification",
                downloads = 12400000,
                likes = 1420,
                summary = "Social stream affect & hostility polarity detector for front-stage vs back-stage discourse drift.",
                lastModifiedIso = "2026-11-01T13:10:00Z",
                tags = listOf("sentiment", "discourse", "roberta")
            ),
            HuggingFaceAsset(
                id = "facebook/bart-large-mnli",
                kind = "MODEL",
                pipelineTag = "zero-shot-classification",
                downloads = 18500000,
                likes = 1680,
                summary = "Zero-shot intent & behavioral threat category classifier for multi-source OSINT feeds.",
                lastModifiedIso = "2026-10-25T17:45:00Z",
                tags = listOf("zero-shot", "nli", "intent-classification")
            )
        )
        val kovacsHash = ForensicMathEngine.sha256("V_KOVACS:${kovacsEval.burrowsDelta}")
        val kovacsLingProfile = ForensicMathEngine.buildLinguisticPersonalityProfile(
            "V_KOVACS", kovacsEval, kovacsChrono, kovacsArtifacts.size
        )
        val kovacsInsights = ForensicMathEngine.synthesizeActionableInsights(
            "V_KOVACS", kovacsEval, kovacsChrono, kovacsRepos, kovacsHf, "AST-PATCH-771CZX", kovacsLingProfile
        )
        val subjBeta = SubjectProfile(
            id = "subj-beta",
            stixId = ForensicMathEngine.generateStixId("identity", "V_KOVACS"),
            handle = "V_KOVACS",
            codeId = "#771C-ZX",
            alias = "Kovacs_Actual // RedLine_Op",
            cluster = "Cluster-01: Zero-Trust Sabotage",
            loc = kovacsChrono.locationLabel,
            lat = kovacsChrono.lat,
            lon = kovacsChrono.lon,
            utcOffset = kovacsChrono.inferredUtcOffset,
            troughWindowLabel = kovacsChrono.windowLabel,
            burrowsDelta = 0.348,
            styloMatch = 89.6,
            mattrScore = kovacsEval.mattr,
            ocean = OceanScores(O = 58, C = 79, E = 71, A = 26, N = 71),
            darkTriad = DarkTriadScores(machiavellianism = 82, narcissism = 65, psychopathy = 77),
            threatMetrics = ThreatMetrics(pathwayToViolence = 92, memeticVirality = 76, contextCollapse = 74, networkCentrality = 84),
            zScores = kovacsEval.zScores30,
            hourlyBins = kovacsChrono.hourlyBins,
            frontStageQuote = "Excited to attend the regional transit infrastructure symposium and collaborate with the open-source security community!",
            backStageQuote = "Drop the hardware near the Kendall locker before 04:00 UTC. Do not message me on clearnet again.",
            githubRepos = kovacsRepos,
            huggingFaceAssets = kovacsHf,
            actionableInsights = kovacsInsights,
            scrapedArtifacts = kovacsArtifacts,
            linguisticProfile = kovacsLingProfile,
            healedPatchId = "AST-PATCH-771CZX",
            provSha256 = kovacsHash,
            updatedAt = now - 5000,
            consentStatus = ConsentStatus.DEMO,
            corpusWordCount = kovacsEval.tokenCount,
            dataProvenance = "DEMO_SEED (fictional persona)"
        )

        val seedTriplets = listOf(
            KnowledgeTripletEntity(
                id = "tr-seed-1",
                subjectHandle = "NULL_ROUTER",
                predicate = "SOCKPUPPET_OF (Δ=0.312)",
                objectNode = "CipherVane_Public",
                nodeCategory = NodeCategory.WALLED_GARDEN.name,
                confidence = 0.94f,
                provSha256 = nullHash,
                timestamp = now
            ),
            KnowledgeTripletEntity(
                id = "tr-seed-2",
                subjectHandle = "NULL_ROUTER",
                predicate = "TASKED_OPERATIVE",
                objectNode = "V_KOVACS",
                nodeCategory = NodeCategory.IDENTITY.name,
                confidence = 0.91f,
                provSha256 = kovacsHash,
                timestamp = now - 1000
            ),
            KnowledgeTripletEntity(
                id = "tr-seed-3",
                subjectHandle = "NULL_ROUTER",
                predicate = "DEPLOYED_MODEL",
                objectNode = "Minej/bert-base-personality",
                nodeCategory = NodeCategory.HF_MODEL.name,
                confidence = 0.96f,
                provSha256 = nullHash,
                timestamp = now - 2000
            )
        )

        val allSeeds = listOf(subjAlpha, subjBeta)
        saveProfile(subjAlpha, allSeeds, seedTriplets)
        saveProfile(subjBeta, allSeeds, emptyList())

        val tiktokSpec = ForensicMathEngine.WALLED_GARDEN_CATALOG["tiktok"]!!
        dao.upsertAstPatch(
            AstPatchEntity(
                platformKey = "tiktok",
                patchId = "AST-PATCH-993AFX",
                platformName = tiktokSpec.name,
                deprecatedSelector = tiktokSpec.deprecatedSelector,
                mutatedDom = tiktokSpec.mutatedDom,
                healedSelector = tiktokSpec.healedSelector,
                strategy = tiktokSpec.strategy,
                pythonSnippet = tiktokSpec.pythonSnippet,
                confidence = "98.4%",
                timestamp = "20:47:01"
            )
        )
    }
}
