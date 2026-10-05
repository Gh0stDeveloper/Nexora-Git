package com.nexora.git.feature.git

object GitWorkflowPolicy {
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
