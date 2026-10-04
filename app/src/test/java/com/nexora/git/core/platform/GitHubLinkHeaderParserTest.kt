package com.nexora.git.core.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubLinkHeaderParserTest {

    private val parser = GitHubLinkHeaderParser()

    @Test
    fun parsesGitHubPaginationRelations() {
        val header =
            "<https://api.github.com/repositories/1/issues?page=2>; rel=\"next\", " +
                "<https://api.github.com/repositories/1/issues?page=5>; rel=\"last\""

        val result = parser.parse(header)

        assertEquals(
            "https://api.github.com/repositories/1/issues?page=2",
            result.nextUrl,
        )
        assertEquals(
            "https://api.github.com/repositories/1/issues?page=5",
            result.lastUrl,
        )
        assertNull(result.previousUrl)
    }

    @Test
    fun ignoresNonGitHubPaginationTargets() {
        val result = parser.parse(
            "<https://example.com/page=2>; rel=\"next\"",
        )

        assertNull(result.nextUrl)
    }
}
