package com.nexora.git.core.storage

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRiskDetector @Inject constructor() {

    fun inspect(
        relativePath: String,
        sizeBytes: Long,
        ignoredByGit: Boolean,
        readText: (() -> String?)?,
    ): List<ProjectRisk> = buildList {
        largeFileRisk(
            relativePath = relativePath,
            sizeBytes = sizeBytes,
            ignoredByGit = ignoredByGit,
        )?.let(::add)

        secretNameRisk(
            relativePath = relativePath,
            ignoredByGit = ignoredByGit,
        )?.let(::add)

        if (readText != null &&
            sizeBytes in 1..MAX_SECRET_SCAN_BYTES
        ) {
            val text = runCatching(readText)
                .getOrNull()
                ?.takeIf { it.isNotBlank() }

            if (text != null &&
                SECRET_PATTERNS.any { it.containsMatchIn(text) }
            ) {
                add(
                    ProjectRisk(
                        path = relativePath,
                        type = ProjectRiskType.SECRET,
                        severity = if (ignoredByGit) {
                            ProjectRiskSeverity.INFO
                        } else {
                            ProjectRiskSeverity.WARNING
                        },
                        message = "File content resembles a credential or private key.",
                        ignoredByGit = ignoredByGit,
                    ),
                )
            }
        }
    }.distinctBy {
        it.type.toString() + ":" + it.path
    }

    private fun largeFileRisk(
        relativePath: String,
        sizeBytes: Long,
        ignoredByGit: Boolean,
    ): ProjectRisk? =
        when {
            sizeBytes > GITHUB_HARD_LIMIT_BYTES -> ProjectRisk(
                path = relativePath,
                type = ProjectRiskType.LARGE_FILE,
                severity = if (ignoredByGit) {
                    ProjectRiskSeverity.INFO
                } else {
                    ProjectRiskSeverity.BLOCKING
                },
                message = "File exceeds GitHub's normal Git 100 MiB file limit.",
                ignoredByGit = ignoredByGit,
            )

            sizeBytes > GITHUB_WARNING_BYTES -> ProjectRisk(
                path = relativePath,
                type = ProjectRiskType.LARGE_FILE,
                severity = if (ignoredByGit) {
                    ProjectRiskSeverity.INFO
                } else {
                    ProjectRiskSeverity.WARNING
                },
                message = "File is at least 50 MiB and should be reviewed before push.",
                ignoredByGit = ignoredByGit,
            )

            else -> null
        }

    private fun secretNameRisk(
        relativePath: String,
        ignoredByGit: Boolean,
    ): ProjectRisk? {
        val filename = relativePath.substringAfterLast('/').lowercase()

        if (!SECRET_FILENAMES.any { it.matches(filename) }) {
            return null
        }

        return ProjectRisk(
            path = relativePath,
            type = ProjectRiskType.SECRET,
            severity = if (ignoredByGit) {
                ProjectRiskSeverity.INFO
            } else {
                ProjectRiskSeverity.WARNING
            },
            message = "Filename commonly contains credentials or signing material.",
            ignoredByGit = ignoredByGit,
        )
    }

    companion object {
        const val GITHUB_WARNING_BYTES = 50L * 1024L * 1024L
        const val GITHUB_HARD_LIMIT_BYTES = 100L * 1024L * 1024L
        private const val MAX_SECRET_SCAN_BYTES = 128L * 1024L

        private val SECRET_FILENAMES = listOf(
            Regex("""^\.env(?:\..+)?$"""),
            Regex(""".*\.pem$"""),
            Regex(""".*\.key$"""),
            Regex(""".*\.jks$"""),
            Regex(""".*\.keystore$"""),
            Regex(""".*\.p12$"""),
            Regex(""".*\.pfx$"""),
            Regex("""^id_rsa$"""),
            Regex("""^id_ed25519$"""),
            Regex("""^google-services\.json$"""),
            Regex("""^credentials.*\.json$"""),
            Regex("""^service[-_]?account.*\.json$"""),
        )

        private val SECRET_PATTERNS = listOf(
            Regex("""-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"""),
            Regex("""\bgh[pousr]_[A-Za-z0-9_]{20,}\b"""),
            Regex("""\bAKIA[0-9A-Z]{16}\b"""),
            Regex(
                """(?i)(?:api[_-]?key|token|password|secret)\s*[:=]\s*["']?[A-Za-z0-9_\-/.+=]{16,}""",
            ),
        )
    }
}
