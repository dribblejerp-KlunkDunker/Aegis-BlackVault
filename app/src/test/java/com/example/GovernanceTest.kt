package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.engine.ForensicMathEngine
import com.example.model.AuditAction
import com.example.model.AuditLogEntity
import com.example.model.ConsentMethod
import com.example.model.ConsentStatus
import com.example.model.ScrapedArtifactItem
import com.example.model.ScraperPlatformMode
import com.example.network.OsintScraperService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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

/** Phase 2 governance: audit log, erasure, retention, chain of custody, consent records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GovernanceTest {

    private lateinit var db: AegisDatabase
    private lateinit var repository: AegisRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AegisDatabase::class.java)
            .addCallback(AegisDatabase.AUDIT_TRIGGERS_CALLBACK)
            .allowMainThreadQueries()
            .build()
        repository = AegisRepository(db.aegisDao())
        FakeGithubApi.requestedUrls.clear()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `audit log chains every entry and verifies intact`() = runTest {
        repository.audit(AuditAction.SCRAPE, "subj-1", "collected 3 artifacts")
        repository.audit(AuditAction.CONSENT_CHANGED, "subj-1", "UNVERIFIED -> SELF via SELF_DECLARED")
        repository.audit(AuditAction.EXPORT, "subj-1", "ROOM_JSON shared")

        val entries = db.aegisDao().allAuditEntriesAsc()
        assertEquals(3, entries.size)
        assertEquals(AuditLogEntity.GENESIS_HASH, entries[0].prevHash)
        assertEquals(entries[0].entryHash, entries[1].prevHash)
        assertEquals(entries[1].entryHash, entries[2].prevHash)

        val status = repository.verifyAuditChain()
        assertTrue(status.intact)
        assertEquals(3, status.entries)
    }

    @Test
    fun `audit log never stores the subject id or handle in readable form`() = runTest {
        repository.audit(AuditAction.SCRAPE, "subj-octo", "collected 3 artifacts")
        val entry = db.aegisDao().lastAuditEntry()!!
        assertEquals(64, entry.subjectRef.length)
        assertFalse(entry.subjectRef.contains("octo"))
        assertEquals(AuditLogEntity.subjectRefFor("subj-octo"), entry.subjectRef)
    }

    @Test
    fun `a forged entry breaks the chain at that entry`() = runTest {
        repository.audit(AuditAction.SCRAPE, "subj-1", "first")
        // Insert a row that does not link to the previous hash (as an attacker editing the file might).
        val forged = AuditLogEntity(
            timestampMillis = 1L,
            action = AuditAction.EXPORT.name,
            subjectRef = "",
            detail = "forged",
            prevHash = AuditLogEntity.GENESIS_HASH,
            entryHash = "f".repeat(64)
        )
        val seq = db.aegisDao().insertAuditEntry(forged)

        val status = repository.verifyAuditChain()
        assertFalse(status.intact)
        assertEquals(seq, status.firstBrokenSeq)
    }

    @Test
    fun `sqlite triggers reject updating or deleting audit rows`() = runTest {
        repository.audit(AuditAction.SCRAPE, "subj-1", "first")
        val sql = db.openHelper.writableDatabase
        assertThrows(Exception::class.java) { sql.execSQL("UPDATE audit_log SET detail = 'edited'") }
        assertThrows(Exception::class.java) { sql.execSQL("DELETE FROM audit_log") }
        assertEquals(1, db.aegisDao().allAuditEntriesAsc().size)
    }

    @Test
    fun `erasing a subject removes every trace except the pseudonymous audit entry`() = runTest {
        repository.ensureSeedData()
        val dao = db.aegisDao()
        val before = dao.observeFullDossierById("subj-alpha").first()!!
        assertTrue(before.rawSocialEntities.isNotEmpty())
        val handle = before.subject.handle

        assertTrue(repository.deleteSubject("subj-alpha", handle, reason = "test"))

        assertNull(dao.observeFullDossierById("subj-alpha").first())
        assertTrue(dao.observeRawSocialEntitiesForSubject("subj-alpha").first().isEmpty())
        assertNull(dao.observeLinguisticProfileForSubject("subj-alpha").first())
        assertTrue(dao.observeMindMapHierarchyForSubject("subj-alpha").first().isEmpty())
        assertTrue(dao.observeTriplets().first().none { it.subjectHandle == handle })

        val last = dao.lastAuditEntry()!!
        assertEquals(AuditAction.SUBJECT_DELETED.name, last.action)
        assertFalse(last.detail.contains(handle))
        assertTrue(repository.verifyAuditChain().intact)
    }

    @Test
    fun `retention purges stale non-demo subjects and keeps demo personas`() = runTest {
        repository.ensureSeedData()
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val stale = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
            .copy(updatedAt = System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000)
        repository.saveProfile(stale, listOf(stale))

        assertEquals(1, repository.purgeExpired(retentionDays = 30))
        assertNull(db.aegisDao().observeFullDossierById(stale.id).first())
        // Demo personas are seeded "now" and are exempt anyway.
        assertTrue(db.aegisDao().observeFullDossierById("subj-alpha").first() != null)
        assertEquals(AuditAction.RETENTION_PURGE.name, db.aegisDao().lastAuditEntry()!!.action)
        assertEquals(0, repository.purgeExpired(retentionDays = 0))
    }

    @Test
    fun `every scraped artifact carries chain of custody and it survives storage`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val profile = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
        assertTrue(profile.scrapedArtifacts.isNotEmpty())
        profile.scrapedArtifacts.forEach { art ->
            assertTrue(art.sourceUrl.startsWith("https://api.github.com/users/${FakeGithubApi.LOGIN}"))
            assertEquals(200, art.httpStatus)
            assertEquals(64, art.rawResponseSha256.length)
            assertTrue(art.fetchedAtMillis > 0)
            assertTrue(art.connectorRunId.startsWith("run-"))
        }

        repository.saveProfile(profile, listOf(profile))
        val stored = db.aegisDao().observeRawSocialEntitiesForSubject(profile.id).first()
        assertTrue(stored.isNotEmpty())
        assertTrue(stored.all { it.rawResponseSha256.length == 64 && it.sourceUrl.isNotBlank() })
    }

    @Test
    fun `expired consent locks restricted outputs again`() = runTest {
        val scraper = OsintScraperService(customClient = FakeGithubApi.client())
        val base = scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
        val now = System.currentTimeMillis()

        val active = scraper.recomputeFromArtifacts(
            base.copy(
                consentStatus = ConsentStatus.CONSENTED,
                consentMethod = ConsentMethod.WRITTEN.name,
                consentRecordedAt = now,
                consentExpiresAt = now + 60_000
            )
        )
        assertTrue(active.restrictedOutputsUnlocked)
        assertTrue(active.linguisticProfile.persuasionAndLeverageHooks.isNotEmpty())

        val expired = active.copy(consentExpiresAt = now - 1)
        assertTrue(expired.consentExpired)
        assertFalse(expired.restrictedOutputsUnlocked)
        assertTrue(ForensicMathEngine.applyConsentGate(expired).linguisticProfile.persuasionAndLeverageHooks.isEmpty())
    }

    @Test
    fun `imported artifacts without a timestamp are not given an invented one`() {
        val art = ScrapedArtifactItem.fromJsonObject(JSONObject("""{"verbatimText":"hello"}"""))
        assertEquals("", art.timestampIso)
        assertFalse(art.hasKnownHour)
    }
}
