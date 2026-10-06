package com.nexora.git.feature.pulls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.pulls.CreateReviewCommentRequest
import com.nexora.git.core.pulls.PullRequestCheckRun
import com.nexora.git.core.pulls.PullRequestFile
import com.nexora.git.core.pulls.PullRequestGateway
import com.nexora.git.core.pulls.PullRequestMergeMethod
import com.nexora.git.core.pulls.PullRequestReview
import com.nexora.git.core.pulls.PullRequestReviewComment
import com.nexora.git.core.pulls.PullRequestReviewEvent
import com.nexora.git.core.pulls.PullRequestState
import com.nexora.git.core.pulls.PullRequestSummary
import com.nexora.git.core.pulls.UpdatePullRequestRequest
import com.nexora.git.core.repository.RepositoryDetails
import com.nexora.git.core.repository.RepositoryGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PullRequestDetailUiState(
    val owner: String = "",
    val repository: String = "",
    val number: Int = 0,
    val pullRequest: PullRequestSummary? = null,
    val repositoryDetails: RepositoryDetails? = null,
    val files: List<PullRequestFile> = emptyList(),
    val reviews: List<PullRequestReview> = emptyList(),
    val reviewComments: List<PullRequestReviewComment> =
        emptyList(),
    val checks: List<PullRequestCheckRun> = emptyList(),
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val allowedMergeMethods: List<PullRequestMergeMethod>
        get() = buildList {
            repositoryDetails?.let { details ->
                if (details.allowMergeCommit) {
                    add(PullRequestMergeMethod.MERGE)
                }
                if (details.allowSquashMerge) {
                    add(PullRequestMergeMethod.SQUASH)
                }
                if (details.allowRebaseMerge) {
                    add(PullRequestMergeMethod.REBASE)
                }
            }
        }

    val successfulChecks: Int
        get() = checks.count {
            it.conclusion.equals("success", ignoreCase = true)
        }

    val failedChecks: Int
        get() = checks.count {
            when (it.conclusion?.lowercase()) {
                "failure",
                "timed_out",
                "cancelled",
                "action_required" -> true
                else -> false
            }
        }
}

