package com.nexora.git.feature.git

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nexora.git.core.git.GitBranch
import com.nexora.git.core.git.GitDivergence
import com.nexora.git.core.git.GitPullStrategy
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class GitWorkspaceContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val synchronizedState = GitWorkspaceUiState(
        workspaceName = "Nexora-Git",
        branch = "feature/mobile",
        branches = listOf(
            GitBranch(
                name = "feature/mobile",
                remote = false,
                head = true,
                upstream = "origin/feature/mobile",
            ),
            GitBranch(
                name = "origin/feature/mobile",
                remote = true,
                head = false,
                upstream = "",
            ),
        ),
        divergence = GitDivergence(
            localRef = "feature/mobile",
            upstreamRef = "origin/feature/mobile",
            localOid = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            upstreamOid = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            ahead = 2,
            behind = 1,
        ),
        loading = false,
    )

    @Test
    fun summaryShowsUpstreamAndDivergence() {
        composeRule.setContent {
            NexoraGitTheme {
                RepositorySummaryCard(synchronizedState)
            }
        }

        composeRule.onNodeWithText("feature/mobile")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Upstream: origin/feature/mobile",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("2 ahead · 1 behind")
            .assertIsDisplayed()
    }

    @Test
    fun pullSurfaceShowsAllStrategies() {
        composeRule.setContent {
            NexoraGitTheme {
                PullCard(
                    remote = "origin",
                    strategy = GitPullStrategy.MERGE,
                    busy = false,
                    conflictCount = 0,
                    rebaseInProgress = false,
                    onStrategyChange = {},
                    onPull = {},
                )
            }
        }

        composeRule.onNodeWithText("Merge")
            .assertIsDisplayed()
        composeRule.onNodeWithText("FF only")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Rebase")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Pull")
            .assertIsEnabled()
    }

    @Test
    fun leasePushRequiresExactTrackedTarget() {
        composeRule.setContent {
            NexoraGitTheme {
                Column {
                    PushCard(
                        state = synchronizedState,
                        remote = "origin",
                        pushTarget = "feature/mobile",
                        onPushTargetChange = {},
                        onPush = {},
                        onForceWithLease = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Push")
            .assertIsEnabled()
        composeRule.onNodeWithText("Force with lease")
            .assertIsEnabled()
    }
}
