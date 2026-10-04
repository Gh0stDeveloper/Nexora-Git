package com.nexora.git.core.git

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class GitHubGitCredentialProviderTest {

    private val provider: GitHubGitCredentialProvider =
        allocateWithoutConstructor()

    @Test
    fun allowsOnlyGitHubHttpsWithoutEmbeddedCredentials() {
        assertTrue(
            provider.isAuthorizedGitHubHttpsUrl(
                "https://github.com/owner/repo.git",
            ),
        )

        assertFalse(
            provider.isAuthorizedGitHubHttpsUrl(
                "http://github.com/owner/repo.git",
            ),
        )

        assertFalse(
            provider.isAuthorizedGitHubHttpsUrl(
                "https://evil.example/owner/repo.git",
            ),
        )

        assertFalse(
            provider.isAuthorizedGitHubHttpsUrl(
                "https://token@github.com/owner/repo.git",
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as Unsafe
        return unsafe.allocateInstance(
            GitHubGitCredentialProvider::class.java,
        ) as T
    }
}
