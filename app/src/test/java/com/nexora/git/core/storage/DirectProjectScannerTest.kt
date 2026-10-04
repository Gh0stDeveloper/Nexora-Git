package com.nexora.git.core.storage

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectProjectScannerTest {

    @Test
    fun scanFindsProjectTypeIgnoreStateAndSecretRisk() = runBlocking {
        val root = Files.createTempDirectory("nexora-storage-test")
            .toFile()

        try {
            root.resolve("settings.gradle.kts")
                .writeText("rootProject.name = \"Sample\"")
            root.resolve(".gitignore")
                .writeText(".env\nbuild/\n")
            root.resolve(".env")
                .writeText("TOKEN=abcdefghijklmnop1234")
            root.resolve("README.md")
                .writeText("# Sample")
            root.resolve("build").mkdirs()
            root.resolve("build/generated.txt")
                .writeText("generated")

            val scanner = DirectProjectScanner(
                gitIgnoreMatcher = GitIgnoreMatcher(),
                riskDetector = ProjectRiskDetector(),
            )

            val result = scanner.scan(root)

            assertTrue("Gradle" in result.detectedProjectTypes)
            assertEquals(1, result.gitIgnoreFiles)
            assertTrue(
                result.files.first {
                    it.relativePath == ".env"
                }.ignoredByGit,
            )
            assertTrue(
                result.files.first {
                    it.relativePath == "build/generated.txt"
                }.ignoredByGit,
            )
            assertTrue(result.secretWarningCount >= 1)
        } finally {
            root.deleteRecursively()
        }
    }
}
