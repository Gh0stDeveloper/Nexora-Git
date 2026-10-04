package com.nexora.git.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Singleton
class SafProjectScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitIgnoreMatcher: GitIgnoreMatcher,
    private val riskDetector: ProjectRiskDetector,
) {

    suspend fun scan(treeUri: Uri): ProjectScanResult =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Unable to resolve selected project folder")

            require(root.exists() && root.isDirectory) {
                "Selected project folder is not available"
            }

            val files = mutableListOf<ProjectScanFile>()
            val risks = mutableListOf<ProjectRisk>()
            val projectTypes = linkedSetOf<String>()
            var gitIgnoreFiles = 0

            suspend fun walk(
                directory: DocumentFile,
                basePath: String,
                inheritedRules: List<GitIgnoreRule>,
            ) {
                coroutineContext.ensureActive()

                val children = directory.listFiles()
                    .sortedBy { it.name.orEmpty().lowercase() }

                val localIgnore = children.firstOrNull {
                    it.isFile && it.name == ".gitignore"
                }

                val localRules = if (localIgnore != null) {
                    gitIgnoreFiles += 1
                    val content = readText(localIgnore, MAX_GITIGNORE_BYTES)
                    inheritedRules + gitIgnoreMatcher.parse(
                        content = content.orEmpty(),
                        basePath = basePath,
                    )
                } else {
                    inheritedRules
                }

                children.forEach { child ->
                    coroutineContext.ensureActive()
                    val name = child.name
                        ?.takeIf(::isSafeName)
                        ?: return@forEach

                    val relativePath = if (basePath.isBlank()) {
                        name
                    } else {
                        "$basePath/$name"
                    }

                    if (child.isDirectory) {
                        walk(
                            directory = child,
                            basePath = relativePath,
                            inheritedRules = localRules,
                        )
                        return@forEach
                    }

                    if (!child.isFile) return@forEach

                    val ignored = gitIgnoreMatcher.isIgnored(
                        relativePath = relativePath,
                        isDirectory = false,
                        rules = localRules,
                    )
                    val size = child.length().coerceAtLeast(0L)
                    val modified = child.lastModified().coerceAtLeast(0L)

                    files += ProjectScanFile(
                        relativePath = relativePath,
                        sizeBytes = size,
                        lastModifiedEpochMillis = modified,
                        ignoredByGit = ignored,
                    )

                    detectProjectType(relativePath)?.let(projectTypes::add)

                    risks += riskDetector.inspect(
                        relativePath = relativePath,
                        sizeBytes = size,
                        ignoredByGit = ignored,
                        readText = {
                            readText(child, MAX_SECRET_READ_BYTES)
                        },
                    )
                }
            }

            walk(
                directory = root,
                basePath = "",
                inheritedRules = emptyList(),
            )

            ProjectScanResult(
                displayName = root.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "Project",
                files = files,
                risks = risks,
                fileCount = files.size.toLong(),
                totalBytes = files.sumOf { it.sizeBytes },
                gitIgnoreFiles = gitIgnoreFiles,
                detectedProjectTypes = projectTypes,
            )
        }

    private fun readText(
        file: DocumentFile,
        maxBytes: Long,
    ): String? {
        if (file.length() > maxBytes) return null

        return context.contentResolver
            .openInputStream(file.uri)
            ?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
    }

    private fun detectProjectType(path: String): String? {
        val normalized = path.lowercase()
        val filename = normalized.substringAfterLast('/')

        return when {
            filename == "project.godot" -> "Godot"
            filename == "package.json" -> "Node.js"
            filename == "cargo.toml" -> "Rust"
            filename == "pyproject.toml" ||
                filename == "requirements.txt" -> "Python"
            filename == "pom.xml" -> "Maven"
            filename == "cmakelists.txt" -> "CMake/C++"
            filename == "build.gradle" ||
                filename == "build.gradle.kts" ||
                filename == "settings.gradle" ||
                filename == "settings.gradle.kts" -> "Gradle"
            normalized.endsWith("androidmanifest.xml") -> "Android"
            else -> null
        }
    }

    private fun isSafeName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name

    companion object {
        private const val MAX_GITIGNORE_BYTES = 1024L * 1024L
        private const val MAX_SECRET_READ_BYTES = 128L * 1024L
    }
}
