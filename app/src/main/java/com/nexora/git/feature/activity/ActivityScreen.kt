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
import androidx.compose.material.icons.outlined.DoneAll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.social.GitHubActivityEvent
import com.nexora.git.core.social.GitHubNotificationThread
import com.nexora.git.core.social.NotificationScope

@Composable
fun ActivityScreen(
    contentPadding: PaddingValues,
    activeLogin: String,
    onOpenRepository: (String, String) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var managingThreadId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(activeLogin) {
        viewModel.start(activeLogin)
    }

    ActivityContent(
        state = state,
        contentPadding = contentPadding,
        onRefresh = viewModel::refresh,
        onSetSection = viewModel::setSection,
        onSetNotificationScope =
            viewModel::setNotificationScope,
        onMarkRead = viewModel::markRead,
        onMarkAllRead = viewModel::markAllRead,
        onManageSubscription = { thread ->
            managingThreadId = thread.id
            viewModel.loadSubscription(thread.id)
        },
        onOpenRepository = onOpenRepository,
    )

    managingThreadId?.let { threadId ->
        val thread = state.notifications
            .firstOrNull { it.id == threadId }

        if (thread != null) {
            NotificationSubscriptionDialog(
                thread = thread,
                loading =
                    state.subscriptionLoadingThreadId ==
                        threadId,
                subscribed =
                    state.subscriptions[threadId]
                        ?.subscribed == true,
                ignored =
                    state.subscriptions[threadId]
                        ?.ignored == true,
                busy = state.operationInProgress,
                onDismiss = {
                    managingThreadId = null
                },
                onSet = { subscribed, ignored ->
                    viewModel.setSubscription(
                        threadId = threadId,
                        subscribed = subscribed,
                        ignored = ignored,
                    )
                },
            )
        }
    }

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

    state.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccess,
            confirmButton = {
                TextButton(onClick = viewModel::dismissSuccess) {
                    Text("Done")
                }
            },
            title = { Text("Nexora Git") },
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
    onSetNotificationScope: (NotificationScope) -> Unit,
    onMarkRead: (GitHubNotificationThread) -> Unit,
    onMarkAllRead: () -> Unit,
    onManageSubscription:
        (GitHubNotificationThread) -> Unit,
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
                        text =
                            if (
                                state.section ==
                                    ActivitySection.NOTIFICATIONS
                            ) {
                                state.unreadCount.toString() +
                                    " unread"
                            } else {
                                "@" + state.login
                            },
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
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivitySection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
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

        if (state.section == ActivitySection.NOTIFICATIONS) {
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
                    NotificationScope.entries.forEach { scope ->
                        FilterChip(
                            selected =
                                state.notificationScope ==
                                    scope,
                            onClick = {
                                onSetNotificationScope(
                                    scope,
                                )
                            },
                            label = {
                                Text(
                                    when (scope) {
                                        NotificationScope.UNREAD ->
                                            "Unread"
                                        NotificationScope.ALL ->
                                            "All"
                                        NotificationScope.PARTICIPATING ->
                                            "Participating"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled =
                        !state.operationInProgress &&
                            state.unreadCount > 0,
                    onClick = onMarkAllRead,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DoneAll,
                        contentDescription = null,
                    )
                    Text("Mark all read")
                }
            }
        }

        if (state.loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            when (state.section) {
                ActivitySection.NOTIFICATIONS -> {
                    if (state.notifications.isEmpty()) {
                        item {
                            ActivityEmptyCard(
                                "No notifications match this filter.",
                            )
                        }
                    } else {
                        items(
                            items = state.notifications,
                            key = {
                                "notification-" + it.id
                            },
                        ) { thread ->
                            NotificationCard(
                                thread = thread,
                                busy =
                                    state.operationInProgress,
                                onMarkRead = {
                                    onMarkRead(thread)
                                },
                                onManageSubscription = {
                                    onManageSubscription(
                                        thread,
                                    )
                                },
                                onOpenRepository = {
                                    openRepository(
                                        fullName =
                                            thread.repositoryFullName,
                                        callback =
                                            onOpenRepository,
                                    )
                                },
                            )
                        }
                    }
                }

                ActivitySection.ACTIVITY -> {
                    if (state.activity.isEmpty()) {
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
}

@Composable
private fun NotificationCard(
    thread: GitHubNotificationThread,
    busy: Boolean,
    onMarkRead: () -> Unit,
    onManageSubscription: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenRepository),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = thread.subjectTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.titleMedium,
                )

                Text(
                    text =
                        if (thread.unread) {
                            "Unread"
                        } else {
                            "Read"
                        },
                    color =
                        if (thread.unread) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            Text(
                text = thread.subjectType +
                    " · " + thread.reason,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = thread.repositoryFullName,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState(),
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                if (thread.unread) {
                    Button(
                        enabled = !busy,
                        onClick = onMarkRead,
                    ) {
                        Text("Mark read")
                    }
                }

                OutlinedButton(
                    enabled = !busy,
                    onClick = onManageSubscription,
                ) {
                    Text("Subscription")
                }
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
            .clickable(onClick = onOpenRepository),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = event.repositoryName,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
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
                    text = metadata.joinToString(" · "),
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            event.title?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            event.createdAt?.let {
                Text(
                    text = it,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NotificationSubscriptionDialog(
    thread: GitHubNotificationThread,
    loading: Boolean,
    subscribed: Boolean,
    ignored: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSet: (Boolean, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text("Notification subscription")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = thread.subjectTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = when {
                            ignored -> "Current: ignored"
                            subscribed -> "Current: subscribed"
                            else -> "Current: default"
                        },
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        onClick = {
                            onSet(true, false)
                        },
                    ) {
                        Text("Subscribe")
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        onClick = {
                            onSet(false, true)
                        },
                    ) {
                        Text("Ignore")
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        onClick = {
                            onSet(false, false)
                        },
                    ) {
                        Text("Use default")
                    }
                }
            }
        },
    )
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
    "PullRequestEvent" -> "Pull request activity"
    "IssuesEvent" -> "Issue activity"
    "IssueCommentEvent" -> "Issue comment"
    "CreateEvent" -> "Created " +
        (event.refType ?: "Git ref")
    "DeleteEvent" -> "Deleted " +
        (event.refType ?: "Git ref")
    "ReleaseEvent" -> "Release activity"
    "ForkEvent" -> "Forked repository"
    "WatchEvent" -> "Starred repository"
    else -> event.type
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
