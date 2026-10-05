package com.nexora.git.core.files

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspacePathPolicyTest {

    private val policy = WorkspacePathPolicy()

    @Test
    fun resolvesSafeNestedPath() {
        val root = Files.createTempDirectory("nexora-browser-root")
            .toFile()

        try {
            val nested = root.resolve("src/main")
            nested.mkdirs()

            val resolved = policy.resolve(
                root = root,
                relativePath = "src/main",
            )

            assertEquals(
                nested.canonicalPath,
                resolved.canonicalPath,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversalAndGitMetadata() {
        val root = Files.createTempDirectory("nexora-browser-root")
            .toFile()

        try {
            assertThrows(IllegalArgumentException::class.java) {
                policy.resolve(
                    root = root,
                    relativePath = "../outside.txt",
                )
            }

            assertThrows(IllegalArgumentException::class.java) {
                policy.resolve(
                    root = root,
                    relativePath = ".git/config",
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkThatEscapesWorkspaceWhenSupported() {
        val root = Files.createTempDirectory("nexora-browser-root")
            .toFile()
        val outside = Files.createTempDirectory("nexora-browser-outside")
            .toFile()

        try {
            val link = root.toPath().resolve("escape")
            val created = runCatching {
                Files.createSymbolicLink(
                    link,
                    outside.toPath(),
                )
            }.isSuccess

            if (!created) {
                return
            }

            assertThrows(IllegalArgumentException::class.java) {
                policy.resolve(
                    root = root,
                    relativePath = "escape",
                )
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
