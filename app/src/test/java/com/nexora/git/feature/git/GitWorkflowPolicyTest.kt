package com.nexora.git.feature.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitWorkflowPolicyTest {

    @Test
    fun pushRefspec_isExplicitAndNeverForcePrefixed() {
        assertEquals(
            "refs/heads/main:refs/heads/release",
            GitWorkflowPolicy.pushRefspec(
                currentBranch = "main",
                targetBranch = "release",
            ),
        )
    }

    @Test
    fun unsafeBranchNames_areRejected() {
        assertFalse(
            GitWorkflowPolicy.isSafeBranchName("+force"),
        )
        assertFalse(
            GitWorkflowPolicy.isSafeBranchName("feature..broken"),
        )
        assertFalse(
            GitWorkflowPolicy.isSafeBranchName("refs bad"),
        )
        assertTrue(
            GitWorkflowPolicy.isSafeBranchName("feat/mobile-git"),
        )
    }

    @Test
    fun remotePolicy_rejectsCredentialsAndNonGithubHosts() {
        assertEquals(
            "origin",
            GitWorkflowPolicy.normalizeRemoteName(" origin "),
        )
        assertEquals(
            "https://github.com/example/project.git",
            GitWorkflowPolicy.normalizeGitHubRemoteUrl(
                " https://github.com/example/project.git ",
            ),
        )

        assertFalse(
            runCatching {
                GitWorkflowPolicy.normalizeGitHubRemoteUrl(
                    "https://token@github.com/example/project.git",
                )
            }.isSuccess,
        )
        assertFalse(
            runCatching {
                GitWorkflowPolicy.normalizeGitHubRemoteUrl(
                    "http://github.com/example/project.git",
                )
            }.isSuccess,
        )
        assertFalse(
            GitWorkflowPolicy.isSafeRemoteName("bad remote"),
        )
    }

    @Test
    fun conflictMarkers_requireAllStandardMarkers() {
        assertTrue(
            GitWorkflowPolicy.hasConflictMarkers(
                "<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>> origin/main",
            ),
        )
        assertFalse(
            GitWorkflowPolicy.hasConflictMarkers(
                "resolved content",
            ),
        )
    }
}
