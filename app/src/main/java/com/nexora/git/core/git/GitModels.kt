package com.nexora.git.core.git

data class GitRepository(
    val path: String,
    val bare: Boolean,
    val head: String,
)

data class GitStatus(
    val branch: String,
    val entries: List<GitStatusEntry>,
    val conflicted: Boolean,
)

data class GitStatusEntry(
    val path: String,
    val flags: Long,
    val staged: Boolean,
    val workingTree: Boolean,
    val untracked: Boolean,
    val conflicted: Boolean,
)

data class GitAuthor(
    val name: String,
    val email: String,
)

data class GitCommit(
    val oid: String,
    val message: String,
)

data class GitBranch(
    val name: String,
    val remote: Boolean,
    val head: Boolean,
    val upstream: String,
)

enum class GitDiffMode(
    internal val wireValue: String,
) {
    ALL("all"),
    STAGED("staged"),
    UNSTAGED("unstaged"),
}

data class GitDiff(
    val patch: String,
    val filesChanged: Long,
    val insertions: Long,
    val deletions: Long,
)

data class GitConflict(
    val path: String,
    val ancestor: String,
    val ours: String,
    val theirs: String,
)

enum class GitMergeState {
    UP_TO_DATE,
    FAST_FORWARD,
    MERGED,
    CONFLICTS,
}

data class GitMergeResult(
    val state: GitMergeState,
    val commitOid: String,
    val conflicts: List<GitConflict>,
)

data class GitPushResult(
    val remote: String,
    val refspec: String,
)

data class GitCloneRequest(
    val url: String,
    val destinationPath: String,
)

data class GitPullRequest(
    val repositoryPath: String,
    val remote: String = "origin",
    val author: GitAuthor,
)

data class GitPushRequest(
    val repositoryPath: String,
    val remote: String = "origin",
    val refspec: String = "",
)

data class GitTransportCredentials(
    val username: String,
    val password: String,
)


data class GitHistoryEntry(
    val oid: String,
    val shortOid: String,
    val summary: String,
    val message: String,
    val authorName: String,
    val authorEmail: String,
    val timestampSeconds: Long,
    val timezoneOffsetMinutes: Int,
    val parentCount: Int,
)

data class GitBlameHunk(
    val startLine: Long,
    val lineCount: Long,
    val finalCommitOid: String,
    val originalCommitOid: String,
    val originalStartLine: Long,
    val originalPath: String,
    val authorName: String,
    val authorEmail: String,
    val timestampSeconds: Long,
    val timezoneOffsetMinutes: Int,
    val boundary: Boolean,
)
