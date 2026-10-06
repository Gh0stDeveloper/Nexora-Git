package com.nexora.git.core.advanced

import com.nexora.git.core.common.AppResult

interface AdvancedGitHubGateway {

    suspend fun listDiscussions(
        owner: String,
        repository: String,
    ): AppResult<AdvancedDiscussionHub>

    suspend fun createDiscussion(
        owner: String,
        repository: String,
        categoryId: String,
        title: String,
        body: String,
    ): AppResult<AdvancedDiscussionSummary>

    suspend fun listProjects(
        owner: String,
        repository: String,
    ): AppResult<AdvancedProjectHub>

    suspend fun createProject(
        owner: String,
        repository: String,
        title: String,
    ): AppResult<AdvancedProjectSummary>

    suspend fun getPages(
        owner: String,
        repository: String,
    ): AppResult<GitHubPagesSite>

    suspend fun enablePages(
        owner: String,
        repository: String,
    ): AppResult<GitHubPagesSite>

    suspend fun requestPagesBuild(
        owner: String,
        repository: String,
    ): AppResult<Unit>

    suspend fun getSecurityOverview(
        owner: String,
        repository: String,
    ): AppResult<RepositorySecurityOverview>

    suspend fun listGists():
        AppResult<List<GitHubGistSummary>>

    suspend fun createGist(
        fileName: String,
        content: String,
        description: String,
        publicGist: Boolean,
    ): AppResult<GitHubGistSummary>

    suspend fun deleteGist(
        gistId: String,
    ): AppResult<Unit>

    suspend fun listCodespaces():
        AppResult<List<GitHubCodespaceSummary>>

    suspend fun createCodespace(
        owner: String,
        repository: String,
    ): AppResult<GitHubCodespaceSummary>

    suspend fun setCodespaceRunning(
        name: String,
        running: Boolean,
    ): AppResult<GitHubCodespaceSummary>

    suspend fun deleteCodespace(
        name: String,
    ): AppResult<Unit>
}
