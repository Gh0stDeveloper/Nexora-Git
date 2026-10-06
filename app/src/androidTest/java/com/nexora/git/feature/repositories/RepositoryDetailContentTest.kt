package com.nexora.git.feature.repositories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.repository.RepositoryDetails
import com.nexora.git.core.repository.RepositoryPermissions
import com.nexora.git.core.repository.RepositorySubscriptionState
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.repository.RepositoryViewerState
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class RepositoryDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysRepositoryActionsAndAdminSettings() {
        val details = RepositoryDetails(
            summary = RepositorySummary(
                id = 1,
                nodeId = "R_1",
                name = "Nexora-Git",
                fullName = "Ghost/Nexora-Git",
                ownerLogin = "Ghost",
                ownerAvatarUrl = null,
                description = "Android Git client",
                privateRepository = false,
                fork = false,
                archived = false,
                visibility = "public",
                language = "Kotlin",
                defaultBranch = "main",
                cloneUrl =
                    "https://github.com/Ghost/Nexora-Git.git",
                htmlUrl =
                    "https://github.com/Ghost/Nexora-Git",
                stars = 10,
                forks = 2,
                openIssues = 3,
                sizeKb = 4096,
                updatedAt = null,
                pushedAt = null,
                permissions = RepositoryPermissions(
                    admin = true,
                    push = true,
                ),
            ),
            homepage = null,
            subscribers = 4,
            hasIssues = true,
            hasWiki = true,
            hasProjects = true,
            hasPages = false,
            deleteBranchOnMerge = true,
            allowMergeCommit = true,
            allowSquashMerge = true,
            allowRebaseMerge = true,
        )

        composeRule.setContent {
            NexoraGitTheme {
                RepositoryDetailContent(
                    state = RepositoryDetailUiState(
                        loading = false,
                        details = details,
                        viewerState = RepositoryViewerState(
                            starred = false,
                            subscription =
                                RepositorySubscriptionState.UNSUBSCRIBED,
                            canSubscribe = true,
                        ),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onToggleStar = {},
                    onToggleWatch = {},
                    onFork = {},
                    onClone = {},
                    onOpenSettings = {},
                    onOpenIssues = { _, _ -> },
                    onOpenPullRequests = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Nexora-Git")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Star")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Watch")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Fork")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Clone")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Repository settings")
            .assertIsDisplayed()
    }
}
