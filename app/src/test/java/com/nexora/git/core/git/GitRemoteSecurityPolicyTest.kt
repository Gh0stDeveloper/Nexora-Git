package com.nexora.git.core.git

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitRemoteSecurityPolicyTest {

    private val policy = GitRemoteSecurityPolicy()

    @Test
    fun allowsOfficialGitHubHttpsRemote() {
        assertTrue(
            policy.allowsGitHubOAuthCredentials(
                "https://github.com/owner/repo.git",
            ),
        )
    }

    @Test
    fun rejectsNonHttpsAndNonGitHubRemotes() {
        assertFalse(
            policy.allowsGitHubOAuthCredentials(
                "http://github.com/owner/repo.git",
            ),
        )
        assertFalse(
            policy.allowsGitHubOAuthCredentials(
                "https://evil.example/owner/repo.git",
            ),
        )
        assertFalse(
            policy.allowsGitHubOAuthCredentials(
                "ssh://git@github.com/owner/repo.git",
            ),
        )
    }

    @Test
    fun rejectsEmbeddedCredentialsAndCustomPorts() {
        assertFalse(
            policy.allowsGitHubOAuthCredentials(
                "https://token@github.com/owner/repo.git",
            ),
        )
        assertFalse(
            policy.allowsGitHubOAuthCredentials(
                "https://github.com:8443/owner/repo.git",
            ),
        )
    }
}
