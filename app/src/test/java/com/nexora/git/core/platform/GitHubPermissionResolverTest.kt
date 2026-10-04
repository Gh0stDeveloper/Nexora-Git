package com.nexora.git.core.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubPermissionResolverTest {

    private val resolver = GitHubPermissionResolver()

    @Test
    fun parsesPermissionAlternatives() {
        val result = resolver.parseAcceptedPermissions(
            "pull_requests=read,contents=read; issues=read,contents=read",
        )

        assertEquals(2, result.alternatives.size)
        assertEquals(
            GitHubPermissionAccess.READ,
            result.alternatives[0].permissions[0].access,
        )
        assertEquals(
            "pull_requests",
            result.alternatives[0].permissions[0].name,
        )
        assertEquals(
            "issues",
            result.alternatives[1].permissions[0].name,
        )
    }
}
