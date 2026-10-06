package com.nexora.git.core.actions

import com.nexora.git.core.common.AppResult

interface GitHubActionsGateway {

    suspend fun listWorkflows(
        owner: String,
        repository: String,
    ): AppResult<List<GitHubWorkflow>>

    suspend fun listRuns(
        owner: String,
        repository: String,
        workflowId: Long? = null,
        status: WorkflowRunStatusFilter =
            WorkflowRunStatusFilter.ALL,
    ): AppResult<List<GitHubWorkflowRun>>

    suspend fun getRun(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<GitHubWorkflowRun>

    suspend fun listJobs(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<List<GitHubActionsJob>>

    suspend fun listArtifacts(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<List<GitHubActionsArtifact>>

    suspend fun dispatch(
        owner: String,
        repository: String,
        workflowId: Long,
        request: WorkflowDispatchRequest,
    ): AppResult<Unit>

    suspend fun cancelRun(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<Unit>

    suspend fun rerun(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<Unit>

    suspend fun rerunFailedJobs(
        owner: String,
        repository: String,
        runId: Long,
    ): AppResult<Unit>

    suspend fun getJobLog(
        owner: String,
        repository: String,
        jobId: Long,
    ): AppResult<GitHubActionsLog>

    suspend fun downloadArtifact(
        owner: String,
        repository: String,
        artifact: GitHubActionsArtifact,
    ): AppResult<GitHubArtifactDownload>
}
