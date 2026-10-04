package com.nexora.git.feature.repositories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.storage.ProjectRisk
import com.nexora.git.core.storage.ProjectRiskSeverity
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceStrategy

@Composable
fun RepositoriesScreen(
    contentPadding: PaddingValues,
    viewModel: RepositoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.importTree(uri)
        }
    }

    RepositoriesContent(
        state = state,
        contentPadding = contentPadding,
        onOpenFolder = {
            folderPicker.launch(null)
        },
        onSync = viewModel::sync,
        onDelete = viewModel::delete,
    )

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            },
            title = {
                Text("Project storage")
            },
            text = {
                Text(message)
            },
        )
    }

    if (state.recentProjectName != null) {
        ImportRiskDialog(
            projectName = state.recentProjectName,
            risks = state.recentRisks,
            onDismiss = viewModel::dismissRisks,
        )
    }
}

@Composable
internal fun RepositoriesContent(
    state: RepositoriesUiState,
    contentPadding: PaddingValues,
    onOpenFolder: () -> Unit,
    onSync: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Repositories",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            Text(
                text = "Open a complete project folder. Nexora Git keeps SAF access and creates a managed Git-ready workspace when Android cannot expose a real filesystem path.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.operationInProgress,
                onClick = onOpenFolder,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                )
                Text(" Open project folder")
            }
        }

        if (state.operationInProgress) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (!state.loading && state.workspaces.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "No local workspaces yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Choose a project folder to register it with Nexora Git.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(
            items = state.workspaces,
            key = Workspace::id,
        ) { workspace ->
            WorkspaceCard(
                workspace = workspace,
                enabled = !state.operationInProgress,
                onSync = {
                    onSync(workspace.id)
                },
                onDelete = {
                    onDelete(workspace.id)
                },
            )
        }
    }
}

@Composable
private fun WorkspaceCard(
    workspace: Workspace,
    enabled: Boolean,
    onSync: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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
                        text = workspace.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (
                            workspace.strategy ==
                                WorkspaceStrategy.MANAGED
                        ) {
                            "Managed workspace"
                        } else {
                            "Direct filesystem"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    enabled = enabled,
                    onClick = onSync,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Sync project",
                    )
                }

                IconButton(
                    enabled = enabled,
                    onClick = onDelete,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove workspace",
                    )
                }
            }

            Text(
                text = workspace.fileCount.toString() +
                    " files · " + humanBytes(workspace.totalBytes),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (workspace.secretWarningCount > 0 ||
                workspace.largeFileWarningCount > 0 ||
                workspace.syncConflictCount > 0
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = workspace.secretWarningCount.toString() +
                            " secret warnings · " +
                            workspace.largeFileWarningCount.toString() +
                            " large-file warnings · " +
                            workspace.syncConflictCount.toString() +
                            " sync conflicts",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportRiskDialog(
    projectName: String,
    risks: List<ProjectRisk>,
    onDismiss: () -> Unit,
) {
    val actionable = risks.filter {
        it.severity != ProjectRiskSeverity.INFO
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        title = {
            Text(
                if (actionable.isEmpty()) {
                    "Project ready"
                } else {
                    "Review before push"
                },
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(projectName)

                if (actionable.isEmpty()) {
                    Text(
                        "The project was scanned and no non-ignored secret or large-file risks were detected.",
                    )
                } else {
                    actionable.take(6).forEach { risk ->
                        Text(
                            text = risk.path + " — " + risk.message,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    if (actionable.size > 6) {
                        Text(
                            text = "+" +
                                (actionable.size - 6).toString() +
                                " more warnings",
                        )
                    }
                }
            }
        },
    )
}

private fun humanBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L ->
            String.format("%.1f GiB", bytes / (1024.0 * 1024.0 * 1024.0))

        bytes >= 1024L * 1024L ->
            String.format("%.1f MiB", bytes / (1024.0 * 1024.0))

        bytes >= 1024L ->
            String.format("%.1f KiB", bytes / 1024.0)

        else -> bytes.toString() + " B"
    }
