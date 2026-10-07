package com.nexora.git.feature.releases

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
import androidx.compose.material.icons.outlined.Delete
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
import com.nexora.git.core.releases.CreateReleaseRequest
import com.nexora.git.core.releases.GitHubRelease
import com.nexora.git.core.releases.GitTag
import com.nexora.git.core.releases.ReleaseLatestPolicy

@Composable
fun ReleasesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenRelease: (Long) -> Unit,
    viewModel: ReleasesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateRelease by rememberSaveable {
        mutableStateOf(false)
    }
    var showCreateTag by rememberSaveable {
        mutableStateOf(false)
    }
    var deletingTag by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    ReleasesContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSetFilter = viewModel::setFilter,
        onOpenRelease = onOpenRelease,
        onCreateRelease = {
            showCreateRelease = true
        },
        onCreateTag = {
            showCreateTag = true
        },
        onDeleteTag = {
            deletingTag = it
        },
    )

    if (showCreateRelease) {
        CreateReleaseDialog(
            defaultBranch = state.defaultBranch,
            busy = state.operationInProgress,
            onDismiss = {
                showCreateRelease = false
            },
            onCreate = {
                showCreateRelease = false
                viewModel.createRelease(it)
            },
        )
    }

    if (showCreateTag) {
        CreateTagDialog(
            busy = state.operationInProgress,
            onDismiss = {
                showCreateTag = false
            },
            onCreate = { name, sha ->
                showCreateTag = false
                viewModel.createTag(name, sha)
            },
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = {
                deletingTag = null
            },
            confirmButton = {
                TextButton(
                    enabled = !state.operationInProgress,
                    onClick = {
                        deletingTag = null
                        viewModel.deleteTag(tag)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletingTag = null
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            title = {
                Text(stringResource(R.string.releases_delete_tag))
            },
            text = {
                Text(
                    "Deleting a Git tag is permanent and does not delete its commit.",
                )
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
            title = { Text(stringResource(R.string.releases_title)) },
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
internal fun ReleasesContent(
    state: ReleasesUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetFilter: (ReleaseListFilter) -> Unit,
    onOpenRelease: (Long) -> Unit,
    onCreateRelease: () -> Unit,
    onCreateTag: () -> Unit,
    onDeleteTag: (String) -> Unit,
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
                        text = stringResource(R.string.releases_title),
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
                        contentDescription = stringResource(R.string.releases_refresh),
                    )
                }
            }
        }

        if (state.canWrite) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !state.operationInProgress,
                        onClick = onCreateRelease,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.releases_new_release))
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !state.operationInProgress,
                        onClick = onCreateTag,
                    ) {
                        Text(stringResource(R.string.releases_new_tag))
                    }
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
                ReleaseListFilter.entries.forEach { value ->
                    FilterChip(
                        selected = state.filter == value,
                        onClick = {
                            onSetFilter(value)
                        },
                        label = {
                            Text(
                                when (value) {
                                    ReleaseListFilter.ALL -> "All"
                                    ReleaseListFilter.PUBLISHED -> "Published"
                                    ReleaseListFilter.DRAFTS -> "Drafts"
                                    ReleaseListFilter.PRERELEASES -> "Prereleases"
                                },
                            )
                        },
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.releases_title),
                style = MaterialTheme.typography.titleMedium,
            )
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
        } else if (state.filteredReleases.isEmpty()) {
            item {
                ReleaseEmptyCard(
                    "No releases match the current filter.",
                )
            }
        } else {
            items(
                items = state.filteredReleases,
                key = { "release-" + it.id },
            ) { release ->
                ReleaseRow(
                    release = release,
                    onOpen = {
                        onOpenRelease(release.id)
                    },
                )
            }
        }

        item {
            Text(
                text = "Tags (" + state.tags.size + ")",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (!state.loading && state.tags.isEmpty()) {
            item {
                ReleaseEmptyCard(
                    "No Git tags are available.",
                )
            }
        } else {
            items(
                items = state.tags,
                key = { "tag-" + it.name },
            ) { tag ->
                TagRow(
                    tag = tag,
                    canWrite =
                        state.canWrite &&
                            !state.operationInProgress,
                    onDelete = {
                        onDeleteTag(tag.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReleaseRow(
    release: GitHubRelease,
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
                    text = release.name
                        ?.takeIf(String::isNotBlank)
                        ?: release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = release.tagName,
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = releaseStatusLabel(release),
                color = when {
                    release.draft ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                    release.prerelease ->
                        MaterialTheme.colorScheme.tertiary
                    else ->
                        MaterialTheme.colorScheme.primary
                },
            )

            Text(
                text = release.assets.size.toString() +
                    " assets · " +
                    release.assets.sumOf {
                        it.downloadCount
                    }.toString() +
                    " downloads",
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (release.immutable) {
                Text(
                    text = stringResource(R.string.releases_immutable),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: GitTag,
    canWrite: Boolean,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = tag.commitSha.take(12),
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (canWrite) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription =
                            "Delete tag " + tag.name,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateReleaseDialog(
    defaultBranch: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CreateReleaseRequest) -> Unit,
) {
    var tagName by rememberSaveable {
        mutableStateOf("")
    }
    var target by rememberSaveable(defaultBranch) {
        mutableStateOf(defaultBranch)
    }
    var name by rememberSaveable {
        mutableStateOf("")
    }
    var body by rememberSaveable {
        mutableStateOf("")
    }
    var draft by rememberSaveable {
        mutableStateOf(false)
    }
    var prerelease by rememberSaveable {
        mutableStateOf(false)
    }
    var generateNotes by rememberSaveable {
        mutableStateOf(false)
    }
    var latestPolicy by rememberSaveable {
        mutableStateOf(ReleaseLatestPolicy.AUTO)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                        tagName.isNotBlank() &&
                        target.isNotBlank() &&
                        name.isNotBlank(),
                onClick = {
                    onCreate(
                        CreateReleaseRequest(
                            tagName = tagName,
                            targetCommitish = target,
                            name = name,
                            body = body,
                            draft = draft,
                            prerelease = prerelease,
                            generateReleaseNotes =
                                generateNotes,
                            makeLatest =
                                if (
                                    draft || prerelease
                                ) {
                                    ReleaseLatestPolicy.NOT_LATEST
                                } else {
                                    latestPolicy
                                },
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
        title = { Text(stringResource(R.string.releases_new_release)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = tagName,
                        onValueChange = { tagName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.releases_tag_name)) },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = target,
                        onValueChange = { target = it },
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.releases_target))
                        },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.releases_name)) },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = body,
                        onValueChange = { body = it },
                        minLines = 4,
                        maxLines = 9,
                        label = { Text(stringResource(R.string.releases_notes)) },
                    )
                }
                item {
                    ReleaseToggleRow(
                        label = "Draft",
                        checked = draft,
                        onCheckedChange = {
                            draft = it
                        },
                    )
                }
                item {
                    ReleaseToggleRow(
                        label = "Prerelease",
                        checked = prerelease,
                        onCheckedChange = {
                            prerelease = it
                        },
                    )
                }
                item {
                    ReleaseToggleRow(
                        label = "Generate release notes",
                        checked = generateNotes,
                        onCheckedChange = {
                            generateNotes = it
                        },
                    )
                }

                if (!draft && !prerelease) {
                    item {
                        Text(
                            text = stringResource(R.string.releases_latest_policy),
                            style =
                                MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(
                        items =
                            ReleaseLatestPolicy.entries,
                        key = { it.name },
                    ) { policy ->
                        FilterChip(
                            selected =
                                latestPolicy == policy,
                            onClick = {
                                latestPolicy = policy
                            },
                            label = {
                                Text(
                                    when (policy) {
                                        ReleaseLatestPolicy.AUTO ->
                                            "Latest"
                                        ReleaseLatestPolicy.NOT_LATEST ->
                                            "Not latest"
                                        ReleaseLatestPolicy.LEGACY ->
                                            "Legacy"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CreateTagDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by rememberSaveable {
        mutableStateOf("")
    }
    var sha by rememberSaveable {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy &&
                        name.isNotBlank() &&
                        sha.length >= 7,
                onClick = {
                    onCreate(name, sha)
                },
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = { Text(stringResource(R.string.releases_lightweight_tag)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.releases_tag_name)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = sha,
                    onValueChange = {
                        sha = it.trim()
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.releases_target_sha)) },
                )
                Text(
                    text = stringResource(R.string.releases_tag_note),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun ReleaseToggleRow(
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

@Composable
private fun ReleaseEmptyCard(
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

private fun releaseStatusLabel(
    release: GitHubRelease,
): String = when {
    release.draft -> "DRAFT"
    release.prerelease -> "PRERELEASE"
    else -> "PUBLISHED"
}
