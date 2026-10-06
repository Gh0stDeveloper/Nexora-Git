package com.nexora.git.feature.actions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.actions.GitHubActionsGateway
import com.nexora.git.core.actions.GitHubWorkflow
import com.nexora.git.core.actions.GitHubWorkflowRun
import com.nexora.git.core.actions.WorkflowDispatchRequest
import com.nexora.git.core.actions.WorkflowRunStatusFilter
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.repository.RepositoryGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActionsUiState(
    val owner: String = "",
    val repository: String = "",
    val defaultBranch: String = "main",
    val workflows: List<GitHubWorkflow> = emptyList(),
    val selectedWorkflowId: Long? = null,
    val runs: List<GitHubWorkflowRun> = emptyList(),
    val statusFilter: WorkflowRunStatusFilter =
        WorkflowRunStatusFilter.ALL,
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class ActionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actions: GitHubActionsGateway,
    private val repositories: RepositoryGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()

    private val mutableState = MutableStateFlow(
        ActionsUiState(
            owner = owner,
            repository = repository,
        ),
    )
    val state: StateFlow<ActionsUiState> =
        mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            val workflowsDeferred = async {
                actions.listWorkflows(owner, repository)
            }
            val repoDeferred = async {
                repositories.getRepository(owner, repository)
            }
            val runsDeferred = async {
                actions.listRuns(
                    owner = owner,
                    repository = repository,
                    workflowId =
                        state.value.selectedWorkflowId,
                    status = state.value.statusFilter,
                )
            }

            val workflowResult = workflowsDeferred.await()
            val repoResult = repoDeferred.await()
            val runResult = runsDeferred.await()

            mutableState.update { current ->
                current.copy(
                    workflows =
                        (workflowResult as? AppResult.Success)
                            ?.value
                            ?: current.workflows,
                    defaultBranch =
                        (repoResult as? AppResult.Success)
                            ?.value
                            ?.summary
                            ?.defaultBranch
                            ?.takeIf(String::isNotBlank)
                            ?: current.defaultBranch,
                    runs =
                        (runResult as? AppResult.Success)
                            ?.value
                            ?: current.runs,
                    loading = false,
                    errorMessage = firstFailure(
                        workflowResult,
                        repoResult,
                        runResult,
                    ),
                )
            }
        }
    }

    fun selectWorkflow(workflowId: Long?) {
        mutableState.update {
            it.copy(selectedWorkflowId = workflowId)
        }
        refreshRuns()
    }

    fun setStatusFilter(
        value: WorkflowRunStatusFilter,
    ) {
        mutableState.update {
            it.copy(statusFilter = value)
        }
        refreshRuns()
    }

    fun dispatch(
        workflowId: Long,
        ref: String,
        inputs: Map<String, String>,
    ) {
        runOperation {
            when (
                val result = actions.dispatch(
                    owner = owner,
                    repository = repository,
                    workflowId = workflowId,
                    request = WorkflowDispatchRequest(
                        ref = ref,
                        inputs = inputs,
                    ),
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            successMessage =
                                "Workflow dispatch accepted by GitHub.",
                        )
                    }
                    refreshRunsInternal()
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

    private fun refreshRuns() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }
            refreshRunsInternal()
            mutableState.update {
                it.copy(loading = false)
            }
        }
    }

    private suspend fun refreshRunsInternal() {
        when (
            val result = actions.listRuns(
                owner = owner,
                repository = repository,
                workflowId =
                    state.value.selectedWorkflowId,
                status = state.value.statusFilter,
            )
        ) {
            is AppResult.Success -> {
                mutableState.update {
                    it.copy(runs = result.value)
                }
            }
            is AppResult.Failure -> fail(result)
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
                    result.error.toActionsMessage(),
            )
        }
    }

    private fun firstFailure(
        vararg results: AppResult<*>,
    ): String? =
        results.firstNotNullOfOrNull {
            (it as? AppResult.Failure)
                ?.error
                ?.toActionsMessage()
        }
}
