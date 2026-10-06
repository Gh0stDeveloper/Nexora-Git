package com.nexora.git.core.editor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFormatterTest {

    private val formatter = EditorFormatter()

    @Test
    fun formatsJsonDeterministically() = runBlocking {
        val result = formatter.format(
            fileName = "package.json",
            language = "JSON",
            text = """{"name":"nexora","private":true}""",
            indentStyle = EditorIndentStyle.SPACES_2,
            syntax = null,
        )

        assertTrue(result.changed)
        assertTrue(result.text.contains("\n"))
        assertTrue(result.text.contains("  \"name\": \"nexora\""))
        assertEquals("JSON", result.formatter)
    }

    @Test
    fun structuredFormatterDoesNotReindentProtectedString() = runBlocking {
        val source = "fun main() {\n\"  keep { this }\"\nprintln(1)\n}\n"
        val stringStart = source.indexOf('"')
        val stringEnd = source.indexOf('"', stringStart + 1) + 1
        val syntax = EditorSyntaxSnapshot(
            engine = "tree-sitter",
            language = "Kotlin",
            rootType = "source_file",
            hasErrors = false,
            truncated = false,
            spans = listOf(
                EditorSyntaxSpan(
                    start = stringStart,
                    endExclusive = stringEnd,
                    kind = EditorSyntaxSpanKind.STRING,
                ),
            ),
            symbols = emptyList(),
            diagnostics = emptyList(),
        )

        val result = formatter.format(
            fileName = "Main.kt",
            language = "Kotlin",
            text = source,
            indentStyle = EditorIndentStyle.SPACES_4,
            syntax = syntax,
        )

        assertTrue(result.text.contains("\"  keep { this }\""))
        assertTrue(result.text.contains("    println(1)"))
    }

    @Test
    fun unsupportedLanguageIsReported() {
        assertFalse(formatter.supports("main.py", "Python"))
        assertTrue(formatter.supports("Main.kt", "Kotlin"))
    }
}
