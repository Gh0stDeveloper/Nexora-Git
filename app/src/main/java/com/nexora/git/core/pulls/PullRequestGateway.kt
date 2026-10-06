package com.nexora.git.core.pulls

import com.nexora.git.core.common.AppResult

interface PullRequestGateway {

    suspend fun listPullRequests(
        owner: String,
        repository: String,
        state: PullRequestState = PullRequestState.OPEN,
    ): AppResult<List<PullRequestSummary>>

    suspend fun getPullRequest(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<PullRequestSummary>

    suspend fun createPullRequest(
        owner: String,
        repository: String,
        request: CreatePullRequestRequest,
    ): AppResult<PullRequestSummary>

    suspend fun updatePullRequest(
        owner: String,
        repository: String,
        number: Int,
        request: UpdatePullRequestRequest,
    ): AppResult<PullRequestSummary>

    suspend fun listFiles(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<PullRequestFile>>

    suspend fun listReviews(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<PullRequestReview>>

    suspend fun listReviewComments(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<PullRequestReviewComment>>

    suspend fun createReviewComment(
        owner: String,
        repository: String,
        number: Int,
        request: CreateReviewCommentRequest,
    ): AppResult<PullRequestReviewComment>

    suspend fun updateReviewComment(
        owner: String,
        repository: String,
        commentId: Long,
        body: String,
    ): AppResult<PullRequestReviewComment>

    suspend fun deleteReviewComment(
        owner: String,
        repository: String,
        commentId: Long,
    ): AppResult<Unit>

    suspend fun submitReview(
        owner: String,
        repository: String,
        number: Int,
        event: PullRequestReviewEvent,
        body: String = "",
    ): AppResult<PullRequestReview>

    suspend fun listChecks(
        owner: String,
        repository: String,
        headSha: String,
    ): AppResult<List<PullRequestCheckRun>>

    suspend fun merge(
        owner: String,
        repository: String,
        number: Int,
        method: PullRequestMergeMethod,
        expectedHeadSha: String,
    ): AppResult<PullRequestMergeResult>

    suspend fun setDraft(
        nodeId: String,
        draft: Boolean,
    ): AppResult<Unit>
}
