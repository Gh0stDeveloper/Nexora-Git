package com.nexora.git.core.platform

import com.nexora.git.core.common.AppError
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubApiErrorMapperTest {

    private val mapper = GitHubApiErrorMapper(
        GitHubPermissionResolver(),
    )

    @Test
    fun mapsPermission403WithAcceptedPermissions() {
        val headers = Headers.Builder()
            .add("X-Accepted-GitHub-Permissions", "contents=write")
            .build()

        val result = mapper.fromHttp(
            statusCode = 403,
            headers = headers,
            body = """{"message":"Resource not accessible"}""",
        )

        assertTrue(result is AppError.PermissionDenied)
        val permission = (result as AppError.PermissionDenied)
            .acceptedPermissions
            .alternatives
            .single()
            .permissions
            .single()

        assertEquals("contents", permission.name)
        assertEquals(GitHubPermissionAccess.WRITE, permission.access)
    }

    @Test
    fun mapsPrimaryRateLimit403() {
        val headers = Headers.Builder()
            .add("X-RateLimit-Remaining", "0")
            .add("X-RateLimit-Reset", "1234")
            .build()

        val result = mapper.fromHttp(
            statusCode = 403,
            headers = headers,
            body = """{"message":"API rate limit exceeded"}""",
        )

        assertTrue(result is AppError.RateLimited)
        assertEquals(
            1234L,
            (result as AppError.RateLimited).resetAtEpochSeconds,
        )
    }

    @Test
    fun mapsSecondaryRateLimit429() {
        val headers = Headers.Builder()
            .add("Retry-After", "60")
            .build()

        val result = mapper.fromHttp(
            statusCode = 429,
            headers = headers,
            body = null,
        )

        assertTrue(result is AppError.RateLimited)
        assertTrue((result as AppError.RateLimited).secondary)
        assertEquals(60L, result.retryAfterSeconds)
    }
}
