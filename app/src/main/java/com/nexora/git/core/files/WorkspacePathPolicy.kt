package com.nexora.git.core.files

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspacePathPolicy @Inject constructor() {

    fun resolve(
        root: File,
        relativePath: String,
    ): File {
        val canonicalRoot = root.canonicalFile
        val normalized = normalize(relativePath)

        require(
            normalized != ".git" &&
                !normalized.startsWith(".git/"),
        ) {
            "Git metadata is not browsable"
        }

        val candidate = if (normalized.isBlank()) {
            canonicalRoot
        } else {
            File(canonicalRoot, normalized).canonicalFile
        }

        require(
            candidate == canonicalRoot ||
                isWithin(canonicalRoot, candidate),
        ) {
            "Path is outside the workspace"
        }

        return candidate
    }

    fun isWithin(
        root: File,
        candidate: File,
    ): Boolean {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile

        return canonicalCandidate == canonicalRoot ||
            canonicalCandidate.canonicalPath.startsWith(
                canonicalRoot.canonicalPath + File.separator,
            )
    }

    fun relativePath(
        root: File,
        candidate: File,
    ): String {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile

        require(isWithin(canonicalRoot, canonicalCandidate)) {
            "Path is outside the workspace"
        }

        return if (canonicalCandidate == canonicalRoot) {
            ""
        } else {
            canonicalCandidate
                .relativeTo(canonicalRoot)
                .invariantSeparatorsPath
        }
    }

    private fun normalize(path: String): String =
        path.replace('\\', '/')
            .trim()
            .trim('/')
}
