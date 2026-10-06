package com.nexora.git.feature.advanced

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.nexora.git.core.advanced.AdvancedDiscussionCategory
import com.nexora.git.core.advanced.AdvancedDiscussionHub
import com.nexora.git.core.advanced.AdvancedDiscussionSummary
import com.nexora.git.core.advanced.AdvancedProjectHub
import com.nexora.git.core.advanced.AdvancedProjectSummary
import com.nexora.git.core.advanced.GitHubCodespaceSummary
import com.nexora.git.core.advanced.GitHubGistSummary
import com.nexora.git.core.advanced.GitHubPagesSite
import com.nexora.git.core.advanced.RepositorySecurityOverview
import com.nexora.git.core.advanced.SecurityAlertFeed
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class AdvancedGitHubContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAllPhaseOSectionsAndActions() {
        val state = AdvancedGitHubUiState(
            discussions = AdvancedSection(
                loading = false,
                value = AdvancedDiscussionHub(
                    repositoryNodeId = "R_1",
                    categories = listOf(
                        AdvancedDiscussionCategory(
                            id = "DIC_1",
                            name = "General",
                            description = null,
                            answerable = false,
                        ),
                    ),
                    discussions = listOf(
                        AdvancedDiscussionSummary(
                            id = "D_1",
                            number = 1,
                            title = "Phase O",
                            url = null,
                            categoryName = "General",
                            authorLogin = "ghost",
                            comments = 2,
                            upvotes = 3,
                            answered = false,
                            updatedAt = null,
                        ),
                    ),
                ),
            ),
            projects = AdvancedSection(
                loading = false,
                value = AdvancedProjectHub(
                    ownerNodeId = "U_1",
                    repositoryNodeId = "R_1",
                    projects = listOf(
                        AdvancedProjectSummary(
                            id = "P_1",
                            number = 2,
                            title = "Android",
                            shortDescription = null,
                            url = null,
                            closed = false,
                            publicProject = true,
                            itemCount = 5,
                            updatedAt = null,
                        ),
                    ),
                ),
            ),
            pages = AdvancedSection(
                loading = false,
                value = GitHubPagesSite(
                    enabled = true,
                    htmlUrl =
                        "https://ghost.github.io/repo/",
                    status = "built",
                    cname = null,
                    httpsEnforced = true,
                    protectedDomainState = "verified",
                    buildType = "legacy",
                    sourceBranch = "main",
                    sourcePath = "/",
                ),
            ),
            security = AdvancedSection(
                loading = false,
                value = RepositorySecurityOverview(
                    dependabot = SecurityAlertFeed(
                        available = true,
                    ),
                    codeScanning = SecurityAlertFeed(
                        available = true,
                    ),
                    secretScanning = SecurityAlertFeed(
                        available = false,
                        message = "Permission required.",
                    ),
                ),
            ),
            gists = AdvancedSection(
                loading = false,
                value = listOf(
                    GitHubGistSummary(
                        id = "abc123def456",
                        description = "Snippet",
                        htmlUrl = null,
                        publicGist = false,
                        fileNames = listOf("a.kt"),
                        comments = 0,
                        createdAt = null,
                        updatedAt = null,
                    ),
                ),
            ),
            codespaces = AdvancedSection(
                loading = false,
                value = listOf(
                    GitHubCodespaceSummary(
                        name = "ghost-repo-123",
                        displayName = "Nexora workspace",
                        state = "Available",
                        repositoryFullName = "ghost/repo",
                        machineName = "2 cores",
                        webUrl = null,
                        createdAt = null,
                        updatedAt = null,
                    ),
                ),
            ),
        )

        composeRule.setContent {
            NexoraGitTheme {
                AdvancedGitHubContent(
                    state = state,
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onCreateDiscussion = {},
                    onCreateProject = {},
                    onEnablePages = {},
                    onBuildPages = {},
                    onCreateGist = {},
                    onDeleteGist = {},
                    onCreateCodespace = {},
                    onSetCodespaceRunning = { _, _ -> },
                    onDeleteCodespace = {},
                )
            }
        }

        composeRule.onNodeWithText("Advanced GitHub")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Discussions")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Phase O")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Projects")
            .assertIsDisplayed()

        composeRule.onNodeWithText("GitHub Pages")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Repository security")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Gists")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Codespaces")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
