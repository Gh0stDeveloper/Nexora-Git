package com.nexora.git.feature.actions

import com.nexora.git.R

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.actions.GitHubActionsArtifact
import com.nexora.git.core.actions.GitHubActionsJob
import com.nexora.git.core.actions.GitHubActionsStep
import com.nexora.git.core.actions.GitHubWorkflowRun
import java.util.Locale

@Composable
fun WorkflowRunDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: WorkflowRunDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkflowRunDetailContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCancel = viewModel::cancel,
        onRerun = viewModel::rerun,
        onRerunFailed = viewModel::rerunFailedJobs,
        onOpenLog = viewModel::loadJobLog,
        onDownloadArtifact = viewModel::downloadArtifact,
    )

    if (state.selectedJobId != null) {
        JobLogDialog(
            jobName = state.selectedJobName.orEmpty(),
            loading = state.logLoading,
            text = state.log?.text,
            truncated = state.log?.truncated == true,
            onDismiss = viewModel::closeLog,
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
            title = { Text(stringResource(R.string.actions_title)) },
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
            title = { Text(stringResource(R.string.actions_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(message)
                    state.lastDownload?.let { download ->
                        SelectionContainer {
                            Text(
                                text = download.filePath,
                                fontFamily = FontFamily.Monospace,
                                style =
                                    MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun WorkflowRunDetailContent(
    state: WorkflowRunDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onRerunFailed: () -> Unit,
    onOpenLog: (GitHubActionsJob) -> Unit,
    onDownloadArtifact: (GitHubActionsArtifact) -> Unit,
) {
    val run = state.run

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
                        contentDescription = stringResource(R.string.workflow_back),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.workflow_run_title),
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
                        contentDescription = stringResource(R.string.workflow_refresh),
                    )
                }
            }
        }

        if (state.loading && run == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (run != null) {
            item {
                WorkflowRunSummaryCard(run)
            }

            item {
                WorkflowRunControls(
                    state = state,
                    onCancel = onCancel,
                    onRerun = onRerun,
                    onRerunFailed = onRerunFailed,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.workflow_jobs),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.jobs.isEmpty()) {
                item {
                    ActionsDetailEmptyCard(
                        "No jobs are available for this run.",
                    )
                }
            } else {
                items(
                    items = state.jobs,
                    key = { "job-" + it.id },
                ) { job ->
                    ActionsJobCard(
                        job = job,
                        busy = state.operationInProgress,
                        onOpenLog = {
                            onOpenLog(job)
                        },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.workflow_artifacts),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.artifacts.isEmpty()) {
                item {
                    ActionsDetailEmptyCard(
                        "This workflow run has no artifacts.",
                    )
                }
            } else {
                items(
                    items = state.artifacts,
                    key = { "artifact-" + it.id },
                ) { artifact ->
                    ArtifactCard(
                        artifact = artifact,
                        busy = state.operationInProgress,
                        onDownload = {
                            onDownloadArtifact(artifact)
                        },
                    )
                }
            }

            state.lastDownload?.let { download ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.workflow_last_downloaded),
                                style =
                                    MaterialTheme.typography.titleSmall,
                            )
                            SelectionContainer {
                                Text(
                                    text = download.filePath,
                                    fontFamily =
                                        FontFamily.Monospace,
                                    style =
                                        MaterialTheme.typography.bodySmall,
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
private fun WorkflowRunSummaryCard(
    run: GitHubWorkflowRun,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = run.displayTitle
                    ?: run.name
                    ?: "Workflow run",
                style = MaterialTheme.typography.headlineSmall,
            )

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
                color = when (
                    run.conclusion?.lowercase()
                ) {
                    "failure",
                    "timed_out",
                    "cancelled",
                    "action_required" ->
                        MaterialTheme.colorScheme.error
                    "success" ->
                        MaterialTheme.colorScheme.primary
                    else ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Text(
                text = stringResource(
                    R.string.workflow_run_attempt,
                    run.runNumber,
                    run.runAttempt,
                ),
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = stringResource(
                    R.string.workflow_branch_sha,
                    run.headBranch
                        ?: stringResource(R.string.workflow_detached),
                    run.headSha.take(12),
                ),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            run.actorLogin?.let {
                Text(
                    text = stringResource(R.string.workflow_triggered_by, it),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkflowRunControls(
    state: WorkflowRunDetailUiState,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onRerunFailed: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.workflow_controls),
                style = MaterialTheme.typography.titleMedium,
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
                            state.canCancel,
                    onClick = onCancel,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }

                OutlinedButton(
                    enabled =
                        !state.operationInProgress &&
                            state.canRerun,
                    onClick = onRerun,
                ) {
                    Text(stringResource(R.string.workflow_rerun_all))
                }

                OutlinedButton(
                    enabled =
                        !state.operationInProgress &&
                            state.canRerun &&
                            state.run?.conclusion != "success",
                    onClick = onRerunFailed,
                ) {
                    Text(stringResource(R.string.workflow_rerun_failed))
                }
            }

            Text(
                text = stringResource(R.string.workflow_authority_note),
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ActionsJobCard(
    job: GitHubActionsJob,
    busy: Boolean,
    onOpenLog: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = job.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = job.conclusion ?: job.status,
                    color = when (
                        job.conclusion?.lowercase()
                    ) {
                        "failure",
                        "timed_out",
                        "cancelled" ->
                            MaterialTheme.colorScheme.error
                        "success" ->
                            MaterialTheme.colorScheme.primary
                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (job.labels.isNotEmpty()) {
                Text(
                    text = job.labels.joinToString(" · "),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            job.runnerName?.let {
                Text(
                    text = stringResource(R.string.workflow_runner, it),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (job.steps.isNotEmpty()) {
                HorizontalDivider()
                job.steps.forEach { step ->
                    JobStepRow(step)
                }
            }

            OutlinedButton(
                enabled = !busy,
                onClick = onOpenLog,
            ) {
                Text(stringResource(R.string.workflow_view_logs))
            }
        }
    }
}

@Composable
private fun JobStepRow(
    step: GitHubActionsStep,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = step.number.toString(),
            fontFamily = FontFamily.Monospace,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = step.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = step.conclusion ?: step.status,
                color = when (
                    step.conclusion?.lowercase()
                ) {
                    "failure",
                    "timed_out",
                    "cancelled" ->
                        MaterialTheme.colorScheme.error
                    "success" ->
                        MaterialTheme.colorScheme.primary
                    else ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ArtifactCard(
    artifact: GitHubActionsArtifact,
    busy: Boolean,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = artifact.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = humanBytes(artifact.sizeInBytes),
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text =
                        if (artifact.expired) {
                            "Expired"
                        } else {
                            "Available"
                        },
                    color =
                        if (artifact.expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }

            artifact.expiresAt?.let {
                Text(
                    text = stringResource(R.string.workflow_expires, it),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                enabled =
                    !busy &&
                        !artifact.expired,
                onClick = onDownload,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                )
                Text(stringResource(R.string.workflow_download_zip))
            }
        }
    }
}

@Composable
private fun JobLogDialog(
    jobName: String,
    loading: Boolean,
    text: String?,
    truncated: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        title = {
            Text(
                text = if (jobName.isBlank()) {
                    "Job logs"
                } else {
                    jobName
                },
            )
        },
        text = {
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                text != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(
                                rememberScrollState(),
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        if (truncated) {
                            Text(
                                text = stringResource(R.string.workflow_preview_truncated),
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                style =
                                    MaterialTheme.typography.bodySmall,
                            )
                        }
                        SelectionContainer {
                            Text(
                                text = text,
                                fontFamily =
                                    FontFamily.Monospace,
                                style =
                                    MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                else -> {
                    Text(stringResource(R.string.workflow_no_logs))
                }
            }
        },
    )
}

@Composable
private fun ActionsDetailEmptyCard(
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

private fun humanBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L ->
            String.format(
                Locale.US,
                "%.2f GiB",
                bytes / (1024.0 * 1024.0 * 1024.0),
            )

        bytes >= 1024L * 1024L ->
            String.format(
                Locale.US,
                "%.1f MiB",
                bytes / (1024.0 * 1024.0),
            )

        bytes >= 1024L ->
            String.format(
                Locale.US,
                "%.1f KiB",
                bytes / 1024.0,
            )

        else -> bytes.toString() + " B"
    }
