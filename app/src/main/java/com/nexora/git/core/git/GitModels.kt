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

data class GitRemote(
    val name: String,
    val url: String,
)

data class GitDivergence(
    val localRef: String,
    val upstreamRef: String,
    val localOid: String,
    val upstreamOid: String,
    val ahead: Long,
    val behind: Long,
)

enum class GitRepositoryOperationState {
    NONE,
    MERGE,
    REBASE,
    OTHER,
}

enum class GitPullStrategy(
    internal val wireValue: String,
) {
    MERGE("merge"),
    FAST_FORWARD_ONLY("ff_only"),
    REBASE("rebase"),
}

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

enum class GitConflictResolution(
    internal val wireValue: String,
) {
    OURS("ours"),
    THEIRS("theirs"),
}

enum class GitMergeState {
    UP_TO_DATE,
    FAST_FORWARD,
    MERGED,
    REBASED,
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
    val forceWithLease: Boolean = false,
)

data class GitCloneRequest(
    val url: String,
    val destinationPath: String,
)

data class GitPullRequest(
    val repositoryPath: String,
    val remote: String = "origin",
    val author: GitAuthor,
    val strategy: GitPullStrategy = GitPullStrategy.MERGE,
)

data class GitPushRequest(
    val repositoryPath: String,
    val remote: String = "origin",
    val refspec: String = "",
    val forceWithLease: Boolean = false,
    val expectedRemoteOid: String = "",
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


enum class GitApplyState {
    APPLIED,
    CONFLICTS,
}

data class GitApplyResult(
    val state: GitApplyState,
    val commitOid: String,
    val conflicts: List<GitConflict>,
)

data class GitStash(
    val index: Int,
    val oid: String,
    val message: String,
)

enum class GitResetMode(
    internal val wireValue: String,
) {
    SOFT("soft"),
    MIXED("mixed"),
    HARD("hard"),
}

data class GitTag(
    val name: String,
    val targetOid: String,
    val annotated: Boolean,
    val message: String,
    val taggerName: String,
    val taggerEmail: String,
)

data class GitSubmodule(
    val name: String,
    val path: String,
    val url: String,
    val headOid: String,
    val workdirOid: String,
    val status: Long,
    val initialized: Boolean,
)
