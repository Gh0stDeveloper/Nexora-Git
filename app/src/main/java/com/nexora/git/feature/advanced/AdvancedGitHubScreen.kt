package com.nexora.git.feature.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.advanced.AdvancedDiscussionCategory
import com.nexora.git.core.advanced.AdvancedDiscussionSummary
import com.nexora.git.core.advanced.AdvancedProjectSummary
import com.nexora.git.core.advanced.GitHubCodespaceSummary
import com.nexora.git.core.advanced.GitHubGistSummary
import com.nexora.git.core.advanced.GitHubPagesSite
import com.nexora.git.core.advanced.RepositorySecurityOverview
import com.nexora.git.core.advanced.SecurityAlertFeed
import com.nexora.git.core.advanced.SecurityAlertSummary

@Composable
fun AdvancedGitHubScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: AdvancedGitHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDiscussionDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showProjectDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showGistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingGistDelete by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var pendingCodespaceDelete by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    AdvancedGitHubContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCreateDiscussion = {
            showDiscussionDialog = true
        },
        onCreateProject = {
            showProjectDialog = true
        },
        onEnablePages = viewModel::enablePages,
        onBuildPages = viewModel::requestPagesBuild,
        onCreateGist = {
            showGistDialog = true
        },
        onDeleteGist = { gistId ->
            pendingGistDelete = gistId
        },
        onCreateCodespace = viewModel::createCodespace,
        onSetCodespaceRunning =
            viewModel::setCodespaceRunning,
        onDeleteCodespace = { codespaceName ->
            pendingCodespaceDelete = codespaceName
        },
    )

    if (showDiscussionDialog) {
        DiscussionDialog(
            categories =
                state.discussions.value?.categories.orEmpty(),
            enabled = !state.operationInProgress,
            onDismiss = {
                showDiscussionDialog = false
            },
            onCreate = { categoryId, title, body ->
                showDiscussionDialog = false
                viewModel.createDiscussion(
                    categoryId,
                    title,
                    body,
                )
            },
        )
    }

    if (showProjectDialog) {
        ProjectDialog(
            enabled = !state.operationInProgress,
            onDismiss = {
                showProjectDialog = false
            },
            onCreate = { title ->
                showProjectDialog = false
                viewModel.createProject(title)
            },
        )
    }

    if (showGistDialog) {
        GistDialog(
            enabled = !state.operationInProgress,
            onDismiss = {
                showGistDialog = false
            },
            onCreate = {
                    fileName,
                    gistContent,
                    description,
                    publicGist,
                ->
                showGistDialog = false
                viewModel.createGist(
                    fileName = fileName,
                    content = gistContent,
                    description = description,
                    publicGist = publicGist,
                )
            },
        )
    }

    pendingGistDelete?.let { gistId ->
        AlertDialog(
            onDismissRequest = {
                pendingGistDelete = null
            },
            confirmButton = {
                Button(
                    enabled = !state.operationInProgress,
                    onClick = {
                        pendingGistDelete = null
                        viewModel.deleteGist(gistId)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.operationInProgress,
                    onClick = {
                        pendingGistDelete = null
                    },
                ) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Delete Gist?")
            },
            text = {
                Text(
                    "This permanently deletes the selected Gist from GitHub.",
                )
            },
        )
    }

    pendingCodespaceDelete?.let { codespaceName ->
        AlertDialog(
            onDismissRequest = {
                pendingCodespaceDelete = null
            },
            confirmButton = {
                Button(
                    enabled = !state.operationInProgress,
                    onClick = {
                        pendingCodespaceDelete = null
                        viewModel.deleteCodespace(codespaceName)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.operationInProgress,
                    onClick = {
                        pendingCodespaceDelete = null
                    },
                ) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Delete Codespace?")
            },
            text = {
                Text(
                    "This permanently deletes the Codespace and its uncommitted data.",
                )
            },
        )
    }

    val message =
        state.errorMessage ?: state.successMessage
    if (message != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessages,
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissMessages,
                ) {
                    Text("OK")
                }
            },
            title = {
                Text(
                    if (state.errorMessage != null) {
                        "Advanced GitHub"
                    } else {
                        "Nexora Git"
                    },
                )
            },
            text = {
                Text(message)
            },
        )
    }
}

