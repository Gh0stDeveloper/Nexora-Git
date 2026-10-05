package com.nexora.git.feature.git

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.git.GitAuthor
import com.nexora.git.core.git.GitBranch
import com.nexora.git.core.git.GitConflict
import com.nexora.git.core.git.GitConflictResolution
import com.nexora.git.core.git.GitDivergence
import com.nexora.git.core.git.GitEngine
import com.nexora.git.core.git.GitHistoryEntry
import com.nexora.git.core.git.GitMergeResult
import com.nexora.git.core.git.GitPullRequest
import com.nexora.git.core.git.GitPullStrategy
import com.nexora.git.core.git.GitPushRequest
import com.nexora.git.core.git.GitRemote
import com.nexora.git.core.git.GitRepositoryOperationState
import com.nexora.git.core.git.GitStatusEntry
import com.nexora.git.core.storage.WorkspaceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GitWorkspaceUiState(
    val workspaceName: String = "",
    val repositoryPath: String = "",
    val branch: String = "",
    val entries: List<GitStatusEntry> = emptyList(),
    val history: List<GitHistoryEntry> = emptyList(),
    val branches: List<GitBranch> = emptyList(),
    val remotes: List<GitRemote> = emptyList(),
    val conflicts: List<GitConflict> = emptyList(),
    val divergence: GitDivergence? = null,
    val repositoryState: GitRepositoryOperationState =
        GitRepositoryOperationState.NONE,
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

    val currentBranch: GitBranch?
        get() = localBranches.firstOrNull { it.head }

    val currentUpstream: String
        get() = currentBranch?.upstream.orEmpty()

    val rebaseInProgress: Boolean
        get() = repositoryState == GitRepositoryOperationState.REBASE

    val mergeInProgress: Boolean
        get() = repositoryState == GitRepositoryOperationState.MERGE
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
            showError(
                "There are no resolvable working-tree changes to stage.",
            )
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

        if (state.value.repositoryState !=
            GitRepositoryOperationState.NONE
        ) {
            showError(
                "Finish the current merge or rebase before creating a normal commit.",
            )
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
        if (state.value.repositoryState !=
            GitRepositoryOperationState.NONE
        ) {
            showError(
                "Finish or abort the current Git operation before switching branches.",
            )
            return
        }

        launchOperation("Switched to " + branch + ".") {
            gitEngine.checkout(
                repositoryPath = workspacePath,
                ref = branch,
            )
        }
    }

    fun addRemote(
        name: String,
        url: String,
    ) {
        val normalizedName = runCatching {
            GitWorkflowPolicy.normalizeRemoteName(name)
        }.getOrElse {
            showError(it.message ?: "Invalid remote name.")
            return
        }
        val normalizedUrl = runCatching {
            GitWorkflowPolicy.normalizeGitHubRemoteUrl(url)
        }.getOrElse {
            showError(it.message ?: "Invalid remote URL.")
            return
        }

        launchOperation("Added remote " + normalizedName + ".") {
            gitEngine.addRemote(
                repositoryPath = workspacePath,
                name = normalizedName,
                url = normalizedUrl,
            )
        }
    }

    fun renameRemote(
        oldName: String,
        newName: String,
    ) {
        val normalized = runCatching {
            GitWorkflowPolicy.normalizeRemoteName(newName)
        }.getOrElse {
            showError(it.message ?: "Invalid remote name.")
            return
        }

        launchOperation(
            "Renamed " + oldName + " to " + normalized + ".",
        ) {
            gitEngine.renameRemote(
                repositoryPath = workspacePath,
                oldName = oldName,
                newName = normalized,
            )
        }
    }

    fun removeRemote(name: String) {
        launchOperation("Removed remote " + name + ".") {
            gitEngine.removeRemote(
                repositoryPath = workspacePath,
                name = name,
            )
        }
    }

    fun setUpstream(upstream: String?) {
        val current = state.value.branch
        if (current.isBlank()) {
            showError("A checked-out local branch is required.")
            return
        }

        launchOperation(
            if (upstream.isNullOrBlank()) {
                "Removed upstream from " + current + "."
            } else {
                "Tracking " + upstream + "."
            },
        ) {
            gitEngine.setUpstream(
                repositoryPath = workspacePath,
                branch = current,
                upstream = upstream,
            )
        }
    }

    fun fetch(remote: String) {
        val normalized = runCatching {
            GitWorkflowPolicy.normalizeRemoteName(remote)
        }.getOrElse {
            showError(it.message ?: "Invalid remote.")
            return
        }

        launchOperation("Fetched " + normalized + ".") {
            gitEngine.fetch(
                repositoryPath = workspacePath,
                remote = normalized,
            )
        }
    }

    fun pull(
        remote: String,
        strategy: GitPullStrategy,
        authorName: String,
        authorEmail: String,
    ) {
        if (state.value.conflicts.isNotEmpty()) {
            showError(
                "Resolve the current conflicts before pulling again.",
            )
            return
        }

        val author = authorOrNull(authorName, authorEmail) ?: return
        val normalized = runCatching {
            GitWorkflowPolicy.normalizeRemoteName(remote)
        }.getOrElse {
            showError(it.message ?: "Invalid remote.")
            return
        }

        launchMergeOperation(
            label = when (strategy) {
                GitPullStrategy.MERGE -> "Pull with merge"
                GitPullStrategy.FAST_FORWARD_ONLY ->
                    "Fast-forward-only pull"
                GitPullStrategy.REBASE -> "Pull with rebase"
            },
        ) {
            gitEngine.pull(
                GitPullRequest(
                    repositoryPath = workspacePath,
                    remote = normalized,
                    author = author,
                    strategy = strategy,
                ),
            )
        }
    }

    fun continueMerge(
        authorName: String,
        authorEmail: String,
    ) {
        val author = authorOrNull(authorName, authorEmail) ?: return

        launchMergeOperation("Continue merge") {
            gitEngine.continueMerge(
                repositoryPath = workspacePath,
                author = author,
            )
        }
    }

    fun continueRebase(
        authorName: String,
        authorEmail: String,
    ) {
        val author = authorOrNull(authorName, authorEmail) ?: return

        launchMergeOperation("Continue rebase") {
            gitEngine.continueRebase(
                repositoryPath = workspacePath,
                author = author,
            )
        }
    }

    fun abortRebase() {
        launchOperation("Rebase aborted and original branch restored.") {
            gitEngine.abortRebase(workspacePath)
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

    fun push(
        remote: String,
        targetBranch: String,
        forceWithLease: Boolean,
    ) {
        val current = state.value.branch
        if (current.isBlank()) {
            showError("Push requires a checked-out local branch.")
            return
        }

        val normalizedRemote = runCatching {
            GitWorkflowPolicy.normalizeRemoteName(remote)
        }.getOrElse {
            showError(it.message ?: "Invalid remote.")
            return
        }

        val target = runCatching {
            GitWorkflowPolicy.normalizeBranchName(
                targetBranch.ifBlank { current },
            )
        }.getOrElse {
            showError(it.message ?: "Invalid push branch.")
            return
        }

        val refspec = runCatching {
            GitWorkflowPolicy.pushRefspec(
                currentBranch = current,
                targetBranch = target,
            )
        }.getOrElse {
            showError(it.message ?: "Invalid push refspec.")
            return
        }

        launchOperation {
            val expectedOid = if (forceWithLease) {
                val remoteTracking =
                    normalizedRemote + "/" + target
                gitEngine.divergence(
                    repositoryPath = workspacePath,
                    localRef = current,
                    upstreamRef = remoteTracking,
                ).upstreamOid.also {
                    require(it.isNotBlank()) {
                        "Force-with-lease requires a verified remote-tracking branch. Fetch first."
                    }
                }
            } else {
                ""
            }

            val result = gitEngine.push(
                GitPushRequest(
                    repositoryPath = workspacePath,
                    remote = normalizedRemote,
                    refspec = refspec,
                    forceWithLease = forceWithLease,
                    expectedRemoteOid = expectedOid,
                ),
            )

            if (!forceWithLease) {
                val upstream =
                    normalizedRemote + "/" + target

                runCatching {
                    gitEngine.fetch(
                        repositoryPath = workspacePath,
                        remote = normalizedRemote,
                    )
                    gitEngine.setUpstream(
                        repositoryPath = workspacePath,
                        branch = current,
                        upstream = upstream,
                    )
                }
            }

            if (result.forceWithLease) {
                "Force-with-lease push completed to " +
                    normalizedRemote + "/" + target + "."
            } else {
                "Pushed " + current + " to " +
                    normalizedRemote + "/" + target + "."
            }
        }
    }

    fun resolveConflictSide(
        path: String,
        resolution: GitConflictResolution,
    ) {
        launchOperation(
            "Resolved " + path + " using " +
                resolution.name.lowercase() + ".",
        ) {
            gitEngine.resolveConflict(
                repositoryPath = workspacePath,
                path = path,
                resolution = resolution,
            )
        }
    }

    fun markConflictResolved(path: String) {
        launchOperation("Marked " + path + " as resolved.") {
            withContext(Dispatchers.IO) {
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
                require(
                    !GitWorkflowPolicy.hasConflictMarkers(text),
                ) {
                    "Conflict markers are still present. Finish editing before marking the file resolved."
                }
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
            val repositoryState =
                gitEngine.repositoryState(workspacePath)
            val conflicts = if (
                status.conflicted ||
                repositoryState !=
                    GitRepositoryOperationState.NONE
            ) {
                gitEngine.conflicts(workspacePath)
            } else {
                emptyList()
            }
            val remotes = gitEngine.remotes(workspacePath)

            val current = branches.firstOrNull {
                !it.remote && it.head
            }
            val divergence = current
                ?.upstream
                ?.takeIf(String::isNotBlank)
                ?.let { upstream ->
                    runCatching {
                        gitEngine.divergence(
                            repositoryPath = workspacePath,
                            localRef = status.branch,
                            upstreamRef = upstream,
                        )
                    }.getOrNull()
                }

            Snapshot(
                branch = status.branch,
                entries = status.entries,
                history = history,
                branches = branches,
                remotes = remotes,
                conflicts = conflicts,
                divergence = divergence,
                repositoryState = repositoryState,
            )
        }.onSuccess { snapshot ->
            val origin = snapshot.remotes.firstOrNull {
                it.name == "origin"
            }

            runCatching {
                workspaceRegistry.bindRepository(
                    workspaceId = workspaceId,
                    remoteUrl = origin?.url,
                    currentBranch = snapshot.branch
                        .takeIf(String::isNotBlank),
                    accountId = null,
                )
            }

            mutableState.update {
                it.copy(
                    branch = snapshot.branch,
                    entries = snapshot.entries,
                    history = snapshot.history,
                    branches = snapshot.branches,
                    remotes = snapshot.remotes,
                    conflicts = snapshot.conflicts,
                    divergence = snapshot.divergence,
                    repositoryState =
                        snapshot.repositoryState,
                    loading = false,
                    refreshing = false,
                )
            }
        }.onFailure { error ->
            mutableState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    errorMessage = friendlyGitError(error),
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
                showError(friendlyGitError(error))
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
                        " conflict(s). Resolve them in the editor."
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
            showError(
                "Commit author name and email are required.",
            )
            return null
        }
        return GitAuthor(
            name = name.trim(),
            email = email.trim(),
        )
    }

    private fun friendlyGitError(error: Throwable): String {
        val raw = error.message
            ?.takeIf(String::isNotBlank)
            ?: "Git operation failed."
        val text = raw.lowercase()

        return when {
            "non-fast" in text ||
                "nonfast" in text ->
                "The remote branch contains commits that are not in your local branch. Fetch first, review ahead/behind, then merge, rebase, or use Force with lease only when you intentionally need to rewrite the remote branch."

            "force-with-lease rejected" in text ->
                "Force with lease was rejected because the remote branch changed after your last verified state. Fetch again and review the new commits before retrying."

            "protected branch" in text ||
                "protected_branch" in text ->
                "GitHub rejected this update because branch protection or a repository rule applies. Nexora Git will not bypass those protections."

            else -> raw
        }
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
        val remotes: List<GitRemote>,
        val conflicts: List<GitConflict>,
        val divergence: GitDivergence?,
        val repositoryState: GitRepositoryOperationState,
    )
}
