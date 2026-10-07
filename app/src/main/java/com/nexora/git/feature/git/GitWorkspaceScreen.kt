package com.nexora.git.feature.git

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.R
import com.nexora.git.core.auth.AuthAccountSummary
import com.nexora.git.core.git.GitBranch
import com.nexora.git.core.git.GitConflictResolution
import com.nexora.git.core.git.GitHistoryEntry
import com.nexora.git.core.git.GitPullStrategy
import com.nexora.git.core.git.GitRemote
import com.nexora.git.core.git.GitResetMode
import com.nexora.git.core.git.GitStash
import com.nexora.git.core.git.GitSubmodule
import com.nexora.git.core.git.GitTag
import com.nexora.git.core.git.GitStatusEntry

private enum class GitWorkspaceTab(
    @param:StringRes val labelRes: Int,
) {
    CHANGES(R.string.git_tab_changes),
    HISTORY(R.string.git_tab_history),
    BRANCHES(R.string.git_tab_branches),
    SYNC(R.string.git_tab_sync),
    ADVANCED(R.string.git_tab_advanced),
}

@Composable
fun GitWorkspaceScreen(
    contentPadding: PaddingValues,
    activeAccount: AuthAccountSummary,
    onBack: () -> Unit,
    onEditFile: (String) -> Unit,
    viewModel: GitWorkspaceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable {
        mutableStateOf(GitWorkspaceTab.CHANGES)
    }
    var commitMessage by rememberSaveable {
        mutableStateOf("")
    }
    var newBranch by rememberSaveable {
        mutableStateOf("")
    }
    var selectedRemote by rememberSaveable {
        mutableStateOf("")
    }
    var pushTarget by rememberSaveable {
        mutableStateOf("")
    }
    var pullStrategy by rememberSaveable {
        mutableStateOf(GitPullStrategy.MERGE)
    }
    var advancedRebaseRef by rememberSaveable {
        mutableStateOf("")
    }
    var advancedCommitRef by rememberSaveable {
        mutableStateOf("")
    }
    var stashMessage by rememberSaveable {
        mutableStateOf("")
    }
    var stashIncludeUntracked by rememberSaveable {
        mutableStateOf(true)
    }
    var resetRef by rememberSaveable {
        mutableStateOf("HEAD~1")
    }
    var resetMode by rememberSaveable {
        mutableStateOf(GitResetMode.MIXED)
    }
    var tagName by rememberSaveable {
        mutableStateOf("")
    }
    var tagTarget by rememberSaveable {
        mutableStateOf("HEAD")
    }
    var tagMessage by rememberSaveable {
        mutableStateOf("")
    }
    var annotatedTag by rememberSaveable {
        mutableStateOf(true)
    }
    var lfsPattern by rememberSaveable {
        mutableStateOf("")
    }
    var pendingHardReset by remember {
        mutableStateOf<String?>(null)
    }
    var pendingStashDrop by remember {
        mutableStateOf<GitStash?>(null)
    }
    var pendingTagDelete by remember {
        mutableStateOf<GitTag?>(null)
    }

    var showAddRemote by rememberSaveable {
        mutableStateOf(false)
    }
    var renameRemote by remember {
        mutableStateOf<GitRemote?>(null)
    }
    var removeRemote by remember {
        mutableStateOf<GitRemote?>(null)
    }
    var showLeaseConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    val authorName = remember(activeAccount.accountId) {
        activeAccount.name
            ?.takeIf(String::isNotBlank)
            ?: activeAccount.login
    }
    val authorEmail = remember(activeAccount.accountId) {
        activeAccount.accountId.toString() +
            "+" + activeAccount.login +
            "@users.noreply.github.com"
    }

    LaunchedEffect(state.remotes) {
        if (state.remotes.none { it.name == selectedRemote }) {
            selectedRemote =
                state.remotes.firstOrNull { it.name == "origin" }
                    ?.name
                    ?: state.remotes.firstOrNull()?.name
                    .orEmpty()
        }
    }

    LaunchedEffect(state.branch) {
        if (pushTarget.isBlank() && state.branch.isNotBlank()) {
            pushTarget = state.branch
        }
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            title = {
                Text(stringResource(R.string.git_operation))
            },
            text = {
                Text(message)
            },
        )
    }

    state.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccess,
            confirmButton = {
                TextButton(onClick = viewModel::dismissSuccess) {
                    Text(stringResource(R.string.action_done))
                }
            },
            title = {
                Text(stringResource(R.string.app_name))
            },
            text = {
                Text(message)
            },
        )
    }

    if (showAddRemote) {
        AddRemoteDialog(
            onDismiss = {
                showAddRemote = false
            },
            onAdd = { name, url ->
                showAddRemote = false
                viewModel.addRemote(name, url)
            },
        )
    }

    renameRemote?.let { remote ->
        RenameRemoteDialog(
            remote = remote,
            onDismiss = {
                renameRemote = null
            },
            onRename = { newName ->
                renameRemote = null
                viewModel.renameRemote(
                    oldName = remote.name,
                    newName = newName,
                )
            },
        )
    }

    removeRemote?.let { remote ->
        AlertDialog(
            onDismissRequest = {
                removeRemote = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeRemote = null
                        viewModel.removeRemote(remote.name)
                    },
                ) {
                    Text(stringResource(R.string.git_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        removeRemote = null
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = {
                Text(stringResource(R.string.git_remove_remote_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.git_remove_remote_body,
                        remote.name,
                    ),
                )
            },
        )
    }

    if (showLeaseConfirmation) {
        ForceWithLeaseDialog(
            remote = selectedRemote,
            branch = pushTarget,
            expectedOid = state.divergence?.upstreamOid.orEmpty(),
            onDismiss = {
                showLeaseConfirmation = false
            },
            onConfirm = {
                showLeaseConfirmation = false
                viewModel.push(
                    remote = selectedRemote,
                    targetBranch = pushTarget,
                    forceWithLease = true,
                )
            },
        )
    }

    pendingHardReset?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingHardReset = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHardReset = null
                        viewModel.reset(
                            targetRef = target,
                            mode = GitResetMode.HARD,
                        )
                    },
                ) {
                    Text(stringResource(R.string.git_reset_hard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingHardReset = null },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = { Text(stringResource(R.string.git_reset_hard_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.git_reset_hard_body,
                        target,
                    ),
                )
            },
        )
    }

    pendingStashDrop?.let { stash ->
        AlertDialog(
            onDismissRequest = { pendingStashDrop = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingStashDrop = null
                        viewModel.dropStash(stash.index)
                    },
                ) {
                    Text(stringResource(R.string.git_drop))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingStashDrop = null },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = { Text(stringResource(R.string.git_drop_stash_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.git_stash_drop_body,
                        stash.index,
                    ),
                )
            },
        )
    }

    pendingTagDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingTagDelete = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTagDelete = null
                        viewModel.deleteTag(tag.name)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingTagDelete = null },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = { Text(stringResource(R.string.git_delete_local_tag_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.git_delete_local_tag_body,
                        tag.name,
                    ),
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 14.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = state.workspaceName.ifBlank {
                            stringResource(R.string.git_workspace_fallback)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.branch.ifBlank {
                            stringResource(R.string.git_repository_fallback)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                IconButton(
                    enabled =
                        !state.operationInProgress &&
                            !state.refreshing,
                    onClick = viewModel::refresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.git_refresh_state),
                    )
                }
            }
        }

        if (state.loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                RepositorySummaryCard(state)
            }

            if (state.durableOperationId != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.git_durable_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = when (state.durableOperationPhase) {
                                        "fetch" -> stringResource(R.string.git_durable_fetch)
                                        "pull" -> stringResource(R.string.git_durable_pull)
                                        "push" -> stringResource(R.string.git_durable_push)
                                        "lfs-upload" -> stringResource(R.string.git_durable_lfs)
                                        else -> stringResource(R.string.git_durable_queued)
                                    },
                                )
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = viewModel::cancelDurableOperation,
                            ) {
                                Text(stringResource(R.string.git_durable_cancel))
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    GitWorkspaceTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                            },
                            label = {
                                Text(stringResource(tab.labelRes))
                            },
                        )
                    }
                }
            }

            when (selectedTab) {
                GitWorkspaceTab.CHANGES -> {
                    item {
                        ChangesActions(
                            state = state,
                            onStageAll = viewModel::stageAll,
                            onUnstageAll = viewModel::unstageAll,
                        )
                    }

                    if (state.rebaseInProgress) {
                        item {
                            RebaseInProgressCard(
                                conflictCount = state.conflicts.size,
                                busy = state.operationInProgress,
                                onContinue = {
                                    viewModel.continueRebase(
                                        authorName = authorName,
                                        authorEmail = authorEmail,
                                    )
                                },
                                onAbort = viewModel::abortRebase,
                            )
                        }
                    }

                    if (state.mergeInProgress) {
                        item {
                            MergeInProgressCard(
                                conflictCount = state.conflicts.size,
                                busy = state.operationInProgress,
                                onContinue = {
                                    viewModel.continueMerge(
                                        authorName = authorName,
                                        authorEmail = authorEmail,
                                    )
                                },
                            )
                        }
                    }

                    if (state.conflicts.isNotEmpty()) {
                        item {
                            SectionTitle(
                                "Conflicts (" +
                                    state.conflicts.size + ")",
                            )
                        }
                        items(
                            items = state.conflicts,
                            key = { "conflict-" + it.path },
                        ) { conflict ->
                            ConflictCard(
                                path = conflict.path,
                                busy = state.operationInProgress,
                                onEdit = {
                                    onEditFile(conflict.path)
                                },
                                onResolved = {
                                    viewModel.markConflictResolved(
                                        conflict.path,
                                    )
                                },
                                onUseOurs = {
                                    viewModel.resolveConflictSide(
                                        path = conflict.path,
                                        resolution =
                                            GitConflictResolution.OURS,
                                    )
                                },
                                onUseTheirs = {
                                    viewModel.resolveConflictSide(
                                        path = conflict.path,
                                        resolution =
                                            GitConflictResolution.THEIRS,
                                    )
                                },
                            )
                        }
                    }

                    item {
                        SectionTitle(
                            "Working tree (" +
                                state.unstagedEntries.size + ")",
                        )
                    }

                    if (state.unstagedEntries.isEmpty()) {
                        item {
                            EmptyGitCard(
                                "Working tree is clean.",
                            )
                        }
                    } else {
                        items(
                            items = state.unstagedEntries,
                            key = { "work-" + it.path },
                        ) { entry ->
                            ChangeCard(
                                entry = entry,
                                stagedView = false,
                                busy = state.operationInProgress,
                                onStage = {
                                    viewModel.stage(entry.path)
                                },
                                onUnstage = {},
                                onEdit = {
                                    onEditFile(entry.path)
                                },
                            )
                        }
                    }

                    item {
                        SectionTitle(
                            "Staged (" +
                                state.stagedEntries.size + ")",
                        )
                    }

                    if (state.stagedEntries.isEmpty()) {
                        item {
                            EmptyGitCard(
                                "Nothing is staged for commit.",
                            )
                        }
                    } else {
                        items(
                            items = state.stagedEntries,
                            key = { "staged-" + it.path },
                        ) { entry ->
                            ChangeCard(
                                entry = entry,
                                stagedView = true,
                                busy = state.operationInProgress,
                                onStage = {},
                                onUnstage = {
                                    viewModel.unstage(entry.path)
                                },
                                onEdit = {
                                    onEditFile(entry.path)
                                },
                            )
                        }
                    }

                    if (!state.rebaseInProgress &&
                        !state.mergeInProgress
                    ) {
                        item {
                            CommitCard(
                                message = commitMessage,
                                busy = state.operationInProgress,
                                stagedCount =
                                    state.stagedEntries.size,
                                conflictCount =
                                    state.conflicts.size,
                                onMessageChange = {
                                    commitMessage = it
                                },
                                onCommit = {
                                    viewModel.commit(
                                        message = commitMessage,
                                        authorName = authorName,
                                        authorEmail = authorEmail,
                                    )
                                    commitMessage = ""
                                },
                            )
                        }
                    }
                }

                GitWorkspaceTab.HISTORY -> {
                    item {
                        SectionTitle(
                            stringResource(
                                R.string.git_commit_history_count,
                                state.history.size,
                            ),
                        )
                    }

                    if (state.history.isEmpty()) {
                        item {
                            EmptyGitCard(stringResource(R.string.git_no_commits))
                        }
                    } else {
                        items(
                            items = state.history,
                            key = { it.oid },
                        ) { commit ->
                            HistoryCard(commit)
                        }
                    }
                }

                GitWorkspaceTab.BRANCHES -> {
                    item {
                        BranchCreateCard(
                            value = newBranch,
                            busy = state.operationInProgress,
                            onValueChange = {
                                newBranch = it
                            },
                            onCreate = {
                                viewModel.createBranch(newBranch)
                                newBranch = ""
                            },
                        )
                    }

                    item {
                        UpstreamCard(
                            currentBranch = state.branch,
                            upstream = state.currentUpstream,
                            remoteBranches = state.remoteBranches,
                            busy = state.operationInProgress,
                            onSetUpstream = viewModel::setUpstream,
                        )
                    }

                    item {
                        SectionTitle(
                            "Local branches (" +
                                state.localBranches.size + ")",
                        )
                    }

                    items(
                        items = state.localBranches,
                        key = { "local-" + it.name },
                    ) { branch ->
                        BranchCard(
                            branch = branch,
                            busy = state.operationInProgress,
                            onCheckout = {
                                viewModel.checkout(branch.name)
                            },
                            onMerge = {
                                viewModel.merge(
                                    ref = branch.name,
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                        )
                    }

                    if (state.remoteBranches.isNotEmpty()) {
                        item {
                            SectionTitle(
                                "Remote branches (" +
                                    state.remoteBranches.size + ")",
                            )
                        }

                        items(
                            items = state.remoteBranches,
                            key = { "remote-" + it.name },
                        ) { branch ->
                            BranchCard(
                                branch = branch,
                                busy = state.operationInProgress,
                                onCheckout = {},
                                onMerge = {
                                    viewModel.merge(
                                        ref = branch.name,
                                        authorName = authorName,
                                        authorEmail = authorEmail,
                                    )
                                },
                            )
                        }
                    }
                }

                GitWorkspaceTab.SYNC -> {
                    item {
                        RemotesHeader(
                            onAdd = {
                                showAddRemote = true
                            },
                        )
                    }

                    if (state.remotes.isEmpty()) {
                        item {
                            EmptyGitCard(
                                stringResource(R.string.git_no_remotes),
                            )
                        }
                    } else {
                        items(
                            items = state.remotes,
                            key = { "remote-config-" + it.name },
                        ) { remote ->
                            RemoteCard(
                                remote = remote,
                                selected =
                                    selectedRemote == remote.name,
                                busy = state.operationInProgress,
                                onSelect = {
                                    selectedRemote = remote.name
                                },
                                onFetch = {
                                    viewModel.fetch(remote.name)
                                },
                                onRename = {
                                    renameRemote = remote
                                },
                                onRemove = {
                                    removeRemote = remote
                                },
                            )
                        }
                    }

                    if (selectedRemote.isNotBlank()) {
                        item {
                            PullCard(
                                remote = selectedRemote,
                                strategy = pullStrategy,
                                busy = state.operationInProgress,
                                conflictCount = state.conflicts.size,
                                rebaseInProgress =
                                    state.rebaseInProgress,
                                onStrategyChange = {
                                    pullStrategy = it
                                },
                                onPull = {
                                    viewModel.pull(
                                        remote = selectedRemote,
                                        strategy = pullStrategy,
                                        authorName = authorName,
                                        authorEmail = authorEmail,
                                    )
                                },
                            )
                        }

                        item {
                            PushCard(
                                state = state,
                                remote = selectedRemote,
                                pushTarget = pushTarget,
                                onPushTargetChange = {
                                    pushTarget = it
                                },
                                onPush = {
                                    viewModel.push(
                                        remote = selectedRemote,
                                        targetBranch = pushTarget,
                                        forceWithLease = false,
                                    )
                                },
                                onForceWithLease = {
                                    showLeaseConfirmation = true
                                },
                            )
                        }
                    }
                }

                GitWorkspaceTab.ADVANCED -> {
                    item {
                        AdvancedGitPanel(
                            state = state,
                            authorName = authorName,
                            authorEmail = authorEmail,
                            rebaseRef = advancedRebaseRef,
                            onRebaseRefChange = {
                                advancedRebaseRef = it
                            },
                            commitRef = advancedCommitRef,
                            onCommitRefChange = {
                                advancedCommitRef = it
                            },
                            stashMessage = stashMessage,
                            onStashMessageChange = {
                                stashMessage = it
                            },
                            stashIncludeUntracked =
                                stashIncludeUntracked,
                            onStashIncludeUntrackedChange = {
                                stashIncludeUntracked = it
                            },
                            resetRef = resetRef,
                            onResetRefChange = {
                                resetRef = it
                            },
                            resetMode = resetMode,
                            onResetModeChange = {
                                resetMode = it
                            },
                            tagName = tagName,
                            onTagNameChange = {
                                tagName = it
                            },
                            tagTarget = tagTarget,
                            onTagTargetChange = {
                                tagTarget = it
                            },
                            tagMessage = tagMessage,
                            onTagMessageChange = {
                                tagMessage = it
                            },
                            annotatedTag = annotatedTag,
                            onAnnotatedTagChange = {
                                annotatedTag = it
                            },
                            lfsPattern = lfsPattern,
                            onLfsPatternChange = {
                                lfsPattern = it
                            },
                            lfsRemoteName = selectedRemote,
                            onRebase = {
                                viewModel.rebase(
                                    upstreamRef = advancedRebaseRef,
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onCherryPick = {
                                viewModel.cherryPick(
                                    commitRef = advancedCommitRef,
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onContinueCherryPick = {
                                viewModel.continueCherryPick(
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onAbortCherryPick =
                                viewModel::abortCherryPick,
                            onSaveStash = {
                                viewModel.saveStash(
                                    message = stashMessage,
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                    includeUntracked =
                                        stashIncludeUntracked,
                                )
                                stashMessage = ""
                            },
                            onApplyStash = { index ->
                                viewModel.applyStash(
                                    index = index,
                                    pop = false,
                                )
                            },
                            onPopStash = { index ->
                                viewModel.applyStash(
                                    index = index,
                                    pop = true,
                                )
                            },
                            onDropStash = { stash ->
                                pendingStashDrop = stash
                            },
                            onReset = {
                                val target = resetRef.trim()
                                if (resetMode == GitResetMode.HARD) {
                                    pendingHardReset = target
                                } else {
                                    viewModel.reset(
                                        targetRef = target,
                                        mode = resetMode,
                                    )
                                }
                            },
                            onRevert = {
                                viewModel.revert(
                                    commitRef = advancedCommitRef,
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onContinueRevert = {
                                viewModel.continueRevert(
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onAbortRevert = viewModel::abortRevert,
                            onCreateTag = {
                                viewModel.createTag(
                                    name = tagName,
                                    targetRef = tagTarget,
                                    message = tagMessage,
                                    annotated = annotatedTag,
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onDeleteTag = { tag ->
                                pendingTagDelete = tag
                            },
                            onSyncSubmodule =
                                viewModel::syncSubmodule,
                            onUpdateSubmodule =
                                viewModel::updateSubmodule,
                            onTrackLfs = {
                                viewModel.trackLfs(lfsPattern)
                                lfsPattern = ""
                            },
                            onUntrackLfs =
                                viewModel::untrackLfs,
                            onDownloadLfs = {
                                viewModel.downloadLfs(selectedRemote)
                            },
                            onUploadLfs = {
                                viewModel.uploadLfs(selectedRemote)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdvancedGitPanel(
    state: GitWorkspaceUiState,
    authorName: String,
    authorEmail: String,
    rebaseRef: String,
    onRebaseRefChange: (String) -> Unit,
    commitRef: String,
    onCommitRefChange: (String) -> Unit,
    stashMessage: String,
    onStashMessageChange: (String) -> Unit,
    stashIncludeUntracked: Boolean,
    onStashIncludeUntrackedChange: (Boolean) -> Unit,
    resetRef: String,
    onResetRefChange: (String) -> Unit,
    resetMode: GitResetMode,
    onResetModeChange: (GitResetMode) -> Unit,
    tagName: String,
    onTagNameChange: (String) -> Unit,
    tagTarget: String,
    onTagTargetChange: (String) -> Unit,
    tagMessage: String,
    onTagMessageChange: (String) -> Unit,
    annotatedTag: Boolean,
    onAnnotatedTagChange: (Boolean) -> Unit,
    lfsPattern: String,
    onLfsPatternChange: (String) -> Unit,
    lfsRemoteName: String,
    onRebase: () -> Unit,
    onCherryPick: () -> Unit,
    onContinueCherryPick: () -> Unit,
    onAbortCherryPick: () -> Unit,
    onSaveStash: () -> Unit,
    onApplyStash: (Int) -> Unit,
    onPopStash: (Int) -> Unit,
    onDropStash: (GitStash) -> Unit,
    onReset: () -> Unit,
    onRevert: () -> Unit,
    onContinueRevert: () -> Unit,
    onAbortRevert: () -> Unit,
    onCreateTag: () -> Unit,
    onDeleteTag: (GitTag) -> Unit,
    onSyncSubmodule: (String) -> Unit,
    onUpdateSubmodule: (String) -> Unit,
    onTrackLfs: () -> Unit,
    onUntrackLfs: (String) -> Unit,
    onDownloadLfs: () -> Unit,
    onUploadLfs: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.git_advanced_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.git_advanced_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.git_rebase), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = rebaseRef,
                    onValueChange = onRebaseRefChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_rebase_onto)) },
                    singleLine = true,
                )
                Button(
                    enabled =
                        !state.operationInProgress &&
                            state.repositoryState.name == "NONE" &&
                            rebaseRef.isNotBlank(),
                    onClick = onRebase,
                ) {
                    Text(stringResource(R.string.git_start_rebase))
                }
                if (state.rebaseInProgress) {
                    Text(
                        stringResource(R.string.git_rebase_in_progress_note),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.git_cherry_revert),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = commitRef,
                    onValueChange = onCommitRefChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_commit_sha_ref)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled =
                            !state.operationInProgress &&
                                state.repositoryState.name == "NONE" &&
                                commitRef.isNotBlank(),
                        onClick = onCherryPick,
                    ) {
                        Text(stringResource(R.string.git_cherry_pick))
                    }
                    OutlinedButton(
                        enabled =
                            !state.operationInProgress &&
                                state.repositoryState.name == "NONE" &&
                                commitRef.isNotBlank(),
                        onClick = onRevert,
                    ) {
                        Text(stringResource(R.string.git_revert))
                    }
                }
                if (state.cherryPickInProgress) {
                    Text(
                        "Cherry-pick conflict state is preserved until you continue or abort.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            enabled =
                                !state.operationInProgress &&
                                    state.conflicts.isEmpty(),
                            onClick = onContinueCherryPick,
                        ) {
                            Text(stringResource(R.string.git_continue))
                        }
                        OutlinedButton(
                            enabled = !state.operationInProgress,
                            onClick = onAbortCherryPick,
                        ) {
                            Text(stringResource(R.string.git_abort))
                        }
                    }
                }
                if (state.revertInProgress) {
                    Text(
                        "Revert conflict state is preserved until you continue or abort.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            enabled =
                                !state.operationInProgress &&
                                    state.conflicts.isEmpty(),
                            onClick = onContinueRevert,
                        ) {
                            Text(stringResource(R.string.git_continue))
                        }
                        OutlinedButton(
                            enabled = !state.operationInProgress,
                            onClick = onAbortRevert,
                        ) {
                            Text(stringResource(R.string.git_abort))
                        }
                    }
                }
                Text(
                    stringResource(
                        R.string.git_commit_author,
                        authorName,
                        authorEmail,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.git_stash), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = stashMessage,
                    onValueChange = onStashMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_message_optional)) },
                    singleLine = true,
                )
                FilterChip(
                    selected = stashIncludeUntracked,
                    onClick = {
                        onStashIncludeUntrackedChange(
                            !stashIncludeUntracked,
                        )
                    },
                    label = { Text(stringResource(R.string.git_include_untracked)) },
                )
                Button(
                    enabled =
                        !state.operationInProgress &&
                            state.repositoryState.name == "NONE",
                    onClick = onSaveStash,
                ) {
                    Text(stringResource(R.string.git_save_stash))
                }

                if (state.stashes.isEmpty()) {
                    Text(
                        stringResource(R.string.git_no_stashes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.stashes.forEach { stash ->
                        HorizontalDivider()
                        Text(
                            "stash@{" + stash.index + "} · " +
                                stash.oid.take(7),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stash.message.ifBlank { stringResource(R.string.git_no_message) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(
                                rememberScrollState(),
                            ),
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                enabled = !state.operationInProgress,
                                onClick = {
                                    onApplyStash(stash.index)
                                },
                            ) {
                                Text(stringResource(R.string.git_apply))
                            }
                            OutlinedButton(
                                enabled = !state.operationInProgress,
                                onClick = {
                                    onPopStash(stash.index)
                                },
                            ) {
                                Text(stringResource(R.string.git_pop))
                            }
                            TextButton(
                                enabled = !state.operationInProgress,
                                onClick = { onDropStash(stash) },
                            ) {
                                Text(stringResource(R.string.git_drop))
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.git_reset), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = resetRef,
                    onValueChange = onResetRefChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_target_ref)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.horizontalScroll(
                        rememberScrollState(),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GitResetMode.entries.forEach { mode ->
                        FilterChip(
                            selected = resetMode == mode,
                            onClick = { onResetModeChange(mode) },
                            label = { Text(mode.name.lowercase()) },
                        )
                    }
                }
                if (resetMode == GitResetMode.HARD) {
                    Text(
                        stringResource(R.string.git_hard_reset_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    enabled =
                        !state.operationInProgress &&
                            state.repositoryState.name == "NONE" &&
                            resetRef.isNotBlank(),
                    onClick = onReset,
                ) {
                    Text(stringResource(R.string.git_reset))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.git_local_tags),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = tagName,
                    onValueChange = onTagNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_tag_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tagTarget,
                    onValueChange = onTagTargetChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_target_ref)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tagMessage,
                    onValueChange = onTagMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_annotation_message)) },
                )
                FilterChip(
                    selected = annotatedTag,
                    onClick = {
                        onAnnotatedTagChange(!annotatedTag)
                    },
                    label = {
                        Text(
                            if (annotatedTag) {
                                "Annotated tag"
                            } else {
                                "Lightweight tag"
                            },
                        )
                    },
                )
                Button(
                    enabled =
                        !state.operationInProgress &&
                            tagName.isNotBlank(),
                    onClick = onCreateTag,
                ) {
                    Text(stringResource(R.string.git_create_local_tag))
                }
                state.tags.forEach { tag ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                tag.targetOid.take(12) +
                                    if (tag.annotated) {
                                        " · annotated"
                                    } else {
                                        " · lightweight"
                                    },
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            enabled = !state.operationInProgress,
                            onClick = { onDeleteTag(tag) },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription =
                                    stringResource(
                                        R.string.git_delete_local_tag_cd,
                                        tag.name,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.git_submodules),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.submodules.isEmpty()) {
                    Text(
                        stringResource(R.string.git_no_submodules),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.submodules.forEach { submodule ->
                        SubmoduleAdvancedRow(
                            submodule = submodule,
                            busy = state.operationInProgress,
                            onSync = {
                                onSyncSubmodule(submodule.name)
                            },
                            onUpdate = {
                                onUpdateSubmodule(submodule.name)
                            },
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Git LFS", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.lfs.transferMessage,
                    color = if (state.lfs.transferSupported) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                OutlinedTextField(
                    value = lfsPattern,
                    onValueChange = onLfsPatternChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.git_lfs_pattern)) },
                    singleLine = true,
                )
                Button(
                    enabled =
                        !state.operationInProgress &&
                            lfsPattern.isNotBlank(),
                    onClick = onTrackLfs,
                ) {
                    Text(stringResource(R.string.git_lfs_track))
                }
                if (lfsRemoteName.isNotBlank()) {
                    Text(
                        stringResource(
                            R.string.git_transfer_remote,
                            lfsRemoteName,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState(),
                        ),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            enabled = !state.operationInProgress,
                            onClick = onDownloadLfs,
                        ) {
                            Text(stringResource(R.string.git_lfs_download))
                        }
                        OutlinedButton(
                            enabled = !state.operationInProgress,
                            onClick = onUploadLfs,
                        ) {
                            Text(stringResource(R.string.git_lfs_upload))
                        }
                    }
                }
                if (state.lfs.trackedPatterns.isNotEmpty()) {
                    Text(
                        stringResource(R.string.git_lfs_tracked),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    state.lfs.trackedPatterns.forEach { pattern ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pattern,
                                modifier = Modifier.weight(1f),
                                fontFamily = FontFamily.Monospace,
                            )
                            TextButton(
                                enabled = !state.operationInProgress,
                                onClick = { onUntrackLfs(pattern) },
                            ) {
                                Text(stringResource(R.string.git_lfs_untrack))
                            }
                        }
                    }
                }
                Text(
                    state.lfs.pointers.size.toString() +
                        " LFS pointer file(s) detected.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubmoduleAdvancedRow(
    submodule: GitSubmodule,
    busy: Boolean,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
) {
    HorizontalDivider()
    Text(
        submodule.name,
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        submodule.path,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        if (submodule.initialized) {
            "Initialized · " +
                submodule.workdirOid.take(12)
        } else {
            "Not initialized"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            enabled = !busy,
            onClick = onSync,
        ) {
            Text(stringResource(R.string.git_sync_url))
        }
        Button(
            enabled = !busy,
            onClick = onUpdate,
        ) {
            Text(
                if (submodule.initialized) "Update" else "Initialize",
            )
        }
    }
}

@Composable
internal fun RepositorySummaryCard(
    state: GitWorkspaceUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Source,
                    contentDescription = null,
                )
                Text(
                    text = state.branch.ifBlank {
                        stringResource(R.string.git_detached_head)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.currentUpstream.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.git_upstream,
                        state.currentUpstream,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.git_no_upstream),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.divergence?.let { divergence ->
                Text(
                    text = stringResource(
                        R.string.git_divergence,
                        divergence.ahead,
                        divergence.behind,
                    ),
                    color = if (
                        divergence.ahead > 0 &&
                        divergence.behind > 0
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Text(
                text = stringResource(
                    R.string.git_worktree_summary,
                    state.stagedEntries.size,
                    state.unstagedEntries.size,
                    state.conflicts.size,
                ),
                color = if (state.conflicts.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            if (state.rebaseInProgress) {
                Text(
                    text = stringResource(R.string.git_rebase_in_progress),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.mergeInProgress) {
                Text(
                    text = stringResource(R.string.git_merge_in_progress),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.cherryPickInProgress) {
                Text(
                    text = stringResource(R.string.git_cherry_in_progress),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.revertInProgress) {
                Text(
                    text = stringResource(R.string.git_revert_in_progress),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun ChangesActions(
    state: GitWorkspaceUiState,
    onStageAll: () -> Unit,
    onUnstageAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled =
                !state.operationInProgress &&
                    state.unstagedEntries.any {
                        !it.conflicted
                    },
            onClick = onStageAll,
        ) {
            Text(stringResource(R.string.git_stage_all))
        }

        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled =
                !state.operationInProgress &&
                    state.stagedEntries.isNotEmpty(),
            onClick = onUnstageAll,
        ) {
            Text(stringResource(R.string.git_unstage_all))
        }
    }
}

@Composable
private fun RebaseInProgressCard(
    conflictCount: Int,
    busy: Boolean,
    onContinue: () -> Unit,
    onAbort: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.git_rebase_in_progress),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (conflictCount > 0) {
                    "Resolve and stage every conflicted file, then continue the rebase."
                } else {
                    "The current rebase operation is ready to continue."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !busy && conflictCount == 0,
                    onClick = onContinue,
                ) {
                    Text(stringResource(R.string.git_continue))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = onAbort,
                ) {
                    Text(stringResource(R.string.git_abort))
                }
            }
        }
    }
}

@Composable
private fun MergeInProgressCard(
    conflictCount: Int,
    busy: Boolean,
    onContinue: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.git_merge_in_progress),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (conflictCount > 0) {
                    "Resolve and stage every conflicted file. Continue merge will create the final two-parent merge commit."
                } else {
                    "All conflicts are resolved and staged. Complete the merge commit."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && conflictCount == 0,
                onClick = onContinue,
            ) {
                Text(stringResource(R.string.git_continue_merge))
            }
        }
    }
}

@Composable
private fun ChangeCard(
    entry: GitStatusEntry,
    stagedView: Boolean,
    busy: Boolean,
    onStage: () -> Unit,
    onUnstage: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.path,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = changeLabel(entry),
                color = if (entry.conflicted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = onEdit,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.git_edit))
                }

                if (stagedView) {
                    TextButton(
                        enabled = !busy,
                        onClick = onUnstage,
                    ) {
                        Text(stringResource(R.string.git_unstage))
                    }
                } else if (!entry.conflicted) {
                    TextButton(
                        enabled = !busy,
                        onClick = onStage,
                    ) {
                        Text(stringResource(R.string.git_stage))
                    }
                } else {
                    Text(
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 12.dp,
                        ),
                        text = "Resolve first",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(
    path: String,
    busy: Boolean,
    onEdit: () -> Unit,
    onResolved: () -> Unit,
    onUseOurs: () -> Unit,
    onUseTheirs: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = path,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.git_conflict_edit_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = onEdit,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.git_edit))
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = onUseOurs,
                ) {
                    Text(stringResource(R.string.git_use_ours))
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = onUseTheirs,
                ) {
                    Text(stringResource(R.string.git_use_theirs))
                }
                Button(
                    enabled = !busy,
                    onClick = onResolved,
                ) {
                    Text(stringResource(R.string.git_mark_resolved))
                }
            }
        }
    }
}

@Composable
private fun CommitCard(
    message: String,
    busy: Boolean,
    stagedCount: Int,
    conflictCount: Int,
    onMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Commit,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.git_commit_staged),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = message,
                onValueChange = onMessageChange,
                minLines = 2,
                maxLines = 5,
                label = {
                    Text(stringResource(R.string.git_commit_message))
                },
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !busy &&
                        conflictCount == 0 &&
                        stagedCount > 0 &&
                        message.isNotBlank(),
                onClick = onCommit,
            ) {
                Text(
                    stringResource(
                        R.string.git_commit_paths,
                        stagedCount,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    commit: GitHistoryEntry,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                )
                Text(
                    text = commit.shortOid,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = commit.summary.ifBlank {
                    "(No commit message)"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = commit.authorName.ifBlank {
                    commit.authorEmail
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BranchCreateCard(
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.git_create_branch),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                singleLine = true,
                onValueChange = onValueChange,
                label = {
                    Text(stringResource(R.string.git_branch_name))
                },
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && value.isNotBlank(),
                onClick = onCreate,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                    contentDescription = null,
                )
                Text(stringResource(R.string.git_create_switch))
            }
        }
    }
}

@Composable
private fun UpstreamCard(
    currentBranch: String,
    upstream: String,
    remoteBranches: List<GitBranch>,
    busy: Boolean,
    onSetUpstream: (String?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.git_upstream_tracking),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (upstream.isBlank()) {
                    stringResource(
                        R.string.git_upstream_not_tracking,
                        currentBranch,
                    )
                } else {
                    stringResource(
                        R.string.git_upstream_tracks,
                        currentBranch,
                        upstream,
                    )
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (remoteBranches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    remoteBranches.forEach { branch ->
                        FilterChip(
                            selected = upstream == branch.name,
                            enabled = !busy,
                            onClick = {
                                onSetUpstream(branch.name)
                            },
                            label = {
                                Text(branch.name)
                            },
                        )
                    }
                }
            }

            if (upstream.isNotBlank()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        onSetUpstream(null)
                    },
                ) {
                    Text(stringResource(R.string.git_unset_upstream))
                }
            }
        }
    }
}

