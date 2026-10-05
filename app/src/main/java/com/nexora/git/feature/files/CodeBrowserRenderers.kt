package com.nexora.git.feature.files

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.files.BrowserFileKind
import com.nexora.git.core.git.GitBlameHunk
import com.nexora.git.core.git.GitHistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CodeContent(file: BrowserFile) {
    when (file.kind) {
        BrowserFileKind.BINARY,
        BrowserFileKind.IMAGE -> {
            BrowserNotice(
                title = if (file.kind == BrowserFileKind.IMAGE) {
                    "Image file"
                } else {
                    "Binary file"
                },
                body = if (file.kind == BrowserFileKind.IMAGE) {
                    "Use Preview to inspect the image. You can also share or save a copy."
                } else {
                    "Binary content is not rendered as text. You can share or save a copy."
                },
            )
        }

        BrowserFileKind.TEXT,
        BrowserFileKind.MARKDOWN -> {
            val colors = MaterialTheme.colorScheme
            val highlighted = highlightCode(
                text = file.text.orEmpty(),
                language = file.language,
                keyword = colors.primary,
                stringColor = colors.tertiary,
                comment = colors.onSurfaceVariant,
                number = colors.secondary,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 28.dp,
                ),
            ) {
                if (file.truncated) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        ) {
                            Text(
                                text = "Preview limited to the first 2 MiB for mobile safety.",
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = highlighted,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PreviewContent(file: BrowserFile) {
    when (file.kind) {
        BrowserFileKind.MARKDOWN -> {
            MarkdownPreview(file.text.orEmpty())
        }

        BrowserFileKind.IMAGE -> {
            SampledImagePreview(file.absolutePath)
        }

        BrowserFileKind.TEXT,
        BrowserFileKind.BINARY -> {
            BrowserNotice(
                title = "No rich preview",
                body = "This file type uses the code or binary view.",
            )
        }
    }
}

@Composable
internal fun HistoryContent(
    loading: Boolean,
    history: List<GitHistoryEntry>,
    error: String?,
) {
    when {
        loading -> CenteredProgress("Loading file history…")
        error != null -> BrowserNotice(
            title = "History unavailable",
            body = error,
        )
        history.isEmpty() -> BrowserNotice(
            title = "No history",
            body = "No committed history was found for this file.",
        )

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = history,
                    key = GitHistoryEntry::oid,
                ) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = entry.summary.ifBlank {
                                    "(no commit summary)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = entry.shortOid +
                                    " · " +
                                    entry.authorName.ifBlank {
                                        "Unknown author"
                                    },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = formatTimestamp(
                                    entry.timestampSeconds,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BlameContent(
    file: BrowserFile,
    loading: Boolean,
    blame: List<GitBlameHunk>,
    error: String?,
) {
    when {
        loading -> CenteredProgress("Loading blame…")
        error != null -> BrowserNotice(
            title = "Blame unavailable",
            body = error,
        )
        blame.isEmpty() -> BrowserNotice(
            title = "No blame",
            body = "No blame information is available for this file.",
        )

        else -> {
            val lines = file.text.orEmpty().split('\n')
            val rows = buildBlameRows(
                lines = lines,
                hunks = blame,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 28.dp,
                ),
            ) {
                itemsIndexed(rows) { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState(),
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 4.dp,
                            ),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = (index + 1)
                                .toString()
                                .padStart(4, ' '),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = row.shortOid.padEnd(7, ' '),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = row.author.take(14)
                                .padEnd(14, ' '),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = row.text,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreview(markdown: String) {
    val blocks = parseMarkdown(markdown)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 14.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(blocks) { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.headlineSmall
                            else -> MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                is MarkdownBlock.Bullet -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("•")
                        Text(
                            text = block.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MarkdownBlock.Quote -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block.text,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is MarkdownBlock.Code -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block.text,
                            modifier = Modifier
                                .horizontalScroll(
                                    rememberScrollState(),
                                )
                                .padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SampledImagePreview(path: String) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = path,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledImage(path)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = requireNotNull(bitmap),
                contentDescription = "Image preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun BrowserNotice(
    title: String,
    body: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

private fun highlightCode(
    text: String,
    language: String?,
    keyword: Color,
    stringColor: Color,
    comment: Color,
    number: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)

    STRING_REGEX.findAll(text).forEach { match ->
        addStyle(
            SpanStyle(color = stringColor),
            match.range.first,
            match.range.last + 1,
        )
    }

    NUMBER_REGEX.findAll(text).forEach { match ->
        addStyle(
            SpanStyle(color = number),
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
                    color = keyword,
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
                SpanStyle(color = comment),
                match.range.first,
                match.range.last + 1,
            )
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

private sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class Bullet(
        val text: String,
    ) : MarkdownBlock

    data class Quote(
        val text: String,
    ) : MarkdownBlock

    data class Code(
        val text: String,
    ) : MarkdownBlock

    data class Paragraph(
        val text: String,
    ) : MarkdownBlock
}

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false
    val fence = 96.toChar().toString().repeat(3)

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(
                paragraph.joinToString(" "),
            )
            paragraph.clear()
        }
    }

    fun flushCode() {
        blocks += MarkdownBlock.Code(
            code.joinToString("\n"),
        )
        code.clear()
    }

    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()

        if (line.trimStart().startsWith(fence)) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            code += line
            return@forEach
        }

        when {
            line.isBlank() -> {
                flushParagraph()
            }

            line.startsWith("#") -> {
                flushParagraph()
                val hashes = line.takeWhile { it == '#' }.length
                if (hashes in 1..6 &&
                    line.getOrNull(hashes) == ' '
                ) {
                    blocks += MarkdownBlock.Heading(
                        level = hashes,
                        text = line.drop(hashes + 1),
                    )
                } else {
                    paragraph += line
                }
            }

            line.startsWith("- ") ||
                line.startsWith("* ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(
                    line.drop(2),
                )
            }

            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(
                    line.drop(2),
                )
            }

            else -> paragraph += line
        }
    }

    if (inCode) {
        flushCode()
    }
    flushParagraph()

    return blocks
}

private data class BlameLine(
    val shortOid: String,
    val author: String,
    val text: String,
)

private fun buildBlameRows(
    lines: List<String>,
    hunks: List<GitBlameHunk>,
): List<BlameLine> {
    val sorted = hunks.sortedBy {
        it.startLine
    }
    var hunkIndex = 0

    return lines.mapIndexed { index, line ->
        val lineNumber = index + 1L

        while (
            hunkIndex + 1 < sorted.size &&
            lineNumber >= sorted[hunkIndex + 1].startLine
        ) {
            hunkIndex += 1
        }

        val hunk = sorted.getOrNull(hunkIndex)
        val applies = hunk != null &&
            lineNumber >= hunk.startLine &&
            lineNumber < hunk.startLine + hunk.lineCount

        BlameLine(
            shortOid = if (applies) {
                hunk.finalCommitOid.take(7)
            } else {
                ""
            },
            author = if (applies) {
                hunk.authorName.ifBlank {
                    "Unknown"
                }
            } else {
                ""
            },
            text = line,
        )
    }
}

private fun decodeSampledImage(
    path: String,
    maxDimension: Int = 2048,
): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, bounds)

    if (bounds.outWidth <= 0 ||
        bounds.outHeight <= 0
    ) {
        return null
    }

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > maxDimension ||
        bounds.outHeight / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }

    return BitmapFactory.decodeFile(path, options)
        ?.asImageBitmap()
}

private fun formatTimestamp(
    timestampSeconds: Long,
): String {
    if (timestampSeconds <= 0L) return "Unknown date"

    return DATE_FORMATTER.format(
        Instant.ofEpochSecond(timestampSeconds)
            .atZone(ZoneId.systemDefault()),
    )
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm",
        Locale.US,
    )
