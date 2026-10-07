package com.nexora.git.feature.issues

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.issues.IssueComment
import com.nexora.git.core.issues.IssueDetails
import com.nexora.git.core.issues.IssueReactionSummary
import com.nexora.git.core.issues.IssueSummary
import com.nexora.git.core.issues.IssueUser
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class IssueDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsIssueActionsCommentAndReactions() {
        val reactions = IssueReactionSummary(
            totalCount = 1,
            plusOne = 1,
            minusOne = 0,
            laugh = 0,
            hooray = 0,
            confused = 0,
            heart = 0,
            rocket = 0,
            eyes = 0,
        )
        val user = IssueUser("ghost", null)
        val summary = IssueSummary(
            id = 10,
            nodeId = "I_10",
            number = 7,
            title = "Issue title",
            state = "open",
            locked = false,
            author = user,
            labels = emptyList(),
            assignees = emptyList(),
            milestone = null,
            comments = 1,
            createdAt = "",
            updatedAt = "",
            closedAt = null,
            htmlUrl = "",
        )
        val comment = IssueComment(
            id = 20,
            nodeId = "IC_20",
            body = "A comment",
            author = user,
            createdAt = "",
            updatedAt = "",
            htmlUrl = "",
            reactions = reactions,
        )

        composeRule.setContent {
            NexoraGitTheme {
                IssueDetailContent(
                    state = IssueDetailUiState(
                        owner = "Ghost",
                        repository = "Nexora-Git",
                        number = 7,
                        issue = IssueDetails(
                            summary = summary,
                            body = "Description",
                            reactions = reactions,
                        ),
                        comments = listOf(comment),
                        loading = false,
                    ),
                    activeLogin = "ghost",
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onUpdateIssue = {},
                    onSetState = {},
                    onCreateComment = {},
                    onUpdateComment = { _, _ -> },
                    onDeleteComment = {},
                    onReactToIssue = {},
                    onReactToComment = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Issue title")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Close")
            .assertIsDisplayed()
        composeRule.onNodeWithText("A comment")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Edit")
            .assertIsDisplayed()
        composeRule.onNodeWithText("+1")
            .assertIsDisplayed()
    }
}
