package com.nexora.git.feature.issues

import com.nexora.git.R

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.issues.IssueComment
import com.nexora.git.core.issues.IssueDetails
import com.nexora.git.core.issues.IssueLabel
import com.nexora.git.core.issues.IssueMilestone
import com.nexora.git.core.issues.IssueReactionContent
import com.nexora.git.core.issues.IssueState
import com.nexora.git.core.issues.IssueUser
import com.nexora.git.core.issues.UpdateIssueRequest

@Composable
fun IssueDetailScreen(
    contentPadding: PaddingValues,
    activeLogin: String,
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    IssueDetailContent(
        state = state,
        activeLogin = activeLogin,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onUpdateIssue = viewModel::updateIssue,
        onSetState = viewModel::setState,
        onCreateComment = viewModel::createComment,
        onUpdateComment = viewModel::updateComment,
        onDeleteComment = viewModel::deleteComment,
        onReactToIssue = viewModel::reactToIssue,
        onReactToComment = viewModel::reactToComment,
    )

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            title = {
                Text(stringResource(R.string.issue_title))
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
}

@Composable
internal fun IssueDetailContent(
    state: IssueDetailUiState,
    activeLogin: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateIssue: (UpdateIssueRequest) -> Unit,
    onSetState: (IssueState) -> Unit,
    onCreateComment: (String) -> Unit,
    onUpdateComment: (Long, String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onReactToIssue: (IssueReactionContent) -> Unit,
    onReactToComment: (Long, IssueReactionContent) -> Unit,
) {
    var showEdit by rememberSaveable {
        mutableStateOf(false)
    }
    var showMetadata by rememberSaveable {
        mutableStateOf(false)
    }
    var commentDraft by rememberSaveable {
        mutableStateOf("")
    }
    var editingCommentId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var editingCommentBody by rememberSaveable {
        mutableStateOf("")
    }
    var deletingCommentId by rememberSaveable {
        mutableStateOf<Long?>(null)
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
                        contentDescription = stringResource(R.string.issue_back),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.issue_number, state.number),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.owner + "/" + state.repository,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                IconButton(
                    enabled = !state.loading,
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.issue_refresh),
                    )
                }
            }
        }

        if (state.loading && state.issue == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        state.issue?.let { issue ->
            item {
                IssueHeaderCard(issue)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !state.operationInProgress,
                        onClick = {
                            showEdit = true
                        },
                    ) {
                        Text(stringResource(R.string.action_edit))
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !state.operationInProgress,
                        onClick = {
                            showMetadata = true
                        },
                    ) {
                        Text(stringResource(R.string.action_manage))
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !state.operationInProgress,
                        onClick = {
                            onSetState(
                                if (issue.summary.state == "open") {
                                    IssueState.CLOSED
                                } else {
                                    IssueState.OPEN
                                },
                            )
                        },
                    ) {
                        Text(
                            if (issue.summary.state == "open") {
                                "Close"
                            } else {
                                "Reopen"
                            },
                        )
                    }
                }
            }

            item {
                ReactionBar(
                    onReaction = onReactToIssue,
                )
            }

            item {
                Text(
                    text = stringResource(
                        R.string.issue_comments,
                        state.comments.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.comments.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = stringResource(R.string.issue_no_comments),
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = state.comments,
                    key = { it.id },
                ) { comment ->
                    CommentCard(
                        comment = comment,
                        ownComment =
                            comment.author.login.equals(
                                activeLogin,
                                ignoreCase = true,
                            ),
                        busy = state.operationInProgress,
                        onEdit = {
                            editingCommentId = comment.id
                            editingCommentBody = comment.body
                        },
                        onDelete = {
                            deletingCommentId = comment.id
                        },
                        onReact = { reaction ->
                            onReactToComment(
                                comment.id,
                                reaction,
                            )
                        },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.issue_add_comment),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = commentDraft,
                            minLines = 3,
                            maxLines = 8,
                            onValueChange = {
                                commentDraft = it
                            },
                            label = {
                                Text(stringResource(R.string.action_comment))
                            },
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled =
                                !state.operationInProgress &&
                                    commentDraft.isNotBlank(),
                            onClick = {
                                val body = commentDraft
                                commentDraft = ""
                                onCreateComment(body)
                            },
                        ) {
                            Text(stringResource(R.string.action_comment))
                        }
                    }
                }
            }
        }
    }

    val issue = state.issue
    if (showEdit && issue != null) {
        EditIssueDialog(
            issue = issue,
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showEdit = false
            },
            onSave = {
                showEdit = false
                onUpdateIssue(it)
            },
        )
    }

    if (showMetadata && issue != null) {
        ManageIssueDialog(
            issue = issue,
            labels = state.labels,
            assignees = state.assignees,
            milestones = state.milestones,
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showMetadata = false
            },
            onSave = {
                showMetadata = false
                onUpdateIssue(it)
            },
        )
    }

    editingCommentId?.let { id ->
        EditCommentDialog(
            initialBody = editingCommentBody,
            operationInProgress = state.operationInProgress,
            onDismiss = {
                editingCommentId = null
                editingCommentBody = ""
            },
            onSave = { body ->
                editingCommentId = null
                editingCommentBody = ""
                onUpdateComment(id, body)
            },
        )
    }

    deletingCommentId?.let { id ->
        AlertDialog(
            onDismissRequest = {
                deletingCommentId = null
            },
            confirmButton = {
                TextButton(
                    enabled = !state.operationInProgress,
                    onClick = {
                        deletingCommentId = null
                        onDeleteComment(id)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletingCommentId = null
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = {
                Text(stringResource(R.string.issue_delete_comment))
            },
            text = {
                Text(stringResource(R.string.issue_delete_comment_note))
            },
        )
    }
}

@Composable
private fun IssueHeaderCard(
    issue: IssueDetails,
) {
    val summary = issue.summary

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = summary.state.uppercase() +
                    " · opened by " + summary.author.login,
                color = if (summary.state == "open") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (!issue.body.isNullOrBlank()) {
                HorizontalDivider()
                Text(
                    text = issue.body,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (summary.labels.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.issue_labels,
                        summary.labels.joinToString(", ") {
                            it.name
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (summary.assignees.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.issue_assignees,
                        summary.assignees.joinToString(", ") {
                            it.login
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            summary.milestone?.let {
                Text(
                    text = stringResource(R.string.issue_milestone, it.title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = reactionSummary(issue),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CommentCard(
    comment: IssueComment,
    ownComment: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (IssueReactionContent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = comment.author.login,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = comment.updatedAt,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(comment.body)

            Text(
                text = commentReactionSummary(comment),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            ReactionBar(onReaction = onReact)

            if (ownComment) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        enabled = !busy,
                        onClick = onEdit,
                    ) {
                        Text(stringResource(R.string.action_edit))
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = onDelete,
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionBar(
    onReaction: (IssueReactionContent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IssueReactionContent.entries.forEach { reaction ->
            FilterChip(
                selected = false,
                onClick = {
                    onReaction(reaction)
                },
                label = {
                    Text(reaction.wireValue)
                },
            )
        }
    }
}

@Composable
private fun EditIssueDialog(
    issue: IssueDetails,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateIssueRequest) -> Unit,
) {
    var title by rememberSaveable(issue.summary.id) {
        mutableStateOf(issue.summary.title)
    }
    var body by rememberSaveable(issue.summary.id) {
        mutableStateOf(issue.body.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !operationInProgress &&
                        title.isNotBlank(),
                onClick = {
                    onSave(
                        UpdateIssueRequest(
                            title = title,
                            body = body,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.issue_edit))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    singleLine = true,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text(stringResource(R.string.issues_title_field))
                    },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = body,
                    minLines = 5,
                    maxLines = 10,
                    onValueChange = {
                        body = it
                    },
                    label = {
                        Text(stringResource(R.string.issues_description))
                    },
                )
            }
        },
    )
}

@Composable
private fun ManageIssueDialog(
    issue: IssueDetails,
    labels: List<IssueLabel>,
    assignees: List<IssueUser>,
    milestones: List<IssueMilestone>,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateIssueRequest) -> Unit,
) {
    var selectedLabels by rememberSaveable(issue.summary.id) {
        mutableStateOf(
            issue.summary.labels.map { it.name },
        )
    }
    var selectedAssignees by rememberSaveable(issue.summary.id) {
        mutableStateOf(
            issue.summary.assignees.map { it.login },
        )
    }
    var milestone by rememberSaveable(issue.summary.id) {
        mutableStateOf(issue.summary.milestone?.number)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !operationInProgress,
                onClick = {
                    onSave(
                        UpdateIssueRequest(
                            labels = selectedLabels.toList(),
                            assignees =
                                selectedAssignees.toList(),
                            milestone = milestone,
                            clearMilestone =
                                milestone == null &&
                                    issue.summary.milestone != null,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.issue_manage))
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.issues_labels),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(labels, key = { "manage-label-" + it.id }) {
                    label ->
                    FilterChip(
                        selected = label.name in selectedLabels,
                        onClick = {
                            selectedLabels =
                                selectedLabels.toggle(label.name)
                        },
                        label = {
                            Text(label.name)
                        },
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.issues_assignees),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(
                    assignees,
                    key = { "manage-assignee-" + it.login },
                ) { user ->
                    FilterChip(
                        selected =
                            user.login in selectedAssignees,
                        onClick = {
                            selectedAssignees =
                                selectedAssignees.toggle(
                                    user.login,
                                )
                        },
                        label = {
                            Text(user.login)
                        },
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.issues_milestone),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                item {
                    FilterChip(
                        selected = milestone == null,
                        onClick = {
                            milestone = null
                        },
                        label = {
                            Text(stringResource(R.string.issue_no_milestone))
                        },
                    )
                }
                items(
                    milestones,
                    key = { "manage-milestone-" + it.number },
                ) { value ->
                    FilterChip(
                        selected = milestone == value.number,
                        onClick = {
                            milestone = value.number
                        },
                        label = {
                            Text(value.title)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun EditCommentDialog(
    initialBody: String,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var body by rememberSaveable(initialBody) {
        mutableStateOf(initialBody)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !operationInProgress &&
                        body.isNotBlank(),
                onClick = {
                    onSave(body)
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.issue_edit_comment))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = body,
                minLines = 4,
                maxLines = 8,
                onValueChange = {
                    body = it
                },
                label = {
                    Text(stringResource(R.string.action_comment))
                },
            )
        },
    )
}

private fun reactionSummary(issue: IssueDetails): String =
    with(issue.reactions) {
        totalCount.toString() + " reactions · " +
            "+1 " + plusOne + " · heart " + heart +
            " · rocket " + rocket + " · eyes " + eyes
    }

private fun commentReactionSummary(
    comment: IssueComment,
): String =
    with(comment.reactions) {
        totalCount.toString() + " reactions · " +
            "+1 " + plusOne + " · heart " + heart +
            " · rocket " + rocket
    }

private fun List<String>.toggle(value: String): List<String> =
    toMutableList().apply {
        if (!add(value)) remove(value)
    }
