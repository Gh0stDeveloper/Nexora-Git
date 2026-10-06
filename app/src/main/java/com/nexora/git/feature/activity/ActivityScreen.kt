package com.nexora.git.feature.activity

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
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.social.GitHubActivityEvent

@Composable
fun ActivityScreen(
    contentPadding: PaddingValues,
    activeLogin: String,
    onOpenRepository: (String, String) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(activeLogin) {
        viewModel.start(activeLogin)
    }

    ActivityContent(
        state = state,
        contentPadding = contentPadding,
        onRefresh = viewModel::refresh,
        onSetSection = viewModel::setSection,
        onOpenGitHubNotifications = {
            uriHandler.openUri(
                "https://github.com/notifications",
            )
        },
        onOpenRepository = onOpenRepository,
    )

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            },
            title = { Text("Activity") },
            text = { Text(message) },
        )
    }
}

@Composable
internal fun ActivityContent(
    state: ActivityUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onSetSection: (ActivitySection) -> Unit,
    onOpenGitHubNotifications: () -> Unit,
    onOpenRepository: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "@" + state.login,
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
                        contentDescription = "Refresh activity",
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState(),
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                ActivitySection.entries.forEach { section ->
                    FilterChip(
                        selected =
                            state.section == section,
                        onClick = {
                            onSetSection(section)
                        },
                        label = {
                            Text(
                                when (section) {
                                    ActivitySection.NOTIFICATIONS ->
                                        "Notifications"
                                    ActivitySection.ACTIVITY ->
                                        "Activity"
                                },
                            )
                        },
                    )
                }
            }
        }

        when (state.section) {
            ActivitySection.NOTIFICATIONS -> {
                item {
                    NotificationCapabilityCard(
                        message =
                            state.notificationLimitation,
                        onOpenGitHubNotifications =
                            onOpenGitHubNotifications,
                    )
                }
            }

            ActivitySection.ACTIVITY -> {
                if (state.loading) {
                    item {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.activity.isEmpty()) {
                    item {
                        ActivityEmptyCard(
                            "No recent GitHub activity is available.",
                        )
                    }
                } else {
                    items(
                        items = state.activity,
                        key = {
                            "event-" +
                                it.id +
                                "-" +
                                it.type
                        },
                    ) { event ->
                        ActivityEventCard(
                            event = event,
                            onOpenRepository = {
                                openRepository(
                                    fullName =
                                        event.repositoryName,
                                    callback =
                                        onOpenRepository,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCapabilityCard(
    message: String,
    onOpenGitHubNotifications: () -> Unit,
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
                text = "GitHub notification inbox",
                style =
                    MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Nexora Git continues to use the primary GitHub App session. A second OAuth credential is not silently requested or stored just to bypass this API restriction.",
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style =
                    MaterialTheme.typography.bodySmall,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick =
                    onOpenGitHubNotifications,
            ) {
                Icon(
                    imageVector =
                        Icons.Outlined.OpenInBrowser,
                    contentDescription = null,
                )
                Text("Open GitHub notifications")
            }
        }
    }
}

@Composable
private fun ActivityEventCard(
    event: GitHubActivityEvent,
    onOpenRepository: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onOpenRepository,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = eventSummary(event),
                    style =
                        MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        if (event.publicEvent) {
                            "Public"
                        } else {
                            "Private"
                        },
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = event.repositoryName,
                fontFamily = FontFamily.Monospace,
                style =
                    MaterialTheme.typography.bodySmall,
            )

            val metadata = listOfNotNull(
                event.action
                    ?.takeIf(String::isNotBlank),
                event.refType
                    ?.takeIf(String::isNotBlank),
                event.ref
                    ?.takeIf(String::isNotBlank),
                event.number
                    ?.let { "#" + it },
            )

            if (metadata.isNotEmpty()) {
                Text(
                    text =
                        metadata.joinToString(
                            " · ",
                        ),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall,
                )
            }

            event.title
                ?.takeIf(String::isNotBlank)
                ?.let {
                    Text(
                        text = it,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            event.createdAt?.let {
                Text(
                    text = it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActivityEmptyCard(
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

private fun eventSummary(
    event: GitHubActivityEvent,
): String = when (event.type) {
    "PushEvent" -> "Pushed commits"
    "PullRequestEvent" ->
        "Pull request activity"
    "IssuesEvent" -> "Issue activity"
    "IssueCommentEvent" -> "Issue comment"
    "CreateEvent" ->
        "Created " +
            (event.refType ?: "Git ref")
    "DeleteEvent" ->
        "Deleted " +
            (event.refType ?: "Git ref")
    "ReleaseEvent" -> "Release activity"
    "ForkEvent" -> "Forked repository"
    "WatchEvent" -> "Starred repository"
    else ->
        event.type
            .removeSuffix("Event")
            .ifBlank { "GitHub activity" }
}

private fun openRepository(
    fullName: String,
    callback: (String, String) -> Unit,
) {
    val slash = fullName.indexOf('/')
    if (
        slash <= 0 ||
        slash >= fullName.lastIndex
    ) {
        return
    }

    callback(
        fullName.substring(0, slash),
        fullName.substring(slash + 1),
    )
}
