package com.nexora.git.feature.releases

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.releases.GitHubRelease
import com.nexora.git.core.releases.ReleaseAsset
import com.nexora.git.core.releases.ReleaseLatestPolicy
import com.nexora.git.core.releases.UpdateReleaseRequest
import java.util.Locale

@Composable
fun ReleaseDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: ReleaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEdit by rememberSaveable {
        mutableStateOf(false)
    }
    var showDeleteRelease by rememberSaveable {
        mutableStateOf(false)
    }
    var editingAssetId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var deletingAssetId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val assetPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAsset(uri.toString())
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onBack()
        }
    }

    ReleaseDetailContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onEdit = {
            showEdit = true
        },
        onPublish = {
            viewModel.update(
                UpdateReleaseRequest(
                    draft = false,
                ),
            )
        },
        onConvertToDraft = {
            viewModel.update(
                UpdateReleaseRequest(
                    draft = true,
                    makeLatest =
                        ReleaseLatestPolicy.NOT_LATEST,
                ),
            )
        },
        onUploadAsset = {
            assetPicker.launch(arrayOf("*/*"))
        },
        onEditAsset = {
            editingAssetId = it.id
        },
        onDeleteAsset = {
            deletingAssetId = it.id
        },
        onDownloadAsset = viewModel::downloadAsset,
        onDeleteRelease = {
            showDeleteRelease = true
        },
    )

    val release = state.release
    if (showEdit && release != null) {
        EditReleaseDialog(
            release = release,
            busy = state.operationInProgress,
            onDismiss = {
                showEdit = false
            },
            onSave = {
                showEdit = false
                viewModel.update(it)
            },
        )
    }

    editingAssetId?.let { assetId ->
        val asset = release
            ?.assets
            ?.firstOrNull { it.id == assetId }

        if (asset != null) {
            EditAssetDialog(
                asset = asset,
                busy = state.operationInProgress,
                onDismiss = {
                    editingAssetId = null
                },
                onSave = { name, label ->
                    editingAssetId = null
                    viewModel.updateAsset(
                        assetId = asset.id,
                        name = name,
                        label = label,
                    )
                },
            )
        }
    }

    deletingAssetId?.let { assetId ->
        val asset = release
            ?.assets
            ?.firstOrNull { it.id == assetId }

        if (asset != null) {
            AlertDialog(
                onDismissRequest = {
                    deletingAssetId = null
                },
                confirmButton = {
                    TextButton(
                        enabled =
                            !state.operationInProgress,
                        onClick = {
                            deletingAssetId = null
                            viewModel.deleteAsset(asset.id)
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            deletingAssetId = null
                        },
                    ) {
                        Text("Cancel")
                    }
                },
                title = {
                    Text("Delete release asset?")
                },
                text = {
                    Text(
                        "This permanently deletes " +
                            asset.name +
                            " from the GitHub release.",
                    )
                },
            )
        }
    }

    if (showDeleteRelease && release != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteRelease = false
            },
            confirmButton = {
                TextButton(
                    enabled = !state.operationInProgress,
                    onClick = {
                        showDeleteRelease = false
                        viewModel.deleteRelease()
                    },
                ) {
                    Text("Delete release")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteRelease = false
                    },
                ) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Delete release?")
            },
            text = {
                Text(
                    "Deleting the release does not automatically delete its Git tag. Release assets attached to it will be removed with the release.",
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
            title = { Text("Release") },
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
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(message)
                    state.lastDownload?.let { download ->
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
            },
        )
    }
}

