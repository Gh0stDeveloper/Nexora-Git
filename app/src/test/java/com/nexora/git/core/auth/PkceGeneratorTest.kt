package com.nexora.git.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceGeneratorTest {

    private val generator = PkceGenerator()

    @Test
    fun challengeFor_matchesRfc7636Example() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        val challenge = generator.challengeFor(verifier)

        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            challenge,
        )
    }

    @Test
    fun generatedPair_isUrlSafeAndUsesS256Length() {
        val pair = generator.generate()

        assertEquals(43, pair.verifier.length)
        assertEquals(43, pair.challenge.length)
        assertTrue(pair.verifier.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(pair.challenge.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun state_isCryptographicallySizedAndUrlSafe() {
        val first = generator.generateState()
        val second = generator.generateState()

        assertEquals(43, first.length)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(first != second)
    }
}
