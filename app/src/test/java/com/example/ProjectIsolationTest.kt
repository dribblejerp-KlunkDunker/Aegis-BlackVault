package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.model.AuditAction
import com.example.model.KnowledgeTripletEntity
import com.example.model.ProjectEntity
import com.example.model.ScraperPlatformMode
import com.example.model.SubjectProfile
import com.example.network.OsintScraperService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 2: projects are the isolation boundary (schema v6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectIsolationTest {

    private lateinit var db: AegisDatabase
    private lateinit var repository: AegisRepository
    private val scraper = OsintScraperService(customClient = FakeGithubApi.client())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .addCallback(AegisDatabase.AUDIT_TRIGGERS_CALLBACK)
            .allowMainThreadQueries()
            .build()
        repository = AegisRepository(db.aegisDao())
        runBlocking { repository.ensureDefaultProject() }
    }

    @After
    fun tearDown() = db.close()

    private var counter = 0

    /** A fresh collection of the same GitHub account, under a distinct subject id each time. */
    private suspend fun scrape(): SubjectProfile =
        scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
            .let { it.copy(id = "${it.id}-${counter++}") }

    private fun triplet(id: String, handle: String) = KnowledgeTripletEntity(
        id = id, subjectHandle = handle, predicate = "USES", objectNode = "kotlin",
        nodeCategory = "SKILL", confidence = 0.9f, provSha256 = "", timestamp = 1L
    )

    private suspend fun idsIn(projectId: String) =
        repository.dossiersInProject(projectId).first().map { it.subject.id }.toSet()

    @Test
    fun `each project sees only its own subjects, artifacts, edges and triplets`() = runTest {
        val alpha = repository.createProject("Alpha")
        val beta = repository.createProject("Beta")
        val a = scrape()
        val b = scrape()
        repository.saveProfile(a, listOf(a), listOf(triplet("t-a", a.handle)), projectId = alpha.id)
        repository.saveProfile(b, listOf(b), listOf(triplet("t-b", b.handle)), projectId = beta.id)

        assertEquals(setOf(a.id), idsIn(alpha.id))
        assertEquals(setOf(b.id), idsIn(beta.id))
        assertTrue(idsIn(ProjectEntity.DEFAULT_ID).isEmpty())

        assertEquals(listOf("t-a"), repository.tripletsInProject(alpha.id).first().map { it.id })
        assertTrue(repository.rawEntitiesInProject(alpha.id).first().all { it.subjectId == a.id })
        assertTrue(repository.rawEntitiesInProject(alpha.id).first().isNotEmpty())
        assertTrue(repository.hierarchyInProject(beta.id).first().all { it.rootSubjectId == b.id })
    }

    @Test
    fun `erasing a subject never touches the same handle in another project`() = runTest {
        val alpha = repository.createProject("Alpha")
        val beta = repository.createProject("Beta")
        val a = scrape()
        val b = scrape()
        // Same GitHub account collected separately in two projects: same handle, different subjects.
        assertEquals(a.handle, b.handle)
        repository.saveProfile(a, listOf(a), listOf(triplet("t-a", a.handle)), projectId = alpha.id)
        repository.saveProfile(b, listOf(b), listOf(triplet("t-b", b.handle)), projectId = beta.id)

        assertTrue(repository.deleteSubject(a.id, a.handle, reason = "test"))

        assertTrue(idsIn(alpha.id).isEmpty())
        assertTrue(repository.tripletsInProject(alpha.id).first().isEmpty())
        assertEquals(setOf(b.id), idsIn(beta.id))
        assertEquals(listOf("t-b"), repository.tripletsInProject(beta.id).first().map { it.id })
    }

    @Test
    fun `a subject can never be moved into another project by saving it there`() = runTest {
        val alpha = repository.createProject("Alpha")
        val beta = repository.createProject("Beta")
        val a = scrape()
        repository.saveProfile(a, listOf(a), projectId = alpha.id)

        val stored = repository.dossiersInProject(alpha.id).first().single().subject.toDomain()
        assertEquals(alpha.id, stored.projectId)
        // Saving the stored profile again keeps it in Alpha whatever the caller's active project is.
        repository.saveProfile(stored, listOf(stored), projectId = beta.id)
        assertEquals(setOf(a.id), idsIn(alpha.id))
        assertTrue(idsIn(beta.id).isEmpty())

        // An explicit attempt to re-home it is refused.
        assertThrows(AegisRepository.CrossProjectWriteException::class.java) {
            runBlocking { repository.saveProfile(stored.copy(projectId = beta.id), listOf(stored)) }
        }
        assertThrows(AegisRepository.CrossProjectWriteException::class.java) {
            runBlocking { repository.saveProfile(scrape(), emptyList(), projectId = "proj-does-not-exist") }
        }
    }

    @Test
    fun `cross-project links need both projects to opt in`() = runTest {
        val alpha = repository.createProject("Alpha")
        val beta = repository.createProject("Beta")
        val a = scrape()
        val b = scrape()
        repository.saveProfile(a, listOf(a), projectId = alpha.id)
        repository.saveProfile(b, listOf(b), projectId = beta.id)

        suspend fun linkable() = repository.linkableDossiers(alpha.id).first().map { it.subject.id }.toSet()

        assertEquals(setOf(a.id), linkable())
        repository.setProjectCrossLinks(alpha.id, true)
        assertEquals("only one side opted in", setOf(a.id), linkable())
        repository.setProjectCrossLinks(beta.id, true)
        assertEquals(setOf(a.id, b.id), linkable())
        repository.setProjectCrossLinks(beta.id, false)
        assertEquals(setOf(a.id), linkable())
    }

    @Test
    fun `retention is per project`() = runTest {
        val alpha = repository.createProject("Alpha")
        val beta = repository.createProject("Beta")
        val old = System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000
        val a = scrape().copy(updatedAt = old)
        val b = scrape().copy(updatedAt = old)
        repository.saveProfile(a, listOf(a), projectId = alpha.id)
        repository.saveProfile(b, listOf(b), projectId = beta.id)

        repository.setProjectRetention(alpha.id, 30)
        assertEquals(1, repository.purgeExpired())
        assertTrue(idsIn(alpha.id).isEmpty())
        assertEquals("Beta keeps its data: no retention set there", setOf(b.id), idsIn(beta.id))
    }

    @Test
    fun `erasing a project removes everything in it and only it, and the audit log keeps no name`() = runTest {
        repository.ensureSeedData()
        val secret = repository.createProject("Case: Jane Q. Example")
        val a = scrape()
        repository.saveProfile(a, listOf(a), listOf(triplet("t-a", a.handle)), projectId = secret.id)
        val defaultBefore = idsIn(ProjectEntity.DEFAULT_ID)

        assertEquals(1, repository.deleteProject(secret.id))

        assertNull(repository.project(secret.id))
        assertNull(db.aegisDao().observeFullDossierById(a.id).first())
        assertTrue(db.aegisDao().observeRawSocialEntitiesForSubject(a.id).first().isEmpty())
        assertTrue(repository.tripletsInProject(secret.id).first().isEmpty())
        assertEquals(defaultBefore, idsIn(ProjectEntity.DEFAULT_ID))

        val log = db.aegisDao().allAuditEntriesAsc()
        assertEquals(AuditAction.PROJECT_DELETED.name, log.last().action)
        assertFalse(log.any { it.detail.contains("Jane") || it.detail.contains(secret.id) })
        assertTrue(log.any { it.action == AuditAction.PROJECT_CREATED.name })
        assertTrue(repository.verifyAuditChain().intact)

        assertEquals("the default project cannot be erased", -1, repository.deleteProject(ProjectEntity.DEFAULT_ID))
    }
}
