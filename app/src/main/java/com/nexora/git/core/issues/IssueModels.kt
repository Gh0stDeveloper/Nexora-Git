package com.nexora.git.core.issues

data class IssueUser(
    val login: String,
    val avatarUrl: String?,
)

data class IssueLabel(
    val id: Long,
    val name: String,
    val color: String,
    val description: String?,
)

data class IssueMilestone(
    val number: Int,
    val title: String,
    val state: String,
    val openIssues: Int,
    val closedIssues: Int,
    val dueAt: String?,
)

data class IssueReactionSummary(
    val totalCount: Int,
    val plusOne: Int,
    val minusOne: Int,
    val laugh: Int,
    val hooray: Int,
    val confused: Int,
    val heart: Int,
    val rocket: Int,
    val eyes: Int,
)

data class IssueSummary(
    val id: Long,
    val nodeId: String,
    val number: Int,
    val title: String,
    val state: String,
    val locked: Boolean,
    val author: IssueUser,
    val labels: List<IssueLabel>,
    val assignees: List<IssueUser>,
    val milestone: IssueMilestone?,
    val comments: Int,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String?,
    val htmlUrl: String,
)

data class IssueDetails(
    val summary: IssueSummary,
    val body: String?,
    val reactions: IssueReactionSummary,
)

data class IssueComment(
    val id: Long,
    val nodeId: String,
    val body: String,
    val author: IssueUser,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val reactions: IssueReactionSummary,
)

data class IssueReaction(
    val id: Long,
    val content: String,
    val user: IssueUser,
)

enum class IssueState(
    val wireValue: String,
) {
    OPEN("open"),
    CLOSED("closed"),
    ALL("all"),
}

enum class IssueSort(
    val wireValue: String,
) {
    CREATED("created"),
    UPDATED("updated"),
    COMMENTS("comments"),
}

enum class IssueDirection(
    val wireValue: String,
) {
    DESC("desc"),
    ASC("asc"),
}

data class IssueFilters(
    val query: String = "",
    val state: IssueState = IssueState.OPEN,
    val labels: Set<String> = emptySet(),
    val assignee: String? = null,
    val milestone: String? = null,
    val sort: IssueSort = IssueSort.UPDATED,
    val direction: IssueDirection = IssueDirection.DESC,
)

data class CreateIssueRequest(
    val title: String,
    val body: String = "",
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    val milestone: Int? = null,
)

data class UpdateIssueRequest(
    val title: String? = null,
    val body: String? = null,
    val state: IssueState? = null,
    val labels: List<String>? = null,
    val assignees: List<String>? = null,
    val milestone: Int? = null,
    val clearMilestone: Boolean = false,
)

enum class IssueReactionContent(
    val wireValue: String,
) {
    PLUS_ONE("+1"),
    MINUS_ONE("-1"),
    LAUGH("laugh"),
    CONFUSED("confused"),
    HEART("heart"),
    HOORAY("hooray"),
    ROCKET("rocket"),
    EYES("eyes"),
}
