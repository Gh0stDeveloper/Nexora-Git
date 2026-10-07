package com.nexora.git.feature.releases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.releases.GitHubRelease
import com.nexora.git.core.releases.GitTag
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ReleasesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsReleaseTagAndAuthoringActions() {
        val release = GitHubRelease(
            id = 10,
            nodeId = "REL_10",
            tagName = "v1.0.0",
            targetCommitish = "main",
            name = "Nexora Git 1.0",
            body = "Notes",
            draft = false,
            prerelease = false,
            immutable = false,
            createdAt = null,
            publishedAt = null,
            htmlUrl = null,
            uploadUrl = null,
            authorLogin = "ghost",
            assets = emptyList(),
        )
        val tag = GitTag(
            name = "v1.0.0",
            commitSha = "abcdef123456",
            commitUrl = null,
            zipballUrl = null,
            tarballUrl = null,
            nodeId = null,
        )

        composeRule.setContent {
            NexoraGitTheme {
                ReleasesContent(
                    state = ReleasesUiState(
                        owner = "ghost",
                        repository = "repo",
                        canWrite = true,
                        releases = listOf(release),
                        tags = listOf(tag),
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onSetFilter = {},
                    onOpenRelease = {},
                    onCreateRelease = {},
                    onCreateTag = {},
                    onDeleteTag = {},
                )
            }
        }

        composeRule.onNodeWithText("Releases")
            .assertIsDisplayed()
        composeRule.onNodeWithText("New release")
            .assertIsDisplayed()
        composeRule.onNodeWithText("New tag")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Nexora Git 1.0")
            .assertIsDisplayed()
        composeRule.onNodeWithText("v1.0.0")
            .assertIsDisplayed()
    }
}
