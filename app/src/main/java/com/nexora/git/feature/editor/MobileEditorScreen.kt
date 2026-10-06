package com.nexora.git.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.auth.AuthAccountSummary
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.editor.EditorSymbol

@Composable
fun MobileEditorScreen(
    contentPadding: PaddingValues,
    activeAccount: AuthAccountSummary,
    onBack: () -> Unit,
    viewModel: MobileEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var discardDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var commitMessage by rememberSaveable {
        mutableStateOf("")
    }
    var authorName by rememberSaveable(activeAccount.accountId) {
        mutableStateOf(
            activeAccount.name
                ?.takeIf(String::isNotBlank)
                ?: activeAccount.login,
        )
    }
    var authorEmail by rememberSaveable(activeAccount.accountId) {
        mutableStateOf(
            activeAccount.accountId.toString() +
                "+" +
                activeAccount.login +
                "@users.noreply.github.com",
        )
    }

    LaunchedEffect(state.commitDialogVisible) {
        if (state.commitDialogVisible) {
            commitMessage = ""
        }
    }

    fun requestBack() {
        if (state.dirty) {
            discardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::requestBack)

    MobileEditorContent(
        state = state,
        contentPadding = contentPadding,
        onBack = ::requestBack,
        onValueChange = viewModel::onValueChange,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onToggleSearch = viewModel::toggleSearch,
        onSearchQueryChange = viewModel::setSearchQuery,
        onReplacementChange = viewModel::setReplacement,
        onMatchCaseChange = viewModel::setMatchCase,
        onNextMatch = viewModel::nextMatch,
        onPreviousMatch = viewModel::previousMatch,
        onReplaceCurrent = viewModel::replaceCurrent,
        onReplaceAll = viewModel::replaceAll,
        onInsertIndent = viewModel::insertIndent,
        onIndentStyleChange = viewModel::setIndentStyle,
        onToggleIntelligence = viewModel::toggleIntelligence,
        onSelectSymbol = viewModel::selectSymbol,
        onFormat = viewModel::formatDocument,
        onSave = viewModel::save,
        onDiff = viewModel::loadDiff,
        onCommit = viewModel::prepareCommit,
    )

    if (discardDialog) {
        AlertDialog(
            onDismissRequest = {
                discardDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialog = false
                        onBack()
                    },
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        discardDialog = false
                    },
                ) {
                    Text("Keep editing")
                }
            },
            title = {
                Text("Discard unsaved changes?")
            },
            text = {
                Text(
                    "The file has changes that have not been saved.",
                )
            },
        )
    }

    val patch = state.diffPreview?.patch ?: state.gitDiffPatch
    if (
        patch != null &&
        !state.commitDialogVisible
    ) {
        DiffDialog(
            patch = patch,
            additions = state.diffAdditions,
            deletions = state.diffDeletions,
            onDismiss = viewModel::dismissDiff,
        )
    }

    if (state.commitDialogVisible) {
        CommitDialog(
            patch = state.gitDiffPatch.orEmpty(),
            additions = state.diffAdditions,
            deletions = state.diffDeletions,
            otherStagedPaths = state.otherStagedPaths,
            committing = state.committing,
            message = commitMessage,
            authorName = authorName,
            authorEmail = authorEmail,
            onMessageChange = {
                commitMessage = it
            },
            onAuthorNameChange = {
                authorName = it
            },
            onAuthorEmailChange = {
                authorEmail = it
            },
            onDismiss = viewModel::cancelCommit,
            onCommit = {
                viewModel.commit(
                    message = commitMessage,
                    authorName = authorName,
                    authorEmail = authorEmail,
                )
            },
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("OK")
                }
            },
            title = {
                Text("Editor")
            },
            text = {
                Text(message)
            },
        )
    }

    state.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccess,
            confirmButton = {
                TextButton(onClick = viewModel::dismissSuccess) {
                    Text("Done")
                }
            },
            title = {
                Text("Nexora Git")
            },
            text = {
                Text(message)
            },
        )
    }
}

