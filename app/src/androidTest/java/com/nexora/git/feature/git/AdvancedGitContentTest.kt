package com.nexora.git.feature.git

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nexora.git.core.git.GitLfsState
import com.nexora.git.core.git.GitResetMode
import com.nexora.git.core.git.GitStash
import com.nexora.git.core.git.GitSubmodule
import com.nexora.git.core.git.GitTag
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class AdvancedGitContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun advancedPanelExposesPhasePOperations() {
        val state = GitWorkspaceUiState(
            loading = false,
            stashes = listOf(
                GitStash(
                    index = 0,
                    oid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    message = "WIP",
                ),
            ),
            tags = listOf(
                GitTag(
                    name = "v1.0.0",
                    targetOid =
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    annotated = true,
                    message = "Release",
                    taggerName = "Nexora",
                    taggerEmail = "nexora@example.invalid",
                ),
            ),
            submodules = listOf(
                GitSubmodule(
                    name = "engine",
                    path = "vendor/engine",
                    url = "https://github.com/example/engine.git",
                    headOid =
                        "cccccccccccccccccccccccccccccccccccccccc",
                    workdirOid =
                        "cccccccccccccccccccccccccccccccccccccccc",
                    status = 0,
                    initialized = true,
                ),
            ),
            lfs = GitLfsState(
                trackedPatterns = listOf("*.psd"),
                pointers = emptyList(),
            ),
        )

        composeRule.setContent {
            NexoraGitTheme {
                AdvancedGitPanel(
                    state = state,
                    authorName = "Nexora",
                    authorEmail = "nexora@example.invalid",
                    rebaseRef = "main",
                    onRebaseRefChange = {},
                    commitRef = "HEAD~1",
                    onCommitRefChange = {},
                    stashMessage = "",
                    onStashMessageChange = {},
                    stashIncludeUntracked = true,
                    onStashIncludeUntrackedChange = {},
                    resetRef = "HEAD~1",
                    onResetRefChange = {},
                    resetMode = GitResetMode.MIXED,
                    onResetModeChange = {},
                    tagName = "v2",
                    onTagNameChange = {},
                    tagTarget = "HEAD",
                    onTagTargetChange = {},
                    tagMessage = "",
                    onTagMessageChange = {},
                    annotatedTag = true,
                    onAnnotatedTagChange = {},
                    lfsPattern = "*.zip",
                    onLfsPatternChange = {},
                    lfsRemoteName = "origin",
                    onRebase = {},
                    onCherryPick = {},
                    onContinueCherryPick = {},
                    onAbortCherryPick = {},
                    onSaveStash = {},
                    onApplyStash = {},
                    onPopStash = {},
                    onDropStash = {},
                    onReset = {},
                    onRevert = {},
                    onContinueRevert = {},
                    onAbortRevert = {},
                    onCreateTag = {},
                    onDeleteTag = {},
                    onSyncSubmodule = {},
                    onUpdateSubmodule = {},
                    onTrackLfs = {},
                    onUntrackLfs = {},
                    onDownloadLfs = {},
                    onUploadLfs = {},
                )
            }
        }

        composeRule.onNodeWithText("Advanced Git")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cherry-pick")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Save stash")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Local tags")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Submodules")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Git LFS")
            .assertIsDisplayed()
    }
}
