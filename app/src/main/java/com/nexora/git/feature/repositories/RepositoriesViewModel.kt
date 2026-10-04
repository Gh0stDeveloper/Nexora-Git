package com.nexora.git.feature.repositories

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.storage.ProjectRisk
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepositoriesUiState(
    val workspaces: List<Workspace> = emptyList(),
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val recentRisks: List<ProjectRisk> = emptyList(),
    val recentProjectName: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class RepositoriesViewModel @Inject constructor(
    private val workspaceRegistry: WorkspaceRegistry,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        RepositoriesUiState(),
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
    }

    fun importTree(uri: Uri) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                )
            }

            runCatching {
                workspaceRegistry.importSafTree(uri)
            }.onSuccess { result ->
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        recentRisks = result.scan.risks,
                        recentProjectName = result.workspace.name,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        errorMessage = error.message
                            ?: "Project import failed.",
                    )
                }
            }
        }
    }

    fun sync(workspaceId: String) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                )
            }

            runCatching {
                workspaceRegistry.sync(workspaceId)
            }.onSuccess { result ->
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        recentRisks = result.scan.risks,
                        recentProjectName = result.workspace.name,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        errorMessage = error.message
                            ?: "Workspace sync failed.",
                    )
                }
            }
        }
    }

    fun delete(workspaceId: String) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                )
            }

            runCatching {
                workspaceRegistry.delete(workspaceId)
            }.onSuccess {
                mutableState.update {
                    it.copy(operationInProgress = false)
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        errorMessage = error.message
                            ?: "Unable to remove workspace.",
                    )
                }
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
}
