package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Encryption at rest for the dossier database (SQLCipher 4, AES-256, 4 KiB pages, HMAC-SHA512).
 *
 * [prepare] runs before Room opens the file:
 *  1. loads the SQLCipher native library;
 *  2. gets the device-bound database key from [DatabaseKeyManager];
 *  3. finishes or rolls back an interrupted conversion from an earlier launch;
 *  4. converts an existing plaintext database (v3.x installs) to an encrypted one, once, and checks the
 *     encrypted copy opens and holds the same rows before the plaintext file is removed.
 *
 * If conversion fails, the plaintext file is left untouched and the app keeps working unencrypted;
 * conversion is retried on the next launch and [status] says so. A file that cannot be decrypted
 * because the key is gone is moved aside (never deleted) and a new, empty database is started.
 *
 * Limits, stated plainly: deleting the old plaintext file does not guarantee the flash blocks are
 * wiped; on a rooted device or with the screen unlocked, an attacker with code execution in the app
 * can still read the data. This protects a powered-off or locked device and copied app data.
 */
object DatabaseEncryption {

    enum class Status(val label: String) {
        ENCRYPTED("Encrypted at rest (SQLCipher AES-256, device-bound key)"),
        PLAINTEXT_PENDING("NOT encrypted yet: conversion failed, will retry on next launch"),
        NOT_IN_THIS_ENVIRONMENT("Not encrypted: JVM test environment (no native SQLCipher)"),
        UNKNOWN("Not opened yet")
    }

    private const val TAG = "DatabaseEncryption"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private const val ENCRYPTING_SUFFIX = ".encrypting"
    private const val PLAINTEXT_BACKUP_SUFFIX = ".plaintext-old"

    @Volatile
    var status: Status = Status.UNKNOWN
        private set

    /** Why the last plaintext-to-encrypted conversion failed, if it did (shown in logs and tests). */
    @Volatile
    var lastConversionError: Throwable? = null
        private set

    @Volatile
    private var nativeLoaded = false

    /** Robolectric runs on the JVM, where SQLCipher's Android native library cannot load. */
    val isJvmTestEnvironment: Boolean
        get() = Build.FINGERPRINT == "robolectric"

    fun loadNativeLibrary() {
        if (!nativeLoaded) synchronized(this) {
            if (!nativeLoaded) {
                System.loadLibrary("sqlcipher")
                nativeLoaded = true
            }
        }
    }

