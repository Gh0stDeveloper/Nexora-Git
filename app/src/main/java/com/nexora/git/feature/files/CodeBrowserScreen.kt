package com.nexora.git.feature.files

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.files.BrowserEntry
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.files.BrowserFileKind
import com.nexora.git.core.search.ProjectSearchMatch
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CodeBrowserScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: CodeBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportSelected(uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collectLatest { intent ->
            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Share file",
                ),
            )
        }
    }

    fun navigateBack() {
        if (!viewModel.navigateUp()) {
            onBack()
        }
    }

    BackHandler(onBack = ::navigateBack)

    CodeBrowserContent(
        state = state,
        contentPadding = contentPadding,
        onBack = ::navigateBack,
        onRefresh = viewModel::refresh,
        onToggleSearch = viewModel::toggleProjectSearch,
        onSearchQueryChange = viewModel::setProjectSearchQuery,
        onSearchRegexChange = viewModel::setProjectSearchRegex,
        onSearchMatchCaseChange = viewModel::setProjectSearchMatchCase,
        onSearchWholeWordChange = viewModel::setProjectSearchWholeWord,
        onSearchIncludeGlobChange = viewModel::setProjectSearchIncludeGlob,
        onSearchExcludeGlobChange = viewModel::setProjectSearchExcludeGlob,
        onSearch = viewModel::searchProject,
        onOpenSearchResult = viewModel::openSearchResult,
        onOpenEntry = viewModel::open,
        onOpenDirectory = viewModel::openDirectory,
        onSelectTab = viewModel::selectTab,
        onShare = viewModel::shareSelected,
        onEdit = onEdit,
        onDownload = {
            state.selectedFile?.let { file ->
                exportLauncher.launch(file.name)
            }
        },
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
                Text("Code browser")
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
internal fun CodeBrowserContent(
    state: CodeBrowserUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchRegexChange: (Boolean) -> Unit,
    onSearchMatchCaseChange: (Boolean) -> Unit,
    onSearchWholeWordChange: (Boolean) -> Unit,
    onSearchIncludeGlobChange: (String) -> Unit,
    onSearchExcludeGlobChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSearchResult: (ProjectSearchMatch) -> Unit,
    onOpenEntry: (BrowserEntry) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onSelectTab: (CodeBrowserTab) -> Unit,
    onShare: () -> Unit,
    onEdit: (String) -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        BrowserHeader(
            workspaceName = state.workspaceName,
            fileName = state.selectedFile?.name,
            loading = state.loading,
            onBack = onBack,
            onRefresh = onRefresh,
            onToggleSearch = onToggleSearch,
        )

        if (state.loading &&
            state.entries.isEmpty() &&
            state.selectedFile == null
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        val file = state.selectedFile
        if (file == null) {
            if (state.searchVisible) {
                ProjectSearchPanel(
                    state = state,
                    onQueryChange = onSearchQueryChange,
                    onRegexChange = onSearchRegexChange,
                    onMatchCaseChange = onSearchMatchCaseChange,
                    onWholeWordChange = onSearchWholeWordChange,
                    onIncludeGlobChange = onSearchIncludeGlobChange,
                    onExcludeGlobChange = onSearchExcludeGlobChange,
                    onSearch = onSearch,
                    onOpenResult = onOpenSearchResult,
                )
            }
            DirectoryBrowser(
                currentDirectory = state.currentDirectory,
                entries = state.entries,
                onOpenEntry = onOpenEntry,
                onOpenDirectory = onOpenDirectory,
                modifier = Modifier.weight(1f),
            )
        } else {
            FileBrowser(
                state = state,
                file = file,
                onSelectTab = onSelectTab,
                onShare = onShare,
                onEdit = onEdit,
                onDownload = onDownload,
            )
        }
    }
}

@Composable
private fun BrowserHeader(
    workspaceName: String,
    fileName: String?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = fileName ?: workspaceName.ifBlank {
                    "Code"
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )

            if (fileName != null &&
                workspaceName.isNotBlank()
            ) {
                Text(
                    text = workspaceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Row {
            IconButton(
                onClick = onToggleSearch,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search project",
                )
            }

        IconButton(
            enabled = !loading,
            onClick = onRefresh,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh",
            )
        }
        }
    }

    HorizontalDivider()
}

