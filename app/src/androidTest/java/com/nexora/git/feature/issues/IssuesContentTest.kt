package com.nexora.git.feature.issues

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.issues.IssueFilters
import com.nexora.git.core.issues.IssueLabel
import com.nexora.git.core.issues.IssueState
import com.nexora.git.core.issues.IssueSummary
import com.nexora.git.core.issues.IssueUser
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class IssuesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsFiltersCreateAndIssueRows() {
        val label = IssueLabel(
            id = 1,
            name = "bug",
            color = "ff0000",
            description = null,
        )
        val issue = IssueSummary(
            id = 10,
            nodeId = "I_10",
            number = 7,
            title = "Fix mobile workflow",
            state = "open",
            locked = false,
            author = IssueUser("ghost", null),
            labels = listOf(label),
            assignees = emptyList(),
            milestone = null,
            comments = 2,
            createdAt = "",
            updatedAt = "",
            closedAt = null,
            htmlUrl = "",
        )

        composeRule.setContent {
            NexoraGitTheme {
                IssuesContent(
                    state = IssuesUiState(
                        owner = "Ghost",
                        repository = "Nexora-Git",
                        issues = listOf(issue),
                        labels = listOf(label),
                        filters = IssueFilters(
                            state = IssueState.OPEN,
                        ),
                        loading = false,
                        metadataLoading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onOpenIssue = {},
                    onSearch = {},
                    onSetState = {},
                    onToggleLabel = {},
                    onSetAssignee = {},
                    onSetMilestone = {},
                    onClearFilters = {},
                    onCreate = {},
                )
            }
        }

        composeRule.onNodeWithText("Issues")
            .assertIsDisplayed()
        composeRule.onNodeWithText("New issue")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Fix mobile workflow")
            .assertIsDisplayed()
        composeRule.onNodeWithText("bug")
            .assertIsDisplayed()
    }
}