@HiltViewModel
class PullRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gateway: PullRequestGateway,
    private val repositories: RepositoryGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()
    private val number =
        savedStateHandle.get<Int>("number") ?: 0

    private val mutableState = MutableStateFlow(
        PullRequestDetailUiState(
            owner = owner,
            repository = repository,
            number = number,
        ),
    )
    val state: StateFlow<PullRequestDetailUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(loading = true, errorMessage = null)
            }

            val pullDeferred = async {
                gateway.getPullRequest(owner, repository, number)
            }
            val repoDeferred = async {
                repositories.getRepository(owner, repository)
            }
            val filesDeferred = async {
                gateway.listFiles(owner, repository, number)
            }
            val reviewsDeferred = async {
                gateway.listReviews(owner, repository, number)
            }
            val commentsDeferred = async {
                gateway.listReviewComments(
                    owner,
                    repository,
                    number,
                )
            }

            val pull = pullDeferred.await()
            val repo = repoDeferred.await()
            val files = filesDeferred.await()
            val reviews = reviewsDeferred.await()
            val comments = commentsDeferred.await()

            val headSha =
                (pull as? AppResult.Success)
                    ?.value
                    ?.head
                    ?.sha
                    .orEmpty()

            val checks = if (headSha.isNotBlank()) {
                gateway.listChecks(
                    owner,
                    repository,
                    headSha,
                )
            } else {
                AppResult.Success(emptyList())
            }

            mutableState.update { current ->
                current.copy(
                    pullRequest =
                        (pull as? AppResult.Success)?.value
                            ?: current.pullRequest,
                    repositoryDetails =
                        (repo as? AppResult.Success)?.value
                            ?: current.repositoryDetails,
                    files =
                        (files as? AppResult.Success)?.value
                            ?: current.files,
                    reviews =
                        (reviews as? AppResult.Success)?.value
                            ?: current.reviews,
                    reviewComments =
                        (comments as? AppResult.Success)?.value
                            ?: current.reviewComments,
                    checks =
                        (checks as? AppResult.Success)?.value
                            ?: current.checks,
                    loading = false,
                    errorMessage = firstFailure(
                        pull,
                        repo,
                        files,
                        reviews,
                        comments,
                        checks,
                    ),
                )
            }
        }
    }

    fun update(request: UpdatePullRequestRequest) {
        runOperation {
            when (
                val result = gateway.updatePullRequest(
                    owner,
                    repository,
                    number,
                    request,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            pullRequest = result.value,
                            successMessage =
                                "Pull request updated.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun setState(value: PullRequestState) {
        if (value == PullRequestState.ALL) return
        update(UpdatePullRequestRequest(state = value))
    }

    fun setDraft(draft: Boolean) {
        val pull = state.value.pullRequest ?: return
        runOperation {
            when (
                val result = gateway.setDraft(
                    nodeId = pull.nodeId,
                    draft = draft,
                )
            ) {
                is AppResult.Success -> {
                    refreshPullOnly()
                    mutableState.update {
                        it.copy(
                            successMessage =
                                if (draft) {
                                    "Pull request converted to draft."
                                } else {
                                    "Pull request is ready for review."
                                },
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun submitReview(
        event: PullRequestReviewEvent,
        body: String,
    ) {
        runOperation {
            when (
                val result = gateway.submitReview(
                    owner,
                    repository,
                    number,
                    event,
                    body,
                )
            ) {
                is AppResult.Success -> {
                    refreshReviews()
                    mutableState.update {
                        it.copy(
                            successMessage =
                                when (event) {
                                    PullRequestReviewEvent.APPROVE ->
                                        "Review approved."
                                    PullRequestReviewEvent.REQUEST_CHANGES ->
                                        "Changes requested."
                                    PullRequestReviewEvent.COMMENT ->
                                        "Review comment submitted."
                                },
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun createReviewComment(
        request: CreateReviewCommentRequest,
    ) {
        runOperation {
            when (
                val result = gateway.createReviewComment(
                    owner,
                    repository,
                    number,
                    request,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            reviewComments =
                                current.reviewComments +
                                    result.value,
                            successMessage =
                                "Inline review comment added.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun updateReviewComment(
        commentId: Long,
        body: String,
    ) {
        runOperation {
            when (
                val result = gateway.updateReviewComment(
                    owner,
                    repository,
                    commentId,
                    body,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            reviewComments =
                                current.reviewComments.map {
                                    if (it.id == commentId) {
                                        result.value
                                    } else {
                                        it
                                    }
                                },
                            successMessage =
                                "Review comment updated.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun deleteReviewComment(commentId: Long) {
        runOperation {
            when (
                val result = gateway.deleteReviewComment(
                    owner,
                    repository,
                    commentId,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            reviewComments =
                                current.reviewComments.filterNot {
                                    it.id == commentId
                                },
                            successMessage =
                                "Review comment deleted.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun merge(method: PullRequestMergeMethod) {
        val pull = state.value.pullRequest ?: return
        runOperation {
            when (
                val result = gateway.merge(
                    owner = owner,
                    repository = repository,
                    number = number,
                    method = method,
                    expectedHeadSha = pull.head.sha,
                )
            ) {
                is AppResult.Success -> {
                    if (result.value.merged) {
                        refreshPullOnly()
                        mutableState.update {
                            it.copy(
                                successMessage =
                                    "Pull request merged with " +
                                        method.wireValue + ".",
                            )
                        }
                    } else {
                        mutableState.update {
                            it.copy(
                                errorMessage =
                                    result.value.message
                                        ?: "GitHub did not merge the pull request.",
                            )
                        }
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun dismissSuccess() {
        mutableState.update { it.copy(successMessage = null) }
    }

    private suspend fun refreshPullOnly() {
        when (
            val result = gateway.getPullRequest(
                owner,
                repository,
                number,
            )
        ) {
            is AppResult.Success -> {
                mutableState.update {
                    it.copy(pullRequest = result.value)
                }
            }
            is AppResult.Failure -> Unit
        }
    }

    private suspend fun refreshReviews() {
        val reviews = gateway.listReviews(
            owner,
            repository,
            number,
        )
        val comments = gateway.listReviewComments(
            owner,
            repository,
            number,
        )
        mutableState.update { current ->
            current.copy(
                reviews =
                    (reviews as? AppResult.Success)?.value
                        ?: current.reviews,
                reviewComments =
                    (comments as? AppResult.Success)?.value
                        ?: current.reviewComments,
            )
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
                errorMessage =
                    result.error.toPullRequestMessage(),
            )
        }
    }

    private fun firstFailure(
        vararg results: AppResult<*>,
    ): String? =
        results.firstNotNullOfOrNull {
            (it as? AppResult.Failure)
                ?.error
                ?.toPullRequestMessage()
        }
}
