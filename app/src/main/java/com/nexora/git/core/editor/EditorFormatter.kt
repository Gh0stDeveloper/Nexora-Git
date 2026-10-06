package com.nexora.git.core.editor

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class EditorFormatResult(
    val text: String,
    val changed: Boolean,
    val formatter: String,
)

@Singleton
class EditorFormatter @Inject constructor() {

    fun supports(
        fileName: String,
        language: String?,
    ): Boolean {
        val extension = fileName.lowercase()
            .substringAfterLast('.', "")
        return extension == "json" ||
            language in STRUCTURED_LANGUAGES
    }

    suspend fun format(
        fileName: String,
        language: String?,
        text: String,
        indentStyle: EditorIndentStyle,
        syntax: EditorSyntaxSnapshot?,
    ): EditorFormatResult = withContext(Dispatchers.Default) {
        val extension = fileName.lowercase()
            .substringAfterLast('.', "")

        val formatted = when {
            extension == "json" || language == "JSON" ->
                formatJson(text, indentStyle)
            language in STRUCTURED_LANGUAGES ->
                formatStructured(
                    text = text,
                    indent = indentStyle.unit,
                    syntax = syntax,
                )
            else ->
                throw IllegalArgumentException(
                    "No safe formatter is available for this file.",
                )
        }

        EditorFormatResult(
            text = formatted,
            changed = formatted != text,
            formatter = if (
                extension == "json" || language == "JSON"
            ) {
                "JSON"
            } else {
                "Nexora structured"
            },
        )
    }

    private fun formatJson(
        text: String,
        indentStyle: EditorIndentStyle,
    ): String {
        val value = JSONTokener(text).nextValue()
        val indent = when (indentStyle) {
            EditorIndentStyle.TABS -> 2
            EditorIndentStyle.SPACES_2 -> 2
            EditorIndentStyle.SPACES_4 -> 4
        }
        val body = when (value) {
            is JSONObject -> value.toString(indent)
            is JSONArray -> value.toString(indent)
            else -> JSONObject.valueToString(value)
        }
        return if (text.endsWith("\n")) {
            body + "\n"
        } else {
            body
        }
    }

    private fun formatStructured(
        text: String,
        indent: String,
        syntax: EditorSyntaxSnapshot?,
    ): String {
        if (text.isEmpty()) return text

        val protected = BooleanArray(text.length)
        syntax?.spans
            ?.asSequence()
            ?.filter {
                it.kind == EditorSyntaxSpanKind.STRING ||
                    it.kind == EditorSyntaxSpanKind.COMMENT
            }
            ?.forEach { span ->
                val start = span.start.coerceIn(0, text.length)
                val end = span.endExclusive.coerceIn(
                    start,
                    text.length,
                )
                for (index in start until end) {
                    protected[index] = true
                }
            }

        val lines = text.split('\n')
        val output = StringBuilder(text.length + 64)
        var depth = 0
        var globalStart = 0

        lines.forEachIndexed { lineIndex, line ->
            val firstContent = line.indexOfFirst {
                it != ' ' && it != '\t'
            }

            val protectedLeading = if (firstContent >= 0) {
                val index = globalStart + firstContent
                index in protected.indices && protected[index]
            } else {
                false
            }

            if (protectedLeading || firstContent < 0) {
                output.append(line)
            } else {
                val content = line.substring(firstContent)
                var leadingClosers = 0
                for (offset in content.indices) {
                    val index = globalStart + firstContent + offset
                    if (index in protected.indices && protected[index]) {
                        break
                    }
                    if (content[offset] == '}') {
                        leadingClosers += 1
                    } else if (!content[offset].isWhitespace()) {
                        break
                    }
                }

                repeat((depth - leadingClosers).coerceAtLeast(0)) {
                    output.append(indent)
                }
                output.append(content)
            }

            var nextDepth = depth
            for (offset in line.indices) {
                val index = globalStart + offset
                if (index in protected.indices && protected[index]) {
                    continue
                }
                when (line[offset]) {
                    '{' -> nextDepth += 1
                    '}' -> nextDepth = (nextDepth - 1)
                        .coerceAtLeast(0)
                }
            }
            depth = nextDepth

            if (lineIndex != lines.lastIndex) {
                output.append('\n')
            }
            globalStart += line.length + 1
        }

        return output.toString()
    }

    private companion object {
        val STRUCTURED_LANGUAGES = setOf(
            "Kotlin",
            "Java",
            "JavaScript",
            "TypeScript",
        )
    }
}