@Composable
private fun BranchCard(
    branch: GitBranch,
    busy: Boolean,
    onCheckout: () -> Unit,
    onMerge: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = branch.name,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (branch.head) {
                    Text(
                        text = stringResource(R.string.git_current),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (branch.upstream.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.git_branch_upstream,
                        branch.upstream,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!branch.head && !branch.remote) {
                    TextButton(
                        enabled = !busy,
                        onClick = onCheckout,
                    ) {
                        Text(stringResource(R.string.git_switch))
                    }
                }

                if (!branch.head) {
                    TextButton(
                        enabled = !busy,
                        onClick = onMerge,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.CallMerge,
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.git_merge))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotesHeader(
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionTitle(stringResource(R.string.git_remotes))
        TextButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
            )
            Text(stringResource(R.string.git_add))
        }
    }
}

@Composable
private fun RemoteCard(
    remote: GitRemote,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onFetch: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = remote.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = remote.url,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                FilterChip(
                    selected = selected,
                    enabled = !busy,
                    onClick = onSelect,
                    label = {
                        Text(
                            if (selected) {
                                "Active"
                            } else {
                                "Use"
                            },
                        )
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = onFetch,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.git_fetch))
                }
                TextButton(
                    enabled = !busy,
                    onClick = onRename,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.git_rename))
                }
                TextButton(
                    enabled = !busy,
                    onClick = onRemove,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.git_remove))
                }
            }
        }
    }
}

