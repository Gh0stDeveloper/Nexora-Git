package com.nexora.git.core.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Singleton
class DirectProjectScanner @Inject constructor(
    private val gitIgnoreMatcher: GitIgnoreMatcher,
    private val riskDetector: ProjectRiskDetector,
) {

    suspend fun scan(directory: File): ProjectScanResult =
        withContext(Dispatchers.IO) {
            val root = directory.canonicalFile
            require(root.isDirectory) {
                "Direct workspace path is not a directory"
            }

            val files = mutableListOf<ProjectScanFile>()
            val risks = mutableListOf<ProjectRisk>()
            val projectTypes = linkedSetOf<String>()
            var gitIgnoreFiles = 0

            suspend fun walk(
                current: File,
                basePath: String,
                inheritedRules: List<GitIgnoreRule>,
            ) {
                coroutineContext.ensureActive()

                val children = current.listFiles()
                    ?.sortedBy { it.name.lowercase() }
                    .orEmpty()

                val ignoreFile = children.firstOrNull {
                    it.isFile && it.name == ".gitignore"
                }

                val rules = if (ignoreFile != null) {
                    gitIgnoreFiles += 1
                    inheritedRules + gitIgnoreMatcher.parse(
                        content = runCatching {
                            ignoreFile.takeIf {
                                it.length() <= MAX_GITIGNORE_BYTES
                            }?.readText().orEmpty()
                        }.getOrDefault(""),
                        basePath = basePath,
                    )
                } else {
                    inheritedRules
                }

                children.forEach { child ->
                    coroutineContext.ensureActive()
                    if (!isInside(root, child)) return@forEach

                    val relativePath = if (basePath.isBlank()) {
                        child.name
                    } else {
                        basePath + "/" + child.name
                    }

                    if (child.isDirectory) {
                        walk(child, relativePath, rules)
                    } else if (child.isFile) {
                        val ignored = gitIgnoreMatcher.isIgnored(
                            relativePath,
                            isDirectory = false,
                            rules = rules,
                        )

                        files += ProjectScanFile(
                            relativePath = relativePath,
                            sizeBytes = child.length(),
                            lastModifiedEpochMillis =
                                child.lastModified().coerceAtLeast(0L),
                            ignoredByGit = ignored,
                        )

                        detectProjectType(relativePath)
                            ?.let(projectTypes::add)

                        if (!isGitMetadataPath(relativePath)) {
                            risks += riskDetector.inspect(
                                relativePath = relativePath,
                                sizeBytes = child.length(),
                                ignoredByGit = ignored,
                                readText = {
                                    child.takeIf {
                                        it.length() <= MAX_SECRET_READ_BYTES
                                    }?.readText()
                                },
                            )
                        }
                    }
                }
            }

            walk(root, "", emptyList())

            ProjectScanResult(
                displayName = root.name.ifBlank { "Project" },
                files = files,
                risks = risks,
                fileCount = files.size.toLong(),
                totalBytes = files.sumOf { it.sizeBytes },
                gitIgnoreFiles = gitIgnoreFiles,
                detectedProjectTypes = projectTypes,
            )
        }

    private fun isInside(
        root: File,
        child: File,
    ): Boolean {
        val rootPath = root.canonicalPath + File.separator
        val childPath = child.canonicalPath
        return childPath.startsWith(rootPath)
    }

    private fun detectProjectType(path: String): String? {
        val normalized = path.lowercase()
        val filename = normalized.substringAfterLast('/')

        return when {
            filename == "project.godot" -> "Godot"
            filename == "package.json" -> "Node.js"
            filename == "cargo.toml" -> "Rust"
            filename == "go.mod" -> "Go"
            filename == "pubspec.yaml" -> "Flutter/Dart"
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

    private fun isGitMetadataPath(relativePath: String): Boolean =
        relativePath == ".git" ||
            relativePath.startsWith(".git/")

    companion object {
        private const val MAX_GITIGNORE_BYTES = 1024L * 1024L
        private const val MAX_SECRET_READ_BYTES = 128L * 1024L
    }
}