    /** True if [file] starts with the plaintext SQLite header. Encrypted files look like random bytes. */
    fun isPlaintextSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val head = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { input ->
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) return false
                read += n
            }
        }
        return head.contentEquals(SQLITE_HEADER)
    }

    /** SQLCipher raw-key form (skips the passphrase KDF; the key is already 256 random bits). */
    fun rawKeyPassphrase(key: ByteArray): ByteArray =
        ("x'" + key.joinToString("") { "%02X".format(it) } + "'").toByteArray(Charsets.US_ASCII)

    /**
     * Returns the passphrase Room should open [dbName] with, or null if the database must be opened
     * without encryption this time (see [status]).
     */
    fun prepare(context: Context, dbName: String): ByteArray? {
        if (isJvmTestEnvironment) {
            status = Status.NOT_IN_THIS_ENVIRONMENT
            return null
        }
        loadNativeLibrary()
        val dbFile = context.getDatabasePath(dbName)
        val keys = DatabaseKeyManager(context)

        val key = try {
            keys.getOrCreateKey()
        } catch (e: DatabaseKeyManager.KeyUnavailableException) {
            // The wrapped key exists but cannot be unwrapped (Keystore reset, restored app data).
            // Without it the encrypted file is unreadable; keep it aside rather than delete it.
            Log.e(TAG, "Database key unavailable; moving the unreadable database aside", e)
            if (dbFile.exists() && !isPlaintextSqlite(dbFile)) quarantine(dbFile)
            keys.discardUnusableKey()
            keys.getOrCreateKey()
        }
        val passphrase = rawKeyPassphrase(key)
        key.fill(0)

        recoverInterruptedConversion(dbFile)

        if (isPlaintextSqlite(dbFile)) {
            try {
                encryptInPlace(dbFile, passphrase)
                lastConversionError = null
                Log.i(TAG, "Converted the plaintext database to SQLCipher")
            } catch (e: Exception) {
                lastConversionError = e
                Log.e(TAG, "Encrypting the existing database failed; keeping it as is for now", e)
                File(dbFile.path + ENCRYPTING_SUFFIX).delete()
                status = Status.PLAINTEXT_PENDING
                return null
            }
        }
        status = Status.ENCRYPTED
        return passphrase
    }

    /**
     * Writes an encrypted copy next to [dbFile], verifies it, then swaps it in.
     * The plaintext original is only removed after the encrypted copy is in place.
     *
     * The copy is done statement by statement (schema from sqlite_master, then rows, then triggers)
     * rather than with ATTACH + sqlcipher_export, whose attachment did not survive across statements
     * in SQLCipher for Android's connection pool. Reading uses the platform SQLite, which also folds in
     * any pending WAL content.
     */
    fun encryptInPlace(dbFile: File, passphrase: ByteArray) {
        loadNativeLibrary()
        val tmp = File(dbFile.path + ENCRYPTING_SUFFIX)
        tmp.delete()
        sidecars(tmp).forEach { it.delete() }

        val plain = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        val expected: Map<String, Long>
        val userVersion: Int
        try {
            userVersion = plain.version
            expected = rowCounts { sql -> plain.rawQuery(sql, null) }
            val enc = SQLiteDatabase.openOrCreateDatabase(tmp, passphrase, null, null, null)
            try {
                copyInto(plain, enc)
                enc.version = userVersion
            } finally {
                enc.close()
            }
        } finally {
            plain.close()
        }

        // Verify the copy with the same key path Room will use, before touching the original.
        val enc = SQLiteDatabase.openDatabase(tmp.path, passphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null)
        try {
            check(enc.version == userVersion) { "Encrypted copy has user_version ${enc.version}, expected $userVersion" }
            val actual = rowCounts { sql -> enc.rawQuery(sql, arrayOf<String>()) }
            check(actual == expected) { "Encrypted copy row counts differ: $actual vs $expected" }
        } finally {
            enc.close()
        }
        check(!isPlaintextSqlite(tmp)) { "Encrypted copy still has a plaintext header" }

        val backup = File(dbFile.path + PLAINTEXT_BACKUP_SUFFIX)
        backup.delete()
        sidecars(dbFile).forEach { it.delete() }
        sidecars(tmp).forEach { it.delete() }
        check(dbFile.renameTo(backup)) { "Could not move the plaintext database aside" }
        check(tmp.renameTo(dbFile)) { "Could not move the encrypted database into place" }
        backup.delete()
        sidecars(backup).forEach { it.delete() }
    }

    /** Tables the platform or SQLCipher create on their own; never copied or counted. */
    private val INTERNAL_TABLES = setOf("android_metadata")

    private fun copyInto(plain: android.database.sqlite.SQLiteDatabase, enc: SQLiteDatabase) {
        data class SchemaObject(val type: String, val name: String, val table: String, val sql: String)
        val objects = mutableListOf<SchemaObject>()
        plain.rawQuery(
            "SELECT type, name, tbl_name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'",
            null
        ).use { c ->
            while (c.moveToNext()) objects += SchemaObject(c.getString(0), c.getString(1), c.getString(2), c.getString(3))
        }
        val tables = objects.filter { it.type == "table" && it.name !in INTERNAL_TABLES }

        enc.beginTransaction()
        try {
            tables.forEach { enc.execSQL(it.sql) }
            for (t in tables) {
                plain.rawQuery("SELECT * FROM `${t.name.replace("`", "``")}`", null).use { c ->
                    val columns = c.columnNames
                    while (c.moveToNext()) {
                        val values = android.content.ContentValues(columns.size)
                        for (i in columns.indices) {
                            when (c.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_NULL -> values.putNull(columns[i])
                                android.database.Cursor.FIELD_TYPE_INTEGER -> values.put(columns[i], c.getLong(i))
                                android.database.Cursor.FIELD_TYPE_FLOAT -> values.put(columns[i], c.getDouble(i))
                                android.database.Cursor.FIELD_TYPE_BLOB -> values.put(columns[i], c.getBlob(i))
                                else -> values.put(columns[i], c.getString(i))
                            }
                        }
                        enc.insertOrThrow(t.name, null, values)
                    }
                }
            }
            // AUTOINCREMENT counters, so the next audit_log seq continues where it left off.
            val hasSequence = plain.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'", null
            ).use { it.moveToFirst() }
            if (hasSequence) {
                plain.rawQuery("SELECT name, seq FROM sqlite_sequence", null).use { c ->
                    while (c.moveToNext()) {
                        enc.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf<Any>(c.getString(0)))
                        enc.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", arrayOf<Any>(c.getString(0), c.getLong(1)))
                    }
                }
            }
            // Indexes, triggers and views last: triggers must not fire while rows are copied.
            objects.filter { it.type != "table" && it.table !in INTERNAL_TABLES }.forEach { enc.execSQL(it.sql) }
            enc.setTransactionSuccessful()
        } finally {
            enc.endTransaction()
        }
    }

    /** Completes or undoes a swap that a crash or kill interrupted. */
    private fun recoverInterruptedConversion(dbFile: File) {
        val tmp = File(dbFile.path + ENCRYPTING_SUFFIX)
        val backup = File(dbFile.path + PLAINTEXT_BACKUP_SUFFIX)
        when {
            // Killed between the two renames: the plaintext original is in the backup slot. Restore it
            // and convert again from scratch.
            !dbFile.exists() && backup.exists() -> {
                tmp.delete()
                backup.renameTo(dbFile)
            }
            // Swap finished but the plaintext backup was not removed yet.
            dbFile.exists() && !isPlaintextSqlite(dbFile) && backup.exists() -> backup.delete()
            // Killed while writing the copy: discard the partial copy.
            else -> tmp.delete()
        }
    }

    private fun quarantine(dbFile: File) {
        val stamp = System.currentTimeMillis()
        (listOf(dbFile) + sidecars(dbFile)).filter { it.exists() }.forEach {
            it.renameTo(File(it.path + ".unreadable-$stamp"))
        }
    }

    private fun sidecars(file: File) = listOf("-wal", "-shm", "-journal").map { File(file.path + it) }

    private fun rowCounts(query: (String) -> android.database.Cursor): Map<String, Long> {
        val tables = mutableListOf<String>()
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        return tables.filter { it !in INTERNAL_TABLES }.sorted().associateWith { table ->
            query("SELECT COUNT(*) FROM `${table.replace("`", "``")}`").use { c ->
                c.moveToFirst(); c.getLong(0)
            }
        }
    }
}
