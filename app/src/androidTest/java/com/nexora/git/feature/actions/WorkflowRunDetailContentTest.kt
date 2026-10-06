package com.nexora.git.feature.actions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.actions.GitHubActionsArtifact
import com.nexora.git.core.actions.GitHubActionsJob
import com.nexora.git.core.actions.GitHubActionsStep
import com.nexora.git.core.actions.GitHubWorkflowRun
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class WorkflowRunDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsJobsStepsLogsAndArtifacts() {
        val run = GitHubWorkflowRun(
            id = 20,
            nodeId = "R_20",
            name = "Android CI",
            displayTitle = "Build app",
            event = "push",
            status = "completed",
            conclusion = "failure",
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
        val job = GitHubActionsJob(
            id = 30,
            runId = 20,
            runAttempt = 1,
            nodeId = "J_30",
            name = "Build, lint and unit test",
            status = "completed",
            conclusion = "failure",
            startedAt = null,
            completedAt = null,
            htmlUrl = null,
            runnerName = "runner",
            runnerGroupName = null,
            labels = listOf("ubuntu-latest"),
            steps = listOf(
                GitHubActionsStep(
                    name = "Build",
                    status = "completed",
                    conclusion = "failure",
                    number = 1,
                    startedAt = null,
                    completedAt = null,
                ),
            ),
        )
        val artifact = GitHubActionsArtifact(
            id = 40,
            nodeId = "A_40",
            name = "app-debug",
            sizeInBytes = 1024,
            expired = false,
            createdAt = null,
            expiresAt = null,
            updatedAt = null,
            workflowRunId = 20,
        )

        composeRule.setContent {
            NexoraGitTheme {
                WorkflowRunDetailContent(
                    state = WorkflowRunDetailUiState(
                        owner = "ghost",
                        repository = "repo",
                        runId = 20,
                        run = run,
                        jobs = listOf(job),
                        artifacts = listOf(artifact),
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onCancel = {},
                    onRerun = {},
                    onRerunFailed = {},
                    onOpenLog = {},
                    onDownloadArtifact = {},
                )
            }
        }

        composeRule.onNodeWithText("Build app")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Re-run all")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Build, lint and unit test")
            .assertIsDisplayed()
        composeRule.onNodeWithText("View logs")
            .assertIsDisplayed()
        composeRule.onNodeWithText("app-debug")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Download ZIP")
            .assertIsDisplayed()
    }
}
