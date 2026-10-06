package com.nexora.git.core.pulls

data class PullRequestUser(
    val login: String,
    val avatarUrl: String?,
)

data class PullRequestRef(
    val label: String,
    val ref: String,
    val sha: String,
    val repositoryFullName: String?,
)

data class PullRequestSummary(
    val id: Long,
    val nodeId: String,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val draft: Boolean,
    val locked: Boolean,
    val merged: Boolean,
    val mergeable: Boolean?,
    val mergeableState: String?,
    val author: PullRequestUser,
    val head: PullRequestRef,
    val base: PullRequestRef,
    val comments: Int,
    val reviewComments: Int,
    val commits: Int,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String?,
    val mergedAt: String?,
    val htmlUrl: String,
)

data class PullRequestFile(
    val sha: String,
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val blobUrl: String?,
    val rawUrl: String?,
    val previousFilename: String?,
    val patch: String?,
)

data class PullRequestReview(
    val id: Long,
    val nodeId: String,
    val author: PullRequestUser,
    val body: String?,
    val state: String,
    val htmlUrl: String?,
    val submittedAt: String?,
    val commitId: String?,
)

data class PullRequestReviewComment(
    val id: Long,
    val nodeId: String,
    val reviewId: Long?,
    val author: PullRequestUser,
    val body: String,
    val path: String,
    val diffHunk: String?,
    val line: Int?,
    val side: String?,
    val startLine: Int?,
    val startSide: String?,
    val commitId: String?,
    val inReplyToId: Long?,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String?,
)

data class PullRequestCheckRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val detailsUrl: String?,
    val startedAt: String?,
    val completedAt: String?,
)

data class CreatePullRequestRequest(
    val title: String,
    val body: String = "",
    val head: String,
    val base: String,
    val draft: Boolean = false,
    val maintainerCanModify: Boolean = true,
)

data class UpdatePullRequestRequest(
    val title: String? = null,
    val body: String? = null,
    val state: PullRequestState? = null,
    val base: String? = null,
    val maintainerCanModify: Boolean? = null,
)

data class CreateReviewCommentRequest(
    val body: String,
    val commitId: String,
    val path: String,
    val line: Int,
    val side: ReviewSide,
    val startLine: Int? = null,
    val startSide: ReviewSide? = null,
)

enum class PullRequestState(
    val wireValue: String,
) {
    OPEN("open"),
    CLOSED("closed"),
    ALL("all"),
}

enum class PullRequestReviewEvent(
    val wireValue: String,
) {
    APPROVE("APPROVE"),
    REQUEST_CHANGES("REQUEST_CHANGES"),
    COMMENT("COMMENT"),
}

enum class ReviewSide(
    val wireValue: String,
) {
    LEFT("LEFT"),
    RIGHT("RIGHT"),
}

enum class PullRequestMergeMethod(
    val wireValue: String,
) {
    MERGE("merge"),
    SQUASH("squash"),
    REBASE("rebase"),
}

data class PullRequestMergeResult(
    val sha: String?,
    val merged: Boolean,
    val message: String?,
)
