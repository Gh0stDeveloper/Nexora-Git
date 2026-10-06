package com.nexora.git.feature.pulls

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.pulls.PullRequestCheckRun
import com.nexora.git.core.pulls.PullRequestRef
import com.nexora.git.core.pulls.PullRequestSummary
import com.nexora.git.core.pulls.PullRequestUser
import com.nexora.git.core.repository.RepositoryDetails
import com.nexora.git.core.repository.RepositoryPermissions
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class PullRequestDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsReviewDraftAndMergeActions() {
        val pull = PullRequestSummary(
            id = 1,
            nodeId = "PR_1",
            number = 12,
            title = "Review feature",
            body = "Body",
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
            commits = 1,
            additions = 5,
            deletions = 1,
            changedFiles = 1,
            createdAt = "",
            updatedAt = "",
            closedAt = null,
            mergedAt = null,
            htmlUrl = "",
        )
        val summary = RepositorySummary(
            id = 1,
            nodeId = "R_1",
            name = "repo",
            fullName = "ghost/repo",
            ownerLogin = "ghost",
            ownerAvatarUrl = null,
            description = null,
            privateRepository = false,
            fork = false,
            archived = false,
            visibility = "public",
            language = "Kotlin",
            defaultBranch = "main",
            cloneUrl = "",
            htmlUrl = "",
            stars = 0,
            forks = 0,
            openIssues = 0,
            sizeKb = 1,
            updatedAt = null,
            pushedAt = null,
            permissions = RepositoryPermissions(
                push = true,
            ),
        )

        composeRule.setContent {
            NexoraGitTheme {
                PullRequestDetailContent(
                    state = PullRequestDetailUiState(
                        owner = "ghost",
                        repository = "repo",
                        number = 12,
                        pullRequest = pull,
                        repositoryDetails = RepositoryDetails(
                            summary = summary,
                            homepage = null,
                            subscribers = 0,
                            hasIssues = true,
                            hasWiki = false,
                            hasProjects = false,
                            hasPages = false,
                            deleteBranchOnMerge = false,
                            allowMergeCommit = true,
                            allowSquashMerge = true,
                            allowRebaseMerge = true,
                        ),
                        checks = listOf(
                            PullRequestCheckRun(
                                id = 10,
                                name = "Android CI",
                                status = "completed",
                                conclusion = "success",
                                detailsUrl = null,
                                startedAt = null,
                                completedAt = null,
                            ),
                        ),
                        loading = false,
                    ),
                    activeLogin = "ghost",
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onUpdate = {},
                    onSetState = {},
                    onSetDraft = {},
                    onSubmitReview = { _, _ -> },
                    onCreateReviewComment = {},
                    onUpdateReviewComment = { _, _ -> },
                    onDeleteReviewComment = {},
                    onMerge = {},
                )
            }
        }

        composeRule.onNodeWithText("Review feature")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Approve")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Request changes")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Draft")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Merge")
            .assertIsDisplayed()
    }
}
