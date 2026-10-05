package com.nexora.git.feature.git

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.git.GitAuthor
import com.nexora.git.core.git.GitBranch
import com.nexora.git.core.git.GitConflict
import com.nexora.git.core.git.GitEngine
import com.nexora.git.core.git.GitHistoryEntry
import com.nexora.git.core.git.GitMergeResult
import com.nexora.git.core.git.GitPullRequest
import com.nexora.git.core.git.GitPushRequest
import com.nexora.git.core.git.GitStatusEntry
import com.nexora.git.core.storage.WorkspaceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitWorkspaceUiState(
    val workspaceName: String = "",
    val repositoryPath: String = "",
    val remoteUrl: String = "",
    val branch: String = "",
    val entries: List<GitStatusEntry> = emptyList(),
    val history: List<GitHistoryEntry> = emptyList(),
    val branches: List<GitBranch> = emptyList(),
    val conflicts: List<GitConflict> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val stagedEntries: List<GitStatusEntry>
        get() = entries.filter { it.staged }

    val unstagedEntries: List<GitStatusEntry>
        get() = entries.filter {
            it.workingTree || it.untracked || it.conflicted
        }

    val localBranches: List<GitBranch>
        get() = branches.filterNot { it.remote }

    val remoteBranches: List<GitBranch>
        get() = branches.filter { it.remote }
}

