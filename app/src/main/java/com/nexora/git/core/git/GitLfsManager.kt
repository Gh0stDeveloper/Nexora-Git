package com.nexora.git.core.git

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class GitLfsPointer(
    val path: String,
    val oidSha256: String,
    val sizeBytes: Long,
)

data class GitLfsState(
    val trackedPatterns: List<String>,
    val pointers: List<GitLfsPointer>,
    val transferSupported: Boolean = false,
    val transferMessage: String =
        "Git LFS tracking and pointer inspection are available. " +
            "Object transfer is not silently emulated because bundled libgit2 " +
            "does not provide the Git LFS clean/smudge transport.",
)

@Singleton
class GitLfsManager @Inject constructor() {
    fun inspect(repositoryPath: String): GitLfsState {
        val root = canonicalRepository(repositoryPath)
        val attributes = File(root, ".gitattributes")
        val patterns = if (attributes.isFile) {
            attributes.readLines()
                .map(String::trim)
                .filter { line ->
                    line.isNotBlank() &&
                        !line.startsWith("#") &&
                        line.split(Regex("\\s+"))
                            .drop(1)
                            .any { it == "filter=lfs" }
                }
                .map { it.split(Regex("\\s+")).first() }
                .distinct()
                .sorted()
        } else {
            emptyList()
        }

        val pointers = mutableListOf<GitLfsPointer>()
        var visited = 0
        root.walkTopDown()
            .onEnter { directory ->
                directory.name != ".git"
            }
            .forEach { file ->
                if (visited >= MAX_SCAN_FILES) return@forEach
                if (!file.isFile || file == attributes) return@forEach
                visited += 1
                if (file.length() > MAX_POINTER_BYTES) return@forEach

                val pointer = runCatching {
                    parsePointer(
                        root = root,
                        file = file,
                    )
                }.getOrNull()
                if (pointer != null) {
                    pointers += pointer
                }
            }

        return GitLfsState(
            trackedPatterns = patterns,
            pointers = pointers.sortedBy(GitLfsPointer::path),
        )
    }

    fun track(
        repositoryPath: String,
        pattern: String,
    ): GitLfsState {
        val normalized = normalizePattern(pattern)
        val root = canonicalRepository(repositoryPath)
        val attributes = File(root, ".gitattributes")
        val existing = if (attributes.isFile) {
            attributes.readLines()
        } else {
            emptyList()
        }

        val alreadyTracked = existing.any { line ->
            val parts = line.trim().split(Regex("\\s+"))
            parts.firstOrNull() == normalized &&
                parts.drop(1).any { it == "filter=lfs" }
        }

        if (!alreadyTracked) {
            val next = buildList {
                addAll(existing)
                if (isNotEmpty() && last().isNotBlank()) add("")
                add(
                    "$normalized filter=lfs diff=lfs merge=lfs -text",
                )
            }
            writeAtomic(attributes, next.joinToString("\n") + "\n")
        }

        return inspect(repositoryPath)
    }

    fun untrack(
        repositoryPath: String,
        pattern: String,
    ): GitLfsState {
        val normalized = normalizePattern(pattern)
        val root = canonicalRepository(repositoryPath)
        val attributes = File(root, ".gitattributes")
        if (!attributes.isFile) return inspect(repositoryPath)

        val next = attributes.readLines().filterNot { line ->
            val parts = line.trim().split(Regex("\\s+"))
            parts.firstOrNull() == normalized &&
                parts.drop(1).any { it == "filter=lfs" }
        }

        writeAtomic(
            attributes,
            if (next.isEmpty()) "" else next.joinToString("\n") + "\n",
        )
        return inspect(repositoryPath)
    }

    private fun parsePointer(
        root: File,
        file: File,
    ): GitLfsPointer? {
        val lines = file.readLines()
        if (lines.firstOrNull() != LFS_VERSION_LINE) return null

        val oid = lines.firstOrNull {
            it.startsWith("oid sha256:")
        }?.removePrefix("oid sha256:")
            ?.trim()
            .orEmpty()

        val size = lines.firstOrNull {
            it.startsWith("size ")
        }?.removePrefix("size ")
            ?.trim()
            ?.toLongOrNull()
            ?: return null

        if (!OID_REGEX.matches(oid)) return null

        val relative = file.canonicalFile
            .relativeTo(root)
            .invariantSeparatorsPath

        return GitLfsPointer(
            path = relative,
            oidSha256 = oid,
            sizeBytes = size,
        )
    }

    private fun canonicalRepository(
        repositoryPath: String,
    ): File {
        require(repositoryPath.isNotBlank()) {
            "Repository path is required."
        }
        return File(repositoryPath).canonicalFile.also { root ->
            require(File(root, ".git").exists()) {
                "Git repository metadata is missing."
            }
        }
    }

    private fun normalizePattern(pattern: String): String {
        val value = pattern.trim()
        require(value.isNotBlank()) {
            "Git LFS pattern is required."
        }
        require(
            !value.contains('\n') &&
                !value.contains('\r') &&
                !value.contains('\u0000'),
        ) {
            "Git LFS pattern contains invalid characters."
        }
        require(value.length <= MAX_PATTERN_LENGTH) {
            "Git LFS pattern is too long."
        }
        return value
    }

    private fun writeAtomic(
        target: File,
        content: String,
    ) {
        target.parentFile?.mkdirs()
        val temporary = File(
            target.parentFile,
            target.name + ".nexora.tmp",
        )
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            target.writeText(content)
            temporary.delete()
        }
    }

    companion object {
        private const val MAX_SCAN_FILES = 5_000
        private const val MAX_POINTER_BYTES = 4_096L
        private const val MAX_PATTERN_LENGTH = 512
        private const val LFS_VERSION_LINE =
            "version https://git-lfs.github.com/spec/v1"
        private val OID_REGEX = Regex("[0-9a-fA-F]{64}")
    }
}
