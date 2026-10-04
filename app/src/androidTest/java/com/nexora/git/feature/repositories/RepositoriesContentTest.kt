package com.nexora.git.feature.repositories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceStrategy
import com.nexora.git.core.storage.WorkspaceSyncState
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class RepositoriesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysManagedWorkspaceAndFolderAction() {
        composeRule.setContent {
            NexoraGitTheme {
                RepositoriesContent(
                    state = RepositoriesUiState(
                        loading = false,
                        workspaces = listOf(
                            Workspace(
                                id = "workspace-1",
                                name = "Sample Project",
                                sourceTreeUri = "content://sample",
                                sourceDisplayName = "Sample Project",
                                sourceAuthority = "sample",
                                sourceWritable = true,
                                strategy = WorkspaceStrategy.MANAGED,
                                workspacePath = "/data/sample",
                                repositoryRemote = null,
                                currentBranch = null,
                                accountId = null,
                                syncState = WorkspaceSyncState.READY,
                                lastSyncedAtEpochMillis = 1L,
                                lastScanAtEpochMillis = 1L,
                                fileCount = 42,
                                totalBytes = 2048,
                                secretWarningCount = 1,
                                largeFileWarningCount = 0,
                                lastOpenedAtEpochMillis = 1L,
                            ),
                        ),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onOpenFolder = {},
                    onSync = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Open project folder")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Sample Project")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Managed workspace")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "1 secret warnings · 0 large-file warnings",
        ).assertIsDisplayed()
    }
}
