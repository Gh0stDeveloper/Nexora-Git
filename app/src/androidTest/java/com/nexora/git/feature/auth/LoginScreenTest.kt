package com.nexora.git.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unconfiguredBuild_disablesGitHubSignIn() {
        composeRule.setContent {
            NexoraGitTheme {
                LoginScreen(
                    configured = false,
                    operationInProgress = false,
                    onSignIn = {},
                )
            }
        }

        composeRule.onNodeWithText("Sign in to GitHub").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with GitHub").assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Authentication is disabled in this build until the GitHub App client ID, broker URL and callback URL are configured.",
        ).assertIsDisplayed()
    }
}
