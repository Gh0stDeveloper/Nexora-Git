package com.nexora.git.core.files

import com.nexora.git.core.storage.WorkspaceRegistry
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CodeBrowserFileSystem @Inject constructor(
    private val workspaceRegistry: WorkspaceRegistry,
) {

    suspend fun list(
        workspaceId: String,
        relativePath: String = "",
    ): BrowserDirectory = withContext(Dispatchers.IO) {
        val root = workspaceRoot(workspaceId)
        val directory = safeResolve(
            root = root,
            relativePath = relativePath,
        )

        require(directory.isDirectory) {
            "Directory is unavailable"
        }

        val entries = directory.listFiles()
            .orEmpty()
            .asSequence()
            .filterNot { file ->
                file.name == ".git"
            }
            .mapNotNull { file ->
                val canonical = runCatching {
                    file.canonicalFile
                }.getOrNull() ?: return@mapNotNull null

                if (!isWithinRoot(root, canonical)) {
                    return@mapNotNull null
                }

                val childRelative = canonical
                    .relativeTo(root)
                    .invariantSeparatorsPath

                BrowserEntry(
                    name = file.name,
                    relativePath = childRelative,
                    directory = canonical.isDirectory,
                    sizeBytes = if (canonical.isFile) {
                        canonical.length().coerceAtLeast(0L)
                    } else {
                        0L
                    },
                    lastModifiedEpochMillis =
                        canonical.lastModified().coerceAtLeast(0L),
                    kind = if (canonical.isFile) {
                        classify(canonical)
                    } else {
                        null
                    },
                    language = if (canonical.isFile) {
                        languageFor(canonical.name)
                    } else {
                        null
                    },
                )
            }
            .sortedWith(
                compareBy<BrowserEntry> { !it.directory }
                    .thenBy { it.name.lowercase() },
            )
            .toList()

        BrowserDirectory(
            relativePath = normalizeRelative(relativePath),
            entries = entries,
        )
    }

    suspend fun read(
        workspaceId: String,
        relativePath: String,
    ): BrowserFile = withContext(Dispatchers.IO) {
        val root = workspaceRoot(workspaceId)
        val file = safeResolve(
            root = root,
            relativePath = relativePath,
        )

        require(file.isFile) {
            "File is unavailable"
        }
        require(!isGitMetadataPath(root, file)) {
            "Git metadata is not browsable"
        }

        val kind = classify(file)
        val size = file.length().coerceAtLeast(0L)
        val mime = mimeType(file)
        val language = languageFor(file.name)

        var text: String? = null
        var truncated = false
        var lineCount = 0

        if (kind == BrowserFileKind.TEXT ||
            kind == BrowserFileKind.MARKDOWN
        ) {
            val readLength = minOf(size, MAX_TEXT_BYTES)
            val bytes = ByteArray(readLength.toInt())

            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(
                        bytes,
                        offset,
                        bytes.size - offset,
                    )
                    if (count <= 0) break
                    offset += count
                }

                text = bytes.copyOf(offset)
                    .toString(Charsets.UTF_8)
            }

            truncated = size > MAX_TEXT_BYTES
            lineCount = text
                ?.let { value ->
                    if (value.isEmpty()) {
                        0
                    } else {
                        value.count { it == '\n' } + 1
                    }
                }
                ?: 0
        }

        BrowserFile(
            name = file.name,
            relativePath = file
                .relativeTo(root)
                .invariantSeparatorsPath,
            absolutePath = file.canonicalPath,
            sizeBytes = size,
            lastModifiedEpochMillis =
                file.lastModified().coerceAtLeast(0L),
            mimeType = mime,
            kind = kind,
            language = language,
            text = text,
            truncated = truncated,
            lineCount = lineCount,
        )
    }

    suspend fun resolveShareableFile(
        workspaceId: String,
        relativePath: String,
    ): File = withContext(Dispatchers.IO) {
        val root = workspaceRoot(workspaceId)
        val file = safeResolve(root, relativePath)

        require(file.isFile) {
            "File is unavailable"
        }
        require(!isGitMetadataPath(root, file)) {
            "Git metadata cannot be shared"
        }

        file
    }

    private suspend fun workspaceRoot(
        workspaceId: String,
    ): File {
        val workspace = workspaceRegistry.findById(workspaceId)
            ?: error("Workspace not found")

        val root = File(workspace.workspacePath).canonicalFile
        require(root.isDirectory) {
            "Workspace directory is unavailable"
        }
        return root
    }

    private fun safeResolve(
        root: File,
        relativePath: String,
    ): File {
        val normalized = normalizeRelative(relativePath)

        require(
            normalized != ".git" &&
                !normalized.startsWith(".git/"),
        ) {
            "Git metadata is not browsable"
        }

        val candidate = if (normalized.isBlank()) {
            root
        } else {
            File(root, normalized).canonicalFile
        }

        require(
            candidate == root || isWithinRoot(root, candidate),
        ) {
            "Path is outside the workspace"
        }

        return candidate
    }

    private fun normalizeRelative(path: String): String =
        path.replace('\\', '/')
            .trim()
            .trim('/')

    private fun isWithinRoot(
        root: File,
        candidate: File,
    ): Boolean =
        candidate.canonicalPath.startsWith(
            root.canonicalPath + File.separator,
        )

    private fun isGitMetadataPath(
        root: File,
        file: File,
    ): Boolean {
        val relative = file
            .relativeTo(root)
            .invariantSeparatorsPath

        return relative == ".git" ||
            relative.startsWith(".git/")
    }

    private fun classify(file: File): BrowserFileKind {
        val lower = file.name.lowercase()

        if (lower.endsWith(".md") ||
            lower.endsWith(".markdown")
        ) {
            return BrowserFileKind.MARKDOWN
        }

        if (IMAGE_EXTENSIONS.any(lower::endsWith)) {
            return BrowserFileKind.IMAGE
        }

        val sampleSize = minOf(
            file.length().coerceAtLeast(0L),
            BINARY_SAMPLE_BYTES,
        ).toInt()

        if (sampleSize == 0) {
            return BrowserFileKind.TEXT
        }

        val sample = ByteArray(sampleSize)
        val bytesRead = FileInputStream(file).use { input ->
            input.read(sample)
        }

        if (bytesRead <= 0) {
            return BrowserFileKind.TEXT
        }

        if (sample
                .take(bytesRead)
                .any { byte -> byte == 0.toByte() }
        ) {
            return BrowserFileKind.BINARY
        }

        val controlBytes = sample
            .take(bytesRead)
            .count { byte ->
                val value = byte.toInt() and 0xff
                value < 0x09 ||
                    (value in 0x0e..0x1f)
            }

        return if (
            controlBytes.toDouble() / bytesRead.toDouble() >
                MAX_CONTROL_RATIO
        ) {
            BrowserFileKind.BINARY
        } else {
            BrowserFileKind.TEXT
        }
    }

    private fun mimeType(file: File): String =
        URLConnection.guessContentTypeFromName(file.name)
            ?: when (classify(file)) {
                BrowserFileKind.MARKDOWN,
                BrowserFileKind.TEXT -> "text/plain"
                BrowserFileKind.IMAGE -> "image/*"
                BrowserFileKind.BINARY ->
                    "application/octet-stream"
            }

    private fun languageFor(name: String): String? {
        val lower = name.lowercase()
        val extension = lower.substringAfterLast(
            '.',
            missingDelimiterValue = "",
        )

        return when {
            lower == "dockerfile" -> "Dockerfile"
            lower == "makefile" -> "Makefile"
            lower.endsWith(".gradle.kts") -> "Kotlin"
            extension == "kt" || extension == "kts" -> "Kotlin"
            extension == "java" -> "Java"
            extension == "xml" -> "XML"
            extension == "json" -> "JSON"
            extension == "yaml" || extension == "yml" -> "YAML"
            extension == "toml" -> "TOML"
            extension == "gradle" -> "Gradle"
            extension == "js" || extension == "jsx" ->
                "JavaScript"
            extension == "ts" || extension == "tsx" ->
                "TypeScript"
            extension == "py" -> "Python"
            extension == "rs" -> "Rust"
            extension == "go" -> "Go"
            extension == "gd" -> "GDScript"
            extension == "c" || extension == "h" -> "C"
            extension in setOf("cpp", "cc", "cxx", "hpp") ->
                "C++"
            extension == "sh" || extension == "bash" ->
                "Shell"
            extension == "md" || extension == "markdown" ->
                "Markdown"
            extension == "html" || extension == "htm" ->
                "HTML"
            extension == "css" || extension == "scss" ->
                "CSS"
            extension == "sql" -> "SQL"
            else -> null
        }
    }

    companion object {
        const val MAX_TEXT_BYTES = 2L * 1024L * 1024L
        private const val BINARY_SAMPLE_BYTES = 8192L
        private const val MAX_CONTROL_RATIO = 0.10

        private val IMAGE_EXTENSIONS = setOf(
            ".png",
            ".jpg",
            ".jpeg",
            ".webp",
            ".gif",
            ".bmp",
        )
    }
}
