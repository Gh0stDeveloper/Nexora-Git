package com.nexora.git.feature.issues

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.issues.IssueComment
import com.nexora.git.core.issues.IssueDetails
import com.nexora.git.core.issues.IssueGateway
import com.nexora.git.core.issues.IssueLabel
import com.nexora.git.core.issues.IssueMilestone
import com.nexora.git.core.issues.IssueReactionContent
import com.nexora.git.core.issues.IssueState
import com.nexora.git.core.issues.IssueUser
import com.nexora.git.core.issues.UpdateIssueRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IssueDetailUiState(
    val owner: String = "",
    val repository: String = "",
    val number: Int = 0,
    val issue: IssueDetails? = null,
    val comments: List<IssueComment> = emptyList(),
    val labels: List<IssueLabel> = emptyList(),
    val assignees: List<IssueUser> = emptyList(),
    val milestones: List<IssueMilestone> = emptyList(),
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gateway: IssueGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()
    private val number =
        savedStateHandle.get<Int>("number") ?: 0

    private val mutableState = MutableStateFlow(
        IssueDetailUiState(
            owner = owner,
            repository = repository,
            number = number,
        ),
    )
    val state: StateFlow<IssueDetailUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            val issueDeferred = async {
                gateway.getIssue(owner, repository, number)
            }
            val commentsDeferred = async {
                gateway.listComments(owner, repository, number)
            }
            val labelsDeferred = async {
                gateway.listLabels(owner, repository)
            }
            val assigneesDeferred = async {
                gateway.listAssignees(owner, repository)
            }
            val milestonesDeferred = async {
                gateway.listMilestones(owner, repository)
            }

            val issue = issueDeferred.await()
            val comments = commentsDeferred.await()
            val labels = labelsDeferred.await()
            val assignees = assigneesDeferred.await()
            val milestones = milestonesDeferred.await()

            mutableState.update { current ->
                current.copy(
                    issue = (issue as? AppResult.Success)
                        ?.value ?: current.issue,
                    comments = (comments as? AppResult.Success)
                        ?.value ?: current.comments,
                    labels = (labels as? AppResult.Success)
                        ?.value ?: current.labels,
                    assignees = (assignees as? AppResult.Success)
                        ?.value ?: current.assignees,
                    milestones = (milestones as? AppResult.Success)
                        ?.value ?: current.milestones,
                    loading = false,
                    errorMessage =
                        firstFailure(
                            issue,
                            comments,
                            labels,
                            assignees,
                            milestones,
                        ),
                )
            }
        }
    }

    fun updateIssue(request: UpdateIssueRequest) {
        runOperation {
            when (
                val result = gateway.updateIssue(
                    owner = owner,
                    repository = repository,
                    number = number,
                    request = request,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            issue = result.value,
                            successMessage = "Issue updated.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun setState(state: IssueState) {
        if (state == IssueState.ALL) return
        updateIssue(UpdateIssueRequest(state = state))
    }

    fun createComment(body: String) {
        runOperation {
            when (
                val result = gateway.createComment(
                    owner = owner,
                    repository = repository,
                    number = number,
                    body = body,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            comments = it.comments + result.value,
                            successMessage = "Comment posted.",
                        )
                    }
                    refreshIssueOnly()
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun updateComment(
        commentId: Long,
        body: String,
    ) {
        runOperation {
            when (
                val result = gateway.updateComment(
                    owner = owner,
                    repository = repository,
                    commentId = commentId,
                    body = body,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            comments = current.comments.map {
                                if (it.id == commentId) {
                                    result.value
                                } else {
                                    it
                                }
                            },
                            successMessage = "Comment updated.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun deleteComment(commentId: Long) {
        runOperation {
            when (
                val result = gateway.deleteComment(
                    owner = owner,
                    repository = repository,
                    commentId = commentId,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            comments = current.comments.filterNot {
                                it.id == commentId
                            },
                            successMessage = "Comment deleted.",
                        )
                    }
                    refreshIssueOnly()
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun reactToIssue(content: IssueReactionContent) {
        runOperation {
            when (
                val result = gateway.addIssueReaction(
                    owner = owner,
                    repository = repository,
                    number = number,
                    content = content,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            successMessage =
                                "Reaction " +
                                    result.value.content +
                                    " added.",
                        )
                    }
                    refreshIssueOnly()
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun reactToComment(
        commentId: Long,
        content: IssueReactionContent,
    ) {
        runOperation {
            when (
                val result = gateway.addCommentReaction(
                    owner = owner,
                    repository = repository,
                    commentId = commentId,
                    content = content,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            successMessage =
                                "Reaction " +
                                    result.value.content +
                                    " added.",
                        )
                    }
                    refreshCommentsOnly()
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun dismissError() {
        mutableState.update {
            it.copy(errorMessage = null)
        }
    }

    fun dismissSuccess() {
        mutableState.update {
            it.copy(successMessage = null)
        }
    }

    private suspend fun refreshIssueOnly() {
        when (
            val result = gateway.getIssue(
                owner,
                repository,
                number,
            )
        ) {
            is AppResult.Success -> {
                mutableState.update {
                    it.copy(issue = result.value)
                }
            }
            is AppResult.Failure -> Unit
        }
    }

    private suspend fun refreshCommentsOnly() {
        when (
            val result = gateway.listComments(
                owner,
                repository,
                number,
            )
        ) {
            is AppResult.Success -> {
                mutableState.update {
                    it.copy(comments = result.value)
                }
            }
            is AppResult.Failure -> Unit
        }
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            try {
                operation()
            } finally {
                mutableState.update {
                    it.copy(operationInProgress = false)
                }
            }
        }
    }

    private fun fail(result: AppResult.Failure) {
        mutableState.update {
            it.copy(
                errorMessage = result.error.toIssueMessage(),
                successMessage = null,
            )
        }
    }

    private fun firstFailure(
        vararg results: AppResult<*>,
    ): String? =
        results.firstNotNullOfOrNull { result ->
            (result as? AppResult.Failure)
                ?.error
                ?.toIssueMessage()
        }
}
