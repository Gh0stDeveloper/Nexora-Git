package com.nexora.git.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEndpointPolicyTest {

    @Test
    fun acceptsExactHttpsBrokerCallbackPair() {
        assertTrue(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com",
                githubCallbackUrl = "https://auth.example.com/oauth/callback",
            ),
        )
    }

    @Test
    fun acceptsExplicitMatchingHttpsPort() {
        assertTrue(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com:8443",
                githubCallbackUrl = "https://auth.example.com:8443/oauth/callback",
            ),
        )
    }

    @Test
    fun rejectsDifferentOriginOrUnexpectedCallbackShape() {
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com",
                githubCallbackUrl = "https://evil.example.com/oauth/callback",
            ),
        )
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com",
                githubCallbackUrl = "https://auth.example.com/other",
            ),
        )
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "http://auth.example.com",
                githubCallbackUrl = "http://auth.example.com/oauth/callback",
            ),
        )
        assertFalse(
            isValidAuthEndpointPair(
                brokerBaseUrl = "https://auth.example.com?redirect=evil",
                githubCallbackUrl = "https://auth.example.com/oauth/callback",
            ),
        )
    }
}
