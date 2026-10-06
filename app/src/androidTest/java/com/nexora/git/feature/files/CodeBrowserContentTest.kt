package com.nexora.git.feature.files

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nexora.git.core.files.BrowserEntry
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.files.BrowserFileKind
import com.nexora.git.core.search.ProjectSearchMatch
import com.nexora.git.ui.theme.NexoraGitTheme
import org.junit.Rule
import org.junit.Test

class CodeBrowserContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysDirectoryAndFileActions() {
        composeRule.setContent {
            NexoraGitTheme {
                CodeBrowserContent(
                    state = CodeBrowserUiState(
                        workspaceName = "Sample",
                        currentDirectory = "src",
                        entries = listOf(
                            BrowserEntry(
                                name = "main",
                                relativePath = "src/main",
                                directory = true,
                                sizeBytes = 0,
                                lastModifiedEpochMillis = 0,
                                kind = null,
                                language = null,
                            ),
                            BrowserEntry(
                                name = "Main.kt",
                                relativePath = "src/Main.kt",
                                directory = false,
                                sizeBytes = 42,
                                lastModifiedEpochMillis = 0,
                                kind = BrowserFileKind.TEXT,
                                language = "Kotlin",
                            ),
                        ),
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onSearchRegexChange = {},
                    onSearchMatchCaseChange = {},
                    onSearchWholeWordChange = {},
                    onSearchIncludeGlobChange = {},
                    onSearchExcludeGlobChange = {},
                    onSearch = {},
                    onOpenSearchResult = {},
                    onOpenEntry = {},
                    onOpenDirectory = {},
                    onSelectTab = {},
                    onShare = {},
                    onEdit = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("root")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Main.kt")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Kotlin · 42 B")
            .assertIsDisplayed()
    }

    @Test
    fun displaysCodeHistoryAndBlameTabsForGitTextFile() {
        composeRule.setContent {
            NexoraGitTheme {
                CodeBrowserContent(
                    state = CodeBrowserUiState(
                        workspaceName = "Sample",
                        selectedFile = BrowserFile(
                            name = "Main.kt",
                            relativePath = "src/Main.kt",
                            absolutePath = "/tmp/Main.kt",
                            sizeBytes = 12,
                            lastModifiedEpochMillis = 0,
                            mimeType = "text/plain",
                            kind = BrowserFileKind.TEXT,
                            language = "Kotlin",
                            text = "fun main() {}",
                            truncated = false,
                            lineCount = 1,
                        ),
                        selectedTab = CodeBrowserTab.CODE,
                        gitAvailable = true,
                        loading = false,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onSearchRegexChange = {},
                    onSearchMatchCaseChange = {},
                    onSearchWholeWordChange = {},
                    onSearchIncludeGlobChange = {},
                    onSearchExcludeGlobChange = {},
                    onSearch = {},
                    onOpenSearchResult = {},
                    onOpenEntry = {},
                    onOpenDirectory = {},
                    onSelectTab = {},
                    onShare = {},
                    onEdit = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("Code")
            .assertIsDisplayed()
        composeRule.onNodeWithText("History")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Blame")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit file")
            .assertIsDisplayed()
    }
    @Test
    fun displaysAdvancedProjectSearch() {
        composeRule.setContent {
            NexoraGitTheme {
                CodeBrowserContent(
                    state = CodeBrowserUiState(
                        workspaceName = "Sample",
                        loading = false,
                        searchVisible = true,
                        projectSearchQuery = "Nexora",
                        projectSearchMatches = listOf(
                            ProjectSearchMatch(
                                path = "src/Main.kt",
                                line = 4,
                                column = 9,
                                preview = "println(\"Nexora\")",
                                start = 30,
                                endExclusive = 36,
                            ),
                        ),
                        projectSearchFilesScanned = 8,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    onBack = {},
                    onRefresh = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onSearchRegexChange = {},
                    onSearchMatchCaseChange = {},
                    onSearchWholeWordChange = {},
                    onSearchIncludeGlobChange = {},
                    onSearchExcludeGlobChange = {},
                    onSearch = {},
                    onOpenSearchResult = {},
                    onOpenEntry = {},
                    onOpenDirectory = {},
                    onSelectTab = {},
                    onShare = {},
                    onEdit = {},
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("Search entire project")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Regex")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Whole word")
            .assertIsDisplayed()
        composeRule.onNodeWithText("src/Main.kt:4:9")
            .assertIsDisplayed()
    }

}
