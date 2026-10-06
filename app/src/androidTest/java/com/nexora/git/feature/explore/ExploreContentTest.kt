package com.nexora.git.feature.explore

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.explore.ExploreSearchSection
import com.nexora.git.core.repository.RepositoryPermissions
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ExploreContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysRealRepositorySearchResult() {
        composeRule.setContent {
            NexoraGitTheme {
                ExploreContent(
                    state = ExploreUiState(
                        query = "Nexora",
                        section =
                            ExploreSearchSection.REPOSITORIES,
                        hasSearched = true,
                        repositories = listOf(
                            RepositorySummary(
                                id = 1,
                                nodeId = "R_1",
                                name = "Nexora-Git",
                                fullName = "Ghost/Nexora-Git",
                                ownerLogin = "Ghost",
                                ownerAvatarUrl = null,
                                description =
                                    "Android Git client",
                                privateRepository = false,
                                fork = false,
                                archived = false,
                                visibility = "public",
                                language = "Kotlin",
                                defaultBranch = "main",
                                cloneUrl = "",
                                htmlUrl = "",
                                stars = 10,
                                forks = 2,
                                openIssues = 1,
                                sizeKb = 100,
                                updatedAt = null,
                                pushedAt = null,
                                permissions =
                                    RepositoryPermissions(),
                            ),
                        ),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onQueryChange = {},
                    onSectionChange = {},
                    onSearch = {},
                    onOpenRepository = { _, _ -> },
                    onOpenUser = {},
                    onOpenCode = {},
                )
            }
        }

        composeRule.onNodeWithText("Explore")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Repositories")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ghost/Nexora-Git")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Kotlin · 10 stars")
            .assertIsDisplayed()
    }
}
