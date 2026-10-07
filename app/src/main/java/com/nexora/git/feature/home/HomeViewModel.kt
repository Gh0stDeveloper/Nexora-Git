package com.nexora.git.feature.home

import com.nexora.git.core.git.GitDivergence
import com.nexora.git.core.git.GitEngine
import com.nexora.git.core.repository.RepositoryGateway
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceRegistry
import com.nexora.git.core.storage.WorkspaceSyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeWorkspaceSummary(
    val id: String,
    val name: String,
    val branch: String?,
    val changedPaths: Int,
    val conflictedPaths: Int,
    val ahead: Long?,
    val behind: Long?,
    val syncState: WorkspaceSyncState,
    val lastOpenedAtEpochMillis: Long,
)

data class HomeUiState(
    val loading: Boolean = true,
    val recentWorkspaces: List<HomeWorkspaceSummary> = emptyList(),
    val recentRepositories: List<RepositorySummary> = emptyList(),
    val workspaceCount: Int = 0,
    val repositoryCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    workspaceRegistry: WorkspaceRegistry,
    repositoryGateway: RepositoryGateway,
    private val gitEngine: GitEngine,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = combine(
        workspaceRegistry.workspaces,
        repositoryGateway.cachedRepositories,
    ) { workspaces, repositories ->
        workspaces to repositories
    }.mapLatest { (workspaces, repositories) ->
        HomeUiState(
            loading = false,
            recentWorkspaces = buildList {
                for (workspace in workspaces.take(MAX_RECENT_WORKSPACES)) {
                    add(workspaceSummary(workspace))
                }
            },
            recentRepositories = repositories
                .sortedByDescending { it.updatedAt.orEmpty() }
                .take(MAX_RECENT_REPOSITORIES),
            workspaceCount = workspaces.size,
            repositoryCount = repositories.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private suspend fun workspaceSummary(
        workspace: Workspace,
    ): HomeWorkspaceSummary {
        val repositoryRoot = File(workspace.workspacePath)
        val hasGit = File(repositoryRoot, ".git").isDirectory

        if (!hasGit) {
            return HomeWorkspaceSummary(
                id = workspace.id,
                name = workspace.name,
                branch = workspace.currentBranch,
                changedPaths = 0,
                conflictedPaths = workspace.syncConflictCount,
                ahead = null,
                behind = null,
                syncState = workspace.syncState,
                lastOpenedAtEpochMillis = workspace.lastOpenedAtEpochMillis,
            )
        }

        val status = runCatching {
            gitEngine.status(workspace.workspacePath)
        }.getOrNull()

        val divergence = status?.branch
            ?.takeIf(String::isNotBlank)
            ?.let { branch ->
                resolveDivergence(
                    workspacePath = workspace.workspacePath,
                    branch = branch,
                )
            }

        return HomeWorkspaceSummary(
            id = workspace.id,
            name = workspace.name,
            branch = status?.branch
                ?.takeIf(String::isNotBlank)
                ?: workspace.currentBranch,
            changedPaths = status?.entries?.size ?: 0,
            conflictedPaths = status?.entries
                ?.count { it.conflicted }
                ?: workspace.syncConflictCount,
            ahead = divergence?.ahead,
            behind = divergence?.behind,
            syncState = workspace.syncState,
            lastOpenedAtEpochMillis = workspace.lastOpenedAtEpochMillis,
        )
    }

    private suspend fun resolveDivergence(
        workspacePath: String,
        branch: String,
    ): GitDivergence? {
        val upstream = runCatching {
            gitEngine.branches(workspacePath)
                .firstOrNull { it.head }
                ?.upstream
                ?.takeIf(String::isNotBlank)
        }.getOrNull() ?: return null

        return runCatching {
            gitEngine.divergence(
                repositoryPath = workspacePath,
                localRef = branch,
                upstreamRef = upstream,
            )
        }.getOrNull()
    }

    companion object {
        private const val MAX_RECENT_WORKSPACES = 3
        private const val MAX_RECENT_REPOSITORIES = 4
    }
}
