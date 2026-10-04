package com.nexora.git.core.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class PkcePair(
    val verifier: String,
    val challenge: String,
)

@Singleton
class PkceGenerator @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun generate(): PkcePair {
        val verifier = randomUrlSafeString(32)
        return PkcePair(
            verifier = verifier,
            challenge = challengeFor(verifier),
        )
    }

    fun generateState(): String = randomUrlSafeString(32)

    fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return base64Url(digest)
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
}
