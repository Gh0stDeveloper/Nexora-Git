package com.nexora.git.core.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubUrlResolverTest {

    private val resolver = GitHubUrlResolver()

    @Test
    fun resolvesRelativePathAndQuery() {
        val result = resolver.resolve(
            pathOrUrl = "/user/repos",
            query = mapOf(
                "per_page" to "100",
                "page" to "2",
            ),
        )

        assertEquals("api.github.com", result.host)
        assertEquals("/user/repos", result.encodedPath)
        assertEquals("100", result.queryParameter("per_page"))
        assertEquals("2", result.queryParameter("page"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExternalAbsoluteUrl() {
        resolver.resolve(
            pathOrUrl = "https://example.com/steal",
            query = emptyMap(),
        )
    }
}
