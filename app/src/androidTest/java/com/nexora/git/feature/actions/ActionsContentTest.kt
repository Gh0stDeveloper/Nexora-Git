package com.nexora.git.feature.actions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.actions.GitHubWorkflow
import com.nexora.git.core.actions.GitHubWorkflowRun
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ActionsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsWorkflowDispatchAndRun() {
        val workflow = GitHubWorkflow(
            id = 10,
            nodeId = "W_10",
            name = "Android CI",
            path = ".github/workflows/android-ci.yml",
            state = "active",
            createdAt = null,
            updatedAt = null,
            htmlUrl = null,
            badgeUrl = null,
        )
        val run = GitHubWorkflowRun(
            id = 20,
            nodeId = "R_20",
            name = "Android CI",
            displayTitle = "Build app",
            event = "push",
            status = "completed",
            conclusion = "success",
            workflowId = 10,
            runNumber = 5,
            runAttempt = 1,
            headBranch = "main",
            headSha = "abcdef123456",
            htmlUrl = null,
            createdAt = null,
            updatedAt = null,
            runStartedAt = null,
            actorLogin = "ghost",
        )

        composeRule.setContent {
            NexoraGitTheme {
                ActionsContent(
                    state = ActionsUiState(
                        owner = "ghost",
                        repository = "repo",
                        workflows = listOf(workflow),
                        runs = listOf(run),
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onSelectWorkflow = {},
                    onSetStatus = {},
                    onOpenRun = {},
                    onDispatch = {},
                )
            }
        }

        composeRule.onNodeWithText("GitHub Actions")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Android CI")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Dispatch")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Build app")
            .assertIsDisplayed()
    }
}
