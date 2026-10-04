package com.nexora.git.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAuthorizationValidatorTest {

    private val validator = PendingAuthorizationValidator()

    @Test
    fun matchingStateWithinWindow_isAccepted() {
        val pending = PendingAuthorization(
            state = "expected-state",
            codeVerifier = "verifier",
            createdAtEpochMillis = 1_000L,
        )

        val result = validator.validate(
            pending = pending,
            returnedState = "expected-state",
            nowEpochMillis = 2_000L,
        )

        assertNull(result)
    }

    @Test
    fun mismatchedState_isRejected() {
        val pending = PendingAuthorization(
            state = "expected-state",
            codeVerifier = "verifier",
            createdAtEpochMillis = 1_000L,
        )

        val result = validator.validate(
            pending = pending,
            returnedState = "attacker-state",
            nowEpochMillis = 2_000L,
        )

        assertEquals(AuthFailure.STATE_MISMATCH, result)
    }

    @Test
    fun oldAuthorization_isRejected() {
        val pending = PendingAuthorization(
            state = "expected-state",
            codeVerifier = "verifier",
            createdAtEpochMillis = 1_000L,
        )

        val result = validator.validate(
            pending = pending,
            returnedState = "expected-state",
            nowEpochMillis = 1_000L + PendingAuthorizationValidator.AUTH_WINDOW_MS + 1L,
        )

        assertEquals(AuthFailure.AUTHORIZATION_EXPIRED, result)
    }

    @Test
    fun futureTimestamp_isRejected() {
        val pending = PendingAuthorization(
            state = "expected-state",
            codeVerifier = "verifier",
            createdAtEpochMillis = 10_000L,
        )

        val result = validator.validate(
            pending = pending,
            returnedState = "expected-state",
            nowEpochMillis = 9_999L,
        )

        assertEquals(AuthFailure.AUTHORIZATION_EXPIRED, result)
    }
}
