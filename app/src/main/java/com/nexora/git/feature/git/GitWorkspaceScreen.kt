package com.nexora.git.feature.git

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.auth.AuthAccountSummary
import com.nexora.git.core.git.GitBranch
import com.nexora.git.core.git.GitHistoryEntry
import com.nexora.git.core.git.GitStatusEntry

private enum class GitWorkspaceTab(
    val label: String,
) {
    CHANGES("Changes"),
    HISTORY("History"),
    BRANCHES("Branches"),
    SYNC("Sync"),
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
    var pushTarget by rememberSaveable {
        mutableStateOf("")
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
                    Text("OK")
                }
            },
            title = { Text("Git operation") },
            text = { Text(message) },
        )
    }

    state.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccess,
            confirmButton = {
                TextButton(onClick = viewModel::dismissSuccess) {
                    Text("Done")
                }
            },
            title = { Text("Nexora Git") },
            text = { Text(message) },
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
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = state.workspaceName.ifBlank {
                            "Git workspace"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.branch.ifBlank {
                            "Repository"
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
                        contentDescription = "Refresh Git state",
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
                                Text(tab.label)
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

                    item {
                        CommitCard(
                            message = commitMessage,
                            busy = state.operationInProgress,
                            stagedCount = state.stagedEntries.size,
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

                GitWorkspaceTab.HISTORY -> {
                    item {
                        SectionTitle(
                            "Commit history (" +
                                state.history.size + ")",
                        )
                    }
                    if (state.history.isEmpty()) {
                        item {
                            EmptyGitCard("No commits yet.")
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
                    }
                }

                GitWorkspaceTab.SYNC -> {
                    item {
                        SyncCard(
                            state = state,
                            pushTarget = pushTarget,
                            onPushTargetChange = {
                                pushTarget = it
                            },
                            onFetch = viewModel::fetch,
                            onPull = {
                                viewModel.pullMerge(
                                    authorName = authorName,
                                    authorEmail = authorEmail,
                                )
                            },
                            onPush = {
                                viewModel.push(pushTarget)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositorySummaryCard(
    state: GitWorkspaceUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        "Detached / unborn HEAD"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = state.remoteUrl.ifBlank {
                    "No origin remote configured"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = state.stagedEntries.size.toString() +
                    " staged · " +
                    state.unstagedEntries.size.toString() +
                    " working tree · " +
                    state.conflicts.size.toString() +
                    " conflicts",
                color = if (state.conflicts.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
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
            Text("Stage all")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled =
                !state.operationInProgress &&
                    state.stagedEntries.isNotEmpty(),
            onClick = onUnstageAll,
        ) {
            Text("Unstage all")
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
                    Text("Edit")
                }

                if (stagedView) {
                    TextButton(
                        enabled = !busy,
                        onClick = onUnstage,
                    ) {
                        Text("Unstage")
                    }
                } else if (!entry.conflicted) {
                    TextButton(
                        enabled = !busy,
                        onClick = onStage,
                    ) {
                        Text("Stage")
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
                "Edit the conflict markers, save the file, then mark it resolved. Nexora Git refuses to stage a file while standard conflict markers remain.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
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
                    Text("Edit")
                }
                Button(
                    enabled = !busy,
                    onClick = onResolved,
                ) {
                    Text("Mark resolved")
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
                    text = "Commit staged changes",
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
                    Text("Commit message")
                },
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !busy &&
                        stagedCount > 0 &&
                        message.isNotBlank(),
                onClick = onCommit,
            ) {
                Text(
                    "Commit " + stagedCount + " path(s)",
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
                text = "Create branch",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                singleLine = true,
                onValueChange = onValueChange,
                label = {
                    Text("Branch name")
                },
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && value.isNotBlank(),
                onClick = onCreate,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CallSplit,
                    contentDescription = null,
                )
                Text("Create and switch")
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
                        "Current",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (branch.upstream.isNotBlank()) {
                Text(
                    text = "Upstream: " + branch.upstream,
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
                        Text("Switch")
                    }
                }
                if (!branch.head) {
                    TextButton(
                        enabled = !busy,
                        onClick = onMerge,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CallMerge,
                            contentDescription = null,
                        )
                        Text("Merge")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncCard(
    state: GitWorkspaceUiState,
    pushTarget: String,
    onPushTargetChange: (String) -> Unit,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Origin",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.remoteUrl.ifBlank {
                    "No origin remote configured."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !state.operationInProgress &&
                        state.remoteUrl.isNotBlank(),
                onClick = onFetch,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                )
                Text("Fetch origin")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !state.operationInProgress &&
                        state.remoteUrl.isNotBlank() &&
                        state.conflicts.isEmpty(),
                onClick = onPull,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                )
                Text("Pull with merge")
            }

            HorizontalDivider()

            Text(
                "Push current branch",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = pushTarget,
                singleLine = true,
                onValueChange = onPushTargetChange,
                label = {
                    Text("Remote branch")
                },
            )
            Text(
                "Push is explicit and never uses force. The source branch is " +
                    state.branch.ifBlank { "not available" } + ".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !state.operationInProgress &&
                        state.remoteUrl.isNotBlank() &&
                        state.branch.isNotBlank() &&
                        pushTarget.isNotBlank() &&
                        state.conflicts.isEmpty(),
                onClick = onPush,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                )
                Text("Push")
            }
        }
    }
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
