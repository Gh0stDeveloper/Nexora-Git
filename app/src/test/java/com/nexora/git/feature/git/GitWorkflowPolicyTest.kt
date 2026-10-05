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
