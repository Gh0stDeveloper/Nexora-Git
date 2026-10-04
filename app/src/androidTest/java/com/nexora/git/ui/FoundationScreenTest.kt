package com.nexora.git.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.ui.components.FoundationScreen
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class FoundationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun foundationScreen_displaysTitleAndDescription() {
        composeRule.setContent {
            NexoraGitTheme {
                FoundationScreen(
                    title = "Repositories",
                    description = "Repository foundation",
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }

        composeRule.onNodeWithText("Repositories").assertIsDisplayed()
        composeRule.onNodeWithText("Repository foundation").assertIsDisplayed()
    }
}