@Composable
internal fun MobileEditorContent(
    state: MobileEditorUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onMatchCaseChange: (Boolean) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onInsertIndent: () -> Unit,
    onIndentStyleChange: (EditorIndentStyle) -> Unit,
    onToggleIntelligence: () -> Unit,
    onSelectSymbol: (EditorSymbol) -> Unit,
    onFormat: () -> Unit,
    onSave: () -> Unit,
    onDiff: () -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(MaterialTheme.colorScheme.background),
    ) {
        EditorHeader(
            state = state,
            onBack = onBack,
            onSave = onSave,
        )

        EditorToolbar(
            state = state,
            onUndo = onUndo,
            onRedo = onRedo,
            onToggleSearch = onToggleSearch,
            onInsertIndent = onInsertIndent,
            onIndentStyleChange = onIndentStyleChange,
            onToggleIntelligence = onToggleIntelligence,
            onFormat = onFormat,
            onDiff = onDiff,
            onCommit = onCommit,
        )

        if (state.intelligenceVisible) {
            IntelligencePanel(
                state = state,
                onSelectSymbol = onSelectSymbol,
            )
        }

        if (state.searchVisible) {
            SearchReplaceBar(
                state = state,
                onSearchQueryChange = onSearchQueryChange,
                onReplacementChange = onReplacementChange,
                onMatchCaseChange = onMatchCaseChange,
                onNextMatch = onNextMatch,
                onPreviousMatch = onPreviousMatch,
                onReplaceCurrent = onReplaceCurrent,
                onReplaceAll = onReplaceAll,
            )
        }

        when {
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.file != null -> {
                EditorSurface(
                    state = state,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                )

                EditorStatusBar(state)
            }
        }
    }
}

@Composable
private fun EditorHeader(
    state: MobileEditorUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
            )
        }

        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = state.file?.name ?: "Editor",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = state.file?.relativePath
                    ?: state.workspaceName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (state.dirty) {
            Text(
                text = "Modified",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        TextButton(
            enabled = state.dirty &&
                !state.saving &&
                !state.loading,
            onClick = onSave,
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = null,
                )
            }
            Text("Save")
        }
    }

    HorizontalDivider()
}

@Composable
private fun EditorToolbar(
    state: MobileEditorUiState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleSearch: () -> Unit,
    onInsertIndent: () -> Unit,
    onIndentStyleChange: (EditorIndentStyle) -> Unit,
    onToggleIntelligence: () -> Unit,
    onFormat: () -> Unit,
    onDiff: () -> Unit,
    onCommit: () -> Unit,
) {
    var indentMenu by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            enabled = state.canUndo,
            onClick = onUndo,
        ) {
            Icon(
                imageVector = Icons.Outlined.Undo,
                contentDescription = "Undo",
            )
        }

        IconButton(
            enabled = state.canRedo,
            onClick = onRedo,
        ) {
            Icon(
                imageVector = Icons.Outlined.Redo,
                contentDescription = "Redo",
            )
        }

        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search and replace",
            )
        }

        IconButton(onClick = onInsertIndent) {
            Icon(
                imageVector = Icons.Outlined.FormatIndentIncrease,
                contentDescription = "Insert indentation",
            )
        }

        Box {
            TextButton(
                onClick = {
                    indentMenu = true
                },
            ) {
                Text(state.indentStyle.label)
            }

            DropdownMenu(
                expanded = indentMenu,
                onDismissRequest = {
                    indentMenu = false
                },
            ) {
                EditorIndentStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = {
                            Text(style.label)
                        },
                        leadingIcon = if (
                            style == state.indentStyle
                        ) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            indentMenu = false
                            onIndentStyleChange(style)
                        },
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleIntelligence,
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = "Language intelligence",
            )
        }

        IconButton(
            enabled = state.formatAvailable && !state.formatting,
            onClick = onFormat,
        ) {
            if (state.formatting) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.FormatAlignLeft,
                    contentDescription = "Format document",
                )
            }
        }

        IconButton(onClick = onDiff) {
            Icon(
                imageVector = Icons.Outlined.Difference,
                contentDescription = "View diff",
            )
        }

        Button(
            enabled = state.gitAvailable &&
                !state.committing &&
                !state.saving,
            onClick = onCommit,
        ) {
            Text("Commit")
        }
    }

    HorizontalDivider()
}


