package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.data.DatabaseEncryption
import com.example.model.AuditAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/**
 * Runs on a real device or emulator (SQLCipher's native library cannot load under Robolectric).
 * Each test uses its own database file so it never touches the app's real dossiers.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AegisDatabase::class.java)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = listOf("enc-fresh.db", "enc-convert.db", "enc-v3.db", "enc-interrupted.db")

    private fun wipe() = names.forEach { name ->
        context.deleteDatabase(name)
        val f = context.getDatabasePath(name)
        listOf(".encrypting", ".plaintext-old").forEach { File(f.path + it).delete() }
    }

    @Before fun setUp() = wipe()
    @After fun tearDown() = wipe()

    private fun open(name: String, passphrase: ByteArray?): AegisDatabase =
        Room.databaseBuilder(context, AegisDatabase::class.java, name)
            .addMigrations(AegisDatabase.MIGRATION_3_4, AegisDatabase.MIGRATION_4_5)
            .addCallback(AegisDatabase.AUDIT_TRIGGERS_CALLBACK)
            .apply { passphrase?.let { openHelperFactory(SupportOpenHelperFactory(it)) } }
            .allowMainThreadQueries()
            .build()

    private fun assertWrongKeyFails(file: File) {
        val wrong = DatabaseEncryption.rawKeyPassphrase(ByteArray(32).also { SecureRandom().nextBytes(it) })
        assertThrows(Exception::class.java) {
            SQLiteDatabase.openDatabase(file.path, wrong, null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master", arrayOf<String>()).use { it.moveToFirst() }
            }
        }
    }

    @Test
    fun freshDatabaseIsEncryptedOnDisk() = runTest {
        val name = "enc-fresh.db"
        val passphrase = DatabaseEncryption.prepare(context, name)!!
        assertEquals(DatabaseEncryption.Status.ENCRYPTED, DatabaseEncryption.status)

        open(name, passphrase).let { db ->
            AegisRepository(db.aegisDao()).ensureSeedData()
            db.close()
        }
        val file = context.getDatabasePath(name)
        assertTrue(file.length() > 0)
        assertFalse("database file must not be readable as plain SQLite", DatabaseEncryption.isPlaintextSqlite(file))
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("NULL_ROUTER"))
        assertWrongKeyFails(file)

        open(name, passphrase).let { db ->
            assertTrue(db.aegisDao().observeFullDossierById("subj-alpha").first() != null)
            db.close()
        }
    }

    @Test
    fun existingPlaintextDatabaseIsConvertedOnceWithEverythingIntact() = runTest {
        val name = "enc-convert.db"
        val before: Int
        open(name, passphrase = null).let { db ->
            val repo = AegisRepository(db.aegisDao())
            repo.ensureSeedData()
            repo.audit(AuditAction.SCRAPE, "subj-alpha", "before encryption")
            before = db.aegisDao().observeAllFullIntelligenceDossiers().first().size
            db.close()
        }
        val file = context.getDatabasePath(name)
        assertTrue(DatabaseEncryption.isPlaintextSqlite(file))

        val passphrase = DatabaseEncryption.prepare(context, name)!!
        assertEquals(DatabaseEncryption.Status.ENCRYPTED, DatabaseEncryption.status)
        assertFalse(DatabaseEncryption.isPlaintextSqlite(file))
        assertFalse(File(file.path + ".encrypting").exists())
        assertFalse(File(file.path + ".plaintext-old").exists())
        assertWrongKeyFails(file)

        open(name, passphrase).let { db ->
            val repo = AegisRepository(db.aegisDao())
            assertEquals(before, db.aegisDao().observeAllFullIntelligenceDossiers().first().size)
            assertEquals("before encryption", db.aegisDao().lastAuditEntry()!!.detail)
            assertTrue(repo.verifyAuditChain().intact)
            assertThrows(Exception::class.java) { db.openHelper.writableDatabase.execSQL("DELETE FROM audit_log") }
            db.close()
        }

        // A second launch must not try to convert again.
        DatabaseEncryption.prepare(context, name)
        open(name, passphrase).let { db ->
            assertEquals(before, db.aegisDao().observeAllFullIntelligenceDossiers().first().size)
            db.close()
        }
    }

    @Test
    fun v3PlaintextInstallIsEncryptedThenMigratedToCurrentSchema() = runTest {
        val name = "enc-v3.db"
        helper.createDatabase(name, 3).use { db ->
            db.execSQL(
                "INSERT INTO knowledge_triplets (id, subjectHandle, predicate, objectNode, nodeCategory, confidence, provSha256, timestamp) " +
                    "VALUES ('t-legacy', 'legacy_handle', 'USES', 'kotlin', 'SKILL', 0.9, '', 1)"
            )
        }
        val passphrase = DatabaseEncryption.prepare(context, name)!!
        assertFalse(DatabaseEncryption.isPlaintextSqlite(context.getDatabasePath(name)))

        open(name, passphrase).let { db ->
            assertTrue(db.aegisDao().observeTriplets().first().any { it.id == "t-legacy" })
            AegisRepository(db.aegisDao()).audit(AuditAction.IMPORT, "subj-x", "after v3 -> v5 upgrade")
            assertEquals(5, db.openHelper.readableDatabase.version)
            db.close()
        }
    }

    @Test
    fun conversionInterruptedBetweenRenamesIsRecovered() = runTest {
        val name = "enc-interrupted.db"
        open(name, passphrase = null).let { db ->
            AegisRepository(db.aegisDao()).ensureSeedData()
            db.close()
        }
        val file = context.getDatabasePath(name)
        // Simulate a kill after the plaintext file was moved aside but before the encrypted copy landed.
        listOf("-wal", "-shm").forEach { File(file.path + it).delete() }
        assertTrue(file.renameTo(File(file.path + ".plaintext-old")))
        File(file.path + ".encrypting").writeText("partial")

        val passphrase = DatabaseEncryption.prepare(context, name)!!
        assertFalse(DatabaseEncryption.isPlaintextSqlite(file))
        assertFalse(File(file.path + ".plaintext-old").exists())
        open(name, passphrase).let { db ->
            assertTrue(db.aegisDao().observeFullDossierById("subj-alpha").first() != null)
            db.close()
        }
    }
}