@Composable
internal fun PullCard(
    remote: String,
    strategy: GitPullStrategy,
    busy: Boolean,
    conflictCount: Int,
    rebaseInProgress: Boolean,
    onStrategyChange: (GitPullStrategy) -> Unit,
    onPull: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.git_pull_from, remote),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.git_pull_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PullStrategyChip(
                    value = GitPullStrategy.MERGE,
                    selected = strategy,
                    label = "Merge",
                    onSelect = onStrategyChange,
                )
                PullStrategyChip(
                    value = GitPullStrategy.FAST_FORWARD_ONLY,
                    selected = strategy,
                    label = "FF only",
                    onSelect = onStrategyChange,
                )
                PullStrategyChip(
                    value = GitPullStrategy.REBASE,
                    selected = strategy,
                    label = "Rebase",
                    onSelect = onStrategyChange,
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !busy &&
                        conflictCount == 0 &&
                        !rebaseInProgress,
                onClick = onPull,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                )
                Text(stringResource(R.string.git_pull))
            }
        }
    }
}

@Composable
private fun PullStrategyChip(
    value: GitPullStrategy,
    selected: GitPullStrategy,
    label: String,
    onSelect: (GitPullStrategy) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = {
            onSelect(value)
        },
        label = {
            Text(label)
        },
    )
}

