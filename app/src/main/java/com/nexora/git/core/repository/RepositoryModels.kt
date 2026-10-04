package com.nexora.git.core.repository

data class RepositoryPermissions(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = true,
) {
    val canManageSettings: Boolean
        get() = admin
}

data class RepositorySummary(
    val id: Long,
    val nodeId: String,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val description: String?,
    val privateRepository: Boolean,
    val fork: Boolean,
    val archived: Boolean,
    val visibility: String,
    val language: String?,
    val defaultBranch: String,
    val cloneUrl: String,
    val htmlUrl: String,
    val stars: Long,
    val forks: Long,
    val openIssues: Long,
    val sizeKb: Long,
    val updatedAt: String?,
    val pushedAt: String?,
    val permissions: RepositoryPermissions,
)

data class RepositoryDetails(
    val summary: RepositorySummary,
    val homepage: String?,
    val subscribers: Long,
    val hasIssues: Boolean,
    val hasWiki: Boolean,
    val hasProjects: Boolean,
    val hasPages: Boolean,
    val deleteBranchOnMerge: Boolean,
    val allowMergeCommit: Boolean,
    val allowSquashMerge: Boolean,
    val allowRebaseMerge: Boolean,
    val offlineSnapshot: Boolean = false,
)

enum class RepositorySubscriptionState {
    SUBSCRIBED,
    UNSUBSCRIBED,
    IGNORED,
    UNAVAILABLE,
    UNKNOWN,
}

data class RepositoryViewerState(
    val starred: Boolean,
    val subscription: RepositorySubscriptionState,
    val canSubscribe: Boolean,
)

data class CreateRepositoryRequest(
    val name: String,
    val description: String = "",
    val privateRepository: Boolean = true,
    val initializeWithReadme: Boolean = true,
)

data class UpdateRepositoryRequest(
    val description: String?,
    val homepage: String?,
    val hasIssues: Boolean,
    val hasWiki: Boolean,
    val deleteBranchOnMerge: Boolean,
)

data class ForkRepositoryRequest(
    val owner: String,
    val name: String,
    val defaultBranchOnly: Boolean = false,
)
