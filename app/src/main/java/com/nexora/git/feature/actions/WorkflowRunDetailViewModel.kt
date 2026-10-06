package com.nexora.git.feature.actions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.actions.GitHubActionsArtifact
import com.nexora.git.core.actions.GitHubActionsGateway
import com.nexora.git.core.actions.GitHubActionsJob
import com.nexora.git.core.actions.GitHubActionsLog
import com.nexora.git.core.actions.GitHubArtifactDownload
import com.nexora.git.core.actions.GitHubWorkflowRun
import com.nexora.git.core.common.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkflowRunDetailUiState(
    val owner: String = "",
    val repository: String = "",
    val runId: Long = 0L,
    val run: GitHubWorkflowRun? = null,
    val jobs: List<GitHubActionsJob> = emptyList(),
    val artifacts: List<GitHubActionsArtifact> = emptyList(),
    val selectedJobId: Long? = null,
    val selectedJobName: String? = null,
    val log: GitHubActionsLog? = null,
    val lastDownload: GitHubArtifactDownload? = null,
    val loading: Boolean = true,
    val logLoading: Boolean = false,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val canCancel: Boolean
        get() = when (run?.status) {
            "queued",
            "in_progress",
            "waiting",
            "pending",
            "requested" -> true
            else -> false
        }

    val canRerun: Boolean
        get() = run?.status == "completed"
}

@HiltViewModel
class WorkflowRunDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actions: GitHubActionsGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()
    private val runId =
        savedStateHandle.get<Long>("runId") ?: 0L

    private val mutableState = MutableStateFlow(
        WorkflowRunDetailUiState(
            owner = owner,
            repository = repository,
            runId = runId,
        ),
    )
    val state: StateFlow<WorkflowRunDetailUiState> =
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

            val runDeferred = async {
                actions.getRun(owner, repository, runId)
            }
            val jobsDeferred = async {
                actions.listJobs(owner, repository, runId)
            }
            val artifactsDeferred = async {
                actions.listArtifacts(
                    owner,
                    repository,
                    runId,
                )
            }

            val runResult = runDeferred.await()
            val jobsResult = jobsDeferred.await()
            val artifactResult = artifactsDeferred.await()

            mutableState.update { current ->
                current.copy(
                    run =
                        (runResult as? AppResult.Success)
                            ?.value
                            ?: current.run,
                    jobs =
                        (jobsResult as? AppResult.Success)
                            ?.value
                            ?: current.jobs,
                    artifacts =
                        (artifactResult as? AppResult.Success)
                            ?.value
                            ?: current.artifacts,
                    loading = false,
                    errorMessage = firstFailure(
                        runResult,
                        jobsResult,
                        artifactResult,
                    ),
                )
            }
        }
    }

    fun loadJobLog(job: GitHubActionsJob) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    selectedJobId = job.id,
                    selectedJobName = job.name,
                    log = null,
                    logLoading = true,
                    errorMessage = null,
                )
            }

            when (
                val result = actions.getJobLog(
                    owner = owner,
                    repository = repository,
                    jobId = job.id,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            log = result.value,
                            logLoading = false,
                        )
                    }
                }
                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            logLoading = false,
                            errorMessage =
                                result.error.toActionsMessage(),
                        )
                    }
                }
            }
        }
    }

    fun closeLog() {
        mutableState.update {
            it.copy(
                selectedJobId = null,
                selectedJobName = null,
                log = null,
                logLoading = false,
            )
        }
    }

    fun cancel() {
        runOperation(
            success = "Cancellation requested.",
        ) {
            actions.cancelRun(owner, repository, runId)
        }
    }

    fun rerun() {
        runOperation(
            success = "Re-run requested.",
        ) {
            actions.rerun(owner, repository, runId)
        }
    }

    fun rerunFailedJobs() {
        runOperation(
            success = "Failed jobs re-run requested.",
        ) {
            actions.rerunFailedJobs(
                owner,
                repository,
                runId,
            )
        }
    }

    fun downloadArtifact(
        artifact: GitHubActionsArtifact,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                    lastDownload = null,
                )
            }

            when (
                val result = actions.downloadArtifact(
                    owner,
                    repository,
                    artifact,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            lastDownload = result.value,
                            successMessage =
                                "Artifact saved as " +
                                    result.value.fileName +
                                    ".",
                        )
                    }
                }
                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            errorMessage =
                                result.error.toActionsMessage(),
                        )
                    }
                }
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

    private fun runOperation(
        success: String,
        action: suspend () -> AppResult<Unit>,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (val result = action()) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            successMessage = success,
                        )
                    }
                    refresh()
                }
                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            errorMessage =
                                result.error.toActionsMessage(),
                        )
                    }
                }
            }
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