@Composable
internal fun AdvancedGitHubContent(
    state: AdvancedGitHubUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateDiscussion: () -> Unit,
    onCreateProject: () -> Unit,
    onEnablePages: () -> Unit,
    onBuildPages: () -> Unit,
    onCreateGist: () -> Unit,
    onDeleteGist: (String) -> Unit,
    onCreateCodespace: () -> Unit,
    onSetCodespaceRunning: (String, Boolean) -> Unit,
    onDeleteCodespace: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Advanced GitHub",
                        style =
                            MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text =
                            "Discussions, Projects, Pages, security, Gists and Codespaces",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled = !state.operationInProgress,
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector =
                            Icons.Outlined.Refresh,
                        contentDescription =
                            "Refresh advanced GitHub",
                    )
                }
            }
        }

        item {
            DiscussionsSection(
                section = state.discussions,
                operationInProgress =
                    state.operationInProgress,
                onCreate = onCreateDiscussion,
            )
        }

        item {
            ProjectsSection(
                section = state.projects,
                operationInProgress =
                    state.operationInProgress,
                onCreate = onCreateProject,
            )
        }

        item {
            PagesSection(
                section = state.pages,
                operationInProgress =
                    state.operationInProgress,
                onEnable = onEnablePages,
                onBuild = onBuildPages,
            )
        }

        item {
            SecuritySection(state.security)
        }

        item {
            GistsSection(
                section = state.gists,
                operationInProgress =
                    state.operationInProgress,
                onCreate = onCreateGist,
                onDelete = onDeleteGist,
            )
        }

        item {
            CodespacesSection(
                section = state.codespaces,
                operationInProgress =
                    state.operationInProgress,
                onCreate = onCreateCodespace,
                onSetRunning = onSetCodespaceRunning,
                onDelete = onDeleteCodespace,
            )
        }

        if (state.operationInProgress) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DiscussionsSection(
    section: AdvancedSection<com.nexora.git.core.advanced.AdvancedDiscussionHub>,
    operationInProgress: Boolean,
    onCreate: () -> Unit,
) {
    SectionCard(
        title = "Discussions",
        loading = section.loading,
        errorMessage = section.errorMessage,
    ) {
        val hub = section.value
        if (hub != null) {
            SectionHeaderAction(
                summary =
                    hub.discussions.size.toString() +
                        " recent discussions · " +
                        hub.categories.size +
                        " categories",
                actionLabel = "New",
                enabled =
                    !operationInProgress &&
                        hub.categories.isNotEmpty(),
                onClick = onCreate,
            )
            if (hub.categories.isEmpty()) {
                Text(
                    text =
                        "No discussion categories are available for this repository.",
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            hub.discussions.take(8).forEach { discussion ->
                DiscussionRow(discussion)
            }
            if (hub.discussions.isEmpty()) {
                EmptySection("No discussions found.")
            }
        }
    }
}

@Composable
private fun DiscussionRow(
    discussion: AdvancedDiscussionSummary,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement =
            Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text =
                "#" + discussion.number + " · " +
                    discussion.title,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                discussion.categoryName +
                    " · " +
                    discussion.comments +
                    " comments · " +
                    discussion.upvotes +
                    " upvotes" +
                    if (discussion.answered) {
                        " · Answered"
                    } else {
                        ""
                    },
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ProjectsSection(
    section: AdvancedSection<com.nexora.git.core.advanced.AdvancedProjectHub>,
    operationInProgress: Boolean,
    onCreate: () -> Unit,
) {
    SectionCard(
        title = "Projects",
        loading = section.loading,
        errorMessage = section.errorMessage,
    ) {
        val hub = section.value
        if (hub != null) {
            SectionHeaderAction(
                summary =
                    hub.projects.size.toString() +
                        " repository projects",
                actionLabel = "New",
                enabled = !operationInProgress,
                onClick = onCreate,
            )
            hub.projects.take(8).forEach { project ->
                ProjectRow(project)
            }
            if (hub.projects.isEmpty()) {
                EmptySection(
                    "No Projects V2 are linked to this repository.",
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: AdvancedProjectSummary,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement =
            Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text =
                "#" + project.number + " · " +
                    project.title,
            fontWeight = FontWeight.SemiBold,
        )
        project.shortDescription?.let {
            Text(
                text = it,
                style =
                    MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text =
                project.itemCount.toString() +
                    " items · " +
                    if (project.closed) {
                        "Closed"
                    } else {
                        "Open"
                    } +
                    " · " +
                    if (project.publicProject) {
                        "Public"
                    } else {
                        "Private"
                    },
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun PagesSection(
    section: AdvancedSection<GitHubPagesSite>,
    operationInProgress: Boolean,
    onEnable: () -> Unit,
    onBuild: () -> Unit,
) {
    SectionCard(
        title = "GitHub Pages",
        loading = section.loading,
        errorMessage = section.errorMessage,
    ) {
        section.value?.let { pages ->
            if (pages.enabled) {
                Text(
                    text =
                        "Status: " +
                            (pages.status ?: "configured"),
                    fontWeight = FontWeight.SemiBold,
                )
                pages.htmlUrl?.let {
                    Text(
                        text = it,
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
                Text(
                    text =
                        "Source: " +
                            (pages.sourceBranch
                                ?: "GitHub Actions") +
                            (pages.sourcePath ?: ""),
                    style =
                        MaterialTheme.typography.bodySmall,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !operationInProgress,
                    onClick = onBuild,
                ) {
                    Text("Request Pages build")
                }
            } else {
                Text(
                    text =
                        "GitHub Pages is not enabled for this repository.",
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !operationInProgress,
                    onClick = onEnable,
                ) {
                    Text("Enable from default branch")
                }
            }
        }
    }
}

@Composable
private fun SecuritySection(
    section: AdvancedSection<RepositorySecurityOverview>,
) {
    SectionCard(
        title = "Repository security",
        loading = section.loading,
        errorMessage = section.errorMessage,
    ) {
        section.value?.let { overview ->
            Text(
                text =
                    overview.totalVisibleAlerts.toString() +
                        " visible open alerts",
                fontWeight = FontWeight.SemiBold,
            )
            SecurityFeedRow(
                label = "Dependabot",
                feed = overview.dependabot,
            )
            SecurityFeedRow(
                label = "Code scanning",
                feed = overview.codeScanning,
            )
            SecurityFeedRow(
                label = "Secret scanning",
                feed = overview.secretScanning,
            )

            val alerts =
                overview.dependabot.alerts +
                    overview.codeScanning.alerts +
                    overview.secretScanning.alerts
            alerts.take(8).forEach {
                SecurityAlertRow(it)
            }
        }
    }
}

@Composable
private fun SecurityFeedRow(
    label: String,
    feed: SecurityAlertFeed,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(
            text =
                if (feed.available) {
                    feed.alerts.size.toString()
                } else {
                    "Unavailable"
                },
            color =
                if (feed.available) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            style = MaterialTheme.typography.labelLarge,
        )
    }
    if (!feed.available && feed.message != null) {
        Text(
            text = feed.message,
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SecurityAlertRow(
    alert: SecurityAlertSummary,
) {
    HorizontalDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement =
            Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = alert.title,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text =
                alert.kind.name
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar {
                        it.titlecase()
                    } +
                    (alert.severity?.let {
                        " · " + it
                    } ?: "") +
                    " · " + alert.state,
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GistsSection(
    section: AdvancedSection<List<GitHubGistSummary>>,
    operationInProgress: Boolean,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
) {
    SectionCard(
        title = "Gists",
        loading = section.loading,
        errorMessage = section.errorMessage,
    ) {
        section.value?.let { gists ->
            SectionHeaderAction(
                summary =
                    gists.size.toString() +
                        " gists for the active account",
                actionLabel = "New",
                enabled = !operationInProgress,
                onClick = onCreate,
            )
            gists.take(10).forEach { gist ->
                GistRow(
                    gist = gist,
                    enabled = !operationInProgress,
                    onDelete = {
                        onDelete(gist.id)
                    },
                )
            }
            if (gists.isEmpty()) {
                EmptySection("No gists found.")
            }
        }
    }
}

@Composable
private fun GistRow(
    gist: GitHubGistSummary,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text =
                    gist.description
                        ?.takeIf { it.isNotBlank() }
                        ?: gist.fileNames
                            .firstOrNull()
                        ?: "Untitled gist",
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    gist.fileNames.size.toString() +
                        " files · " +
                        if (gist.publicGist) {
                            "Public"
                        } else {
                            "Secret"
                        },
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )
        }
        IconButton(
            enabled = enabled,
            onClick = onDelete,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete gist",
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun CodespacesSection(
    section: AdvancedSection<List<GitHubCodespaceSummary>>,
    operationInProgress: Boolean,
    onCreate: () -> Unit,
    onSetRunning: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    SectionCard(
        title = "Codespaces",
        loading = section.loading,
        errorMessage = section.errorMessage,
    ) {
        section.value?.let { codespaces ->
            SectionHeaderAction(
                summary =
                    codespaces.size.toString() +
                        " codespaces for the active account",
                actionLabel = "New",
                enabled = !operationInProgress,
                onClick = onCreate,
            )
            codespaces.take(10).forEach { codespace ->
                CodespaceRow(
                    codespace = codespace,
                    enabled = !operationInProgress,
                    onSetRunning = onSetRunning,
                    onDelete = onDelete,
                )
            }
            if (codespaces.isEmpty()) {
                EmptySection("No codespaces found.")
            }
        }
    }
}

@Composable
private fun CodespaceRow(
    codespace: GitHubCodespaceSummary,
    enabled: Boolean,
    onSetRunning: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement =
            Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = codespace.displayName,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text =
                (codespace.repositoryFullName
                    ?: "Unknown repository") +
                    " · " + codespace.state,
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            val isRunning =
                codespace.state.equals(
                    "Available",
                    ignoreCase = true,
                )
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = {
                    onSetRunning(
                        codespace.name,
                        !isRunning,
                    )
                },
            ) {
                Text(
                    if (isRunning) {
                        "Stop"
                    } else {
                        "Start"
                    },
                )
            }
            TextButton(
                enabled = enabled,
                onClick = {
                    onDelete(codespace.name)
                },
            ) {
                Text("Delete")
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SectionCard(
    title: String,
    loading: Boolean,
    errorMessage: String?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
            )
            if (loading) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Loading…")
                }
            }
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style =
                        MaterialTheme.typography.bodyMedium,
                )
            }
            if (!loading) {
                content()
            }
        }
    }
}

@Composable
private fun SectionHeaderAction(
    summary: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
            )
            Text(actionLabel)
        }
    }
}

@Composable
private fun EmptySection(message: String) {
    Text(
        text = message,
        color =
            MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun DiscussionDialog(
    categories: List<AdvancedDiscussionCategory>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
) {
    var title by rememberSaveable {
        mutableStateOf("")
    }
    var body by rememberSaveable {
        mutableStateOf("")
    }
    var categoryIndex by rememberSaveable {
        mutableIntStateOf(0)
    }
    val category =
        categories.getOrNull(categoryIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled =
                    enabled &&
                        category != null &&
                        title.isNotBlank() &&
                        body.isNotBlank(),
                onClick = {
                    category?.let {
                        onCreate(
                            it.id,
                            title,
                            body,
                        )
                    }
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                enabled = enabled,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("New discussion")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(
                    max = 430.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                if (category != null) {
                    OutlinedButton(
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled =
                            enabled &&
                                categories.size > 1,
                        onClick = {
                            categoryIndex =
                                (categoryIndex + 1) %
                                    categories.size
                        },
                    ) {
                        Text(
                            "Category: " +
                                category.name,
                        )
                    }
                } else {
                    Text(
                        "This repository has no discussion categories.",
                        color =
                            MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text("Title")
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = body,
                    onValueChange = {
                        body = it
                    },
                    label = {
                        Text("Body")
                    },
                    minLines = 5,
                    maxLines = 10,
                )
            }
        },
    )
}

@Composable
private fun ProjectDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by rememberSaveable {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled =
                    enabled && title.isNotBlank(),
                onClick = {
                    onCreate(title)
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                enabled = enabled,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("New Project")
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = {
                    title = it
                },
                label = {
                    Text("Project title")
                },
                singleLine = true,
            )
        },
    )
}

@Composable
private fun GistDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        String,
        String,
        String,
        Boolean,
    ) -> Unit,
) {
    var fileName by rememberSaveable {
        mutableStateOf("snippet.txt")
    }
    var description by rememberSaveable {
        mutableStateOf("")
    }
    var content by rememberSaveable {
        mutableStateOf("")
    }
    var publicGist by rememberSaveable {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled =
                    enabled &&
                        fileName.isNotBlank(),
                onClick = {
                    onCreate(
                        fileName,
                        content,
                        description,
                        publicGist,
                    )
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                enabled = enabled,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text("New Gist")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(
                    max = 470.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = fileName,
                    onValueChange = {
                        fileName = it
                    },
                    label = {
                        Text("File name")
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    label = {
                        Text("Description")
                    },
                    maxLines = 2,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = content,
                    onValueChange = {
                        content = it
                    },
                    label = {
                        Text("Content")
                    },
                    minLines = 5,
                    maxLines = 10,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                ) {
                    Text("Public Gist")
                    Switch(
                        checked = publicGist,
                        onCheckedChange = {
                            publicGist = it
                        },
                    )
                }
            }
        },
    )
}
