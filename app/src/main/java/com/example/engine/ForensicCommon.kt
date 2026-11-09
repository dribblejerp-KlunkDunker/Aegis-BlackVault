package com.example.engine

import com.example.model.*
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.*

/**
 * Hashing and deterministic STIX identifiers shared by the engines.
 */
object ForensicCommon {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateStixId(type: String, deterministicSeed: String? = null): String {
        val uuid = if (deterministicSeed != null) {
            UUID.nameUUIDFromBytes(deterministicSeed.toByteArray(Charsets.UTF_8)).toString()
        } else {
            UUID.randomUUID().toString()
        }
        return "$type--$uuid"
    }
}
