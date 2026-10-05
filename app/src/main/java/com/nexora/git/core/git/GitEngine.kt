package com.nexora.git.core.git

interface GitEngine {
    suspend fun version(): String

    suspend fun init(
        workspacePath: String,
    ): GitRepository

    suspend fun clone(
        request: GitCloneRequest,
    ): GitRepository

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