@Composable
private fun IntelligencePanel(
    state: MobileEditorUiState,
    onSelectSymbol: (EditorSymbol) -> Unit,
) {
    val snapshot = state.syntaxSnapshot

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 210.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Language intelligence",
                    style = MaterialTheme.typography.titleSmall,
                )
                when {
                    state.syntaxLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    snapshot != null -> {
                        Text(
                            "Tree-sitter · " + snapshot.rootType,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            "Regex fallback",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (snapshot == null && !state.syntaxLoading) {
                Text(
                    "No bundled Tree-sitter grammar is available for this file. Syntax highlighting continues with the local fallback.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (snapshot != null) {
                if (snapshot.diagnostics.isNotEmpty()) {
                    Text(
                        snapshot.diagnostics.size.toString() +
                            " syntax diagnostic(s)",
                        color = MaterialTheme.colorScheme.error,
                    )
                    snapshot.diagnostics.take(4).forEach { diagnostic ->
                        Text(
                            "L" + diagnostic.line +
                                ":" + diagnostic.column +
                                " · " + diagnostic.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        "No syntax errors detected.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (snapshot.symbols.isNotEmpty()) {
                    Text(
                        "Symbols",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    snapshot.symbols.take(12).forEach { symbol ->
                        TextButton(
                            onClick = {
                                onSelectSymbol(symbol)
                            },
                        ) {
                            Text(
                                symbol.kind + " · " +
                                    symbol.name +
                                    " · L" +
                                    symbol.line,
                            )
                        }
                    }
                }

                if (snapshot.truncated) {
                    Text(
                        "Analysis was capped to protect mobile performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchReplaceBar(
    state: MobileEditorUiState,
    onSearchQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onMatchCaseChange: (Boolean) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 10.dp,
                vertical = 8.dp,
            ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    label = {
                        Text("Find")
                    },
                )

                Text(
                    text = if (state.searchMatches.isEmpty()) {
                        "0 / 0"
                    } else {
                        (state.activeMatchIndex + 1).toString() +
                            " / " +
                            state.searchMatches.size.toString()
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = state.searchMatches.isNotEmpty(),
                    onClick = onPreviousMatch,
                ) {
                    Text("Previous")
                }
                TextButton(
                    enabled = state.searchMatches.isNotEmpty(),
                    onClick = onNextMatch,
                ) {
                    Text("Next")
                }
                FilterChip(
                    selected = state.matchCase,
                    onClick = {
                        onMatchCaseChange(!state.matchCase)
                    },
                    label = {
                        Text("Match case")
                    },
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.replacement,
                onValueChange = onReplacementChange,
                singleLine = true,
                label = {
                    Text("Replace with")
                },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = state.searchMatches.isNotEmpty(),
                    onClick = onReplaceCurrent,
                ) {
                    Text("Replace")
                }
                TextButton(
                    enabled = state.searchMatches.isNotEmpty(),
                    onClick = onReplaceAll,
                ) {
                    Text("Replace all")
                }
            }
        }
    }
}

@Composable
private fun EditorSurface(
    state: MobileEditorUiState,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = requireNotNull(state.file)
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val lineCount = remember(state.value.text) {
        if (state.value.text.isEmpty()) {
            1
        } else {
            state.value.text.count { it == '\n' } + 1
        }
    }
    val lineNumbers = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    val scheme = MaterialTheme.colorScheme
    val transformation = remember(
        file.language,
        scheme,
        state.searchMatches,
        state.activeMatchIndex,
        state.syntaxSnapshot,
    ) {
        EditorSyntaxTransformation(
            language = file.language,
            keywordColor = scheme.primary,
            stringColor = scheme.tertiary,
            commentColor = scheme.onSurfaceVariant,
            numberColor = scheme.secondary,
            matchColor = scheme.secondaryContainer,
            activeMatchColor = scheme.tertiaryContainer,
            syntaxSpans = state.syntaxSnapshot?.spans.orEmpty(),
            matches = state.searchMatches,
            activeMatchIndex = state.activeMatchIndex,
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                end = 10.dp,
                bottom = 8.dp,
            ),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline,
        ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vertical),
        ) {
            Text(
                text = lineNumbers,
                modifier = Modifier
                    .width(54.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 12.dp,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )

            BasicTextField(
                value = state.value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontal)
                    .padding(
                        horizontal = 12.dp,
                        vertical = 12.dp,
                    ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(
                    MaterialTheme.colorScheme.primary,
                ),
                visualTransformation = transformation,
            )
        }
    }
}

@Composable
private fun EditorStatusBar(
    state: MobileEditorUiState,
) {
    val cursor = state.value.selection.end
        .coerceIn(0, state.value.text.length)
    val before = state.value.text.substring(0, cursor)
    val line = before.count { it == '\n' } + 1
    val column = cursor -
        before.lastIndexOf('\n').let {
            if (it < 0) -1 else it
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp,
                vertical = 7.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildString {
                append(state.file?.language ?: "Plain text")
                state.syntaxSnapshot?.let {
                    append(" · Tree-sitter")
                    if (it.diagnostics.isNotEmpty()) {
                        append(" · ")
                        append(it.diagnostics.size)
                        append(" issue")
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Ln " + line + ", Col " + column,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.indentStyle.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (state.dirty) {
                    "Modified"
                } else {
                    "Saved"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.dirty) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DiffDialog(
    patch: String,
    additions: Long,
    deletions: Long,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                "Diff  +" + additions + "  -" + deletions,
            )
        },
        text = {
            Text(
                text = patch,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

@Composable
private fun CommitDialog(
    patch: String,
    additions: Long,
    deletions: Long,
    otherStagedPaths: List<String>,
    committing: Boolean,
    message: String,
    authorName: String,
    authorEmail: String,
    onMessageChange: (String) -> Unit,
    onAuthorNameChange: (String) -> Unit,
    onAuthorEmailChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!committing) {
                onDismiss()
            }
        },
        confirmButton = {
            TextButton(
                enabled = !committing &&
                    message.isNotBlank() &&
                    authorName.isNotBlank() &&
                    authorEmail.isNotBlank(),
                onClick = onCommit,
            ) {
                if (committing) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Commit")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !committing,
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                "Commit changes  +" +
                    additions +
                    "  -" +
                    deletions,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (otherStagedPaths.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Other staged files will also be committed.",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            otherStagedPaths.take(5).forEach { path ->
                                Text(
                                    text = path,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            if (otherStagedPaths.size > 5) {
                                Text(
                                    text = "+" +
                                        (otherStagedPaths.size - 5) +
                                        " more",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = message,
                    onValueChange = onMessageChange,
                    label = {
                        Text("Commit message")
                    },
                    minLines = 2,
                    maxLines = 4,
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = authorName,
                    onValueChange = onAuthorNameChange,
                    label = {
                        Text("Author name")
                    },
                    singleLine = true,
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = authorEmail,
                    onValueChange = onAuthorEmailChange,
                    label = {
                        Text("Author email")
                    },
                    singleLine = true,
                )

                Text(
                    text = "File diff",
                    style = MaterialTheme.typography.titleSmall,
                )

                Text(
                    text = patch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
