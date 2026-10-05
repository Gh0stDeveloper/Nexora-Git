package com.nexora.git.feature.issues

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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.issues.CreateIssueRequest
import com.nexora.git.core.issues.IssueLabel
import com.nexora.git.core.issues.IssueMilestone
import com.nexora.git.core.issues.IssueState
import com.nexora.git.core.issues.IssueSummary
import com.nexora.git.core.issues.IssueUser

@Composable
fun IssuesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenIssue: (Int) -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable {
        mutableStateOf(false)
    }

    IssuesContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refreshAll,
        onOpenIssue = onOpenIssue,
        onSearch = viewModel::setQuery,
        onSetState = viewModel::setState,
        onToggleLabel = viewModel::toggleLabel,
        onSetAssignee = viewModel::setAssignee,
        onSetMilestone = viewModel::setMilestone,
        onClearFilters = viewModel::clearFilters,
        onCreate = {
            showCreate = true
        },
    )

    if (showCreate) {
        CreateIssueDialog(
            labels = state.labels,
            assignees = state.assignees,
            milestones = state.milestones,
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showCreate = false
            },
            onCreate = { request ->
                showCreate = false
                viewModel.createIssue(request)
            },
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            },
            title = {
                Text("Issues")
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
                    Text("Done")
                }
            },
            title = {
                Text("Nexora Git")
            },
            text = {
                Text(message)
            },
        )
    }
}

@Composable
internal fun IssuesContent(
    state: IssuesUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenIssue: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onSetState: (IssueState) -> Unit,
    onToggleLabel: (String) -> Unit,
    onSetAssignee: (String?) -> Unit,
    onSetMilestone: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onCreate: () -> Unit,
) {
    var search by rememberSaveable(state.owner, state.repository) {
        mutableStateOf(state.filters.query)
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
                        text = "Issues",
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
                        contentDescription = "Refresh issues",
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = search,
                        singleLine = true,
                        onValueChange = {
                            search = it
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text("Search issues")
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !state.loading,
                            onClick = {
                                onSearch(search)
                            },
                        ) {
                            Text("Search")
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.loading,
                            onClick = {
                                search = ""
                                onClearFilters()
                            },
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        item {
            FilterSection(
                state = state,
                onSetState = onSetState,
                onToggleLabel = onToggleLabel,
                onSetAssignee = onSetAssignee,
                onSetMilestone = onSetMilestone,
            )
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
                Text("New issue")
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
        } else if (state.issues.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier.padding(18.dp),
                        text = "No issues match the current filters.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = state.issues,
                key = { it.id },
            ) { issue ->
                IssueRow(
                    issue = issue,
                    onOpen = {
                        onOpenIssue(issue.number)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    state: IssuesUiState,
    onSetState: (IssueState) -> Unit,
    onToggleLabel: (String) -> Unit,
    onSetAssignee: (String?) -> Unit,
    onSetMilestone: (String?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IssueState.entries.forEach { value ->
                    FilterChip(
                        selected = state.filters.state == value,
                        onClick = {
                            onSetState(value)
                        },
                        label = {
                            Text(
                                when (value) {
                                    IssueState.OPEN -> "Open"
                                    IssueState.CLOSED -> "Closed"
                                    IssueState.ALL -> "All"
                                },
                            )
                        },
                    )
                }
            }

            if (state.labels.isNotEmpty()) {
                Text(
                    text = "Labels",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.labels.forEach { label ->
                        FilterChip(
                            selected =
                                label.name in state.filters.labels,
                            onClick = {
                                onToggleLabel(label.name)
                            },
                            label = {
                                Text(label.name)
                            },
                        )
                    }
                }
            }

            if (state.assignees.isNotEmpty()) {
                Text(
                    text = "Assignee",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.filters.assignee == null,
                        onClick = {
                            onSetAssignee(null)
                        },
                        label = {
                            Text("Anyone")
                        },
                    )
                    state.assignees.forEach { user ->
                        FilterChip(
                            selected =
                                state.filters.assignee == user.login,
                            onClick = {
                                onSetAssignee(user.login)
                            },
                            label = {
                                Text(user.login)
                            },
                        )
                    }
                }
            }

            if (state.milestones.isNotEmpty()) {
                Text(
                    text = "Milestone",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.filters.milestone == null,
                        onClick = {
                            onSetMilestone(null)
                        },
                        label = {
                            Text("Any")
                        },
                    )
                    state.milestones.forEach { milestone ->
                        FilterChip(
                            selected =
                                state.filters.milestone ==
                                    milestone.number.toString(),
                            onClick = {
                                onSetMilestone(
                                    milestone.number.toString(),
                                )
                            },
                            label = {
                                Text(milestone.title)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: IssueSummary,
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
                    text = issue.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "#" + issue.number,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = issue.state.uppercase() +
                    " · " + issue.author.login +
                    " · " + issue.comments + " comments",
                color = if (issue.state == "open") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )

            if (issue.labels.isNotEmpty()) {
                Text(
                    text = issue.labels.joinToString(" · ") {
                        it.name
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            issue.milestone?.let {
                Text(
                    text = "Milestone: " + it.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CreateIssueDialog(
    labels: List<IssueLabel>,
    assignees: List<IssueUser>,
    milestones: List<IssueMilestone>,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CreateIssueRequest) -> Unit,
) {
    var title by rememberSaveable {
        mutableStateOf("")
    }
    var body by rememberSaveable {
        mutableStateOf("")
    }
    var selectedLabels by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var selectedAssignees by rememberSaveable {
        mutableStateOf(emptySet<String>())
    }
    var selectedMilestone by rememberSaveable {
        mutableStateOf<Int?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !operationInProgress &&
                        title.isNotBlank(),
                onClick = {
                    onCreate(
                        CreateIssueRequest(
                            title = title,
                            body = body,
                            labels = selectedLabels.toList(),
                            assignees =
                                selectedAssignees.toList(),
                            milestone = selectedMilestone,
                        ),
                    )
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !operationInProgress,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("New issue")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = title,
                        singleLine = true,
                        onValueChange = {
                            title = it
                        },
                        label = {
                            Text("Title")
                        },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = body,
                        minLines = 4,
                        maxLines = 8,
                        onValueChange = {
                            body = it
                        },
                        label = {
                            Text("Description")
                        },
                    )
                }
                if (labels.isNotEmpty()) {
                    item {
                        Text(
                            text = "Labels",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(labels, key = { "label-" + it.id }) {
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
                }
                if (assignees.isNotEmpty()) {
                    item {
                        Text(
                            text = "Assignees",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(
                        assignees,
                        key = { "assignee-" + it.login },
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
                }
                if (milestones.isNotEmpty()) {
                    item {
                        Text(
                            text = "Milestone",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(
                        milestones,
                        key = { "milestone-" + it.number },
                    ) { milestone ->
                        FilterChip(
                            selected =
                                selectedMilestone ==
                                    milestone.number,
                            onClick = {
                                selectedMilestone =
                                    if (
                                        selectedMilestone ==
                                            milestone.number
                                    ) {
                                        null
                                    } else {
                                        milestone.number
                                    }
                            },
                            label = {
                                Text(milestone.title)
                            },
                        )
                    }
                }
            }
        },
    )
}

private fun Set<String>.toggle(value: String): Set<String> =
    toMutableSet().apply {
        if (!add(value)) remove(value)
    }
