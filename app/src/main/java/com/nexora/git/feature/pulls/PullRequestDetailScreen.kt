package com.nexora.git.feature.pulls

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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Comment
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.pulls.CreateReviewCommentRequest
import com.nexora.git.core.pulls.PullRequestCheckRun
import com.nexora.git.core.pulls.PullRequestFile
import com.nexora.git.core.pulls.PullRequestMergeMethod
import com.nexora.git.core.pulls.PullRequestReview
import com.nexora.git.core.pulls.PullRequestReviewComment
import com.nexora.git.core.pulls.PullRequestReviewEvent
import com.nexora.git.core.pulls.PullRequestState
import com.nexora.git.core.pulls.PullRequestSummary
import com.nexora.git.core.pulls.ReviewSide
import com.nexora.git.core.pulls.UpdatePullRequestRequest

private enum class PullRequestDetailTab(
    val label: String,
) {
    OVERVIEW("Overview"),
    FILES("Files"),
    REVIEWS("Reviews"),
    CHECKS("Checks"),
}

@Composable
fun PullRequestDetailScreen(
    contentPadding: PaddingValues,
    activeLogin: String,
    onBack: () -> Unit,
    viewModel: PullRequestDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullRequestDetailContent(
        state = state,
        activeLogin = activeLogin,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onUpdate = viewModel::update,
        onSetState = viewModel::setState,
        onSetDraft = viewModel::setDraft,
        onSubmitReview = viewModel::submitReview,
        onCreateReviewComment =
            viewModel::createReviewComment,
        onUpdateReviewComment =
            viewModel::updateReviewComment,
        onDeleteReviewComment =
            viewModel::deleteReviewComment,
        onMerge = viewModel::merge,
    )

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            },
            title = { Text("Pull request") },
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
}