@HiltViewModel
class GitWorkspaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspaceRegistry: WorkspaceRegistry,
    private val gitEngine: GitEngine,
) : ViewModel() {

    private val workspaceId =
        savedStateHandle.get<String>("workspaceId").orEmpty()

    private var workspacePath: String = ""

    private val mutableState = MutableStateFlow(
        GitWorkspaceUiState(),
    )
    val state: StateFlow<GitWorkspaceUiState> =
        mutableState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshInternal(showBusy = true)
        }
    }

    fun stage(path: String) {
        launchOperation("Staged " + path + ".") {
            val entry = state.value.entries.firstOrNull {
                it.path == path
            }
            require(entry?.conflicted != true) {
                "Resolve this conflict in the editor before staging it."
            }
            gitEngine.stage(
                repositoryPath = workspacePath,
                paths = listOf(path),
            )
        }
    }

    fun unstage(path: String) {
        launchOperation("Unstaged " + path + ".") {
            gitEngine.unstage(
                repositoryPath = workspacePath,
                paths = listOf(path),
            )
        }
    }

    fun stageAll() {
        val paths = state.value.entries
            .asSequence()
            .filter {
                !it.conflicted &&
                    (it.workingTree || it.untracked)
            }
            .map { it.path }
            .distinct()
            .toList()

        if (paths.isEmpty()) {
            showError("There are no resolvable working-tree changes to stage.")
            return
        }

        launchOperation(
            "Staged " + paths.size + " changed path(s).",
        ) {
            gitEngine.stage(workspacePath, paths)
        }
    }

    fun unstageAll() {
        val paths = state.value.stagedEntries
            .map { it.path }
            .distinct()

        if (paths.isEmpty()) {
            showError("There are no staged changes.")
            return
        }

        launchOperation(
            "Unstaged " + paths.size + " path(s).",
        ) {
            gitEngine.unstage(workspacePath, paths)
        }
    }

    fun commit(
        message: String,
        authorName: String,
        authorEmail: String,
    ) {
        if (message.isBlank()) {
            showError("Commit message is required.")
            return
        }
        val author = authorOrNull(authorName, authorEmail) ?: return

        if (state.value.stagedEntries.isEmpty()) {
            showError("Stage at least one change before committing.")
            return
        }

        launchOperation {
            val commit = gitEngine.commit(
                repositoryPath = workspacePath,
                message = message.trim(),
                author = author,
            )
            "Committed " + commit.oid.take(7) + "."
        }
    }

    fun createBranch(name: String) {
        val branch = runCatching {
            GitWorkflowPolicy.normalizeBranchName(name)
        }.getOrElse {
            showError(it.message ?: "Invalid branch name.")
            return
        }

        launchOperation("Created and switched to " + branch + ".") {
            gitEngine.createBranch(
                repositoryPath = workspacePath,
                name = branch,
            )
            gitEngine.checkout(
                repositoryPath = workspacePath,
                ref = branch,
            )
        }
    }

    fun checkout(branch: String) {
        launchOperation("Switched to " + branch + ".") {
            gitEngine.checkout(
                repositoryPath = workspacePath,
                ref = branch,
            )
        }
    }

    fun fetch() {
        launchOperation("Fetch completed.") {
            gitEngine.fetch(
                repositoryPath = workspacePath,
                remote = "origin",
            )
        }
    }

    fun pullMerge(
        authorName: String,
        authorEmail: String,
    ) {
        val author = authorOrNull(authorName, authorEmail) ?: return

        launchMergeOperation("Pull") {
            gitEngine.pull(
                GitPullRequest(
                    repositoryPath = workspacePath,
                    remote = "origin",
                    author = author,
                ),
            )
        }
    }

    fun merge(
        ref: String,
        authorName: String,
        authorEmail: String,
    ) {
        if (ref.isBlank()) {
            showError("Choose a branch or remote ref to merge.")
            return
        }
        val author = authorOrNull(authorName, authorEmail) ?: return

        launchMergeOperation("Merge " + ref) {
            gitEngine.merge(
                repositoryPath = workspacePath,
                ref = ref,
                author = author,
            )
        }
    }

    fun push(targetBranch: String) {
        val current = state.value.branch
        val target = targetBranch.ifBlank { current }

        val refspec = runCatching {
            GitWorkflowPolicy.pushRefspec(
                currentBranch = current,
                targetBranch = target,
            )
        }.getOrElse {
            showError(it.message ?: "Invalid push branch.")
            return
        }

        launchOperation {
            val result = gitEngine.push(
                GitPushRequest(
                    repositoryPath = workspacePath,
                    remote = "origin",
                    refspec = refspec,
                ),
            )
            "Pushed " + current + " to " +
                result.remote + "/" + target + "."
        }
    }

    fun markConflictResolved(path: String) {
        launchOperation("Marked " + path + " as resolved.") {
            val root = File(workspacePath).canonicalFile
            val file = File(root, path).canonicalFile
            val rootPrefix = root.path + File.separator

            require(
                file.path == root.path ||
                    file.path.startsWith(rootPrefix),
            ) {
                "Conflict path escapes the workspace."
            }
            require(file.isFile) {
                "Conflict file does not exist."
            }

            val text = file.readText()
            require(!GitWorkflowPolicy.hasConflictMarkers(text)) {
                "Conflict markers are still present. Finish editing before marking the file resolved."
            }

            gitEngine.stage(
                repositoryPath = workspacePath,
                paths = listOf(path),
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

    private fun load() {
        viewModelScope.launch {
            if (workspaceId.isBlank()) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Workspace is missing.",
                    )
                }
                return@launch
            }

            runCatching {
                workspaceRegistry.findById(workspaceId)
                    ?: error("Workspace not found")
            }.onSuccess { workspace ->
                workspacePath = workspace.workspacePath
                val gitDirectory = File(workspacePath, ".git")
                if (!gitDirectory.isDirectory) {
                    mutableState.update {
                        it.copy(
                            workspaceName = workspace.name,
                            repositoryPath = workspacePath,
                            loading = false,
                            errorMessage =
                                "This workspace is not initialized as a Git repository.",
                        )
                    }
                    return@onSuccess
                }

                mutableState.update {
                    it.copy(
                        workspaceName = workspace.name,
                        repositoryPath = workspacePath,
                    )
                }
                refreshInternal(showBusy = false)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message
                            ?: "Unable to open Git workspace.",
                    )
                }
            }
        }
    }

    private suspend fun refreshInternal(
        showBusy: Boolean,
    ) {
        if (workspacePath.isBlank()) return

        mutableState.update {
            it.copy(
                loading = if (showBusy) it.loading else true,
                refreshing = showBusy,
                errorMessage = null,
            )
        }

        runCatching {
            val status = gitEngine.status(workspacePath)
            val branches = gitEngine.branches(workspacePath)
            val history = gitEngine.history(
                repositoryPath = workspacePath,
                limit = 80,
            )
            val conflicts = if (status.conflicted) {
                gitEngine.conflicts(workspacePath)
            } else {
                emptyList()
            }
            val remoteUrl = runCatching {
                gitEngine.remoteUrl(
                    repositoryPath = workspacePath,
                    remote = "origin",
                )
            }.getOrNull().orEmpty()

            Snapshot(
                branch = status.branch,
                entries = status.entries,
                history = history,
                branches = branches,
                conflicts = conflicts,
                remoteUrl = remoteUrl,
            )
        }.onSuccess { snapshot ->
            runCatching {
                workspaceRegistry.bindRepository(
                    workspaceId = workspaceId,
                    remoteUrl = snapshot.remoteUrl
                        .takeIf { it.isNotBlank() },
                    currentBranch = snapshot.branch
                        .takeIf { it.isNotBlank() },
                    accountId = null,
                )
            }

            mutableState.update {
                it.copy(
                    branch = snapshot.branch,
                    entries = snapshot.entries,
                    history = snapshot.history,
                    branches = snapshot.branches,
                    conflicts = snapshot.conflicts,
                    remoteUrl = snapshot.remoteUrl,
                    loading = false,
                    refreshing = false,
                )
            }
        }.onFailure { error ->
            mutableState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    errorMessage = error.message
                        ?: "Unable to refresh Git state.",
                )
            }
        }
    }

    private fun launchOperation(
        successMessage: String,
        operation: suspend () -> Unit,
    ) {
        launchOperation {
            operation()
            successMessage
        }
    }

    private fun launchOperation(
        operation: suspend () -> String,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            runCatching {
                operation()
            }.onSuccess { message ->
                refreshInternal(showBusy = false)
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                        successMessage = message,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(operationInProgress = false)
                }
                showError(
                    error.message ?: "Git operation failed.",
                )
            }
        }
    }

    private fun launchMergeOperation(
        label: String,
        operation: suspend () -> GitMergeResult,
    ) {
        launchOperation {
            val result = operation()
            when {
                result.conflicts.isNotEmpty() ->
                    label + " produced " +
                        result.conflicts.size +
                        " conflict(s). Resolve them before committing."
                result.commitOid.isNotBlank() ->
                    label + " completed at " +
                        result.commitOid.take(7) + "."
                else ->
                    label + " completed: " +
                        result.state.name.lowercase()
                            .replace('_', ' ') + "."
            }
        }
    }

    private fun authorOrNull(
        name: String,
        email: String,
    ): GitAuthor? {
        if (name.isBlank() || email.isBlank()) {
            showError("Commit author name and email are required.")
            return null
        }
        return GitAuthor(
            name = name.trim(),
            email = email.trim(),
        )
    }

    private fun showError(message: String) {
        mutableState.update {
            it.copy(errorMessage = message)
        }
    }

    private data class Snapshot(
        val branch: String,
        val entries: List<GitStatusEntry>,
        val history: List<GitHistoryEntry>,
        val branches: List<GitBranch>,
        val conflicts: List<GitConflict>,
        val remoteUrl: String,
    )
}
