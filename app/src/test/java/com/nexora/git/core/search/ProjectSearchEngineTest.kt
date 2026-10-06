package com.nexora.git.core.search

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSearchEngineTest {

    private val engine = ProjectSearchEngine()

    @Test
    fun searchesTextAndSkipsGeneratedDirectories() = runBlocking {
        val root = Files.createTempDirectory("nexora-search").toFile()
        try {
            root.resolve("src").mkdirs()
            root.resolve("src/Main.kt").writeText(
                "val Nexora = 1\nprintln(Nexora)\n",
            )
            root.resolve("node_modules").mkdirs()
            root.resolve("node_modules/hidden.js").writeText(
                "Nexora",
            )

            val result = engine.search(
                workspacePath = root.path,
                query = ProjectSearchQuery(
                    text = "Nexora",
                    matchCase = true,
                ),
            )

            assertEquals(2, result.matches.size)
            assertTrue(
                result.matches.all {
                    it.path == "src/Main.kt"
                },
            )
            assertEquals(listOf(1, 2), result.matches.map { it.line })
            assertEquals(listOf(5, 9), result.matches.map { it.column })
            assertFalse(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotTraverseSymbolicDirectories() = runBlocking {
        val root = Files.createTempDirectory("nexora-search-root").toFile()
        val outside = Files.createTempDirectory("nexora-search-outside").toFile()
        try {
            outside.resolve("secret.txt").writeText("Nexora")
            val link = root.toPath().resolve("linked")
            val linked = runCatching {
                Files.createSymbolicLink(link, outside.toPath())
            }.isSuccess
            if (!linked) return@runBlocking

            val result = engine.search(
                workspacePath = root.path,
                query = ProjectSearchQuery(text = "Nexora"),
            )

            assertTrue(result.matches.isEmpty())
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun supportsRegexWholeWordAndPathGlobs() = runBlocking {
        val root = Files.createTempDirectory("nexora-search").toFile()
        try {
            root.resolve("src").mkdirs()
            root.resolve("docs").mkdirs()
            root.resolve("src/Main.kt").writeText(
                "cat catalog cat\n",
            )
            root.resolve("docs/readme.md").writeText(
                "cat\n",
            )

            val result = engine.search(
                workspacePath = root.path,
                query = ProjectSearchQuery(
                    text = "c.t",
                    regex = true,
                    wholeWord = true,
                    includeGlob = "src/**",
                ),
            )

            assertEquals(2, result.matches.size)
            assertTrue(
                result.matches.all {
                    it.path == "src/Main.kt"
                },
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
