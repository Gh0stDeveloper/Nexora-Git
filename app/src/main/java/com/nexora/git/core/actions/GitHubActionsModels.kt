package com.nexora.git.core.actions

data class GitHubWorkflow(
    val id: Long,
    val nodeId: String,
    val name: String,
    val path: String,
    val state: String,
    val createdAt: String?,
    val updatedAt: String?,
    val htmlUrl: String?,
    val badgeUrl: String?,
)

data class GitHubWorkflowRun(
    val id: Long,
    val nodeId: String,
    val name: String?,
    val displayTitle: String?,
    val event: String,
    val status: String?,
    val conclusion: String?,
    val workflowId: Long,
    val runNumber: Long,
    val runAttempt: Long,
    val headBranch: String?,
    val headSha: String,
    val htmlUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val runStartedAt: String?,
    val actorLogin: String?,
)

data class GitHubActionsStep(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int,
    val startedAt: String?,
    val completedAt: String?,
)

data class GitHubActionsJob(
    val id: Long,
    val runId: Long,
    val runAttempt: Long,
    val nodeId: String,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String?,
    val completedAt: String?,
    val htmlUrl: String?,
    val runnerName: String?,
    val runnerGroupName: String?,
    val labels: List<String>,
    val steps: List<GitHubActionsStep>,
)

data class GitHubActionsArtifact(
    val id: Long,
    val nodeId: String,
    val name: String,
    val sizeInBytes: Long,
    val expired: Boolean,
    val createdAt: String?,
    val expiresAt: String?,
    val updatedAt: String?,
    val workflowRunId: Long?,
)

data class GitHubActionsLog(
    val text: String,
    val truncated: Boolean,
)

data class GitHubArtifactDownload(
    val filePath: String,
    val fileName: String,
    val sizeInBytes: Long,
)

data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap(),
)

enum class WorkflowRunStatusFilter(
    val wireValue: String?,
) {
    ALL(null),
    QUEUED("queued"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILURE("failure"),
    SUCCESS("success"),
    CANCELLED("cancelled"),
}
