package com.nexora.git.feature.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.nexora.git.core.editor.EditorSearchMatch
import com.nexora.git.core.editor.EditorSyntaxSpan
import com.nexora.git.core.editor.EditorSyntaxSpanKind

internal class EditorSyntaxTransformation(
    private val language: String?,
    private val keywordColor: Color,
    private val stringColor: Color,
    private val commentColor: Color,
    private val numberColor: Color,
    private val matchColor: Color,
    private val activeMatchColor: Color,
    private val syntaxSpans: List<EditorSyntaxSpan>,
    private val matches: List<EditorSearchMatch>,
    private val activeMatchIndex: Int,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            text = highlight(text.text),
            offsetMapping = OffsetMapping.Identity,
        )

    private fun highlight(text: String): AnnotatedString =
        buildAnnotatedString {
            append(text)

            if (syntaxSpans.isNotEmpty()) {
                syntaxSpans.forEach { span ->
                    val start = span.start.coerceIn(0, text.length)
                    val end = span.endExclusive.coerceIn(
                        start,
                        text.length,
                    )
                    if (end <= start) return@forEach

                    val style = when (span.kind) {
                        EditorSyntaxSpanKind.KEYWORD ->
                            SpanStyle(
                                color = keywordColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        EditorSyntaxSpanKind.STRING ->
                            SpanStyle(color = stringColor)
                        EditorSyntaxSpanKind.COMMENT ->
                            SpanStyle(color = commentColor)
                        EditorSyntaxSpanKind.NUMBER ->
                            SpanStyle(color = numberColor)
                        EditorSyntaxSpanKind.TYPE ->
                            SpanStyle(
                                color = keywordColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        EditorSyntaxSpanKind.FUNCTION ->
                            SpanStyle(
                                color = numberColor,
                                fontWeight = FontWeight.Medium,
                            )
                        EditorSyntaxSpanKind.PROPERTY ->
                            SpanStyle(color = stringColor)
                    }
                    addStyle(style, start, end)
                }
            } else {
                STRING_REGEX.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = stringColor),
                        match.range.first,
                        match.range.last + 1,
                    )
                }

                NUMBER_REGEX.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = numberColor),
                        match.range.first,
                        match.range.last + 1,
                    )
                }

                val keywords = keywordsFor(language)
                if (keywords.isNotEmpty()) {
                    val pattern = Regex(
                        "\\b(" +
                            keywords.joinToString(
                                separator = "|",
                                transform = Regex::escape,
                            ) +
                            ")\\b",
                    )

                    pattern.findAll(text).forEach { match ->
                        addStyle(
                            SpanStyle(
                                color = keywordColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            match.range.first,
                            match.range.last + 1,
                        )
                    }
                }

                commentRegexFor(language)
                    .findAll(text)
                    .forEach { match ->
                        addStyle(
                            SpanStyle(color = commentColor),
                            match.range.first,
                            match.range.last + 1,
                        )
                    }
            }

            matches.forEachIndexed { index, match ->
                val start = match.start.coerceIn(0, text.length)
                val end = match.endExclusive.coerceIn(
                    start,
                    text.length,
                )
                addStyle(
                    SpanStyle(
                        background = if (index == activeMatchIndex) {
                            activeMatchColor
                        } else {
                            matchColor
                        },
                    ),
                    start,
                    end,
                )
            }
        }
}

private fun keywordsFor(language: String?): Set<String> =
    when (language) {
        "Kotlin" -> setOf(
            "class", "object", "interface", "fun", "val", "var",
            "if", "else", "when", "for", "while", "return",
            "suspend", "override", "private", "public", "internal",
            "data", "sealed", "enum", "is", "as", "in", "null",
            "true", "false", "package", "import",
        )
        "Java" -> setOf(
            "class", "interface", "enum", "public", "private",
            "protected", "static", "final", "void", "new", "return",
            "if", "else", "switch", "for", "while", "try", "catch",
            "extends", "implements", "package", "import", "null",
            "true", "false",
        )
        "JavaScript", "TypeScript" -> setOf(
            "const", "let", "var", "function", "class", "interface",
            "type", "async", "await", "return", "if", "else", "for",
            "while", "switch", "import", "export", "from", "new",
            "null", "undefined", "true", "false",
        )
        "Python" -> setOf(
            "def", "class", "return", "if", "elif", "else", "for",
            "while", "try", "except", "with", "as", "import", "from",
            "async", "await", "yield", "None", "True", "False",
        )
        "Rust" -> setOf(
            "fn", "let", "mut", "struct", "enum", "impl", "trait",
            "pub", "use", "mod", "match", "if", "else", "loop",
            "while", "for", "return", "async", "await", "self",
            "Self", "true", "false",
        )
        "Go" -> setOf(
            "package", "import", "func", "type", "struct", "interface",
            "var", "const", "return", "if", "else", "for", "range",
            "go", "defer", "select", "case", "switch", "nil", "true",
            "false",
        )
        "C", "C++" -> setOf(
            "class", "struct", "enum", "namespace", "template",
            "public", "private", "protected", "const", "static",
            "void", "int", "long", "double", "float", "bool",
            "if", "else", "for", "while", "switch", "return",
            "nullptr", "true", "false", "include",
        )
        else -> emptySet()
    }

private fun commentRegexFor(language: String?): Regex =
    when (language) {
        "Python", "Shell", "YAML", "TOML" ->
            Regex("(?m)#.*$")
        "XML", "HTML" ->
            Regex("(?s)<!--.*?-->")
        else ->
            Regex("(?m)//.*$")
    }

private val STRING_REGEX = Regex(
    """(?s)"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""",
)

private val NUMBER_REGEX = Regex(
    """\b(?:0x[0-9A-Fa-f]+|\d+(?:\.\d+)?)\b""",
)
