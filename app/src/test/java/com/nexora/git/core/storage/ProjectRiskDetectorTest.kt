package com.nexora.git.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRiskDetectorTest {

    private val detector = ProjectRiskDetector()

    @Test
    fun flagsSecretFilenameWhenNotIgnored() {
        val risks = detector.inspect(
            relativePath = ".env",
            sizeBytes = 32,
            ignoredByGit = false,
            readText = {
                "API_KEY=abcdefghijklmnop123456"
            },
        )

        assertTrue(
            risks.any {
                it.type == ProjectRiskType.SECRET &&
                    it.severity == ProjectRiskSeverity.WARNING
            },
        )
    }

    @Test
    fun ignoredSecretBecomesInformational() {
        val risks = detector.inspect(
            relativePath = ".env",
            sizeBytes = 10,
            ignoredByGit = true,
            readText = {
                "password=abcdefghijklmnop"
            },
        )

        assertTrue(risks.isNotEmpty())
        assertTrue(
            risks.all {
                it.severity == ProjectRiskSeverity.INFO
            },
        )
    }

    @Test
    fun moreThan100MiBIsBlockingForNormalGitHubPush() {
        val risks = detector.inspect(
            relativePath = "assets/archive.bin",
            sizeBytes =
                ProjectRiskDetector.GITHUB_HARD_LIMIT_BYTES + 1,
            ignoredByGit = false,
            readText = null,
        )

        assertEquals(1, risks.size)
        assertEquals(
            ProjectRiskSeverity.BLOCKING,
            risks.single().severity,
        )
    }
}
