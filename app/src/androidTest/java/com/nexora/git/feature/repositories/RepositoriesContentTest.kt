package com.nexora.git.feature.repositories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.repository.RepositoryPermissions
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.storage.Workspace
import com.nexora.git.core.storage.WorkspaceStrategy
import com.nexora.git.core.storage.WorkspaceSyncState
import com.nexora.git.core.templates.ProjectTemplateSummary
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class RepositoriesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysRemoteRepositoryAndManagedWorkspace() {
        composeRule.setContent {
            NexoraGitTheme {
                RepositoriesContent(
                    state = RepositoriesUiState(
                        loading = false,
                        templates = listOf(
                            ProjectTemplateSummary(
                                id = "kotlin-cli",
                                name = "Kotlin CLI",
                                description = "Kotlin project",
                            ),
                        ),
                        remoteRepositories = listOf(
                            RepositorySummary(
                                id = 10L,
                                nodeId = "R_repo",
                                name = "Nexora-Git",
                                fullName = "Ghost/Nexora-Git",
                                ownerLogin = "Ghost",
                                ownerAvatarUrl = null,
                                description = "Android Git client",
                                privateRepository = false,
                                fork = false,
                                archived = false,
                                visibility = "public",
                                language = "Kotlin",
                                defaultBranch = "main",
                                cloneUrl =
                                    "https://github.com/Ghost/Nexora-Git.git",
                                htmlUrl =
                                    "https://github.com/Ghost/Nexora-Git",
                                stars = 12,
                                forks = 3,
                                openIssues = 1,
                                sizeKb = 2048,
                                updatedAt = null,
                                pushedAt = null,
                                permissions = RepositoryPermissions(
                                    admin = true,
                                    push = true,
                                ),
                            ),
                        ),
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
                                currentBranch = "main",
                                accountId = null,
                                syncState = WorkspaceSyncState.READY,
                                lastSyncedAtEpochMillis = 1L,
                                lastScanAtEpochMillis = 1L,
                                fileCount = 42,
                                totalBytes = 2048,
                                secretWarningCount = 1,
                                largeFileWarningCount = 0,
                                syncConflictCount = 0,
                                lastOpenedAtEpochMillis = 1L,
                            ),
                        ),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onRefresh = {},
                    onCreate = {},
                    onCloneUrl = {},
                    onOpenFolder = {},
                    onNewTemplate = {},
                    onOpenRepository = { _, _ -> },
                    onBrowseWorkspace = {},
                    onOpenGitWorkspace = {},
                    onCloneRepository = {},
                    onInitializeGit = {},
                    onSync = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Create repository")
            .assertIsDisplayed()
        composeRule.onNodeWithText("New from template")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Nexora-Git")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Clone to device")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Sample Project")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Managed Android workspace")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Branch: main")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Browse code")
            .assertIsDisplayed()
    }
}
