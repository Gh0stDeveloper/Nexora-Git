package com.nexora.git.feature.pulls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.pulls.CreatePullRequestRequest
import com.nexora.git.core.pulls.PullRequestGateway
import com.nexora.git.core.pulls.PullRequestState
import com.nexora.git.core.pulls.PullRequestSummary
import com.nexora.git.core.repository.RepositoryGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PullRequestsUiState(
    val owner: String = "",
    val repository: String = "",
    val defaultBranch: String = "main",
    val stateFilter: PullRequestState = PullRequestState.OPEN,
    val pullRequests: List<PullRequestSummary> = emptyList(),
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class PullRequestsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pullRequests: PullRequestGateway,
    private val repositories: RepositoryGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()

    private val mutableState = MutableStateFlow(
        PullRequestsUiState(
            owner = owner,
            repository = repository,
        ),
    )
    val state: StateFlow<PullRequestsUiState> =
        mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(loading = true, errorMessage = null)
            }

            val repoDeferred = async {
                repositories.getRepository(owner, repository)
            }
            val pullsDeferred = async {
                pullRequests.listPullRequests(
                    owner,
                    repository,
                    state.value.stateFilter,
                )
            }

            val repoResult = repoDeferred.await()
            val pullsResult = pullsDeferred.await()

            mutableState.update { current ->
                current.copy(
                    defaultBranch =
                        (repoResult as? AppResult.Success)
                            ?.value
                            ?.summary
                            ?.defaultBranch
                            ?.takeIf(String::isNotBlank)
                            ?: current.defaultBranch,
                    pullRequests =
                        (pullsResult as? AppResult.Success)
                            ?.value
                            ?: current.pullRequests,
                    loading = false,
                    errorMessage =
                        (pullsResult as? AppResult.Failure)
                            ?.error
                            ?.toPullRequestMessage()
                            ?: (repoResult as? AppResult.Failure)
                                ?.error
                                ?.toPullRequestMessage(),
                )
            }
        }
    }

    fun setStateFilter(value: PullRequestState) {
        mutableState.update {
            it.copy(stateFilter = value)
        }
        refreshPullRequests()
    }

    fun create(request: CreatePullRequestRequest) {
        runOperation {
            when (
                val result = pullRequests.createPullRequest(
                    owner,
                    repository,
                    request,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            successMessage =
                                "Pull request #" +
                                    result.value.number +
                                    " created.",
                        )
                    }
                    refreshPullRequests()
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

    private fun refreshPullRequests() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(loading = true, errorMessage = null)
            }
            when (
                val result = pullRequests.listPullRequests(
                    owner,
                    repository,
                    state.value.stateFilter,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            pullRequests = result.value,
                            loading = false,
                        )
                    }
                }
                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            loading = false,
                            errorMessage =
                                result.error.toPullRequestMessage(),
                        )
                    }
                }
            }
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
}
