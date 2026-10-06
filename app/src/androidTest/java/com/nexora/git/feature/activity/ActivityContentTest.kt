package com.nexora.git.feature.activity

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.social.GitHubActivityEvent
import com.nexora.git.core.social.GitHubNotificationThread
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ActivityContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsNotificationControls() {
        val notification =
            GitHubNotificationThread(
                id = "100",
                unread = true,
                reason = "mention",
                updatedAt = "now",
                lastReadAt = null,
                subjectTitle = "Review requested",
                subjectType = "PullRequest",
                subjectUrl = null,
                latestCommentUrl = null,
                repositoryFullName =
                    "ghost/repo",
                repositoryHtmlUrl = null,
            )

        composeRule.setContent {
            NexoraGitTheme {
                ActivityContent(
                    state = ActivityUiState(
                        login = "ghost",
                        notifications =
                            listOf(notification),
                        loading = false,
                    ),
                    contentPadding =
                        PaddingValues(0.dp),
                    onRefresh = {},
                    onSetSection = {},
                    onSetNotificationScope = {},
                    onMarkRead = {},
                    onMarkAllRead = {},
                    onManageSubscription = {},
                    onOpenRepository = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Notifications")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Review requested")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Mark read")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Mark all read")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Subscription")
            .assertIsDisplayed()
    }

    @Test
    fun showsActivityFeed() {
        val event = GitHubActivityEvent(
            id = "10",
            type = "PushEvent",
            actorLogin = "ghost",
            actorAvatarUrl = null,
            repositoryName = "ghost/repo",
            publicEvent = true,
            createdAt = "now",
            action = null,
            ref = "refs/heads/main",
            refType = null,
            number = null,
            title = null,
        )

        composeRule.setContent {
            NexoraGitTheme {
                ActivityContent(
                    state = ActivityUiState(
                        login = "ghost",
                        section =
                            ActivitySection.ACTIVITY,
                        activity = listOf(event),
                        loading = false,
                    ),
                    contentPadding =
                        PaddingValues(0.dp),
                    onRefresh = {},
                    onSetSection = {},
                    onSetNotificationScope = {},
                    onMarkRead = {},
                    onMarkAllRead = {},
                    onManageSubscription = {},
                    onOpenRepository = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Pushed commits")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ghost/repo")
            .assertIsDisplayed()
    }
}
