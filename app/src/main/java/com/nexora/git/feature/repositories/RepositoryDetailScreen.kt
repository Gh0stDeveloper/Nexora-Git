package com.nexora.git.feature.repositories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.repository.RepositoryDetails
import com.nexora.git.core.repository.RepositorySubscriptionState
import com.nexora.git.core.repository.RepositoryViewerState
import com.nexora.git.core.repository.UpdateRepositoryRequest
import java.util.Locale

@Composable
fun RepositoryDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenIssues: (String, String) -> Unit,
    onOpenPullRequests: (String, String) -> Unit,
    onOpenActions: (String, String) -> Unit,
    onOpenReleases: (String, String) -> Unit,
    onOpenAdvancedGitHub: (String, String) -> Unit,
    viewModel: RepositoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    RepositoryDetailContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggleStar = viewModel::toggleStar,
        onToggleWatch = viewModel::toggleWatch,
        onFork = viewModel::fork,
        onClone = viewModel::clone,
        onOpenSettings = {
            showSettings = true
        },
        onOpenIssues = onOpenIssues,
        onOpenPullRequests = onOpenPullRequests,
        onOpenActions = onOpenActions,
        onOpenReleases = onOpenReleases,
        onOpenAdvancedGitHub = onOpenAdvancedGitHub,
    )

    val details = state.details
    if (showSettings && details != null) {
        RepositorySettingsDialog(
            details = details,
            operationInProgress = state.operationInProgress,
            onDismiss = {
                showSettings = false
            },
            onSave = { request ->
                showSettings = false
                viewModel.updateSettings(request)
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
                Text("Repository")
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
internal fun RepositoryDetailContent(
    state: RepositoryDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleStar: () -> Unit,
    onToggleWatch: () -> Unit,
    onFork: () -> Unit,
    onClone: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenIssues: (String, String) -> Unit,
    onOpenPullRequests: (String, String) -> Unit,
    onOpenActions: (String, String) -> Unit,
    onOpenReleases: (String, String) -> Unit,
    onOpenAdvancedGitHub: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }

                Text(
                    text = "Repository",
                    style = MaterialTheme.typography.titleLarge,
                )

                IconButton(
                    enabled =
                        !state.loading &&
                            !state.operationInProgress,
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh repository",
                    )
                }
            }
        }

        if (state.loading && state.details == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Loading repository…")
                    }
                }
            }
        }

        state.details?.let { details ->
            item {
                RepositoryIdentityCard(details)
            }

            if (details.offlineSnapshot) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Offline snapshot. Metadata is cached; social actions and repository settings require GitHub connectivity.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                RepositoryActionCard(
                    details = details,
                    viewerState = state.viewerState,
                    viewerCapabilitiesUnavailable =
                        state.viewerCapabilitiesUnavailable,
                    enabled =
                        !state.operationInProgress &&
                            !details.offlineSnapshot,
                    onToggleStar = onToggleStar,
                    onToggleWatch = onToggleWatch,
                    onFork = onFork,
                    onClone = onClone,
                )
            }

            item {
                RepositoryStatsCard(details)
            }

            if (details.hasIssues) {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            !state.operationInProgress &&
                                !details.offlineSnapshot,
                        onClick = {
                            onOpenIssues(
                                details.summary.ownerLogin,
                                details.summary.name,
                            )
                        },
                    ) {
                        Text("Issues")
                    }
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !state.operationInProgress &&
                            !details.offlineSnapshot,
                    onClick = {
                        onOpenPullRequests(
                            details.summary.ownerLogin,
                            details.summary.name,
                        )
                    },
                ) {
                    Text("Pull requests")
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !state.operationInProgress &&
                            !details.offlineSnapshot,
                    onClick = {
                        onOpenActions(
                            details.summary.ownerLogin,
                            details.summary.name,
                        )
                    },
                ) {
                    Text("GitHub Actions")
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !state.operationInProgress &&
                            !details.offlineSnapshot,
                    onClick = {
                        onOpenReleases(
                            details.summary.ownerLogin,
                            details.summary.name,
                        )
                    },
                ) {
                    Text("Releases")
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !state.operationInProgress &&
                            !details.offlineSnapshot,
                    onClick = {
                        onOpenAdvancedGitHub(
                            details.summary.ownerLogin,
                            details.summary.name,
                        )
                    },
                ) {
                    Text("Advanced GitHub")
                }
            }

            if (details.summary.permissions.canManageSettings) {
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            !state.operationInProgress &&
                                !details.offlineSnapshot,
                        onClick = onOpenSettings,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                        )
                        Text("Repository settings")
                    }
                }
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
    }
}

