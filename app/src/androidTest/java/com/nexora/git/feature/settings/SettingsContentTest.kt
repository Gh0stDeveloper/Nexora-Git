package com.nexora.git.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.settings.AppLanguage
import com.nexora.git.core.settings.AppThemeMode
import com.nexora.git.core.settings.ProductPreferences
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesThemeLanguageAndEditorPreferences() {
        var selectedTheme: AppThemeMode? = null
        var selectedLanguage: AppLanguage? = null
        var selectedIndent: EditorIndentStyle? = null

        composeRule.setContent {
            NexoraGitTheme {
                SettingsContent(
                    state = SettingsUiState(
                        product = ProductPreferences(),
                        editorIndentStyle = EditorIndentStyle.SPACES_4,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onTheme = { selectedTheme = it },
                    onDynamicColor = {},
                    onLanguage = { selectedLanguage = it },
                    onIndent = { selectedIndent = it },
                    onConfirmForcePush = {},
                    onWifiOnly = {},
                    onReplayOnboarding = {},
                    onReset = {},
                )
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("AMOLED").performClick()
        composeRule.onNodeWithText("Español (México)").performClick()
        composeRule.onNodeWithText("2 spaces").performClick()

        composeRule.runOnIdle {
            assertEquals(AppThemeMode.AMOLED, selectedTheme)
            assertEquals(AppLanguage.SPANISH, selectedLanguage)
            assertEquals(EditorIndentStyle.SPACES_2, selectedIndent)
        }
    }
}
