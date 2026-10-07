package com.nexora.git.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesRealFirstActions() {
        var action: OnboardingAction? = null

        composeRule.setContent {
            NexoraGitTheme {
                OnboardingScreen(
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText("Build from your phone")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Clone repository")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(OnboardingAction.CLONE, action)
        }
    }
}
