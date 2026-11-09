package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.engine.ChronoGeoEngine
import com.example.engine.ConsentGate
import com.example.engine.ForensicCommon
import com.example.engine.MindMapEngine
import com.example.engine.PlaybookEngine
import com.example.engine.StylometryEngine
import com.example.engine.WalledGardenCatalog
import com.example.model.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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

    @Query(
        "SELECT * FROM mind_map_hierarchy_edges WHERE rootSubjectId = :rootSubjectId AND parentNodeId = :parentNodeId ORDER BY confidenceWeight DESC"
    )
    fun observeMindMapChildrenByParent(rootSubjectId: String, parentNodeId: String): Flow<List<MindMapHierarchyEntity>>

    @Query(
        "SELECT * FROM mind_map_hierarchy_edges WHERE rootSubjectId = :rootSubjectId AND hierarchyDepth = :depth ORDER BY confidenceWeight DESC"
    )
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
    // 5b. Erasure and retention (Phase 2). Child tables cascade from subject_profiles;
    //     knowledge triplets are keyed by handle, so they are deleted explicitly.
    // ------------------------------------------------------------------------
    @Query("DELETE FROM subject_profiles WHERE id = :subjectId")
    suspend fun deleteSubjectRow(subjectId: String): Int

    @Query("DELETE FROM knowledge_triplets WHERE subjectHandle = :handle AND projectId = :projectId")
    suspend fun deleteTripletsForHandle(handle: String, projectId: String): Int

    // ------------------------------------------------------------------------
    // Projects (schema v6): the isolation boundary
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM projects ORDER BY createdAtMillis ASC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY createdAtMillis ASC")
    suspend fun allProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun projectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjectIfAbsent(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity): Int

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectRow(projectId: String): Int

    @Query("SELECT projectId FROM subject_profiles WHERE id = :subjectId LIMIT 1")
    suspend fun projectOfSubject(subjectId: String): String?

    @Transaction
    @Query("SELECT * FROM subject_profiles WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeDossiersInProject(projectId: String): Flow<List<SubjectIntelligenceDossierRelation>>

    /**
     * Subjects a new mind map in [projectId] may link to: its own, plus those in other projects when
     * both this project and the other one allow cross-project links.
     */
    @Transaction
    @Query(
        "SELECT * FROM subject_profiles WHERE projectId = :projectId OR (" +
            "(SELECT allowCrossProjectLinks FROM projects WHERE id = :projectId) = 1 AND " +
            "projectId IN (SELECT id FROM projects WHERE allowCrossProjectLinks = 1)) ORDER BY updatedAt DESC"
    )
    fun observeLinkableDossiers(projectId: String): Flow<List<SubjectIntelligenceDossierRelation>>

    @Query("SELECT * FROM knowledge_triplets WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT 60")
    fun observeTripletsInProject(projectId: String): Flow<List<KnowledgeTripletEntity>>

    @Query(
        "SELECT r.* FROM raw_social_media_entities r JOIN subject_profiles s ON s.id = r.subjectId " +
            "WHERE s.projectId = :projectId ORDER BY r.postedTimestampIso DESC LIMIT 100"
    )
    fun observeRawEntitiesInProject(projectId: String): Flow<List<RawSocialMediaEntity>>

    @Query(
        "SELECT m.* FROM mind_map_hierarchy_edges m JOIN subject_profiles s ON s.id = m.rootSubjectId " +
            "WHERE s.projectId = :projectId ORDER BY m.hierarchyDepth ASC, m.updatedAtMillis DESC LIMIT 150"
    )
    fun observeHierarchyInProject(projectId: String): Flow<List<MindMapHierarchyEntity>>

    @Query("SELECT * FROM subject_profiles WHERE projectId = :projectId")
    suspend fun profilesInProject(projectId: String): List<SubjectProfileEntity>

    @Query("SELECT * FROM subject_profiles WHERE projectId = :projectId AND updatedAt < :cutoffMillis AND consentStatus != 'DEMO'")
    suspend fun profilesOlderThanInProject(projectId: String, cutoffMillis: Long): List<SubjectProfileEntity>

    @Query("DELETE FROM knowledge_triplets WHERE projectId = :projectId")
    suspend fun deleteTripletsInProject(projectId: String): Int

    /** Erases a project and everything in it (subjects cascade to artifacts, profiles and edges). */
    @Transaction
    suspend fun deleteProjectEverywhere(projectId: String): Int {
        val subjects = profilesInProject(projectId)
        subjects.forEach { deleteSubjectRow(it.id) }
        deleteTripletsInProject(projectId)
        deleteProjectRow(projectId)
        return subjects.size
    }

    @Transaction
    suspend fun deleteSubjectEverywhere(subjectId: String, handle: String): Int {
        val projectId = projectOfSubject(subjectId) ?: return 0
        deleteTripletsForHandle(handle, projectId)
        return deleteSubjectRow(subjectId)
    }

    // ------------------------------------------------------------------------
    // 5c. Append-only, hash-chained audit log (Phase 2)
    // ------------------------------------------------------------------------
    @Query("SELECT * FROM audit_log ORDER BY seq DESC LIMIT 1")
    suspend fun lastAuditEntry(): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditEntry(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY seq ASC")
    suspend fun allAuditEntriesAsc(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log ORDER BY seq DESC LIMIT :limit")
    fun observeRecentAudit(limit: Int): Flow<List<AuditLogEntity>>

    /** Appends one entry chained to the current last entry, atomically. */
    @Transaction
    suspend fun appendAudit(action: String, subjectRef: String, detail: String, nowMillis: Long): AuditLogEntity {
        val prev = lastAuditEntry()?.entryHash ?: AuditLogEntity.GENESIS_HASH
        val hash = AuditLogEntity.computeHash(prev, nowMillis, action, subjectRef, detail)
        val entry = AuditLogEntity(
            timestampMillis = nowMillis,
            action = action,
            subjectRef = subjectRef,
            detail = detail,
            prevHash = prev,
            entryHash = hash
        )
        val seq = insertAuditEntry(entry)
        return entry.copy(seq = seq)
    }

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
        AstPatchEntity::class,
        AuditLogEntity::class,
        ProjectEntity::class
    ],
    version = 6,
    // Schema JSON is exported to app/schemas (see room.schemaLocation in app/build.gradle.kts)
    // so future migrations can be written and tested against real schemas.
    exportSchema = true
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        @Volatile
        private var cachedInstance: AegisDatabase? = null

        const val DB_NAME = "aegis_blackvault.db"

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

        /**
         * v4 -> v5 (Phase 2 governance): append-only audit log, consent records on subjects,
         * chain-of-custody columns on collected artifacts.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_log` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestampMillis` INTEGER NOT NULL, `action` TEXT NOT NULL, `subjectRef` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, `prevHash` TEXT NOT NULL, `entryHash` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_timestampMillis` ON `audit_log` (`timestampMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_subjectRef` ON `audit_log` (`subjectRef`)")
                installAuditTriggers(db)

                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN consentMethod TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN consentRecordedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN consentExpiresAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE raw_social_media_entities ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE raw_social_media_entities ADD COLUMN fetchedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE raw_social_media_entities ADD COLUMN httpStatus INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE raw_social_media_entities ADD COLUMN rawResponseSha256 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE raw_social_media_entities ADD COLUMN connectorRunId TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v5 -> v6: projects as the isolation boundary. Every existing subject and triplet moves into
         * the default project; the global retention setting is carried over by the ViewModel.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `retentionDays` INTEGER NOT NULL, " +
                        "`allowCrossProjectLinks` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO projects (id, name, createdAtMillis, retentionDays, allowCrossProjectLinks) " +
                        "VALUES ('${ProjectEntity.DEFAULT_ID}', '${ProjectEntity.DEFAULT_NAME}', ${System.currentTimeMillis()}, 0, 0)"
                )
                db.execSQL("ALTER TABLE subject_profiles ADD COLUMN projectId TEXT NOT NULL DEFAULT '${ProjectEntity.DEFAULT_ID}'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subject_profiles_projectId` ON `subject_profiles` (`projectId`)")
                db.execSQL("ALTER TABLE knowledge_triplets ADD COLUMN projectId TEXT NOT NULL DEFAULT '${ProjectEntity.DEFAULT_ID}'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_triplets_projectId` ON `knowledge_triplets` (`projectId`)")
            }
        }

        /** Every migration, in order, for the builder and the migration tests. */
        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        /** SQLite-level enforcement that audit_log rows can be added but never changed or removed. */
        fun installAuditTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS audit_log_no_update BEFORE UPDATE ON audit_log " +
                    "BEGIN SELECT RAISE(ABORT, 'audit_log is append-only'); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS audit_log_no_delete BEFORE DELETE ON audit_log " +
                    "BEGIN SELECT RAISE(ABORT, 'audit_log is append-only'); END"
            )
        }

        /** Installs the triggers on fresh installs too (migrations only run on upgrades). */
        val AUDIT_TRIGGERS_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installAuditTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                installAuditTriggers(db)
            }
        }

        fun getInstance(context: Context): AegisDatabase = cachedInstance ?: synchronized(this) {
            val appContext = context.applicationContext
            val builder = Room.databaseBuilder(appContext, AegisDatabase::class.java, DB_NAME)
                // v3 data is migrated, never wiped. (v1/v2 were pre-release AI Studio builds only.)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(AUDIT_TRIGGERS_CALLBACK)
            // Encryption at rest: converts an existing plaintext file once, then opens with SQLCipher.
            DatabaseEncryption.prepare(appContext, DB_NAME)?.let { passphrase ->
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
            }
            val instance = builder.build()
            cachedInstance = instance
            instance
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

    fun observeRawEntitiesForSubject(subjectId: String): Flow<List<RawSocialMediaEntity>> = dao.observeRawSocialEntitiesForSubject(subjectId)

    fun observeLinguisticProfileForSubject(subjectId: String): Flow<LinguisticPersonalityProfileEntity?> = dao.observeLinguisticProfileForSubject(subjectId)

    fun observeMindMapHierarchyForSubject(rootSubjectId: String): Flow<List<MindMapHierarchyEntity>> = dao.observeMindMapHierarchyForSubject(rootSubjectId)

    class CrossProjectWriteException(message: String) : IllegalStateException(message)

    /**
     * Saves [profile] into its project ([projectId] when the profile has none yet). A subject never
     * moves between projects: saving a profile whose id already belongs to another project throws
     * [CrossProjectWriteException].
     */
    suspend fun saveProfile(
        profile: SubjectProfile,
        allProfiles: List<SubjectProfile> = emptyList(),
        triplets: List<KnowledgeTripletEntity> = emptyList(),
        projectId: String = ProjectEntity.DEFAULT_ID
    ) {
        val owner = profile.projectId.ifBlank { projectId }
        dao.projectOfSubject(profile.id)?.let { existing ->
            if (existing != owner) {
                throw CrossProjectWriteException("Subject ${profile.id} belongs to another project; refusing to move it")
            }
        }
        if (dao.projectById(owner) == null) {
            throw CrossProjectWriteException("Project $owner does not exist")
        }
        // Single persistence choke point: restricted inferences are never stored for subjects
        // without recorded consent, no matter which screen or service produced the profile.
        @Suppress("NAME_SHADOWING")
        val profile = ConsentGate.applyConsentGate(profile.copy(projectId = owner))

        @Suppress("NAME_SHADOWING")
        val triplets = triplets.map { it.copy(projectId = owner) }
        val subjectEntity = profile.toEntity()
        val rawEntities = profile.toRawSocialMediaEntities()
        val lingEntity = profile.toLinguisticProfileEntity()
        val hierarchyEntities = MindMapEngine.buildMindMapHierarchyEntities(
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

    // ------------------------------------------------------------------------
    // Governance (Phase 2)
    // ------------------------------------------------------------------------

    val recentAuditFlow: Flow<List<AuditLogEntity>> = dao.observeRecentAudit(200)

    /** Appends an audit entry. [detail] must never contain profile content or the subject's handle. */
    suspend fun audit(action: AuditAction, subjectId: String?, detail: String): AuditLogEntity = dao.appendAudit(
        action = action.name,
        subjectRef = AuditLogEntity.subjectRefFor(subjectId),
        detail = detail.take(500),
        nowMillis = System.currentTimeMillis()
    )

    /** Re-walks the whole chain and reports the first entry whose hash or link does not match. */
    suspend fun verifyAuditChain(): AuditChainStatus {
        val entries = dao.allAuditEntriesAsc()
        var prev = AuditLogEntity.GENESIS_HASH
        for (e in entries) {
            val expected = AuditLogEntity.computeHash(e.prevHash, e.timestampMillis, e.action, e.subjectRef, e.detail)
            if (e.prevHash != prev || e.entryHash != expected) {
                return AuditChainStatus(entries = entries.size, intact = false, firstBrokenSeq = e.seq)
            }
            prev = e.entryHash
        }
        return AuditChainStatus(entries = entries.size, intact = true)
    }

    /**
     * Erases a subject: profile, collected artifacts, linguistic profile and mind-map edges (cascade),
     * plus knowledge triplets. The audit entry records only the pseudonymous subject reference.
     */
    suspend fun deleteSubject(subjectId: String, handle: String, reason: String): Boolean {
        val removed = dao.deleteSubjectEverywhere(subjectId, handle)
        if (removed > 0) {
            audit(AuditAction.SUBJECT_DELETED, subjectId, "Subject erased ($reason): profile, artifacts, graph edges, triplets")
        }
        return removed > 0
    }

    /**
     * Applies each project's own retention period: non-demo subjects not updated within it are erased.
     * Returns how many were erased in total.
     */
    suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        var total = 0
        for (project in dao.allProjects()) {
            if (project.retentionDays <= 0) continue
            val cutoff = nowMillis - project.retentionDays * 24L * 60 * 60 * 1000
            val stale = dao.profilesOlderThanInProject(project.id, cutoff)
            stale.forEach { dao.deleteSubjectEverywhere(it.id, it.handle) }
            if (stale.isNotEmpty()) {
                audit(
                    AuditAction.RETENTION_PURGE,
                    null,
                    "Retention purge in project ${ProjectEntity.refFor(project.id)}: ${stale.size} subject(s) " +
                        "older than ${project.retentionDays} days erased"
                )
            }
            total += stale.size
        }
        return total
    }

    // ------------------------------------------------------------------------
    // Projects (schema v6)
    // ------------------------------------------------------------------------

    val projectsFlow: Flow<List<ProjectEntity>> = dao.observeProjects()

    fun dossiersInProject(projectId: String) = dao.observeDossiersInProject(projectId)
    fun linkableDossiers(projectId: String) = dao.observeLinkableDossiers(projectId)
    fun tripletsInProject(projectId: String) = dao.observeTripletsInProject(projectId)
    fun rawEntitiesInProject(projectId: String) = dao.observeRawEntitiesInProject(projectId)
    fun hierarchyInProject(projectId: String) = dao.observeHierarchyInProject(projectId)

    suspend fun project(projectId: String): ProjectEntity? = dao.projectById(projectId)

    suspend fun ensureDefaultProject() {
        dao.insertProjectIfAbsent(
            ProjectEntity(ProjectEntity.DEFAULT_ID, ProjectEntity.DEFAULT_NAME, createdAtMillis = 0L)
        )
    }

    /** Creates a project and returns it. The audit entry records only a pseudonymous reference, not the name. */
    suspend fun createProject(name: String, nowMillis: Long = System.currentTimeMillis()): ProjectEntity {
        val clean = name.trim().take(60).ifBlank { "Untitled project" }
        val project = ProjectEntity(
            id = "proj-" + ForensicCommon.sha256("$clean:$nowMillis:${System.nanoTime()}").take(12),
            name = clean,
            createdAtMillis = nowMillis
        )
        dao.insertProjectIfAbsent(project)
        audit(AuditAction.PROJECT_CREATED, null, "Project ${ProjectEntity.refFor(project.id)} created")
        return project
    }

    suspend fun setProjectRetention(projectId: String, days: Int) {
        val project = dao.projectById(projectId) ?: return
        dao.updateProject(project.copy(retentionDays = days.coerceAtLeast(0)))
        audit(
            AuditAction.SETTINGS_CHANGED,
            null,
            "Project ${ProjectEntity.refFor(projectId)}: " + if (days > 0) "retention set to $days days" else "retention disabled"
        )
    }

    suspend fun setProjectCrossLinks(projectId: String, allowed: Boolean) {
        val project = dao.projectById(projectId) ?: return
        dao.updateProject(project.copy(allowCrossProjectLinks = allowed))
        audit(
            AuditAction.SETTINGS_CHANGED,
            null,
            "Project ${ProjectEntity.refFor(projectId)}: cross-project links " + if (allowed) "allowed" else "blocked"
        )
    }

    /**
     * Erases a project with every subject, artifact, profile, graph edge and triplet in it.
     * The default project cannot be deleted. Returns how many subjects were erased, or -1 if refused.
     */
    suspend fun deleteProject(projectId: String): Int {
        if (projectId == ProjectEntity.DEFAULT_ID || dao.projectById(projectId) == null) return -1
        val erased = dao.deleteProjectEverywhere(projectId)
        audit(
            AuditAction.PROJECT_DELETED,
            null,
            "Project ${ProjectEntity.refFor(projectId)} erased with $erased subject(s), their artifacts, graph edges and triplets"
        )
        return erased
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
        ensureDefaultProject()
        if (dao.getProfileCount() > 0) return

        val now = System.currentTimeMillis()

        // Seed 1: NULL_ROUTER
        val nullArtifacts = listOf(
            StylometryEngine.createTimestampedArtifact(
                id = "art-null-1",
                platform = "GITHUB_COMMIT",
                timestampIso = "2026-11-08T08:14:22Z",
                verbatimText = "Enforce deterministic SHA-256 provenance verification across all distributed FalkorDB graph nodes before key rotation.",
                contextLabel = "null-router/falkordb-osint-graphrag@993afx1"
            ),
            StylometryEngine.createTimestampedArtifact(
                id = "art-null-2",
                platform = "GITHUB_PR_REVIEW",
                timestampIso = "2026-11-07T11:42:09Z",
                verbatimText = "We are committed to inclusive governance where every contributor is empowered across our distributed consensus architecture.",
                contextLabel = "PR #44: Governance & Consensus Spec"
            ),
            StylometryEngine.createTimestampedArtifact(
                id = "art-null-3",
                platform = "REDDIT_SHADOW_DOM",
                timestampIso = "2026-11-06T16:05:48Z",
                verbatimText = "These idiots actually bought the PR. Lock down the repo keys and purge anyone asking about the commits or shadow hydration leaks.",
                contextLabel = "r/netsec // u/CipherVane_Public"
            ),
            StylometryEngine.createTimestampedArtifact(
                id = "art-null-4",
                platform = "HUGGING_FACE_CARD",
                timestampIso = "2026-11-04T14:29:31Z",
                verbatimText = "Calibrated 50-word Burrows' Delta variance matrix paired with 384-d MiniLM embeddings for authorship attribution.",
                contextLabel = "hf.co/Minej/bert-base-personality"
            ),
            StylometryEngine.createTimestampedArtifact(
                id = "art-null-5",
                platform = "TIKTOK_REHYDRATION",
                timestampIso = "2026-11-02T19:51:10Z",
                verbatimText = "Never trust default DOM selectors; intercept the hydration state tree directly from the script payload.",
                contextLabel = "@Ghost_Protocol_99 // Video #8821"
            )
        )
        val nullCorpus = nullArtifacts.joinToString(" ") { it.verbatimText }
        val nullEval = StylometryEngine.evaluateCorpus(nullCorpus, repoCount = 14, hfCount = 5, followerCount = 840)
        val nullChrono = ChronoGeoEngine.evaluateChronoAndGeo(
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
        val nullHash = ForensicCommon.sha256("NULL_ROUTER:${nullEval.burrowsDelta}")
        val nullLingProfile = StylometryEngine.buildLinguisticPersonalityProfile(
            "NULL_ROUTER",
            nullEval,
            nullChrono,
            nullArtifacts.size
        )
        val nullInsights = PlaybookEngine.synthesizeActionableInsights(
            "NULL_ROUTER",
            nullEval,
            nullChrono,
            nullRepos,
            nullHf,
            "AST-PATCH-993AFX",
            nullLingProfile
        )
        val subjAlpha = SubjectProfile(
            id = "subj-alpha",
            stixId = ForensicCommon.generateStixId("identity", "NULL_ROUTER"),
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
            StylometryEngine.createTimestampedArtifact(
                id = "art-kov-1",
                platform = "GITHUB_COMMIT",
                timestampIso = "2026-11-08T15:30:12Z",
                verbatimText = "Calibrate sliding 6-hour minimum activity window over 24-hour UTC commit histograms to detect physical timezone drift.",
                contextLabel = "v-kovacs/chronolocate-diurnal@771czx"
            ),
            StylometryEngine.createTimestampedArtifact(
                id = "art-kov-2",
                platform = "GITHUB_ISSUE",
                timestampIso = "2026-11-07T18:12:44Z",
                verbatimText = "Excited to attend the regional transit infrastructure symposium and collaborate with the open-source security community!",
                contextLabel = "Issue #12: Boston Meetup & Transit Telemetry"
            ),
            StylometryEngine.createTimestampedArtifact(
                id = "art-kov-3",
                platform = "TELEGRAM_BRIDGE",
                timestampIso = "2026-11-06T01:48:05Z",
                verbatimText = "Drop the hardware near the Kendall locker before 04:00 UTC. Ensure the relay firmware overrides the default telemetry check. Do not message me on clearnet again.",
                contextLabel = "Relay Channel #RedLine_Op"
            )
        )
        val kovacsCorpus = kovacsArtifacts.joinToString(" ") { it.verbatimText }
        val kovacsEval = StylometryEngine.evaluateCorpus(kovacsCorpus, repoCount = 8, hfCount = 3, followerCount = 310)
        val kovacsChrono = ChronoGeoEngine.evaluateChronoAndGeo(
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
        val kovacsHash = ForensicCommon.sha256("V_KOVACS:${kovacsEval.burrowsDelta}")
        val kovacsLingProfile = StylometryEngine.buildLinguisticPersonalityProfile(
            "V_KOVACS",
            kovacsEval,
            kovacsChrono,
            kovacsArtifacts.size
        )
        val kovacsInsights = PlaybookEngine.synthesizeActionableInsights(
            "V_KOVACS",
            kovacsEval,
            kovacsChrono,
            kovacsRepos,
            kovacsHf,
            "AST-PATCH-771CZX",
            kovacsLingProfile
        )
        val subjBeta = SubjectProfile(
            id = "subj-beta",
            stixId = ForensicCommon.generateStixId("identity", "V_KOVACS"),
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

        val tiktokSpec = WalledGardenCatalog.WALLED_GARDEN_CATALOG["tiktok"]!!
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
