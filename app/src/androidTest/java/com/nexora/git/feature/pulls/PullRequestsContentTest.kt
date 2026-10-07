package com.nexora.git.feature.pulls

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.pulls.PullRequestRef
import com.nexora.git.core.pulls.PullRequestState
import com.nexora.git.core.pulls.PullRequestSummary
import com.nexora.git.core.pulls.PullRequestUser
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class PullRequestsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsStateFiltersCreateAndPullRows() {
        val pull = PullRequestSummary(
            id = 1,
            nodeId = "PR_1",
            number = 12,
            title = "Phase K",
            body = null,
            state = "open",
            draft = false,
            locked = false,
            merged = false,
            mergeable = true,
            mergeableState = "clean",
            author = PullRequestUser("ghost", null),
            head = PullRequestRef(
                "ghost:feature",
                "feature",
                "aaaaaaa",
                "ghost/repo",
            ),
            base = PullRequestRef(
                "ghost:main",
                "main",
                "bbbbbbb",
                "ghost/repo",
            ),
            comments = 0,
            reviewComments = 0,
            commits = 2,
            additions = 5,
            deletions = 1,
            changedFiles = 2,
            createdAt = "",
            updatedAt = "",
            closedAt = null,
            mergedAt = null,
            htmlUrl = "",
        )

        composeRule.setContent {
            NexoraGitTheme {
                PullRequestsContent(
                    state = PullRequestsUiState(
                        owner = "ghost",
                        repository = "repo",
                        stateFilter = PullRequestState.OPEN,
                        pullRequests = listOf(pull),
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onSetState = {},
                    onOpenPullRequest = {},
                    onCreate = {},
                )
            }
        }

        composeRule.onNodeWithText("Pull requests")
            .assertIsDisplayed()
        composeRule.onNodeWithText("New pull request")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Phase K")
            .assertIsDisplayed()
    }
}