@Composable
internal fun PushCard(
    state: GitWorkspaceUiState,
    remote: String,
    pushTarget: String,
    onPushTargetChange: (String) -> Unit,
    onPush: () -> Unit,
    onForceWithLease: () -> Unit,
) {
    val divergence = state.divergence
    val targetUpstream = remote + "/" + pushTarget
    val leaseAvailable =
        state.currentUpstream == targetUpstream &&
            divergence != null &&
            divergence.upstreamRef == targetUpstream &&
            divergence.upstreamOid.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.git_push_to, remote),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = pushTarget,
                singleLine = true,
                onValueChange = onPushTargetChange,
                label = {
                    Text(stringResource(R.string.git_remote_branch))
                },
            )

            Text(
                text = stringResource(R.string.git_push_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !state.operationInProgress &&
                        state.branch.isNotBlank() &&
                        pushTarget.isNotBlank() &&
                        state.conflicts.isEmpty() &&
                        !state.rebaseInProgress,
                onClick = onPush,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                )
                Text(stringResource(R.string.git_push))
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.git_history_rewrite),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.git_force_lease_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !state.operationInProgress &&
                        leaseAvailable &&
                        pushTarget.isNotBlank() &&
                        state.conflicts.isEmpty() &&
                        !state.rebaseInProgress,
                onClick = onForceWithLease,
            ) {
                Text(stringResource(R.string.git_force_lease))
            }
        }
    }
}

