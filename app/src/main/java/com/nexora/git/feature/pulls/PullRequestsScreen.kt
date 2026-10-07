package com.nexora.git.feature.pulls

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nexora.git.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.pulls.CreatePullRequestRequest
import com.nexora.git.core.pulls.PullRequestState
import com.nexora.git.core.pulls.PullRequestSummary

@Composable
fun PullRequestsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPullRequest: (Int) -> Unit,
    viewModel: PullRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable {
        mutableStateOf(false)
    }

    PullRequestsContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refreshAll,
        onSetState = viewModel::setStateFilter,
        onOpenPullRequest = onOpenPullRequest,
        onCreate = {
            showCreate = true
        },
    )

    if (showCreate) {
        CreatePullRequestDialog(
            defaultBase = state.defaultBranch,
            busy = state.operationInProgress,
            onDismiss = {
                showCreate = false
            },
            onCreate = {
                showCreate = false
                viewModel.create(it)
            },
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            title = { Text(stringResource(R.string.pulls_title)) },
            text = { Text(message) },
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
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(message) },
        )
    }
}

@Composable
internal fun PullRequestsContent(
    state: PullRequestsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetState: (PullRequestState) -> Unit,
    onOpenPullRequest: (Int) -> Unit,
    onCreate: () -> Unit,
) {
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
                        text = stringResource(R.string.pulls_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.owner + "/" + state.repository,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled = !state.loading,
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.pulls_refresh),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PullRequestState.entries.forEach { value ->
                    FilterChip(
                        selected = state.stateFilter == value,
                        onClick = {
                            onSetState(value)
                        },
                        label = {
                            Text(
                                when (value) {
                                    PullRequestState.OPEN -> "Open"
                                    PullRequestState.CLOSED -> "Closed"
                                    PullRequestState.ALL -> "All"
                                },
                            )
                        },
                    )
                }
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.operationInProgress,
                onClick = onCreate,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                )
                Text(stringResource(R.string.pulls_new))
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
        } else if (state.pullRequests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.padding(18.dp),
                        text = stringResource(R.string.pulls_no_matches),
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = state.pullRequests,
                key = { it.id },
            ) { pull ->
                PullRequestRow(
                    pull = pull,
                    onOpen = {
                        onOpenPullRequest(pull.number)
                    },
                )
            }
        }
    }
}

@Composable
private fun PullRequestRow(
    pull: PullRequestSummary,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = pull.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "#" + pull.number,
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = buildString {
                    append(
                        if (pull.merged) {
                            "MERGED"
                        } else {
                            pull.state.uppercase()
                        },
                    )
                    if (pull.draft) append(" · DRAFT")
                    append(" · ")
                    append(pull.author.login)
                },
                color = if (
                    pull.state == "open" && !pull.draft
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CallMerge,
                    contentDescription = null,
                )
                Text(
                    text = pull.head.label +
                        " → " + pull.base.label,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (pull.changedFiles > 0) {
                Text(
                    text = pull.commits.toString() +
                        " commits · " +
                        pull.changedFiles.toString() +
                        " files · +" +
                        pull.additions.toString() +
                        " / -" +
                        pull.deletions.toString(),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CreatePullRequestDialog(
    defaultBase: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CreatePullRequestRequest) -> Unit,
) {
    var title by rememberSaveable {
        mutableStateOf("")
    }
    var body by rememberSaveable {
        mutableStateOf("")
    }
    var head by rememberSaveable {
        mutableStateOf("")
    }
    var base by rememberSaveable(defaultBase) {
        mutableStateOf(defaultBase)
    }
    var draft by rememberSaveable {
        mutableStateOf(false)
    }
    var maintainersCanModify by rememberSaveable {
        mutableStateOf(true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                        title.isNotBlank() &&
                        head.isNotBlank() &&
                        base.isNotBlank(),
                onClick = {
                    onCreate(
                        CreatePullRequestRequest(
                            title = title,
                            body = body,
                            head = head,
                            base = base,
                            draft = draft,
                            maintainerCanModify =
                                maintainersCanModify,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.pulls_new))
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pulls_title_field)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = body,
                    onValueChange = { body = it },
                    minLines = 3,
                    maxLines = 7,
                    label = { Text(stringResource(R.string.pulls_description)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = head,
                    onValueChange = { head = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pulls_head_branch)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = base,
                    onValueChange = { base = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pulls_base_branch)) },
                )
                ToggleRow(
                    label = stringResource(R.string.pulls_create_draft),
                    checked = draft,
                    onCheckedChange = { draft = it },
                )
                ToggleRow(
                    label = stringResource(R.string.pulls_maintainer_edits),
                    checked = maintainersCanModify,
                    onCheckedChange = {
                        maintainersCanModify = it
                    },
                )
            }
        },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
