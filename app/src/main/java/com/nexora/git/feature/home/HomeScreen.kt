package com.nexora.git.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.R
import com.nexora.git.core.repository.RepositorySummary

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    activeLogin: String,
    onOpenRepositories: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenRepository: (String, String) -> Unit,
    onClone: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        contentPadding = contentPadding,
        activeLogin = activeLogin,
        onOpenRepositories = onOpenRepositories,
        onOpenExplore = onOpenExplore,
        onOpenActivity = onOpenActivity,
        onOpenWorkspace = onOpenWorkspace,
        onOpenRepository = onOpenRepository,
        onClone = onClone,
        onImport = onImport,
        onCreate = onCreate,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    activeLogin: String,
    onOpenRepositories: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenRepository: (String, String) -> Unit,
    onClone: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "@" + activeLogin,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!state.loading) {
                    Text(
                        text = stringResource(
                            R.string.home_workspace_summary,
                            state.workspaceCount,
                            state.repositoryCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_quick_actions),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickAction(
                    Modifier.weight(1f),
                    stringResource(R.string.home_quick_clone),
                    Icons.Outlined.CloudDownload,
                    onClone,
                )
                QuickAction(
                    Modifier.weight(1f),
                    stringResource(R.string.home_quick_import),
                    Icons.Outlined.FolderOpen,
                    onImport,
                )
                QuickAction(
                    Modifier.weight(1f),
                    stringResource(R.string.home_quick_create),
                    Icons.Outlined.Add,
                    onCreate,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_recent_workspaces),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.loading) {
            item { CircularProgressIndicator() }
        } else if (state.recentWorkspaces.isEmpty()) {
            item {
                EmptyCard(
                    stringResource(R.string.home_no_workspaces),
                    stringResource(R.string.home_repositories_button),
                    onOpenRepositories,
                )
            }
        } else {
            items(
                items = state.recentWorkspaces,
                key = { "workspace-" + it.id },
            ) { workspace ->
                WorkspaceCard(workspace) {
                    onOpenWorkspace(workspace.id)
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_recent_repositories),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (!state.loading && state.recentRepositories.isEmpty()) {
            item {
                EmptyCard(
                    stringResource(R.string.home_no_repositories),
                    stringResource(R.string.home_explore_button),
                    onOpenExplore,
                )
            }
        } else {
            items(
                items = state.recentRepositories,
                key = { "repository-" + it.id },
            ) { repository ->
                RepositoryCard(repository) {
                    onOpenRepository(
                        repository.ownerLogin,
                        repository.name,
                    )
                }
            }
        }

        item {
            ActionCard(
                stringResource(R.string.home_activity_title),
                stringResource(R.string.home_activity_description),
                stringResource(R.string.home_activity_button),
                Icons.Outlined.History,
                onOpenActivity,
            )
        }
        item {
            ActionCard(
                stringResource(R.string.home_explore_title),
                stringResource(R.string.home_explore_description),
                stringResource(R.string.home_explore_button),
                Icons.Outlined.Explore,
                onOpenExplore,
            )
        }
        item {
            Text(
                text = stringResource(R.string.home_auth_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null)
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WorkspaceCard(
    workspace: HomeWorkspaceSummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(workspace.name, style = MaterialTheme.typography.titleMedium)
            workspace.branch?.let {
                Text(
                    stringResource(R.string.home_branch, it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.home_changes,
                        workspace.changedPaths,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (workspace.conflictedPaths > 0) {
                    Text(
                        stringResource(
                            R.string.home_conflicts,
                            workspace.conflictedPaths,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (workspace.ahead != null && workspace.behind != null) {
                    Text(
                        stringResource(
                            R.string.home_ahead_behind,
                            workspace.ahead,
                            workspace.behind,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepositoryCard(
    repository: RepositorySummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(repository.fullName, style = MaterialTheme.typography.titleMedium)
            repository.description
                ?.takeIf(String::isNotBlank)
                ?.let {
                    Text(
                        text = it,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            Text(
                text = listOfNotNull(
                    repository.language,
                    repository.visibility,
                    repository.defaultBranch,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCard(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
            ) {
                Text(action)
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    buttonText: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
            ) {
                Text(buttonText)
            }
        }
    }
}
