package com.nexora.git.core.repository

import com.nexora.git.core.auth.AuthSessionRepository
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.git.GitCloneRequest
import com.nexora.git.core.git.GitEngine
import com.nexora.git.core.git.GitLfsTransport
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceRegistry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedGitWorkspace(
    val workspace: Workspace,
    val initialized: Boolean,
    val branch: String,
    val changedPaths: Int,
)

@Singleton
class RepositoryWorkspaceCoordinator @Inject constructor(
    private val gitEngine: GitEngine,
    private val workspaceRegistry: WorkspaceRegistry,
    private val authSessionRepository: AuthSessionRepository,
    private val lfsTransport: GitLfsTransport,
) {

    suspend fun clone(
        repository: RepositorySummary,
    ): AppResult<Workspace> {
        val accountId = authSessionRepository.getActiveAccountId()
            ?: return AppResult.Failure(
                AppError.Authentication(
                    "No active GitHub account",
                ),
            )

        val existing = workspaceRegistry.findByRemote(
            repository.cloneUrl,
        )

        if (existing != null &&
            File(existing.workspacePath, ".git").isDirectory
        ) {
            workspaceRegistry.markOpened(existing.id)
            return AppResult.Success(existing)
        }

        val reserved = try {
            workspaceRegistry.prepareRemoteClone(
                name = repository.name,
                fullName = repository.fullName,
                remoteUrl = repository.cloneUrl,
                defaultBranch = repository.defaultBranch,
                accountId = accountId,
            )
        } catch (error: Exception) {
            return AppResult.Failure(AppError.Unknown(error))
        }

        return try {
            gitEngine.clone(
                GitCloneRequest(
                    url = repository.cloneUrl,
                    destinationPath = reserved.workspacePath,
                ),
            )

            if (lfsTransport.supportsRemote(repository.cloneUrl)) {
                runCatching {
                    lfsTransport.downloadMissing(
                        repositoryPath = reserved.workspacePath,
                        remoteUrl = repository.cloneUrl,
                    )
                }
            }

            val status = gitEngine.status(reserved.workspacePath)
            val completed = workspaceRegistry.completeRemoteClone(
                workspaceId = reserved.id,
                currentBranch = status.branch,
            )

            AppResult.Success(completed.workspace)
        } catch (error: Exception) {
            workspaceRegistry.discardRemoteClone(reserved.id)
            AppResult.Failure(AppError.Unknown(error))
        }
    }

    suspend fun importWorkspace(
        workspaceId: String,
    ): AppResult<ImportedGitWorkspace> {
        val workspace = workspaceRegistry.findById(workspaceId)
            ?: return AppResult.Failure(
                AppError.NotFound("Workspace not found"),
            )

        val directory = File(workspace.workspacePath)
        if (!directory.isDirectory) {
            return AppResult.Failure(
                AppError.NotFound(
                    "Workspace directory is unavailable",
                ),
            )
        }

        return try {
            val gitDirectory = File(directory, ".git")
            val initialized = !gitDirectory.isDirectory

            if (initialized) {
                gitEngine.init(workspace.workspacePath)
            }

            val status = gitEngine.status(workspace.workspacePath)
            val accountId = authSessionRepository.getActiveAccountId()

            val updated = workspaceRegistry.bindRepository(
                workspaceId = workspace.id,
                remoteUrl = workspace.repositoryRemote,
                currentBranch = status.branch,
                accountId = accountId,
            )

            AppResult.Success(
                ImportedGitWorkspace(
                    workspace = updated,
                    initialized = initialized,
                    branch = status.branch,
                    changedPaths = status.entries.size,
                ),
            )
        } catch (error: Exception) {
            AppResult.Failure(AppError.Unknown(error))
        }
    }
}
