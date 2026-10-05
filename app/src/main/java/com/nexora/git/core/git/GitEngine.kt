package com.nexora.git.core.git

interface GitEngine {
    suspend fun version(): String

    suspend fun init(
        workspacePath: String,
    ): GitRepository

    suspend fun clone(
        request: GitCloneRequest,
    ): GitRepository

    suspend fun remoteUrl(
        repositoryPath: String,
        remote: String = "origin",
    ): String?

    suspend fun remotes(
        repositoryPath: String,
    ): List<GitRemote>

    suspend fun addRemote(
        repositoryPath: String,
        name: String,
        url: String,
    )

    suspend fun renameRemote(
        repositoryPath: String,
        oldName: String,
        newName: String,
    )

    suspend fun removeRemote(
        repositoryPath: String,
        name: String,
    )

    suspend fun status(
        repositoryPath: String,
    ): GitStatus

    suspend fun stage(
        repositoryPath: String,
        paths: List<String>,
    )

    suspend fun unstage(
        repositoryPath: String,
        paths: List<String>,
    )

    suspend fun commit(
        repositoryPath: String,
        message: String,
        author: GitAuthor,
    ): GitCommit

    suspend fun branches(
        repositoryPath: String,
    ): List<GitBranch>

    suspend fun setUpstream(
        repositoryPath: String,
        branch: String,
        upstream: String?,
    )

    suspend fun divergence(
        repositoryPath: String,
        localRef: String,
        upstreamRef: String,
    ): GitDivergence

    suspend fun repositoryState(
        repositoryPath: String,
    ): GitRepositoryOperationState

    suspend fun createBranch(
        repositoryPath: String,
        name: String,
        startPoint: String? = null,
    )

    suspend fun checkout(
        repositoryPath: String,
        ref: String,
    )

    suspend fun fetch(
        repositoryPath: String,
        remote: String = "origin",
    )

    suspend fun pull(
        request: GitPullRequest,
    ): GitMergeResult

    suspend fun continueMerge(
        repositoryPath: String,
        author: GitAuthor,
    ): GitMergeResult

    suspend fun continueRebase(
        repositoryPath: String,
        author: GitAuthor,
    ): GitMergeResult

    suspend fun abortRebase(
        repositoryPath: String,
    )

    suspend fun push(
        request: GitPushRequest,
    ): GitPushResult

    suspend fun diff(
        repositoryPath: String,
        mode: GitDiffMode = GitDiffMode.ALL,
        relativePath: String? = null,
    ): GitDiff

    suspend fun merge(
        repositoryPath: String,
        ref: String,
        author: GitAuthor,
    ): GitMergeResult

    suspend fun conflicts(
        repositoryPath: String,
    ): List<GitConflict>

    suspend fun history(
        repositoryPath: String,
        relativePath: String? = null,
        limit: Int = 50,
    ): List<GitHistoryEntry>

    suspend fun blame(
        repositoryPath: String,
        relativePath: String,
    ): List<GitBlameHunk>
}
