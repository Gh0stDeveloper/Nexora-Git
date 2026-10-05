package com.nexora.git.feature.git

object GitWorkflowPolicy {
    fun normalizeRemoteName(value: String): String {
        val remote = value.trim()
        require(isSafeRemoteName(remote)) {
            "Invalid Git remote name."
        }
        return remote
    }

    fun normalizeGitHubRemoteUrl(value: String): String {
        val url = value.trim()
        require(url.startsWith("https://github.com/")) {
            "Remote URL must use https://github.com/."
        }
        require(
            !url.contains('@') &&
                !url.contains('?') &&
                !url.contains('#') &&
                url.removePrefix("https://github.com/")
                    .count { it == '/' } >= 1
        ) {
            "Remote URL is not a safe GitHub repository URL."
        }
        return url
    }

    fun isSafeRemoteName(value: String): Boolean {
        val remote = value.trim()
        if (remote.isBlank() || remote.length > 80) return false
        return remote.all {
            it.isLetterOrDigit() ||
                it == '-' ||
                it == '_' ||
                it == '.'
        }
    }

    fun normalizeBranchName(value: String): String {
        val branch = value.trim()
        require(isSafeBranchName(branch)) {
            "Invalid Git branch name."
        }
        return branch
    }

    fun pushRefspec(
        currentBranch: String,
        targetBranch: String,
    ): String {
        val source = normalizeBranchName(currentBranch)
        val target = normalizeBranchName(targetBranch)
        return "refs/heads/" + source + ":refs/heads/" + target
    }

    fun hasConflictMarkers(text: String): Boolean =
        text.contains("<<<<<<<") &&
            text.contains("=======") &&
            text.contains(">>>>>>>")

    fun isSafeBranchName(value: String): Boolean {
        val branch = value.trim()
        if (branch.isBlank()) return false
        if (branch.startsWith("-") ||
            branch.startsWith("+") ||
            branch.startsWith(".") ||
            branch.endsWith(".") ||
            branch.endsWith("/") ||
            branch.endsWith(".lock")
        ) {
            return false
        }

        if (".." in branch ||
            "@{" in branch ||
            "//" in branch
        ) {
            return false
        }

        val forbidden = setOf(
            ' ', '~', '^', ':', '?', '*', '[', '\\',
            '\n', '\r', '\t',
        )
        return branch.none { it in forbidden || it.code < 32 }
    }
}
