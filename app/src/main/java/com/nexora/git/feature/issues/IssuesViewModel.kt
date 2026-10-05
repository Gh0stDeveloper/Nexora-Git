package com.nexora.git.feature.issues

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.issues.CreateIssueRequest
import com.nexora.git.core.issues.IssueFilters
import com.nexora.git.core.issues.IssueGateway
import com.nexora.git.core.issues.IssueLabel
import com.nexora.git.core.issues.IssueMilestone
import com.nexora.git.core.issues.IssueState
import com.nexora.git.core.issues.IssueSummary
import com.nexora.git.core.issues.IssueUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IssuesUiState(
    val owner: String = "",
    val repository: String = "",
    val issues: List<IssueSummary> = emptyList(),
    val labels: List<IssueLabel> = emptyList(),
    val assignees: List<IssueUser> = emptyList(),
    val milestones: List<IssueMilestone> = emptyList(),
    val filters: IssueFilters = IssueFilters(),
    val loading: Boolean = true,
    val metadataLoading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class IssuesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gateway: IssueGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()

    private val mutableState = MutableStateFlow(
        IssuesUiState(
            owner = owner,
            repository = repository,
        ),
    )
    val state: StateFlow<IssuesUiState> =
        mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    metadataLoading = true,
                    errorMessage = null,
                )
            }

            val filters = state.value.filters
            val issuesDeferred = async {
                gateway.listIssues(owner, repository, filters)
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

            val issues = issuesDeferred.await()
            val labels = labelsDeferred.await()
            val assignees = assigneesDeferred.await()
            val milestones = milestonesDeferred.await()

            mutableState.update { current ->
                current.copy(
                    issues = (issues as? AppResult.Success)
                        ?.value ?: current.issues,
                    labels = (labels as? AppResult.Success)
                        ?.value ?: current.labels,
                    assignees = (assignees as? AppResult.Success)
                        ?.value ?: current.assignees,
                    milestones = (milestones as? AppResult.Success)
                        ?.value ?: current.milestones,
                    loading = false,
                    metadataLoading = false,
                    errorMessage =
                        firstFailure(
                            issues,
                            labels,
                            assignees,
                            milestones,
                        ),
                )
            }
        }
    }

    fun applyFilters(filters: IssueFilters) {
        mutableState.update {
            it.copy(filters = filters)
        }
        refreshIssues()
    }

    fun setQuery(query: String) {
        applyFilters(
            state.value.filters.copy(query = query),
        )
    }

    fun setState(issueState: IssueState) {
        applyFilters(
            state.value.filters.copy(state = issueState),
        )
    }

    fun toggleLabel(name: String) {
        val current = state.value.filters
        val labels = current.labels.toMutableSet()
        if (!labels.add(name)) {
            labels.remove(name)
        }
        applyFilters(current.copy(labels = labels))
    }

    fun setAssignee(login: String?) {
        applyFilters(
            state.value.filters.copy(assignee = login),
        )
    }

    fun setMilestone(value: String?) {
        applyFilters(
            state.value.filters.copy(milestone = value),
        )
    }

    fun clearFilters() {
        applyFilters(IssueFilters())
    }

    fun createIssue(request: CreateIssueRequest) {
        runOperation {
            when (
                val result = gateway.createIssue(
                    owner = owner,
                    repository = repository,
                    request = request,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            successMessage =
                                "Issue #" +
                                    result.value.summary.number +
                                    " created.",
                        )
                    }
                    refreshIssues()
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

    private fun refreshIssues() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            when (
                val result = gateway.listIssues(
                    owner = owner,
                    repository = repository,
                    filters = state.value.filters,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            issues = result.value,
                            loading = false,
                        )
                    }
                }

                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            loading = false,
                            errorMessage =
                                result.error.toIssueMessage(),
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

    private fun fail(
        result: AppResult.Failure,
    ) {
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