@Composable
private fun RepositoryIdentityCard(
    details: RepositoryDetails,
) {
    val repository = details.summary

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (repository.privateRepository) {
                        Icons.Outlined.Lock
                    } else {
                        Icons.Outlined.Public
                    },
                    contentDescription = if (
                        repository.privateRepository
                    ) {
                        "Private repository"
                    } else {
                        "Public repository"
                    },
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = repository.name,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = repository.ownerLogin,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            repository.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RepositoryBadge(repository.visibility)
                if (repository.fork) {
                    RepositoryBadge("fork")
                }
                if (repository.archived) {
                    RepositoryBadge("archived")
                }
            }

            Text(
                text = "Default branch: " +
                    repository.defaultBranch,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Language: " +
                    (repository.language ?: "Not detected"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RepositoryBadge(text: String) {
    Card {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RepositoryActionCard(
    details: RepositoryDetails,
    viewerState: RepositoryViewerState?,
    viewerCapabilitiesUnavailable: Boolean,
    enabled: Boolean,
    onToggleStar: () -> Unit,
    onToggleWatch: () -> Unit,
    onFork: () -> Unit,
    onClone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = enabled && viewerState != null,
                    onClick = onToggleStar,
                ) {
                    Icon(
                        imageVector = if (
                            viewerState?.starred == true
                        ) {
                            Icons.Filled.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = null,
                    )
                    Text(
                        if (viewerState?.starred == true) {
                            "Unstar"
                        } else {
                            "Star"
                        },
                    )
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled =
                        enabled &&
                            viewerState?.canSubscribe == true,
                    onClick = onToggleWatch,
                ) {
                    val watching =
                        viewerState?.subscription ==
                            RepositorySubscriptionState.SUBSCRIBED

                    Icon(
                        imageVector = if (watching) {
                            Icons.Outlined.Notifications
                        } else {
                            Icons.Outlined.NotificationsNone
                        },
                        contentDescription = null,
                    )
                    Text(
                        if (watching) {
                            "Unwatch"
                        } else {
                            "Watch"
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = onFork,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ForkRight,
                        contentDescription = null,
                    )
                    Text("Fork")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = onClone,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                    )
                    Text("Clone")
                }
            }

            if (viewerCapabilitiesUnavailable) {
                Text(
                    text = "Star/watch state is unavailable for the current GitHub App permissions or connection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RepositoryStatsCard(
    details: RepositoryDetails,
) {
    val repository = details.summary

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Repository details",
                style = MaterialTheme.typography.titleMedium,
            )

            RepositoryDetailRow(
                label = "Stars",
                value = repository.stars.toString(),
            )
            RepositoryDetailRow(
                label = "Forks",
                value = repository.forks.toString(),
            )
            RepositoryDetailRow(
                label = "Open issues",
                value = repository.openIssues.toString(),
            )
            RepositoryDetailRow(
                label = "Watchers",
                value = details.subscribers.toString(),
            )
            RepositoryDetailRow(
                label = "Size",
                value = humanRepositorySize(repository.sizeKb),
            )
            RepositoryDetailRow(
                label = "Permission",
                value = permissionLabel(details),
            )

            HorizontalDivider()

            RepositoryDetailRow(
                label = "Issues",
                value = enabledLabel(details.hasIssues),
            )
            RepositoryDetailRow(
                label = "Wiki",
                value = enabledLabel(details.hasWiki),
            )
            RepositoryDetailRow(
                label = "Projects",
                value = enabledLabel(details.hasProjects),
            )
            RepositoryDetailRow(
                label = "Pages",
                value = enabledLabel(details.hasPages),
            )
        }
    }
}

@Composable
private fun RepositoryDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RepositorySettingsDialog(
    details: RepositoryDetails,
    operationInProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateRepositoryRequest) -> Unit,
) {
    var description by rememberSaveable(details.summary.id) {
        mutableStateOf(details.summary.description.orEmpty())
    }
    var homepage by rememberSaveable(details.summary.id) {
        mutableStateOf(details.homepage.orEmpty())
    }
    var hasIssues by rememberSaveable(details.summary.id) {
        mutableStateOf(details.hasIssues)
    }
    var hasWiki by rememberSaveable(details.summary.id) {
        mutableStateOf(details.hasWiki)
    }
    var deleteBranchOnMerge by rememberSaveable(
        details.summary.id,
    ) {
        mutableStateOf(details.deleteBranchOnMerge)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = !operationInProgress,
                onClick = {
                    onSave(
                        UpdateRepositoryRequest(
                            description = description,
                            homepage = homepage,
                            hasIssues = hasIssues,
                            hasWiki = hasWiki,
                            deleteBranchOnMerge =
                                deleteBranchOnMerge,
                        ),
                    )
                },
            ) {
                Text("Save")
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
            Text("Repository settings")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    label = {
                        Text("Description")
                    },
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = homepage,
                    singleLine = true,
                    onValueChange = {
                        homepage = it
                    },
                    label = {
                        Text("Homepage")
                    },
                )
                SettingsSwitchRow(
                    label = "Issues",
                    checked = hasIssues,
                    onCheckedChange = {
                        hasIssues = it
                    },
                )
                SettingsSwitchRow(
                    label = "Wiki",
                    checked = hasWiki,
                    onCheckedChange = {
                        hasWiki = it
                    },
                )
                SettingsSwitchRow(
                    label = "Delete branch after merge",
                    checked = deleteBranchOnMerge,
                    onCheckedChange = {
                        deleteBranchOnMerge = it
                    },
                )
            }
        },
    )
}

@Composable
private fun SettingsSwitchRow(
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

private fun permissionLabel(
    details: RepositoryDetails,
): String {
    val permissions = details.summary.permissions
    return when {
        permissions.admin -> "Admin"
        permissions.maintain -> "Maintain"
        permissions.push -> "Write"
        permissions.triage -> "Triage"
        permissions.pull -> "Read"
        else -> "None"
    }
}

private fun enabledLabel(enabled: Boolean): String =
    if (enabled) "Enabled" else "Disabled"

private fun humanRepositorySize(sizeKb: Long): String =
    when {
        sizeKb >= 1024L * 1024L ->
            String.format(
                Locale.US,
                "%.1f GiB",
                sizeKb / (1024.0 * 1024.0),
            )

        sizeKb >= 1024L ->
            String.format(
                Locale.US,
                "%.1f MiB",
                sizeKb / 1024.0,
            )

        else -> sizeKb.toString() + " KiB"
    }