@Composable
internal fun PullRequestDetailContent(
    state: PullRequestDetailUiState,
    activeLogin: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: (UpdatePullRequestRequest) -> Unit,
    onSetState: (PullRequestState) -> Unit,
    onSetDraft: (Boolean) -> Unit,
    onSubmitReview: (
        PullRequestReviewEvent,
        String,
    ) -> Unit,
    onCreateReviewComment:
        (CreateReviewCommentRequest) -> Unit,
    onUpdateReviewComment: (Long, String) -> Unit,
    onDeleteReviewComment: (Long) -> Unit,
    onMerge: (PullRequestMergeMethod) -> Unit,
) {
    var selectedTab by rememberSaveable {
        mutableStateOf(PullRequestDetailTab.OVERVIEW)
    }
    var showEdit by rememberSaveable {
        mutableStateOf(false)
    }
    var reviewEvent by rememberSaveable {
        mutableStateOf<PullRequestReviewEvent?>(null)
    }
    var inlineFile by rememberSaveable {
        mutableStateOf<String?>(null)
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
    var mergeMethod by rememberSaveable {
        mutableStateOf<PullRequestMergeMethod?>(null)
    }

    val pull = state.pullRequest

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
                        text = "Pull request #" + state.number,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = state.owner + "/" + state.repository,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled =
                        !state.loading &&
                            !state.operationInProgress,
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh pull request",
                    )
                }
            }
        }

        if (state.loading && pull == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (pull != null) {
            item {
                PullRequestHeaderCard(
                    pull = pull,
                    state = state,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    PullRequestDetailTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                            },
                            label = {
                                Text(
                                    when (tab) {
                                        PullRequestDetailTab.OVERVIEW ->
                                            "Overview"
                                        PullRequestDetailTab.FILES ->
                                            "Files (" +
                                                state.files.size +
                                                ")"
                                        PullRequestDetailTab.REVIEWS ->
                                            "Reviews (" +
                                                state.reviews.size +
                                                ")"
                                        PullRequestDetailTab.CHECKS ->
                                            "Checks (" +
                                                state.checks.size +
                                                ")"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            when (selectedTab) {
                PullRequestDetailTab.OVERVIEW -> {
                    item {
                        OverviewActions(
                            pull = pull,
                            state = state,
                            onEdit = {
                                showEdit = true
                            },
                            onSetState = onSetState,
                            onSetDraft = onSetDraft,
                            onMerge = {
                                mergeMethod = it
                            },
                        )
                    }

                    item {
                        ReviewActionCard(
                            busy = state.operationInProgress,
                            draft = pull.draft,
                            merged = pull.merged,
                            closed = pull.state == "closed",
                            onReview = {
                                reviewEvent = it
                            },
                        )
                    }

                    item {
                        PullRequestBodyCard(pull)
                    }
                }

                PullRequestDetailTab.FILES -> {
                    if (state.files.isEmpty()) {
                        item {
                            EmptyCard(
                                "No changed files are available.",
                            )
                        }
                    } else {
                        items(
                            items = state.files,
                            key = { it.filename },
                        ) { file ->
                            PullRequestFileCard(
                                file = file,
                                busy =
                                    state.operationInProgress,
                                onComment = {
                                    inlineFile =
                                        file.filename
                                },
                            )
                        }
                    }
                }

                PullRequestDetailTab.REVIEWS -> {
                    item {
                        Text(
                            text = "Submitted reviews",
                            style =
                                MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (state.reviews.isEmpty()) {
                        item {
                            EmptyCard("No submitted reviews.")
                        }
                    } else {
                        items(
                            items = state.reviews,
                            key = { "review-" + it.id },
                        ) { review ->
                            ReviewCard(review)
                        }
                    }

                    item {
                        Text(
                            text = "Inline comments",
                            style =
                                MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (state.reviewComments.isEmpty()) {
                        item {
                            EmptyCard(
                                "No inline review comments.",
                            )
                        }
                    } else {
                        items(
                            items = state.reviewComments,
                            key = { "comment-" + it.id },
                        ) { comment ->
                            ReviewCommentCard(
                                comment = comment,
                                ownComment =
                                    comment.author.login.equals(
                                        activeLogin,
                                        ignoreCase = true,
                                    ),
                                busy =
                                    state.operationInProgress,
                                onEdit = {
                                    editingCommentId =
                                        comment.id
                                    editingCommentBody =
                                        comment.body
                                },
                                onDelete = {
                                    deletingCommentId =
                                        comment.id
                                },
                            )
                        }
                    }
                }

                PullRequestDetailTab.CHECKS -> {
                    item {
                        ChecksSummaryCard(state)
                    }
                    if (state.checks.isEmpty()) {
                        item {
                            EmptyCard(
                                "No check runs are associated with the current head commit.",
                            )
                        }
                    } else {
                        items(
                            items = state.checks,
                            key = { "check-" + it.id },
                        ) { check ->
                            CheckCard(check)
                        }
                    }
                }
            }
        }
    }

    if (showEdit && pull != null) {
        EditPullRequestDialog(
            pull = pull,
            busy = state.operationInProgress,
            onDismiss = {
                showEdit = false
            },
            onSave = {
                showEdit = false
                onUpdate(it)
            },
        )
    }

    reviewEvent?.let { event ->
        ReviewDialog(
            event = event,
            busy = state.operationInProgress,
            onDismiss = {
                reviewEvent = null
            },
            onSubmit = { body ->
                reviewEvent = null
                onSubmitReview(event, body)
            },
        )
    }

    inlineFile?.let { path ->
        if (pull != null) {
            InlineReviewCommentDialog(
                path = path,
                headSha = pull.head.sha,
                busy = state.operationInProgress,
                onDismiss = {
                    inlineFile = null
                },
                onSubmit = {
                    inlineFile = null
                    onCreateReviewComment(it)
                },
            )
        }
    }

    editingCommentId?.let { id ->
        EditReviewCommentDialog(
            initialBody = editingCommentBody,
            busy = state.operationInProgress,
            onDismiss = {
                editingCommentId = null
                editingCommentBody = ""
            },
            onSave = { body ->
                editingCommentId = null
                editingCommentBody = ""
                onUpdateReviewComment(id, body)
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
                        onDeleteReviewComment(id)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletingCommentId = null
                    },
                ) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete review comment?") },
            text = {
                Text(
                    "This permanently deletes the inline GitHub review comment.",
                )
            },
        )
    }

    mergeMethod?.let { method ->
        if (pull != null) {
            MergeConfirmationDialog(
                pull = pull,
                method = method,
                busy = state.operationInProgress,
                onDismiss = {
                    mergeMethod = null
                },
                onConfirm = {
                    mergeMethod = null
                    onMerge(method)
                },
            )
        }
    }
}

@Composable
private fun PullRequestHeaderCard(
    pull: PullRequestSummary,
    state: PullRequestDetailUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = pull.title,
                style = MaterialTheme.typography.headlineSmall,
            )

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
            )

            Text(
                text = pull.head.label +
                    " → " + pull.base.label,
                fontFamily = FontFamily.Monospace,
            )

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
            )

            Text(
                text = "Checks: " +
                    state.successfulChecks +
                    " passed · " +
                    state.failedChecks +
                    " failed",
                color = if (state.failedChecks > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            pull.mergeableState?.let {
                Text(
                    text = "Merge state: " + it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewActions(
    pull: PullRequestSummary,
    state: PullRequestDetailUiState,
    onEdit: () -> Unit,
    onSetState: (PullRequestState) -> Unit,
    onSetDraft: (Boolean) -> Unit,
    onMerge: (PullRequestMergeMethod) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Pull request actions",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled =
                        !state.operationInProgress &&
                            !pull.merged,
                    onClick = onEdit,
                ) {
                    Text("Edit")
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled =
                        !state.operationInProgress &&
                            !pull.merged &&
                            pull.state == "open",
                    onClick = {
                        onSetDraft(!pull.draft)
                    },
                ) {
                    Text(
                        if (pull.draft) {
                            "Ready"
                        } else {
                            "Draft"
                        },
                    )
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled =
                        !state.operationInProgress &&
                            !pull.merged,
                    onClick = {
                        onSetState(
                            if (pull.state == "open") {
                                PullRequestState.CLOSED
                            } else {
                                PullRequestState.OPEN
                            },
                        )
                    },
                ) {
                    Text(
                        if (pull.state == "open") {
                            "Close"
                        } else {
                            "Reopen"
                        },
                    )
                }
            }

            if (
                pull.state == "open" &&
                !pull.draft &&
                !pull.merged
            ) {
                HorizontalDivider()
                Text(
                    text = "Merge",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.allowedMergeMethods.isEmpty()) {
                    Text(
                        text = "No merge method is enabled for this repository.",
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState(),
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        state.allowedMergeMethods.forEach {
                            method ->
                            Button(
                                enabled =
                                    !state.operationInProgress &&
                                        pull.mergeable != false,
                                onClick = {
                                    onMerge(method)
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Outlined.CallMerge,
                                    contentDescription = null,
                                )
                                Text(
                                    when (method) {
                                        PullRequestMergeMethod.MERGE ->
                                            "Merge"
                                        PullRequestMergeMethod.SQUASH ->
                                            "Squash"
                                        PullRequestMergeMethod.REBASE ->
                                            "Rebase"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewActionCard(
    busy: Boolean,
    draft: Boolean,
    merged: Boolean,
    closed: Boolean,
    onReview: (PullRequestReviewEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Submit review",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (draft) {
                    "This pull request is still a draft. Reviews can be prepared after it is marked ready."
                } else {
                    "Approve, request changes, or leave a general review comment."
                },
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled =
                        !busy &&
                            !draft &&
                            !merged &&
                            !closed,
                    onClick = {
                        onReview(
                            PullRequestReviewEvent.APPROVE,
                        )
                    },
                ) {
                    Text("Approve")
                }
                OutlinedButton(
                    enabled =
                        !busy &&
                            !draft &&
                            !merged &&
                            !closed,
                    onClick = {
                        onReview(
                            PullRequestReviewEvent.REQUEST_CHANGES,
                        )
                    },
                ) {
                    Text("Request changes")
                }
                OutlinedButton(
                    enabled =
                        !busy &&
                            !merged &&
                            !closed,
                    onClick = {
                        onReview(
                            PullRequestReviewEvent.COMMENT,
                        )
                    },
                ) {
                    Text("Comment")
                }
            }
        }
    }
}

@Composable
private fun PullRequestBodyCard(
    pull: PullRequestSummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pull.body
                    ?.takeIf(String::isNotBlank)
                    ?: "No description.",
                color =
                    if (pull.body.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

@Composable
private fun PullRequestFileCard(
    file: PullRequestFile,
    busy: Boolean,
    onComment: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = file.filename,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Text(
                text = file.status +
                    " · +" + file.additions +
                    " / -" + file.deletions,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            file.previousFilename?.let {
                Text(
                    text = "Previous: " + it,
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            file.patch?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                enabled = !busy,
                onClick = onComment,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Comment,
                    contentDescription = null,
                )
                Text("Add inline comment")
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: PullRequestReview,
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = review.author.login,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = review.state,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            review.body?.takeIf(String::isNotBlank)?.let {
                Text(it)
            }
            review.commitId?.let {
                Text(
                    text = "Commit " + it.take(7),
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReviewCommentCard(
    comment: PullRequestReviewComment,
    ownComment: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
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
                    text = comment.path,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(comment.body)

            Text(
                text = buildString {
                    append("Line ")
                    append(comment.line ?: "?")
                    comment.side?.let {
                        append(" · ")
                        append(it)
                    }
                },
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            comment.diffHunk?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ownComment) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        enabled = !busy,
                        onClick = onEdit,
                    ) {
                        Text("Edit")
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = onDelete,
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecksSummaryCard(
    state: PullRequestDetailUiState,
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
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                )
                Text(
                    text = "Checks",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = state.successfulChecks.toString() +
                    " passed · " +
                    state.failedChecks.toString() +
                    " failed · " +
                    state.checks.count {
                        it.status != "completed"
                    }.toString() +
                    " running/pending",
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckCard(
    check: PullRequestCheckRun,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = check.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = check.status +
                    (
                        check.conclusion
                            ?.let { " · " + it }
                            .orEmpty()
                        ),
                color = if (
                    when (check.conclusion?.lowercase()) {
                        "failure",
                        "timed_out",
                        "cancelled",
                        "action_required" -> true
                        else -> false
                    }
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun EditPullRequestDialog(
    pull: PullRequestSummary,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdatePullRequestRequest) -> Unit,
) {
    var title by rememberSaveable(pull.id) {
        mutableStateOf(pull.title)
    }
    var body by rememberSaveable(pull.id) {
        mutableStateOf(pull.body.orEmpty())
    }
    var base by rememberSaveable(pull.id) {
        mutableStateOf(pull.base.ref)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                        title.isNotBlank() &&
                        base.isNotBlank(),
                onClick = {
                    onSave(
                        UpdatePullRequestRequest(
                            title = title,
                            body = body,
                            base = base,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = { Text("Edit pull request") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Title") },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = body,
                    onValueChange = { body = it },
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("Description") },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = base,
                    onValueChange = { base = it },
                    singleLine = true,
                    label = { Text("Base branch") },
                )
            }
        },
    )
}

@Composable
private fun ReviewDialog(
    event: PullRequestReviewEvent,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var body by rememberSaveable(event.name) {
        mutableStateOf("")
    }
    val requiresBody =
        event != PullRequestReviewEvent.APPROVE

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                        (!requiresBody || body.isNotBlank()),
                onClick = {
                    onSubmit(body)
                },
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                when (event) {
                    PullRequestReviewEvent.APPROVE ->
                        "Approve pull request"
                    PullRequestReviewEvent.REQUEST_CHANGES ->
                        "Request changes"
                    PullRequestReviewEvent.COMMENT ->
                        "Review comment"
                },
            )
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = body,
                onValueChange = { body = it },
                minLines = 4,
                maxLines = 8,
                label = {
                    Text(
                        if (requiresBody) {
                            "Review message"
                        } else {
                            "Optional message"
                        },
                    )
                },
            )
        },
    )
}

@Composable
private fun InlineReviewCommentDialog(
    path: String,
    headSha: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CreateReviewCommentRequest) -> Unit,
) {
    var body by rememberSaveable(path) {
        mutableStateOf("")
    }
    var lineText by rememberSaveable(path) {
        mutableStateOf("")
    }
    var side by rememberSaveable(path) {
        mutableStateOf(ReviewSide.RIGHT)
    }

    val line = lineText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                        body.isNotBlank() &&
                        line != null &&
                        line > 0,
                onClick = {
                    onSubmit(
                        CreateReviewCommentRequest(
                            body = body,
                            commitId = headSha,
                            path = path,
                            line = requireNotNull(line),
                            side = side,
                        ),
                    )
                },
            ) {
                Text("Comment")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("Inline review comment")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = path,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = lineText,
                    onValueChange = {
                        lineText = it.filter(Char::isDigit)
                    },
                    singleLine = true,
                    label = { Text("Line number") },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReviewSide.entries.forEach { value ->
                        FilterChip(
                            selected = side == value,
                            onClick = { side = value },
                            label = {
                                Text(
                                    if (value == ReviewSide.RIGHT) {
                                        "New"
                                    } else {
                                        "Old"
                                    },
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = body,
                    onValueChange = { body = it },
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("Comment") },
                )
                Text(
                    text = "Head commit: " + headSha.take(12),
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun EditReviewCommentDialog(
    initialBody: String,
    busy: Boolean,
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
                enabled = !busy && body.isNotBlank(),
                onClick = { onSave(body) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = { Text("Edit review comment") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = body,
                onValueChange = { body = it },
                minLines = 4,
                maxLines = 8,
                label = { Text("Comment") },
            )
        },
    )
}

@Composable
private fun MergeConfirmationDialog(
    pull: PullRequestSummary,
    method: PullRequestMergeMethod,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onConfirm,
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = { Text("Merge pull request?") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Method: " + method.wireValue,
                )
                Text(
                    text = "Nexora Git will require the current head SHA. GitHub rejects the merge if the pull request changes before this request reaches the server.",
                )
                Text(
                    text = pull.head.sha.take(12),
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun EmptyCard(
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