@Composable
private fun ProjectSearchPanel(
    state: CodeBrowserUiState,
    onQueryChange: (String) -> Unit,
    onRegexChange: (Boolean) -> Unit,
    onMatchCaseChange: (Boolean) -> Unit,
    onWholeWordChange: (Boolean) -> Unit,
    onIncludeGlobChange: (String) -> Unit,
    onExcludeGlobChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenResult: (ProjectSearchMatch) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.projectSearchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                label = {
                    Text("Search entire project")
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.projectSearchRegex,
                    onClick = {
                        onRegexChange(!state.projectSearchRegex)
                    },
                    label = {
                        Text("Regex")
                    },
                )
                FilterChip(
                    selected = state.projectSearchMatchCase,
                    onClick = {
                        onMatchCaseChange(!state.projectSearchMatchCase)
                    },
                    label = {
                        Text("Match case")
                    },
                )
                FilterChip(
                    selected = state.projectSearchWholeWord,
                    onClick = {
                        onWholeWordChange(!state.projectSearchWholeWord)
                    },
                    label = {
                        Text("Whole word")
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.projectSearchIncludeGlob,
                    onValueChange = onIncludeGlobChange,
                    singleLine = true,
                    label = {
                        Text("Include glob")
                    },
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.projectSearchExcludeGlob,
                    onValueChange = onExcludeGlobChange,
                    singleLine = true,
                    label = {
                        Text("Exclude glob")
                    },
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.projectSearchQuery.isNotBlank() &&
                    !state.projectSearchLoading,
                onClick = onSearch,
            ) {
                if (state.projectSearchLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                }
                Text("Search project")
            }

            if (
                state.projectSearchMatches.isNotEmpty() ||
                state.projectSearchFilesScanned > 0
            ) {
                Text(
                    state.projectSearchMatches.size.toString() +
                        " matches · " +
                        state.projectSearchFilesScanned.toString() +
                        " files scanned" +
                        if (state.projectSearchTruncated) {
                            " · capped"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.projectSearchMatches.take(12).forEach { match ->
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onOpenResult(match)
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            match.path +
                                ":" + match.line +
                                ":" + match.column,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            match.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }

            if (state.projectSearchMatches.size > 12) {
                Text(
                    "+" +
                        (state.projectSearchMatches.size - 12) +
                        " more results",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DirectoryBrowser(
    currentDirectory: String,
    entries: List<BrowserEntry>,
    onOpenEntry: (BrowserEntry) -> Unit,
    onOpenDirectory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 14.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Breadcrumbs(
                currentDirectory = currentDirectory,
                onOpenDirectory = onOpenDirectory,
            )
        }

        if (entries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Empty directory",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "There are no browsable files here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(
            items = entries,
            key = BrowserEntry::relativePath,
        ) { entry ->
            FileEntryCard(
                entry = entry,
                onClick = {
                    onOpenEntry(entry)
                },
            )
        }
    }
}

@Composable
private fun Breadcrumbs(
    currentDirectory: String,
    onOpenDirectory: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = {
                onOpenDirectory("")
            },
        ) {
            Text("root")
        }

        var accumulated = ""
        currentDirectory
            .split('/')
            .filter(String::isNotBlank)
            .forEach { segment ->
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                accumulated = if (accumulated.isBlank()) {
                    segment
                } else {
                    accumulated + "/" + segment
                }
                val target = accumulated

                TextButton(
                    onClick = {
                        onOpenDirectory(target)
                    },
                ) {
                    Text(segment)
                }
            }
    }
}

@Composable
private fun FileEntryCard(
    entry: BrowserEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 13.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = entryIcon(entry),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                )

                Text(
                    text = if (entry.directory) {
                        "Folder"
                    } else {
                        buildString {
                            append(
                                entry.language
                                    ?: entry.kind?.name
                                        ?.lowercase()
                                        ?.replaceFirstChar {
                                            it.titlecase()
                                        }
                                    ?: "File",
                            )
                            append(" · ")
                            append(humanBytes(entry.sizeBytes))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun entryIcon(
    entry: BrowserEntry,
) = when {
    entry.directory -> Icons.Outlined.Folder
    entry.kind == BrowserFileKind.IMAGE ->
        Icons.Outlined.Image
    entry.kind == BrowserFileKind.MARKDOWN ->
        Icons.Outlined.Description
    entry.kind == BrowserFileKind.TEXT ->
        Icons.Outlined.Code
    else -> Icons.Outlined.InsertDriveFile
}

@Composable
private fun FileBrowser(
    state: CodeBrowserUiState,
    file: BrowserFile,
    onSelectTab: (CodeBrowserTab) -> Unit,
    onShare: () -> Unit,
    onEdit: (String) -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        FileMetadataBar(
            file = file,
            onShare = onShare,
            onEdit = {
                onEdit(file.relativePath)
            },
            onDownload = onDownload,
        )

        val tabs = buildList {
            add(CodeBrowserTab.CODE)

            if (file.kind == BrowserFileKind.MARKDOWN ||
                file.kind == BrowserFileKind.IMAGE
            ) {
                add(CodeBrowserTab.PREVIEW)
            }

            if (state.gitAvailable &&
                (
                    file.kind == BrowserFileKind.TEXT ||
                        file.kind == BrowserFileKind.MARKDOWN
                    )
            ) {
                add(CodeBrowserTab.HISTORY)
                add(CodeBrowserTab.BLAME)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = 14.dp,
                    vertical = 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { tab ->
                FilterChip(
                    selected = state.selectedTab == tab,
                    onClick = {
                        onSelectTab(tab)
                    },
                    label = {
                        Text(tab.label())
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (tab) {
                                CodeBrowserTab.CODE ->
                                    Icons.Outlined.Code
                                CodeBrowserTab.PREVIEW ->
                                    Icons.Outlined.Description
                                CodeBrowserTab.HISTORY ->
                                    Icons.Outlined.History
                                CodeBrowserTab.BLAME ->
                                    Icons.Outlined.Source
                            },
                            contentDescription = null,
                        )
                    },
                )
            }
        }

        HorizontalDivider()

        when (state.selectedTab) {
            CodeBrowserTab.CODE ->
                CodeContent(file)

            CodeBrowserTab.PREVIEW ->
                PreviewContent(file)

            CodeBrowserTab.HISTORY ->
                HistoryContent(
                    loading = state.historyLoading,
                    history = state.history,
                    error = state.historyError,
                )

            CodeBrowserTab.BLAME ->
                BlameContent(
                    file = file,
                    loading = state.blameLoading,
                    blame = state.blame,
                    error = state.blameError,
                )
        }
    }
}

@Composable
private fun FileMetadataBar(
    file: BrowserFile,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = file.relativePath,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(file.language ?: file.kind.name.lowercase())
                    append(" · ")
                    append(humanBytes(file.sizeBytes))
                    if (file.lineCount > 0) {
                        append(" · ")
                        append(file.lineCount)
                        append(" lines")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (
            (file.kind == BrowserFileKind.TEXT ||
                file.kind == BrowserFileKind.MARKDOWN) &&
            !file.truncated
        ) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit file",
                )
            }
        }

        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share file",
            )
        }

        IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Save copy",
            )
        }
    }
}

private fun CodeBrowserTab.label(): String =
    when (this) {
        CodeBrowserTab.CODE -> "Code"
        CodeBrowserTab.PREVIEW -> "Preview"
        CodeBrowserTab.HISTORY -> "History"
        CodeBrowserTab.BLAME -> "Blame"
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
