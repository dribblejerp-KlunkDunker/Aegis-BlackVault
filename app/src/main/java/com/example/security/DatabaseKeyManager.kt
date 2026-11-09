package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the key that encrypts the dossier database (SQLCipher, AES-256).
 *
 * - The database key is 32 random bytes, generated once on this device.
 * - It is stored only in wrapped form: AES-256-GCM under a separate Android Keystore key
 *   (hardware-backed on most devices). The Keystore key never leaves the Keystore.
 * - Unlike [SecureKeyVault], a value that cannot be unwrapped is **never** cleared or replaced
 *   silently: losing this key means losing the database, so the caller decides what to do
 *   ([KeyUnavailableException]).
 * - The prefs file is excluded from backup and device transfer (res/xml rules).
 */
class DatabaseKeyManager(context: Context) {

    class KeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasStoredKey(): Boolean = prefs.contains(ENTRY)

    /**
     * Removes a wrapped key that can no longer be unwrapped. Only call this after the database it
     * protected has been moved aside, since that file becomes permanently unreadable.
     */
    fun discardUnusableKey() {
        prefs.edit().remove(ENTRY).commit()
    }

    /** Returns the 32-byte database key, creating and storing it on first use. */
    @Throws(KeyUnavailableException::class)
    fun getOrCreateKey(): ByteArray {
        prefs.getString(ENTRY, null)?.let { return unwrap(it) }

        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(key)
        // commit(), not apply(): the key must be on disk before anything is encrypted with it.
        if (!prefs.edit().putString(ENTRY, wrapped).commit()) {
            throw KeyUnavailableException("Could not persist the database key")
        }
        // Read back through the same path the next launch will use.
        val check = unwrap(prefs.getString(ENTRY, null) ?: throw KeyUnavailableException("Database key vanished after write"))
        if (!check.contentEquals(key)) throw KeyUnavailableException("Database key did not round-trip")
        return key
    }

    private fun wrap(key: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected GCM IV length ${iv.size}" }
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun unwrap(stored: String): ByteArray = try {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        require(blob.size > IV_BYTES) { "Stored database key is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            existingWrappingKey() ?: throw KeyUnavailableException("Keystore key for the database is missing"),
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES))
        )
        cipher.updateAAD(AAD)
        cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)).also {
            if (it.size != KEY_BYTES) throw KeyUnavailableException("Stored database key has the wrong length")
        }
    } catch (e: KeyUnavailableException) {
        throw e
    } catch (e: Exception) {
        throw KeyUnavailableException("Could not unwrap the database key", e)
    }

    private fun existingWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun wrappingKey(): SecretKey = existingWrappingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }
        .generateKey()

    companion object {
        /** Wrapped database key; excluded from backup in res/xml rules. */
        const val PREFS_NAME = "aegis_db_key"
        private const val ENTRY = "wrapped_db_key_v1"
        private const val KEY_ALIAS = "aegis_db_wrap_aes_gcm_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val AAD = "aegis-db-key-v1".toByteArray(Charsets.UTF_8)
    }
}
