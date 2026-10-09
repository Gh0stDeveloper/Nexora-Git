package com.nexora.git.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.settings.ProductPreferences
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class SettingsAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preferenceRowsExposeActionableSemantics() {
        composeRule.setContent {
            NexoraGitTheme {
                SettingsContent(
                    state = SettingsUiState(
                        product = ProductPreferences(),
                        editorIndentStyle = EditorIndentStyle.SPACES_4,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onTheme = {},
                    onDynamicColor = {},
                    onLanguage = {},
                    onIndent = {},
                    onConfirmForcePush = {},
                    onWifiOnly = {},
                    onReplayOnboarding = {},
                    onOpenPrivacy = {},
                    onOpenSource = {},
                    onReset = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Dynamic color")
            .onParent()
            .onParent()
            .assertIsToggleable()
            .assertHasClickAction()

        composeRule
            .onNodeWithText("Confirm force push")
            .onParent()
            .onParent()
            .assertIsToggleable()
            .assertHasClickAction()
    }
}
