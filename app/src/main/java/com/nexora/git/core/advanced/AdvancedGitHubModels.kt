package com.nexora.git.core.advanced

data class AdvancedDiscussionCategory(
    val id: String,
    val name: String,
    val description: String?,
    val answerable: Boolean,
)

data class AdvancedDiscussionSummary(
    val id: String,
    val number: Int,
    val title: String,
    val url: String?,
    val categoryName: String,
    val authorLogin: String?,
    val comments: Int,
    val upvotes: Int,
    val answered: Boolean,
    val updatedAt: String?,
)

data class AdvancedDiscussionHub(
    val repositoryNodeId: String,
    val categories: List<AdvancedDiscussionCategory>,
    val discussions: List<AdvancedDiscussionSummary>,
)

data class AdvancedProjectSummary(
    val id: String,
    val number: Int,
    val title: String,
    val shortDescription: String?,
    val url: String?,
    val closed: Boolean,
    val publicProject: Boolean,
    val itemCount: Int,
    val updatedAt: String?,
)

data class AdvancedProjectHub(
    val ownerNodeId: String,
    val repositoryNodeId: String,
    val projects: List<AdvancedProjectSummary>,
)

data class GitHubPagesSite(
    val enabled: Boolean,
    val htmlUrl: String?,
    val status: String?,
    val cname: String?,
    val httpsEnforced: Boolean,
    val protectedDomainState: String?,
    val buildType: String?,
    val sourceBranch: String?,
    val sourcePath: String?,
)

enum class SecurityAlertKind {
    DEPENDABOT,
    CODE_SCANNING,
    SECRET_SCANNING,
}

data class SecurityAlertSummary(
    val id: String,
    val kind: SecurityAlertKind,
    val title: String,
    val severity: String?,
    val state: String,
    val htmlUrl: String?,
    val createdAt: String?,
)

data class SecurityAlertFeed(
    val available: Boolean,
    val message: String? = null,
    val alerts: List<SecurityAlertSummary> = emptyList(),
)

data class RepositorySecurityOverview(
    val dependabot: SecurityAlertFeed,
    val codeScanning: SecurityAlertFeed,
    val secretScanning: SecurityAlertFeed,
) {
    val totalVisibleAlerts: Int
        get() = dependabot.alerts.size +
            codeScanning.alerts.size +
            secretScanning.alerts.size
}

data class GitHubGistSummary(
    val id: String,
    val description: String?,
    val htmlUrl: String?,
    val publicGist: Boolean,
    val fileNames: List<String>,
    val comments: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

data class GitHubCodespaceSummary(
    val name: String,
    val displayName: String,
    val state: String,
    val repositoryFullName: String?,
    val machineName: String?,
    val webUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
)
