package com.nexora.git.core.search

import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Singleton
class ProjectSearchEngine @Inject constructor() {

    suspend fun search(
        workspacePath: String,
        query: ProjectSearchQuery,
    ): ProjectSearchResult = withContext(Dispatchers.IO) {
        require(query.text.isNotBlank()) {
            "Search text is required."
        }
        require(query.text.length <= MAX_PATTERN_LENGTH) {
            "Search pattern is too long."
        }

        val root = File(workspacePath).canonicalFile
        require(root.isDirectory && root.canRead()) {
            "Workspace is not readable."
        }

        val matcher = buildMatcher(query)
        val include = query.includeGlob
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(::globRegex)
        val exclude = query.excludeGlob
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(::globRegex)

        val matches = mutableListOf<ProjectSearchMatch>()
        var filesScanned = 0
        var filesSkipped = 0
        var truncated = false
        val rootPrefix = root.path + File.separator

        root.walkTopDown()
            .onEnter { directory ->
                if (directory == root) {
                    true
                } else {
                    val canonical = runCatching {
                        directory.canonicalFile
                    }.getOrNull()
                    canonical != null &&
                        canonical.path.startsWith(rootPrefix) &&
                        !Files.isSymbolicLink(directory.toPath()) &&
                        relative(root, canonical)
                            .split('/')
                            .none {
                                it in DEFAULT_SKIPPED_DIRECTORIES
                            }
                }
            }
            .forEach { file ->
                coroutineContext.ensureActive()
                if (truncated || !file.isFile) {
                    return@forEach
                }

                if (filesScanned >= MAX_FILES) {
                    truncated = true
                    return@forEach
                }

                val canonical = runCatching {
                    file.canonicalFile
                }.getOrNull()
                if (
                    canonical == null ||
                    !canonical.path.startsWith(rootPrefix) ||
                    Files.isSymbolicLink(file.toPath())
                ) {
                    filesSkipped += 1
                    return@forEach
                }

                val path = relative(root, canonical)
                if (
                    include != null && !include.matches(path) ||
                    exclude != null && exclude.matches(path)
                ) {
                    filesSkipped += 1
                    return@forEach
                }

                if (
                    file.length() > MAX_FILE_BYTES ||
                    looksBinary(file)
                ) {
                    filesSkipped += 1
                    return@forEach
                }

                filesScanned += 1
                val text = runCatching {
                    file.readText(Charsets.UTF_8)
                }.getOrElse {
                    filesSkipped += 1
                    return@forEach
                }

                var lineNumber = 1
                var lineStart = 0
                var nextLineBreak = text.indexOf('\n')

                matcher.findAll(text).forEach { match ->
                    if (matches.size >= MAX_MATCHES) {
                        truncated = true
                        return@forEach
                    }

                    while (
                        nextLineBreak >= 0 &&
                        nextLineBreak < match.range.first
                    ) {
                        lineNumber += 1
                        lineStart = nextLineBreak + 1
                        nextLineBreak = text.indexOf(
                            '\n',
                            startIndex = lineStart,
                        )
                    }

                    val lineEnd = if (nextLineBreak >= 0) {
                        nextLineBreak
                    } else {
                        text.length
                    }
                    val column =
                        match.range.first - lineStart + 1
                    val preview = text
                        .substring(lineStart, lineEnd)
                        .trim()
                        .take(MAX_PREVIEW_LENGTH)

                    matches += ProjectSearchMatch(
                        path = path,
                        line = lineNumber,
                        column = column,
                        preview = preview,
                        start = match.range.first,
                        endExclusive = match.range.last + 1,
                    )
                }
            }

        ProjectSearchResult(
            matches = matches,
            filesScanned = filesScanned,
            filesSkipped = filesSkipped,
            truncated = truncated,
        )
    }

    private fun buildMatcher(query: ProjectSearchQuery): Regex {
        val base = if (query.regex) {
            query.text
        } else {
            Regex.escape(query.text)
        }
        val pattern = if (query.wholeWord) {
            "(?<![\\\\p{L}\\\\p{N}_])(?:$base)(?![\\\\p{L}\\\\p{N}_])"
        } else {
            base
        }
        val options = if (query.matchCase) {
            emptySet()
        } else {
            setOf(RegexOption.IGNORE_CASE)
        }
        return try {
            Regex(pattern, options)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Invalid regular expression: " +
                    (error.message ?: "syntax error"),
            )
        }
    }

    private fun globRegex(glob: String): Regex {
        val normalized = glob.replace('\\', '/')
        val out = StringBuilder("^")
        var index = 0
        while (index < normalized.length) {
            val ch = normalized[index]
            when {
                ch == '*' &&
                    index + 1 < normalized.length &&
                    normalized[index + 1] == '*' -> {
                    out.append(".*")
                    index += 1
                }
                ch == '*' -> out.append("[^/]*")
                ch == '?' -> out.append("[^/]")
                ch in ".+()^$|{}[]" -> {
                    out.append('\\').append(ch)
                }
                else -> out.append(ch)
            }
            index += 1
        }
        out.append('$')
        return Regex(out.toString())
    }

    private fun looksBinary(file: File): Boolean {
        file.inputStream().use { input ->
            val buffer = ByteArray(BINARY_SAMPLE_BYTES)
            val count = input.read(buffer)
            if (count <= 0) return false
            for (index in 0 until count) {
                if (buffer[index] == 0.toByte()) return true
            }
        }
        return false
    }

    private fun relative(
        root: File,
        file: File,
    ): String =
        if (root == file) {
            ""
        } else {
            file.relativeTo(root).invariantSeparatorsPath
        }

    private companion object {
        const val MAX_PATTERN_LENGTH = 256
        const val MAX_FILES = 5_000
        const val MAX_MATCHES = 500
        const val MAX_FILE_BYTES = 1L * 1024L * 1024L
        const val BINARY_SAMPLE_BYTES = 4_096
        const val MAX_PREVIEW_LENGTH = 180

        val DEFAULT_SKIPPED_DIRECTORIES = setOf(
            ".git",
            ".gradle",
            ".idea",
            "build",
            "node_modules",
            "dist",
        )
    }
}
