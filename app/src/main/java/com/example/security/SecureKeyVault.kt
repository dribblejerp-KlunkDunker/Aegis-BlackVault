package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted store for user-entered API keys and tokens.
 *
 * - Values are encrypted with AES-256-GCM using a key held in the Android Keystore
 *   (hardware-backed on most devices); the key material never leaves the Keystore.
 * - Only ciphertext (IV + GCM output, Base64) is written to SharedPreferences.
 * - The entry name is bound as GCM associated data, so ciphertext copied between
 *   entries fails to decrypt instead of silently swapping keys.
 * - The prefs file is excluded from cloud backup and device transfer (see
 *   res/xml/backup_rules.xml and data_extraction_rules.xml). Even if it were copied,
 *   the Keystore key does not travel with it.
 *
 * Replaces the previous plaintext storage in "aegis_ai_model_vault" (EncryptedSharedPreferences
 * is deprecated, so this uses the platform Keystore directly with no extra dependency).
 */
class SecureKeyVault(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(name: String): String {
        val stored = prefs.getString(ENTRY_PREFIX + name, null) ?: return ""
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            if (blob.size <= IV_BYTES) return ""
            val iv = blob.copyOfRange(0, IV_BYTES)
            val ct = blob.copyOfRange(IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated (e.g. device restore) or tampered value: drop it rather than crash.
            Log.w(TAG, "Could not decrypt vault entry '$name'; clearing it.", e)
            prefs.edit { remove(ENTRY_PREFIX + name) }
            ""
        }
    }

    fun put(name: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            prefs.edit { remove(ENTRY_PREFIX + name) }
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            val ct = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            check(iv.size == IV_BYTES) { "Unexpected GCM IV length ${iv.size}" }
            prefs.edit { putString(ENTRY_PREFIX + name, Base64.encodeToString(iv + ct, Base64.NO_WRAP)) }
        } catch (e: Exception) {
            // Never fall back to plaintext.
            Log.e(TAG, "Could not encrypt vault entry '$name'; value was NOT saved.", e)
        }
    }

    fun remove(name: String) {
        prefs.edit { remove(ENTRY_PREFIX + name) }
    }

    /**
     * One-time migration: moves plaintext keys from the legacy prefs file into the encrypted vault
     * and deletes the plaintext copies.
     */
    fun migrateLegacyPlaintext(legacyPrefs: SharedPreferences, legacyKeyToVaultName: Map<String, String>) {
        legacyKeyToVaultName.forEach { (legacyKey, vaultName) ->
            val plaintext = legacyPrefs.getString(legacyKey, null)
            if (!plaintext.isNullOrBlank() && get(vaultName).isEmpty()) {
                put(vaultName, plaintext)
            }
            if (legacyPrefs.contains(legacyKey)) {
                legacyPrefs.edit { remove(legacyKey) }
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Keystore must generate the IV; callers cannot supply their own for encryption.
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "SecureKeyVault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aegis_vault_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val ENTRY_PREFIX = "enc_"

        /** Encrypted prefs file name; excluded from backup in res/xml rules. */
        const val PREFS_NAME = "aegis_secure_vault"

        const val GITHUB_TOKEN = "GITHUB_TOKEN"
    }
}
