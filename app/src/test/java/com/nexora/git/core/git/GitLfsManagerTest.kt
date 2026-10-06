package com.nexora.git.core.git

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitLfsManagerTest {
    private val manager = GitLfsManager()

    @Test
    fun tracksAndUntracksPatternsWithoutDuplicatingRules() {
        val root = Files.createTempDirectory("nexora-lfs").toFile()
        try {
            java.io.File(root, ".git").mkdirs()

            manager.track(root.path, "*.psd")
            manager.track(root.path, "*.psd")

            val tracked = manager.inspect(root.path)
            assertEquals(listOf("*.psd"), tracked.trackedPatterns)
            assertFalse(tracked.transferSupported)

            val attributes = java.io.File(root, ".gitattributes")
                .readText()
            assertEquals(
                1,
                attributes.lines()
                    .count { it.startsWith("*.psd ") },
            )

            val untracked = manager.untrack(root.path, "*.psd")
            assertTrue(untracked.trackedPatterns.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun detectsStandardLfsPointerFiles() {
        val root = Files.createTempDirectory("nexora-lfs-pointer").toFile()
        try {
            java.io.File(root, ".git").mkdirs()
            java.io.File(root, "asset.bin").writeText(
                """
                version https://git-lfs.github.com/spec/v1
                oid sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
                size 123456
                """.trimIndent() + "\n",
            )

            val state = manager.inspect(root.path)

            assertEquals(1, state.pointers.size)
            assertEquals("asset.bin", state.pointers.single().path)
            assertEquals(123456L, state.pointers.single().sizeBytes)
        } finally {
            root.deleteRecursively()
        }
    }
}