@Composable
internal fun ReleaseDetailContent(
    state: ReleaseDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onConvertToDraft: () -> Unit,
    onUploadAsset: () -> Unit,
    onEditAsset: (ReleaseAsset) -> Unit,
    onDeleteAsset: (ReleaseAsset) -> Unit,
    onDownloadAsset: (ReleaseAsset) -> Unit,
    onDeleteRelease: () -> Unit,
) {
    val release = state.release
    val mutable =
        state.canWrite &&
            release?.immutable != true &&
            !state.operationInProgress

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
                        text = "Release",
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
                        contentDescription = "Refresh release",
                    )
                }
            }
        }

        if (state.loading && release == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (release != null) {
            item {
                ReleaseHeaderCard(release)
            }

            if (release.immutable) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = "GitHub marks this release as immutable. Editing, deleting, and asset mutations are disabled.",
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.canWrite) {
                item {
                    ReleaseActionsCard(
                        release = release,
                        enabled = mutable,
                        onEdit = onEdit,
                        onPublish = onPublish,
                        onConvertToDraft =
                            onConvertToDraft,
                        onUploadAsset = onUploadAsset,
                        onDeleteRelease =
                            onDeleteRelease,
                    )
                }
            }

            item {
                ReleaseNotesCard(release)
            }

            item {
                Text(
                    text = "Assets (" +
                        release.assets.size + ")",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (release.assets.isEmpty()) {
                item {
                    ReleaseDetailEmptyCard(
                        "This release has no assets.",
                    )
                }
            } else {
                items(
                    items = release.assets,
                    key = { "asset-" + it.id },
                ) { asset ->
                    ReleaseAssetCard(
                        asset = asset,
                        canManage = mutable,
                        downloading =
                            state.operationInProgress,
                        onDownload = {
                            onDownloadAsset(asset)
                        },
                        onEdit = {
                            onEditAsset(asset)
                        },
                        onDelete = {
                            onDeleteAsset(asset)
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
                                text = "Last downloaded asset",
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
private fun ReleaseHeaderCard(
    release: GitHubRelease,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = release.name
                    ?.takeIf(String::isNotBlank)
                    ?: release.tagName,
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = {
                        Text(
                            when {
                                release.draft -> "Draft"
                                release.prerelease -> "Prerelease"
                                else -> "Published"
                            },
                        )
                    },
                )

                if (release.immutable) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = {
                            Text("Immutable")
                        },
                    )
                }
            }

            Text(
                text = "Tag: " + release.tagName,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = "Target: " +
                    release.targetCommitish,
                fontFamily = FontFamily.Monospace,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            release.authorLogin?.let {
                Text(
                    text = "Author: " + it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            release.publishedAt?.let {
                Text(
                    text = "Published: " + it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReleaseActionsCard(
    release: GitHubRelease,
    enabled: Boolean,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onConvertToDraft: () -> Unit,
    onUploadAsset: () -> Unit,
    onDeleteRelease: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Release actions",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    enabled = enabled,
                    onClick = onEdit,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                    )
                    Text("Edit")
                }

                if (release.draft) {
                    Button(
                        enabled = enabled,
                        onClick = onPublish,
                    ) {
                        Text("Publish")
                    }
                } else {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = onConvertToDraft,
                    ) {
                        Text("Convert to draft")
                    }
                }

                OutlinedButton(
                    enabled = enabled,
                    onClick = onUploadAsset,
                ) {
                    Icon(
                        imageVector =
                            Icons.Outlined.UploadFile,
                        contentDescription = null,
                    )
                    Text("Upload asset")
                }

                OutlinedButton(
                    enabled = enabled,
                    onClick = onDeleteRelease,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                    )
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesCard(
    release: GitHubRelease,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Release notes",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()
            Text(
                text = release.body
                    ?.takeIf(String::isNotBlank)
                    ?: "No release notes.",
                color =
                    if (release.body.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

@Composable
private fun ReleaseAssetCard(
    asset: ReleaseAsset,
    canManage: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    asset.label?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = asset.state,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = humanBytes(asset.sizeInBytes) +
                    " · " +
                    asset.downloadCount +
                    " downloads",
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            asset.digest?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = !downloading,
                    onClick = onDownload,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                    )
                    Text("Download")
                }

                if (canManage) {
                    OutlinedButton(
                        enabled = !downloading,
                        onClick = onEdit,
                    ) {
                        Text("Rename")
                    }

                    OutlinedButton(
                        enabled = !downloading,
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
private fun EditReleaseDialog(
    release: GitHubRelease,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateReleaseRequest) -> Unit,
) {
    var tagName by rememberSaveable(release.id) {
        mutableStateOf(release.tagName)
    }
    var target by rememberSaveable(release.id) {
        mutableStateOf(release.targetCommitish)
    }
    var name by rememberSaveable(release.id) {
        mutableStateOf(release.name.orEmpty())
    }
    var body by rememberSaveable(release.id) {
        mutableStateOf(release.body.orEmpty())
    }
    var draft by rememberSaveable(release.id) {
        mutableStateOf(release.draft)
    }
    var prerelease by rememberSaveable(release.id) {
        mutableStateOf(release.prerelease)
    }
    var latestPolicy by rememberSaveable(release.id) {
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
                    onSave(
                        UpdateReleaseRequest(
                            tagName = tagName,
                            targetCommitish = target,
                            name = name,
                            body = body,
                            draft = draft,
                            prerelease = prerelease,
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
        title = { Text("Edit release") },
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
                        label = { Text("Tag") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = target,
                        onValueChange = { target = it },
                        singleLine = true,
                        label = { Text("Target") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Name") },
                    )
                }
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = body,
                        onValueChange = { body = it },
                        minLines = 4,
                        maxLines = 9,
                        label = { Text("Release notes") },
                    )
                }
                item {
                    ReleaseDetailToggleRow(
                        label = "Draft",
                        checked = draft,
                        onCheckedChange = { draft = it },
                    )
                }
                item {
                    ReleaseDetailToggleRow(
                        label = "Prerelease",
                        checked = prerelease,
                        onCheckedChange = {
                            prerelease = it
                        },
                    )
                }

                if (!draft && !prerelease) {
                    item {
                        Text(
                            text = "Latest policy",
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
private fun EditAssetDialog(
    asset: ReleaseAsset,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by rememberSaveable(asset.id) {
        mutableStateOf(asset.name)
    }
    var label by rememberSaveable(asset.id) {
        mutableStateOf(asset.label.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled =
                    !busy && name.isNotBlank(),
                onClick = {
                    onSave(
                        name,
                        label.takeIf(String::isNotBlank),
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
        title = { Text("Edit release asset") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Asset name") },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Label (optional)") },
                )
            }
        },
    )
}

@Composable
private fun ReleaseDetailToggleRow(
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
private fun ReleaseDetailEmptyCard(
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

private fun humanBytes(
    bytes: Long,
): String =
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

        else ->
            bytes.toString() + " B"
    }
