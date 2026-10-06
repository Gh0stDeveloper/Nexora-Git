package com.nexora.git.feature.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.editor.EditorSearchMatch
import com.nexora.git.core.editor.EditorSyntaxSnapshot
import com.nexora.git.core.editor.EditorSymbol
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.files.BrowserFileKind
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class MobileEditorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val file = BrowserFile(
        name = "Main.kt",
        relativePath = "app/src/Main.kt",
        absolutePath = "/tmp/Main.kt",
        sizeBytes = 13,
        lastModifiedEpochMillis = 1,
        mimeType = "text/plain",
        kind = BrowserFileKind.TEXT,
        language = "Kotlin",
        text = "fun main() {}",
        truncated = false,
        lineCount = 1,
    )

    @Test
    fun displaysGitHubStyleEditorActions() {
        composeRule.setContent {
            NexoraGitTheme {
                MobileEditorContent(
                    state = MobileEditorUiState(
                        workspaceName = "Nexora-Git",
                        file = file,
                        value = TextFieldValue(
                            text = "fun main() {}",
                            selection = TextRange(3),
                        ),
                        loading = false,
                        dirty = true,
                        canUndo = true,
                        gitAvailable = true,
                        indentStyle = EditorIndentStyle.SPACES_4,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onValueChange = {},
                    onUndo = {},
                    onRedo = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onReplacementChange = {},
                    onMatchCaseChange = {},
                    onNextMatch = {},
                    onPreviousMatch = {},
                    onReplaceCurrent = {},
                    onReplaceAll = {},
                    onInsertIndent = {},
                    onIndentStyleChange = {},
                    onToggleIntelligence = {},
                    onSelectSymbol = {},
                    onFormat = {},
                    onSave = {},
                    onDiff = {},
                    onCommit = {},
                )
            }
        }

        composeRule.onNodeWithText("Main.kt")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Save")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Commit")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Undo")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("View diff")
            .assertIsDisplayed()
        composeRule.onNodeWithText("4 spaces")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Modified")
            .assertIsDisplayed()
    }

    @Test
    fun displaysSearchAndReplaceControls() {
        composeRule.setContent {
            NexoraGitTheme {
                MobileEditorContent(
                    state = MobileEditorUiState(
                        workspaceName = "Nexora-Git",
                        file = file,
                        value = TextFieldValue("foo foo"),
                        loading = false,
                        searchVisible = true,
                        searchQuery = "foo",
                        replacement = "bar",
                        searchMatches = listOf(
                            EditorSearchMatch(0, 3),
                            EditorSearchMatch(4, 7),
                        ),
                        activeMatchIndex = 0,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onValueChange = {},
                    onUndo = {},
                    onRedo = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onReplacementChange = {},
                    onMatchCaseChange = {},
                    onNextMatch = {},
                    onPreviousMatch = {},
                    onReplaceCurrent = {},
                    onReplaceAll = {},
                    onInsertIndent = {},
                    onIndentStyleChange = {},
                    onToggleIntelligence = {},
                    onSelectSymbol = {},
                    onFormat = {},
                    onSave = {},
                    onDiff = {},
                    onCommit = {},
                )
            }
        }

        composeRule.onNodeWithText("Find")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Replace with")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Replace all")
            .assertIsDisplayed()
        composeRule.onNodeWithText("1 / 2")
            .assertIsDisplayed()
    }
    @Test
    fun displaysTreeSitterIntelligenceAndFormatter() {
        composeRule.setContent {
            NexoraGitTheme {
                MobileEditorContent(
                    state = MobileEditorUiState(
                        workspaceName = "Nexora-Git",
                        file = file,
                        value = TextFieldValue("fun main() {}"),
                        loading = false,
                        formatAvailable = true,
                        intelligenceVisible = true,
                        syntaxSnapshot = EditorSyntaxSnapshot(
                            engine = "tree-sitter",
                            language = "Kotlin",
                            rootType = "source_file",
                            hasErrors = false,
                            truncated = false,
                            spans = emptyList(),
                            symbols = listOf(
                                EditorSymbol(
                                    name = "main",
                                    kind = "function",
                                    start = 4,
                                    endExclusive = 8,
                                    line = 1,
                                ),
                            ),
                            diagnostics = emptyList(),
                        ),
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onValueChange = {},
                    onUndo = {},
                    onRedo = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onReplacementChange = {},
                    onMatchCaseChange = {},
                    onNextMatch = {},
                    onPreviousMatch = {},
                    onReplaceCurrent = {},
                    onReplaceAll = {},
                    onInsertIndent = {},
                    onIndentStyleChange = {},
                    onToggleIntelligence = {},
                    onSelectSymbol = {},
                    onFormat = {},
                    onSave = {},
                    onDiff = {},
                    onCommit = {},
                )
            }
        }

        composeRule.onNodeWithText("Language intelligence")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Tree-sitter · source_file")
            .assertIsDisplayed()
        composeRule.onNodeWithText("function · main · L1")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Format document")
            .assertIsDisplayed()
    }

}
