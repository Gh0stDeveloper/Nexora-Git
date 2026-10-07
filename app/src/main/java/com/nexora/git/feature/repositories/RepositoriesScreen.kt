package com.nexora.git.feature.repositories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.R
import com.nexora.git.core.repository.CreateRepositoryRequest
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.storage.ProjectRisk
import com.nexora.git.core.storage.ProjectRiskSeverity
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceStrategy
import com.nexora.git.core.templates.ProjectTemplateSummary
import java.util.Locale

enum class RepositoryEntryAction {
    NONE,
    CLONE,
    IMPORT,
    CREATE,
}

@Composable
fun RepositoriesScreen(
    contentPadding: PaddingValues,
    onOpenRepository: (String, String) -> Unit,
    onBrowseWorkspace: (String) -> Unit,
    onOpenGitWorkspace: (String) -> Unit,
    initialAction: RepositoryEntryAction = RepositoryEntryAction.NONE,
    onInitialActionConsumed: () -> Unit = {},
    viewModel: RepositoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showCloneDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showTemplateDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.importTree(uri)
        }
    }

    LaunchedEffect(initialAction) {
        when (initialAction) {
            RepositoryEntryAction.NONE -> Unit
            RepositoryEntryAction.CLONE -> showCloneDialog = true
            RepositoryEntryAction.IMPORT -> folderPicker.launch(null)
            RepositoryEntryAction.CREATE -> showCreateDialog = true
        }
        if (initialAction != RepositoryEntryAction.NONE) {
            onInitialActionConsumed()
        }
    }

    RepositoriesContent(
        state = state,
        contentPadding = contentPadding,
        onRefresh = viewModel::refreshRepositories,
        onCreate = {
            showCreateDialog = true
        },
        onCloneUrl = {
            showCloneDialog = true
        },
        onOpenFolder = {
            folderPicker.launch(null)
        },
        onNewTemplate = {
            showTemplateDialog = true
        },
        onOpenRepository = onOpenRepository,
        onBrowseWorkspace = onBrowseWorkspace,
        onOpenGitWorkspace = onOpenGitWorkspace,
        onCloneRepository = viewModel::cloneRepository,
        onCancelClone = viewModel::cancelDurableClone,
        onCancelWorkspaceOperation = viewModel::cancelDurableWorkspaceOperation,
        onInitializeGit = viewModel::initializeWorkspaceGit,
        onSync = viewModel::sync,
        onDelete = viewModel::delete,
    )

    if (showCreateDialog) {
        CreateRepositoryDialog(
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showCreateDialog = false
            },
            onCreate = { request ->
                showCreateDialog = false
                viewModel.createRepository(request)
            },
        )
    }

    if (showTemplateDialog) {
        ProjectTemplateDialog(
            templates = state.templates,
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showTemplateDialog = false
            },
            onCreate = { templateId, projectName ->
                showTemplateDialog = false
                viewModel.createFromTemplate(
                    templateId = templateId,
                    projectName = projectName,
                )
            },
        )
    }

    if (showCloneDialog) {
        CloneRepositoryDialog(
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showCloneDialog = false
            },
            onClone = { url ->
                showCloneDialog = false
                viewModel.cloneUrl(url)
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
            title = {
                Text(stringResource(R.string.repo_operation))
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

    state.recentProjectName?.let { projectName ->
        ImportRiskDialog(
            projectName = projectName,
            risks = state.recentRisks,
            onDismiss = viewModel::dismissRisks,
        )
    }
}

@Composable
internal fun RepositoriesContent(
    state: RepositoriesUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onCloneUrl: () -> Unit,
    onOpenFolder: () -> Unit,
    onNewTemplate: () -> Unit,
    onOpenRepository: (String, String) -> Unit,
    onBrowseWorkspace: (String) -> Unit,
    onOpenGitWorkspace: (String) -> Unit,
    onCloneRepository: (RepositorySummary) -> Unit,
    onCancelClone: () -> Unit,
    onCancelWorkspaceOperation: () -> Unit,
    onInitializeGit: (String) -> Unit,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.repo_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.repo_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Text(stringResource(R.string.repo_create))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !state.operationInProgress,
                    onClick = onCloneUrl,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.repo_clone_url))
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !state.operationInProgress,
                    onClick = onOpenFolder,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.repo_folder))
                }
            }
        }


        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.operationInProgress,
                onClick = onNewTemplate,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                )
                Text(stringResource(R.string.repo_new_template))
            }
        }

        if (state.operationInProgress) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = when {
                                    state.durableWorkspaceId != null -> {
                                        when (state.durableWorkspacePhase) {
                                            "import" -> stringResource(R.string.workspace_durable_import)
                                            "sync" -> stringResource(R.string.workspace_durable_sync)
                                            else -> stringResource(R.string.workspace_durable_queued)
                                        }
                                    }
                                    else -> {
                                        when (state.durableClonePhase) {
                                            "resolve" -> stringResource(R.string.clone_durable_resolving)
                                            "clone" -> stringResource(R.string.clone_durable_running)
                                            "queued" -> stringResource(R.string.clone_durable_queued)
                                            else -> stringResource(R.string.clone_durable_running)
                                        }
                                    }
                                },
                            )
                        }

                        if (state.durableWorkspaceId != null) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onCancelWorkspaceOperation,
                            ) {
                                Text(stringResource(R.string.workspace_durable_cancel))
                            }
                        } else if (state.durableCloneId != null) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onCancelClone,
                            ) {
                                Text(stringResource(R.string.clone_durable_cancel))
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.repo_github_section),
                actionLabel = if (state.refreshing) {
                    stringResource(R.string.repo_refreshing)
                } else {
                    stringResource(R.string.repo_refresh)
                },
                actionEnabled =
                    !state.refreshing && !state.operationInProgress,
                onAction = onRefresh,
            )
        }

        if (state.loading && state.remoteRepositories.isEmpty()) {
            item {
                LoadingCard(stringResource(R.string.repo_loading_github))
            }
        } else if (state.remoteRepositories.isEmpty()) {
            item {
                EmptyCard(
                    title = stringResource(R.string.repo_no_github),
                    body = stringResource(R.string.repo_no_github_body),
                )
            }
        } else {
            items(
                items = state.remoteRepositories,
                key = { repository ->
                    "remote-" + repository.id
                },
            ) { repository ->
                RemoteRepositoryCard(
                    repository = repository,
                    enabled = !state.operationInProgress,
                    onOpen = {
                        onOpenRepository(
                            repository.ownerLogin,
                            repository.name,
                        )
                    },
                    onClone = {
                        onCloneRepository(repository)
                    },
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        item {
            SectionHeader(
                title = stringResource(R.string.repo_device_section),
                actionLabel = null,
                actionEnabled = false,
                onAction = {},
            )
        }

        if (!state.loading && state.workspaces.isEmpty()) {
            item {
                EmptyCard(
                    title = stringResource(R.string.repo_no_local),
                    body = stringResource(R.string.repo_no_local_body),
                )
            }
        }

        items(
            items = state.workspaces,
            key = { workspace ->
                "workspace-" + workspace.id
            },
        ) { workspace ->
            WorkspaceCard(
                workspace = workspace,
                enabled = !state.operationInProgress,
                onBrowse = {
                    onBrowseWorkspace(workspace.id)
                },
                onOpenGit = {
                    onOpenGitWorkspace(workspace.id)
                },
                onInitializeGit = {
                    onInitializeGit(workspace.id)
                },
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
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        if (actionLabel != null) {
            TextButton(
                enabled = actionEnabled,
                onClick = onAction,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                )
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun RemoteRepositoryCard(
    repository: RepositorySummary,
    enabled: Boolean,
    onOpen: () -> Unit,
    onClone: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onOpen,
            ),
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
                        text = repository.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = repository.ownerLogin,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (repository.privateRepository) {
                        Icons.Outlined.Lock
                    } else {
                        Icons.Outlined.Public
                    },
                    contentDescription = if (
                        repository.privateRepository
                    ) {
                        stringResource(R.string.repo_private_setting)
                    } else {
                        stringResource(R.string.repo_public)
                    },
                )
            }

            repository.description?.let { description ->
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepositoryMetric(
                    icon = Icons.Outlined.StarBorder,
                    value = repository.stars.toString(),
                    description = stringResource(R.string.repo_stars),
                )
                RepositoryMetric(
                    icon = Icons.AutoMirrored.Outlined.CallSplit,
                    value = repository.forks.toString(),
                    description = stringResource(R.string.repo_forks),
                )
                Text(
                    text = repository.language ?: stringResource(R.string.repo_no_language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !repository.archived,
                onClick = onClone,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                )
                Text(stringResource(R.string.repo_clone_device))
            }
        }
    }
}

@Composable
private fun RepositoryMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WorkspaceCard(
    workspace: Workspace,
    enabled: Boolean,
    onBrowse: () -> Unit,
    onOpenGit: () -> Unit,
    onInitializeGit: () -> Unit,
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
                        text = workspaceStrategyLabel(
                            workspace.strategy,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    enabled = enabled,
                    onClick = onDelete,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.repo_remove_workspace),
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.repo_files_size,
                    workspace.fileCount,
                    humanBytes(workspace.totalBytes),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            workspace.currentBranch
                ?.takeIf { it.isNotBlank() }
                ?.let { branch ->
                    Text(
                        text = stringResource(R.string.repo_branch, branch),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                        text = stringResource(
                            R.string.repo_risk_summary,
                            workspace.secretWarningCount,
                            workspace.largeFileWarningCount,
                            workspace.syncConflictCount,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = onBrowse,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                )
                Text(stringResource(R.string.repo_browse_code))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = if (
                        workspace.currentBranch.isNullOrBlank()
                    ) {
                        onInitializeGit
                    } else {
                        onOpenGit
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Source,
                        contentDescription = null,
                    )
                    Text(
                        if (workspace.currentBranch.isNullOrBlank()) {
                            stringResource(R.string.repo_init_git)
                        } else {
                            stringResource(R.string.repo_git_workspace)
                        },
                    )
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = onSync,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                    )
                    Text(
                        if (
                            workspace.strategy ==
                                WorkspaceStrategy.REMOTE_CLONE ||
                            workspace.strategy ==
                                WorkspaceStrategy.GENERATED
                        ) {
                            stringResource(R.string.repo_rescan)
                        } else {
                            stringResource(R.string.repo_sync)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun ProjectTemplateDialog(
    templates: List<ProjectTemplateSummary>,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var projectName by rememberSaveable {
        mutableStateOf("")
    }
    var selectedId by rememberSaveable {
        mutableStateOf(templates.firstOrNull()?.id.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !operationInProgress &&
                    selectedId.isNotBlank() &&
                    projectName.isNotBlank(),
                onClick = {
                    onCreate(selectedId, projectName)
                },
            ) {
                Text(stringResource(R.string.repo_create_project))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !operationInProgress,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.repo_new_template))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = projectName,
                    singleLine = true,
                    onValueChange = {
                        projectName = it
                    },
                    label = {
                        Text(stringResource(R.string.repo_project_name))
                    },
                )

                templates.forEach { template ->
                    FilterChip(
                        selected = selectedId == template.id,
                        onClick = {
                            selectedId = template.id
                        },
                        label = {
                            Column {
                                Text(template.name)
                                Text(
                                    template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                    )
                }

                Text(
                    "Templates are created locally. Git is initialized only when you choose Init Git.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun CreateRepositoryDialog(
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CreateRepositoryRequest) -> Unit,
) {
    var name by rememberSaveable {
        mutableStateOf("")
    }
    var description by rememberSaveable {
        mutableStateOf("")
    }
    var privateRepository by rememberSaveable {
        mutableStateOf(true)
    }
    var initializeWithReadme by rememberSaveable {
        mutableStateOf(true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !operationInProgress && name.isNotBlank(),
                onClick = {
                    onCreate(
                        CreateRepositoryRequest(
                            name = name,
                            description = description,
                            privateRepository = privateRepository,
                            initializeWithReadme =
                                initializeWithReadme,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !operationInProgress,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.repo_create))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    singleLine = true,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text(stringResource(R.string.repo_name))
                    },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    label = {
                        Text(stringResource(R.string.repo_description))
                    },
                    minLines = 2,
                    maxLines = 4,
                )
                SettingSwitch(
                    label = stringResource(R.string.repo_private_setting),
                    checked = privateRepository,
                    onCheckedChange = {
                        privateRepository = it
                    },
                )
                SettingSwitch(
                    label = stringResource(R.string.repo_readme_setting),
                    checked = initializeWithReadme,
                    onCheckedChange = {
                        initializeWithReadme = it
                    },
                )
            }
        },
    )
}

@Composable
private fun CloneRepositoryDialog(
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onClone: (String) -> Unit,
) {
    var url by rememberSaveable {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !operationInProgress && url.isNotBlank(),
                onClick = {
                    onClone(url)
                },
            ) {
                Text(stringResource(R.string.repo_clone))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !operationInProgress,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.repo_clone_github))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = url,
                singleLine = true,
                onValueChange = {
                    url = it
                },
                label = {
                    Text(stringResource(R.string.repo_url_hint))
                },
            )
        },
    )
}

@Composable
private fun SettingSwitch(
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
            text = label,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ImportRiskDialog(
    projectName: String,
    risks: List<ProjectRisk>,
    onDismiss: () -> Unit,
) {
    val actionable = remember(risks) {
        risks.filter {
            it.severity != ProjectRiskSeverity.INFO
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
        title = {
            Text(
                if (actionable.isEmpty()) {
                    stringResource(R.string.repo_project_ready)
                } else {
                    stringResource(R.string.repo_review_before_push)
                },
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(projectName)

                if (actionable.isEmpty()) {
                    Text(stringResource(R.string.repo_no_risks))
                } else {
                    actionable.take(6).forEach { risk ->
                        Text(
                            text = risk.path + " — " + risk.message,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    if (actionable.size > 6) {
                        Text(
                            text = stringResource(
                                R.string.repo_more_warnings,
                                actionable.size - 6,
                            ),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun workspaceStrategyLabel(
    strategy: WorkspaceStrategy,
): String =
    when (strategy) {
        WorkspaceStrategy.DIRECT -> stringResource(R.string.repo_strategy_direct)
        WorkspaceStrategy.MANAGED -> stringResource(R.string.repo_strategy_managed)
        WorkspaceStrategy.REMOTE_CLONE -> stringResource(R.string.repo_strategy_clone)
        WorkspaceStrategy.GENERATED -> stringResource(R.string.repo_strategy_generated)
    }

private fun humanBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L ->
            String.format(
                Locale.US,
                "%.1f GiB",
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
