package com.nexora.git.core.platform

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRateLimitManagerTest {

    @Test
    fun tracksResourceAndExhaustion() {
        val manager = GitHubRateLimitManager()
        val headers = Headers.Builder()
            .add("X-RateLimit-Resource", "core")
            .add("X-RateLimit-Limit", "5000")
            .add("X-RateLimit-Remaining", "0")
            .add("X-RateLimit-Used", "5000")
            .add("X-RateLimit-Reset", "2000")
            .build()

        val result = manager.updateFromHeaders(
            headers = headers,
            fallbackResource = "core",
            nowEpochMillis = 1_000_000L,
        )

        assertEquals(5000L, result?.limit)
        assertEquals(0L, result?.remaining)
        assertFalse(manager.mayRequest("core", nowEpochMillis = 1_500_000L))
        assertTrue(manager.mayRequest("core", nowEpochMillis = 2_000_000L))
    }

    @Test
    fun retryAfterTakesPriorityOverPrimaryReset() {
        val manager = GitHubRateLimitManager()
        val headers = Headers.Builder()
            .add("X-RateLimit-Remaining", "0")
            .add("X-RateLimit-Reset", "10")
            .add("Retry-After", "30")
            .build()

        val result = manager.updateFromHeaders(
            headers = headers,
            fallbackResource = "graphql",
            nowEpochMillis = 5_000L,
        )

        assertEquals(35_000L, result?.retryAtEpochMillis())
    }
}
