package com.nexora.git.feature.activity

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.social.GitHubActivityEvent
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ActivityContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsNotificationCapabilityFallback() {
        composeRule.setContent {
            NexoraGitTheme {
                ActivityContent(
                    state = ActivityUiState(
                        login = "ghost",
                        loading = false,
                    ),
                    contentPadding =
                        PaddingValues(0.dp),
                    onRefresh = {},
                    onSetSection = {},
                    onOpenGitHubNotifications = {},
                    onOpenRepository = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(
            "GitHub notification inbox",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Open GitHub notifications",
        ).assertIsDisplayed()
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
                    onOpenGitHubNotifications = {},
                    onOpenRepository = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(
            "Pushed commits",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "ghost/repo",
        ).assertIsDisplayed()
    }
}
