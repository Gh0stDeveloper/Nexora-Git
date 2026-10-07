package com.nexora.git.feature.repositories

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.repository.CreateRepositoryRequest
import com.nexora.git.core.repository.GitHubRepositoryUrlParser
import com.nexora.git.core.repository.RepositoryGateway
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.repository.RepositoryWorkspaceCoordinator
import com.nexora.git.core.storage.ProjectRisk
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceRegistry
import com.nexora.git.core.templates.ProjectTemplateManager
import com.nexora.git.core.templates.ProjectTemplateSummary
import com.nexora.git.core.work.DurableRepositoryOperations
import com.nexora.git.core.work.RepositoryCloneWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepositoriesUiState(
    val remoteRepositories: List<RepositorySummary> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
    val templates: List<ProjectTemplateSummary> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val operationInProgress: Boolean = false,
    val durableCloneId: String? = null,
    val durableClonePhase: String? = null,
    val recentRisks: List<ProjectRisk> = emptyList(),
    val recentProjectName: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class RepositoriesViewModel @Inject constructor(
    private val workspaceRegistry: WorkspaceRegistry,
    private val repositoryGateway: RepositoryGateway,
    private val workspaceCoordinator: RepositoryWorkspaceCoordinator,
    private val urlParser: GitHubRepositoryUrlParser,
    private val templateManager: ProjectTemplateManager,
    private val durableOperations: DurableRepositoryOperations,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        RepositoriesUiState(templates = templateManager.templates),
    )
    val state: StateFlow<RepositoriesUiState> =
        mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceRegistry.workspaces.collect { workspaces ->
                mutableState.update {
                    it.copy(
                        workspaces = workspaces,
                        loading = false,
                    )
                }
            }
        }

        viewModelScope.launch {
            repositoryGateway.cachedRepositories.collect { repositories ->
                mutableState.update {
                    it.copy(
                        remoteRepositories = repositories,
                        loading = false,
                    )
                }
            }
        }

        restoreDurableClone()
        refreshRepositories()
    }

    fun refreshRepositories() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    refreshing = true,
                    errorMessage = null,
                )
            }

            when (val result = repositoryGateway.refreshRepositories()) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            refreshing = false,
                            remoteRepositories = result.value,
                        )
                    }
                }

                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            refreshing = false,
                            errorMessage =
                                result.error.toRepositoryMessage(),
                        )
                    }
                }
            }
        }
    }

    fun createRepository(
        request: CreateRepositoryRequest,
    ) {
        runOperation {
            when (val result = repositoryGateway.createRepository(request)) {
                is AppResult.Success -> {
                    refreshRepositories()
                    success(
                        "Created " + result.value.summary.fullName + ".",
                    )
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
            }
        }
    }

    fun cloneRepository(
        repository: RepositorySummary,
    ) {
        viewModelScope.launch {
            scheduleDurableClone(repository)
        }
    }

    fun cloneUrl(url: String) {
        val coordinates = urlParser.parse(url)
        if (coordinates == null) {
            failure(
                "Use a valid https://github.com/owner/repository URL.",
            )
            return
        }

        viewModelScope.launch {
            setOperationBusy()
            when (
                val repository = repositoryGateway.getRepository(
                    coordinates.owner,
                    coordinates.name,
                )
            ) {
                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(operationInProgress = false)
                    }
                    failure(repository.error.toRepositoryMessage())
                }

                is AppResult.Success -> {
                    scheduleDurableClone(
                        repository.value.summary,
                        alreadyBusy = true,
                    )
                }
            }
        }
    }

    fun cancelDurableClone() {
        val id = mutableState.value.durableCloneId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        durableOperations.cancel(id)
    }

    fun importTree(uri: Uri) {
        runOperation {
            runCatching {
                workspaceRegistry.importSafTree(uri)
            }.onSuccess { result ->
                mutableState.update {
                    it.copy(
                        recentRisks = result.scan.risks,
                        recentProjectName = result.workspace.name,
                    )
                }
                success("Project folder imported.")
            }.onFailure { error ->
                failure(
                    error.message ?: "Project import failed.",
                )
            }
        }
    }

    fun createFromTemplate(
        templateId: String,
        projectName: String,
    ) {
        runOperation {
            runCatching {
                templateManager.create(templateId, projectName)
            }.onSuccess { workspace ->
                success(
                    workspace.name +
                        " was created from a project template.",
                )
            }.onFailure { error ->
                failure(
                    error.message ?: "Unable to create project template.",
                )
            }
        }
    }

    fun initializeWorkspaceGit(workspaceId: String) {
        runOperation {
            when (
                val result = workspaceCoordinator.importWorkspace(
                    workspaceId,
                )
            ) {
                is AppResult.Success -> {
                    val prefix = if (result.value.initialized) {
                        "Git initialized"
                    } else {
                        "Git repository opened"
                    }

                    success(
                        prefix + " on " +
                            result.value.workspace.name +
                            " · " +
                            result.value.branch.ifBlank { "unborn branch" } +
                            " · " +
                            result.value.changedPaths +
                            " changed paths.",
                    )
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
            }
        }
    }

    fun sync(workspaceId: String) {
        runOperation {
            runCatching {
                workspaceRegistry.sync(workspaceId)
            }.onSuccess { result ->
                mutableState.update {
                    it.copy(
                        recentRisks = result.scan.risks,
                        recentProjectName = result.workspace.name,
                    )
                }
                success("Workspace refreshed.")
            }.onFailure { error ->
                failure(
                    error.message ?: "Workspace sync failed.",
                )
            }
        }
    }

    fun delete(workspaceId: String) {
        runOperation {
            runCatching {
                workspaceRegistry.delete(workspaceId)
            }.onSuccess {
                success("Workspace removed from Nexora Git.")
            }.onFailure { error ->
                failure(
                    error.message ?: "Unable to remove workspace.",
                )
            }
        }
    }

    fun dismissRisks() {
        mutableState.update {
            it.copy(
                recentRisks = emptyList(),
                recentProjectName = null,
            )
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

    private fun restoreDurableClone() {
        val rawId = savedStateHandle.get<String>(KEY_ACTIVE_CLONE_ID)
            ?: return
        val id = runCatching { UUID.fromString(rawId) }.getOrNull()
            ?: run {
                clearSavedClone()
                return
            }
        val name = savedStateHandle.get<String>(KEY_ACTIVE_CLONE_NAME)
            ?: "Repository"

        viewModelScope.launch {
            observeDurableClone(id, name)
        }
    }

    private suspend fun scheduleDurableClone(
        repository: RepositorySummary,
        alreadyBusy: Boolean = false,
    ) {
        if (!alreadyBusy) {
            setOperationBusy()
        }

        try {
            val id = durableOperations.enqueueClone(repository)
            savedStateHandle[KEY_ACTIVE_CLONE_ID] = id.toString()
            savedStateHandle[KEY_ACTIVE_CLONE_NAME] = repository.fullName
            observeDurableClone(id, repository.fullName)
        } catch (error: Exception) {
            clearSavedClone()
            mutableState.update {
                it.copy(
                    operationInProgress = false,
                    durableCloneId = null,
                    durableClonePhase = null,
                )
            }
            failure(error.message ?: "Unable to schedule repository clone.")
        }
    }

    private suspend fun observeDurableClone(
        id: UUID,
        repositoryName: String,
    ) {
        mutableState.update {
            it.copy(
                operationInProgress = true,
                durableCloneId = id.toString(),
                durableClonePhase = "queued",
                errorMessage = null,
                successMessage = null,
            )
        }

        val terminal = durableOperations.observe(id).first { info ->
            val phase = info.progress
                .getString(RepositoryCloneWorker.KEY_PHASE)
                ?: when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    -> "queued"
                    WorkInfo.State.RUNNING -> "clone"
                    else -> null
                }
            mutableState.update {
                it.copy(durableClonePhase = phase)
            }
            info.state.isFinished
        }

        clearSavedClone()
        mutableState.update {
            it.copy(
                operationInProgress = false,
                durableCloneId = null,
                durableClonePhase = null,
            )
        }

        when (terminal.state) {
            WorkInfo.State.SUCCEEDED -> {
                success("$repositoryName is ready on this device.")
            }

            WorkInfo.State.CANCELLED -> {
                failure("Repository clone was canceled.")
            }

            WorkInfo.State.FAILED -> {
                failure(
                    terminal.outputData
                        .getString(RepositoryCloneWorker.KEY_ERROR)
                        ?.takeIf(String::isNotBlank)
                        ?: "Repository clone failed.",
                )
            }

            else -> Unit
        }
    }

    private fun clearSavedClone() {
        savedStateHandle.remove<String>(KEY_ACTIVE_CLONE_ID)
        savedStateHandle.remove<String>(KEY_ACTIVE_CLONE_NAME)
    }

    private fun setOperationBusy() {
        mutableState.update {
            it.copy(
                operationInProgress = true,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            setOperationBusy()

            try {
                operation()
            } finally {
                mutableState.update {
                    it.copy(operationInProgress = false)
                }
            }
        }
    }

    private fun success(message: String) {
        mutableState.update {
            it.copy(
                successMessage = message,
                errorMessage = null,
            )
        }
    }

    private fun failure(message: String) {
        mutableState.update {
            it.copy(
                errorMessage = message,
                successMessage = null,
            )
        }
    }

    companion object {
        private const val KEY_ACTIVE_CLONE_ID =
            "active_durable_clone_id"
        private const val KEY_ACTIVE_CLONE_NAME =
            "active_durable_clone_name"
    }
}
