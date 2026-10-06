package com.nexora.git.feature.actions

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.actions.GitHubWorkflow
import com.nexora.git.core.actions.GitHubWorkflowRun
import com.nexora.git.core.actions.WorkflowRunStatusFilter

@Composable
fun ActionsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenRun: (Long) -> Unit,
    viewModel: ActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dispatchWorkflowId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    val dispatchWorkflow = state.workflows
        .firstOrNull { it.id == dispatchWorkflowId }

    ActionsContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refreshAll,
        onSelectWorkflow = viewModel::selectWorkflow,
        onSetStatus = viewModel::setStatusFilter,
        onOpenRun = onOpenRun,
        onDispatch = {
            dispatchWorkflowId = it.id
        },
    )

    dispatchWorkflow?.let { workflow ->
        DispatchWorkflowDialog(
            workflow = workflow,
            defaultRef = state.defaultBranch,
            busy = state.operationInProgress,
            onDismiss = {
                dispatchWorkflowId = null
            },
            onDispatch = { ref, inputs ->
                dispatchWorkflowId = null
                viewModel.dispatch(
                    workflowId = workflow.id,
                    ref = ref,
                    inputs = inputs,
                )
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
            title = { Text("GitHub Actions") },
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
            title = { Text("GitHub Actions") },
            text = { Text(message) },
        )
    }
}

@Composable
internal fun ActionsContent(
    state: ActionsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectWorkflow: (Long?) -> Unit,
    onSetStatus: (WorkflowRunStatusFilter) -> Unit,
    onOpenRun: (Long) -> Unit,
    onDispatch: (GitHubWorkflow) -> Unit,
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
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "GitHub Actions",
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
                        contentDescription = "Refresh Actions",
                    )
                }
            }
        }

        item {
            Text(
                text = "Workflows",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (state.workflows.isEmpty() && !state.loading) {
            item {
                EmptyActionsCard(
                    "No workflows are available in this repository.",
                )
            }
        } else {
            items(
                items = state.workflows,
                key = { "workflow-" + it.id },
            ) { workflow ->
                WorkflowCard(
                    workflow = workflow,
                    selected =
                        state.selectedWorkflowId ==
                            workflow.id,
                    busy = state.operationInProgress,
                    onSelect = {
                        onSelectWorkflow(
                            if (
                                state.selectedWorkflowId ==
                                    workflow.id
                            ) {
                                null
                            } else {
                                workflow.id
                            },
                        )
                    },
                    onDispatch = {
                        onDispatch(workflow)
                    },
                )
            }
        }

        item {
            Text(
                text = "Runs",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkflowRunStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected =
                            state.statusFilter == status,
                        onClick = {
                            onSetStatus(status)
                        },
                        label = {
                            Text(statusLabel(status))
                        },
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
        } else if (state.runs.isEmpty()) {
            item {
                EmptyActionsCard(
                    "No workflow runs match the current filters.",
                )
            }
        } else {
            items(
                items = state.runs,
                key = { "run-" + it.id },
            ) { run ->
                WorkflowRunCard(
                    run = run,
                    onOpen = {
                        onOpenRun(run.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: GitHubWorkflow,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onDispatch: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = workflow.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = workflow.path,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (selected) {
                        "Selected"
                    } else {
                        workflow.state
                    },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            OutlinedButton(
                enabled =
                    !busy &&
                        workflow.state.equals(
                            "active",
                            ignoreCase = true,
                        ),
                onClick = onDispatch,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                )
                Text("Dispatch")
            }
        }
    }
}

@Composable
private fun WorkflowRunCard(
    run: GitHubWorkflowRun,
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
                    text = run.displayTitle
                        ?: run.name
                        ?: "Workflow run",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "#" + run.runNumber,
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = buildString {
                    append(run.status ?: "unknown")
                    run.conclusion?.let {
                        append(" · ")
                        append(it)
                    }
                    append(" · ")
                    append(run.event)
                },
                color = runStatusColor(run),
            )

            Text(
                text = (run.headBranch ?: "detached") +
                    " · " + run.headSha.take(10),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            run.actorLogin?.let {
                Text(
                    text = "Triggered by " + it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun runStatusColor(
    run: GitHubWorkflowRun,
) = when (run.conclusion?.lowercase()) {
    "failure",
    "timed_out",
    "cancelled",
    "action_required" ->
        MaterialTheme.colorScheme.error

    "success" ->
        MaterialTheme.colorScheme.primary

    else ->
        MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DispatchWorkflowDialog(
    workflow: GitHubWorkflow,
    defaultRef: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDispatch: (
        ref: String,
        inputs: Map<String, String>,
    ) -> Unit,
) {
    var ref by rememberSaveable(
        workflow.id,
        defaultRef,
    ) {
        mutableStateOf(defaultRef)
    }
    var rawInputs by rememberSaveable(workflow.id) {
        mutableStateOf("")
    }
    var localError by rememberSaveable(workflow.id) {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !busy && ref.isNotBlank(),
                onClick = {
                    when (
                        val parsed =
                            parseDispatchInputs(rawInputs)
                    ) {
                        is DispatchInputParseResult.Success -> {
                            localError = null
                            onDispatch(
                                ref.trim(),
                                parsed.inputs,
                            )
                        }
                        is DispatchInputParseResult.Failure -> {
                            localError =
                                "Line " +
                                    parsed.line +
                                    ": " +
                                    parsed.message
                        }
                    }
                },
            ) {
                Text("Run workflow")
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
            Text("Dispatch " + workflow.name)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = ref,
                    onValueChange = { ref = it },
                    singleLine = true,
                    label = { Text("Git ref") },
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = rawInputs,
                    onValueChange = {
                        rawInputs = it
                        localError = null
                    },
                    minLines = 4,
                    maxLines = 9,
                    label = {
                        Text("Inputs (optional)")
                    },
                    supportingText = {
                        Text(
                            "One key=value pair per line.",
                        )
                    },
                )

                localError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = "GitHub validates whether this workflow supports workflow_dispatch and which inputs are accepted.",
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun EmptyActionsCard(
    text: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = text,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusLabel(
    status: WorkflowRunStatusFilter,
): String = when (status) {
    WorkflowRunStatusFilter.ALL -> "All"
    WorkflowRunStatusFilter.QUEUED -> "Queued"
    WorkflowRunStatusFilter.IN_PROGRESS -> "Running"
    WorkflowRunStatusFilter.COMPLETED -> "Completed"
    WorkflowRunStatusFilter.FAILURE -> "Failure"
    WorkflowRunStatusFilter.SUCCESS -> "Success"
    WorkflowRunStatusFilter.CANCELLED -> "Cancelled"
}
