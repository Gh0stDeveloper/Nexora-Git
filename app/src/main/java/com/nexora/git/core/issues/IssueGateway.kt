package com.nexora.git.core.issues

import com.nexora.git.core.common.AppResult

interface IssueGateway {

    suspend fun listIssues(
        owner: String,
        repository: String,
        filters: IssueFilters = IssueFilters(),
    ): AppResult<List<IssueSummary>>

    suspend fun getIssue(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<IssueDetails>

    suspend fun createIssue(
        owner: String,
        repository: String,
        request: CreateIssueRequest,
    ): AppResult<IssueDetails>

    suspend fun updateIssue(
        owner: String,
        repository: String,
        number: Int,
        request: UpdateIssueRequest,
    ): AppResult<IssueDetails>

    suspend fun listLabels(
        owner: String,
        repository: String,
    ): AppResult<List<IssueLabel>>

    suspend fun listAssignees(
        owner: String,
        repository: String,
    ): AppResult<List<IssueUser>>

    suspend fun listMilestones(
        owner: String,
        repository: String,
    ): AppResult<List<IssueMilestone>>

    suspend fun listComments(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<IssueComment>>

    suspend fun createComment(
        owner: String,
        repository: String,
        number: Int,
        body: String,
    ): AppResult<IssueComment>

    suspend fun updateComment(
        owner: String,
        repository: String,
        commentId: Long,
        body: String,
    ): AppResult<IssueComment>

    suspend fun deleteComment(
        owner: String,
        repository: String,
        commentId: Long,
    ): AppResult<Unit>

    suspend fun addIssueReaction(
        owner: String,
        repository: String,
        number: Int,
        content: IssueReactionContent,
    ): AppResult<IssueReaction>

    suspend fun addCommentReaction(
        owner: String,
        repository: String,
        commentId: Long,
        content: IssueReactionContent,
    ): AppResult<IssueReaction>

    suspend fun deleteReaction(
        owner: String,
        repository: String,
        reactionId: Long,
    ): AppResult<Unit>
}
