package com.nexora.git.core.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubRepositoryUrlParserTest {

    private val parser = GitHubRepositoryUrlParser()

    @Test
    fun parsesCanonicalGitHubHttpsUrl() {
        val result = parser.parse(
            "https://github.com/Gh0stDeveloper/Nexora-Git.git",
        )

        assertEquals("Gh0stDeveloper", result?.owner)
        assertEquals("Nexora-Git", result?.name)
    }

    @Test
    fun rejectsCredentialsCustomPortsAndOtherHosts() {
        assertNull(
            parser.parse(
                "https://token@github.com/Gh0stDeveloper/Nexora-Git.git",
            ),
        )
        assertNull(
            parser.parse(
                "https://github.com:8443/Gh0stDeveloper/Nexora-Git.git",
            ),
        )
        assertNull(
            parser.parse(
                "https://example.com/Gh0stDeveloper/Nexora-Git.git",
            ),
        )
    }

    @Test
    fun rejectsExtraPathSegments() {
        assertNull(
            parser.parse(
                "https://github.com/Gh0stDeveloper/Nexora-Git/tree/main",
            ),
        )
    }
}
