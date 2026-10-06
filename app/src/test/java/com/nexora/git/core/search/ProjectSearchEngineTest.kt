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
                "val Nexora = 1\\nprintln(Nexora)\\n",
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
            assertFalse(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun supportsRegexWholeWordAndPathGlobs() = runBlocking {
        val root = Files.createTempDirectory("nexora-search").toFile()
        try {
            root.resolve("src").mkdirs()
            root.resolve("docs").mkdirs()
            root.resolve("src/Main.kt").writeText(
                "cat catalog cat\\n",
            )
            root.resolve("docs/readme.md").writeText(
                "cat\\n",
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
