package com.nexora.git.feature.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nexora.git.core.auth.AuthAccountSummary
import com.nexora.git.core.social.GitHubUserProfile
import com.nexora.git.core.social.StarredRepository
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ProfileContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsProfileMetricsAccountAndStarredRepositories() {
        val account = AuthAccountSummary(
            accountId = 1,
            login = "ghost",
            name = "Ghost Developer",
            avatarUrl = null,
            tokenExpiresAtEpochMillis = null,
        )
        val profile = GitHubUserProfile(
            id = 1,
            nodeId = "U_1",
            login = "ghost",
            name = "Ghost Developer",
            avatarUrl = null,
            htmlUrl = null,
            bio = "Android developer",
            company = "Nexora",
            location = "Mexico",
            blog = null,
            email = null,
            twitterUsername = null,
            hireable = true,
            publicRepos = 20,
            publicGists = 2,
            followers = 100,
            following = 30,
            createdAt = null,
            updatedAt = null,
        )
        val starred = StarredRepository(
            id = 2,
            nodeId = "R_2",
            name = "repo",
            fullName = "friend/repo",
            ownerLogin = "friend",
            ownerAvatarUrl = null,
            privateRepository = false,
            description = "Repository",
            htmlUrl = null,
            language = "Kotlin",
            stars = 15,
            forks = 3,
            updatedAt = null,
        )

        var state = ProfileUiState(
            profile = profile,
            starredRepositories = listOf(starred),
            loading = false,
        )

        composeRule.setContent {
            NexoraGitTheme {
                ProfileContent(
                    state = state,
                    contentPadding =
                        PaddingValues(0.dp),
                    activeAccount = account,
                    accounts = listOf(account),
                    authOperationInProgress = false,
                    onRefresh = {},
                    onSetSection = {
                        state = state.copy(
                            section = it,
                        )
                    },
                    onEditProfile = {},
                    onSwitchAccount = {},
                    onAddAccount = {},
                    onSignOut = {},
                    onOpenRepository = { _, _ -> },
                    onUnstar = {},
                    onSetFollowing = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Ghost Developer")
            .assertIsDisplayed()
        composeRule.onNodeWithText("100 followers")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Edit GitHub profile")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add GitHub account")
            .assertIsDisplayed()
    }
}
