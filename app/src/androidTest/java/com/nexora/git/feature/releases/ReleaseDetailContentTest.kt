package com.nexora.git.feature.releases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.releases.GitHubRelease
import com.nexora.git.core.releases.ReleaseAsset
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class ReleaseDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsDraftPublishingAndAssetActions() {
        val asset = ReleaseAsset(
            id = 20,
            nodeId = "ASSET_20",
            name = "app.apk",
            label = "Android",
            state = "uploaded",
            contentType = "application/vnd.android.package-archive",
            sizeInBytes = 2048,
            downloadCount = 10,
            digest = "sha256:abc",
            createdAt = null,
            updatedAt = null,
            browserDownloadUrl = null,
            uploaderLogin = "ghost",
        )
        val release = GitHubRelease(
            id = 10,
            nodeId = "REL_10",
            tagName = "v1.0.0",
            targetCommitish = "main",
            name = "Nexora Git 1.0",
            body = "Release notes",
            draft = true,
            prerelease = false,
            immutable = false,
            createdAt = null,
            publishedAt = null,
            htmlUrl = null,
            uploadUrl = null,
            authorLogin = "ghost",
            assets = listOf(asset),
        )

        composeRule.setContent {
            NexoraGitTheme {
                ReleaseDetailContent(
                    state = ReleaseDetailUiState(
                        owner = "ghost",
                        repository = "repo",
                        releaseId = 10,
                        release = release,
                        canWrite = true,
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onEdit = {},
                    onPublish = {},
                    onConvertToDraft = {},
                    onUploadAsset = {},
                    onEditAsset = {},
                    onDeleteAsset = {},
                    onDownloadAsset = {},
                    onDeleteRelease = {},
                )
            }
        }

        composeRule.onNodeWithText("Nexora Git 1.0")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Publish")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Upload asset")
            .assertIsDisplayed()
        composeRule.onNodeWithText("app.apk")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Download")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Rename")
            .assertIsDisplayed()
    }
}