@Composable
private fun AddRemoteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var name by rememberSaveable {
        mutableStateOf("")
    }
    var url by rememberSaveable {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    name.isNotBlank() &&
                        url.isNotBlank(),
                onClick = {
                    onAdd(name, url)
                },
            ) {
                Text(stringResource(R.string.git_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.git_add_remote))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    singleLine = true,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text(stringResource(R.string.git_remote_name))
                    },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    singleLine = true,
                    onValueChange = {
                        url = it
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(stringResource(R.string.git_github_https_url))
                    },
                )
                Text(
                    text = stringResource(R.string.git_remote_https_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun RenameRemoteDialog(
    remote: GitRemote,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(remote.name) {
        mutableStateOf(remote.name)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    name.isNotBlank() &&
                        name != remote.name,
                onClick = {
                    onRename(name)
                },
            ) {
                Text(stringResource(R.string.git_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.git_rename_remote))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                singleLine = true,
                onValueChange = {
                    name = it
                },
                label = {
                    Text(stringResource(R.string.git_remote_name))
                },
            )
        },
    )
}

@Composable
private fun ForceWithLeaseDialog(
    remote: String,
    branch: String,
    expectedOid: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmation by rememberSaveable(branch) {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    branch.isNotBlank() &&
                        confirmation == branch &&
                        expectedOid.isNotBlank(),
                onClick = onConfirm,
            ) {
                Text(stringResource(R.string.git_force_lease))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.git_rewrite_remote_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.git_rewrite_remote_body,
                        remote,
                        branch,
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.git_expected_remote,
                        expectedOid.take(12),
                    ),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = confirmation,
                    singleLine = true,
                    onValueChange = {
                        confirmation = it
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.git_type_branch_confirm,
                                branch,
                            ),
                        )
                    },
                )
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun EmptyGitCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun changeLabel(
    entry: GitStatusEntry,
): String {
    val labels = buildList {
        if (entry.conflicted) add("conflict")
        if (entry.staged) add("staged")
        if (entry.untracked) add("untracked")
        if (entry.workingTree) add("working tree")
    }

    return labels.ifEmpty {
        listOf("changed")
    }.joinToString(" · ")
}
