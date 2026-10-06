package com.nexora.git.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.editor.EditorDiffPreview
import com.nexora.git.core.editor.EditorFileStore
import com.nexora.git.core.editor.EditorFormatter
import com.nexora.git.core.editor.EditorHistory
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.editor.EditorRevision
import com.nexora.git.core.editor.EditorSearchMatch
import com.nexora.git.core.editor.EditorTextOperations
import com.nexora.git.core.editor.EditorSyntaxEngine
import com.nexora.git.core.editor.EditorSyntaxSnapshot
import com.nexora.git.core.editor.EditorSymbol
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.git.GitAuthor
import com.nexora.git.core.git.GitDiffMode
import com.nexora.git.core.git.GitEngine
import com.nexora.git.core.settings.SettingsRepository
import com.nexora.git.core.storage.WorkspaceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MobileEditorUiState(
    val workspaceName: String = "",
    val file: BrowserFile? = null,
    val value: TextFieldValue = TextFieldValue(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val committing: Boolean = false,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val gitAvailable: Boolean = false,
    val indentStyle: EditorIndentStyle = EditorIndentStyle.SPACES_4,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val replacement: String = "",
    val matchCase: Boolean = false,
    val searchMatches: List<EditorSearchMatch> = emptyList(),
    val activeMatchIndex: Int = -1,
    val syntaxSnapshot: EditorSyntaxSnapshot? = null,
    val syntaxLoading: Boolean = false,
    val intelligenceVisible: Boolean = false,
    val formatAvailable: Boolean = false,
    val formatting: Boolean = false,
    val diffPreview: EditorDiffPreview? = null,
    val gitDiffPatch: String? = null,
    val diffAdditions: Long = 0,
    val diffDeletions: Long = 0,
    val commitDialogVisible: Boolean = false,
    val otherStagedPaths: List<String> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class MobileEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspaceRegistry: WorkspaceRegistry,
    private val fileStore: EditorFileStore,
    private val gitEngine: GitEngine,
    private val textOperations: EditorTextOperations,
    private val settingsRepository: SettingsRepository,
    private val syntaxEngine: EditorSyntaxEngine,
    private val formatter: EditorFormatter,
) : ViewModel() {

    private val workspaceId =
        savedStateHandle.get<String>("workspaceId").orEmpty()
    private val relativePath =
        savedStateHandle.get<String>("path").orEmpty()

    private val history = EditorHistory()
    private var workspacePath = ""
    private var lastSavedText = ""
    private var expectedLastModified = 0L
    private var syntaxJob: Job? = null

    private val mutableState = MutableStateFlow(
        MobileEditorUiState(),
    )
    val state: StateFlow<MobileEditorUiState> =
        mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.editorIndentStyle.collect { style ->
                mutableState.update {
                    it.copy(indentStyle = style)
                }
            }
        }
        load()
    }

    fun onValueChange(value: TextFieldValue) {
        val current = state.value.value

        if (current.text != value.text) {
            history.record(current.toRevision())
        }

        mutableState.update {
            it.copy(
                value = value,
                dirty = value.text != lastSavedText,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
            ).withSearchResults()
        }
        scheduleSyntaxAnalysis()
    }

    fun undo() {
        val current = state.value.value
        val revision = history.undo(current.toRevision()) ?: return
        applyRevision(revision)
    }

    fun redo() {
        val current = state.value.value
        val revision = history.redo(current.toRevision()) ?: return
        applyRevision(revision)
    }

    fun insertIndent() {
        val current = state.value
        val revision = textOperations.insertIndent(
            revision = current.value.toRevision(),
            style = current.indentStyle,
        )

        history.record(current.value.toRevision())
        applyRevision(revision)
    }

    fun setIndentStyle(style: EditorIndentStyle) {
        viewModelScope.launch {
            settingsRepository.setEditorIndentStyle(style)
        }
    }

    fun toggleSearch() {
        mutableState.update {
            it.copy(
                searchVisible = !it.searchVisible,
                replacement = if (it.searchVisible) {
                    ""
                } else {
                    it.replacement
                },
            )
        }
    }

    fun setSearchQuery(query: String) {
        mutableState.update {
            it.copy(
                searchQuery = query,
                activeMatchIndex = if (query.isBlank()) -1 else 0,
            ).withSearchResults()
        }

        selectActiveMatch()
    }

    fun setReplacement(value: String) {
        mutableState.update {
            it.copy(replacement = value)
        }
    }

    fun setMatchCase(enabled: Boolean) {
        mutableState.update {
            it.copy(
                matchCase = enabled,
                activeMatchIndex = if (
                    it.searchQuery.isBlank()
                ) {
                    -1
                } else {
                    0
                },
            ).withSearchResults()
        }

        selectActiveMatch()
    }

    fun nextMatch() {
        val current = state.value
        if (current.searchMatches.isEmpty()) return

        mutableState.update {
            it.copy(
                activeMatchIndex =
                    (it.activeMatchIndex + 1)
                        .mod(it.searchMatches.size),
            )
        }
        selectActiveMatch()
    }

    fun previousMatch() {
        val current = state.value
        if (current.searchMatches.isEmpty()) return

        mutableState.update {
            val next = if (it.activeMatchIndex <= 0) {
                it.searchMatches.lastIndex
            } else {
                it.activeMatchIndex - 1
            }
            it.copy(activeMatchIndex = next)
        }
        selectActiveMatch()
    }

    fun replaceCurrent() {
        val current = state.value
        val match = current.searchMatches
            .getOrNull(current.activeMatchIndex)
            ?: return

        history.record(current.value.toRevision())

        val revision = textOperations.replace(
            text = current.value.text,
            match = match,
            replacement = current.replacement,
        )
        applyRevision(revision)

        mutableState.update {
            it.copy(activeMatchIndex = 0)
                .withSearchResults()
        }
        selectActiveMatch()
    }

    fun replaceAll() {
        val current = state.value
        if (current.searchQuery.isBlank()) return

        history.record(current.value.toRevision())

        val revision = textOperations.replaceAll(
            text = current.value.text,
            query = current.searchQuery,
            replacement = current.replacement,
            matchCase = current.matchCase,
        )

        applyRevision(revision)
        mutableState.update {
            it.copy(activeMatchIndex = -1)
                .withSearchResults()
        }
    }

    fun save() {
        viewModelScope.launch {
            saveInternal()
        }
    }

    fun formatDocument() {
        val current = state.value
        val file = current.file ?: return
        if (!current.formatAvailable || current.formatting) return

        viewModelScope.launch {
            mutableState.update {
                it.copy(formatting = true, errorMessage = null)
            }

            runCatching {
                formatter.format(
                    fileName = file.name,
                    language = file.language,
                    text = current.value.text,
                    indentStyle = current.indentStyle,
                    syntax = current.syntaxSnapshot,
                )
            }.onSuccess { result ->
                if (result.changed) {
                    history.record(current.value.toRevision())
                    applyRevision(
                        EditorRevision(
                            text = result.text,
                            selectionStart = current.value.selection.start
                                .coerceAtMost(result.text.length),
                            selectionEnd = current.value.selection.end
                                .coerceAtMost(result.text.length),
                        ),
                    )
                }
                mutableState.update {
                    it.copy(
                        formatting = false,
                        successMessage = if (result.changed) {
                            "Formatted with " + result.formatter + "."
                        } else {
                            "Already formatted."
                        },
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(formatting = false)
                }
                showError(error)
            }
        }
    }

    fun loadDiff() {
        viewModelScope.launch {
            val current = state.value
            val file = current.file ?: return@launch

            if (current.dirty) {
                val preview = textOperations.localDiff(
                    original = lastSavedText,
                    current = current.value.text,
                    relativePath = file.relativePath,
                )
                mutableState.update {
                    it.copy(
                        diffPreview = preview,
                        gitDiffPatch = null,
                        diffAdditions = preview.additions.toLong(),
                        diffDeletions = preview.deletions.toLong(),
                    )
                }
                return@launch
            }

            if (!current.gitAvailable) {
                mutableState.update {
                    it.copy(
                        diffPreview = EditorDiffPreview(
                            patch = "No unsaved changes.",
                            additions = 0,
                            deletions = 0,
                        ),
                        gitDiffPatch = null,
                        diffAdditions = 0,
                        diffDeletions = 0,
                    )
                }
                return@launch
            }

            runCatching {
                gitEngine.diff(
                    repositoryPath = workspacePath,
                    mode = GitDiffMode.ALL,
                    relativePath = file.relativePath,
                )
            }.onSuccess { diff ->
                mutableState.update {
                    it.copy(
                        diffPreview = null,
                        gitDiffPatch = diff.patch.ifBlank {
                            "No Git changes for this file."
                        },
                        diffAdditions = diff.insertions,
                        diffDeletions = diff.deletions,
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun dismissDiff() {
        mutableState.update {
            it.copy(
                diffPreview = null,
                gitDiffPatch = null,
                diffAdditions = 0,
                diffDeletions = 0,
            )
        }
    }

    fun prepareCommit() {
        viewModelScope.launch {
            val current = state.value
            val file = current.file ?: return@launch

            if (!current.gitAvailable) {
                showError(
                    IllegalStateException(
                        "This workspace is not a Git repository.",
                    ),
                )
                return@launch
            }

            if (current.dirty && !saveInternal(showSuccess = false)) {
                return@launch
            }

            runCatching {
                val status = gitEngine.status(workspacePath)
                val diff = gitEngine.diff(
                    repositoryPath = workspacePath,
                    mode = GitDiffMode.ALL,
                    relativePath = file.relativePath,
                )

                val otherStaged = status.entries
                    .asSequence()
                    .filter { entry ->
                        entry.staged &&
                            entry.path != file.relativePath
                    }
                    .map { it.path }
                    .distinct()
                    .sorted()
                    .toList()

                diff to otherStaged
            }.onSuccess { (diff, otherStaged) ->
                if (diff.patch.isBlank()) {
                    showError(
                        IllegalStateException(
                            "There are no Git changes for this file.",
                        ),
                    )
                    return@onSuccess
                }

                mutableState.update {
                    it.copy(
                        gitDiffPatch = diff.patch,
                        diffPreview = null,
                        diffAdditions = diff.insertions,
                        diffDeletions = diff.deletions,
                        otherStagedPaths = otherStaged,
                        commitDialogVisible = true,
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun cancelCommit() {
        mutableState.update {
            it.copy(
                commitDialogVisible = false,
                gitDiffPatch = null,
                otherStagedPaths = emptyList(),
            )
        }
    }

    fun commit(
        message: String,
        authorName: String,
        authorEmail: String,
    ) {
        val current = state.value
        val file = current.file ?: return

        if (message.isBlank()) {
            showError(
                IllegalArgumentException(
                    "Commit message is required.",
                ),
            )
            return
        }
        if (authorName.isBlank() || authorEmail.isBlank()) {
            showError(
                IllegalArgumentException(
                    "Commit author name and email are required.",
                ),
            )
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(committing = true, errorMessage = null)
            }

            runCatching {
                gitEngine.stage(
                    repositoryPath = workspacePath,
                    paths = listOf(file.relativePath),
                )

                gitEngine.commit(
                    repositoryPath = workspacePath,
                    message = message.trim(),
                    author = GitAuthor(
                        name = authorName.trim(),
                        email = authorEmail.trim(),
                    ),
                )
            }.onSuccess { commit ->
                history.clear()
                mutableState.update {
                    it.copy(
                        committing = false,
                        commitDialogVisible = false,
                        gitDiffPatch = null,
                        otherStagedPaths = emptyList(),
                        canUndo = false,
                        canRedo = false,
                        successMessage =
                            "Committed " + commit.oid.take(7) + ".",
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(committing = false)
                }
                showError(error)
            }
        }
    }

    fun dismissError() {
        mutableState.update {
            it.copy(errorMessage = null)
        }
    }

    fun dismissSuccess() {
        mutableState.update {
            it.copy(successMessage = null)
        }
    }

    fun toggleIntelligence() {
        mutableState.update {
            it.copy(intelligenceVisible = !it.intelligenceVisible)
        }
    }

    fun selectSymbol(symbol: EditorSymbol) {
        mutableState.update { current ->
            val start = symbol.start.coerceIn(
                0,
                current.value.text.length,
            )
            val end = symbol.endExclusive.coerceIn(
                start,
                current.value.text.length,
            )
            current.copy(
                value = current.value.copy(
                    selection = TextRange(start, end),
                ),
            )
        }
    }

    private fun load() {
        viewModelScope.launch {
            if (workspaceId.isBlank() || relativePath.isBlank()) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Editor path is missing.",
                    )
                }
                return@launch
            }

            runCatching {
                val workspace = workspaceRegistry.findById(workspaceId)
                    ?: error("Workspace not found")
                val file = fileStore.load(
                    workspaceId = workspaceId,
                    relativePath = relativePath,
                )

                Triple(
                    workspace,
                    file,
                    File(workspace.workspacePath, ".git").isDirectory,
                )
            }.onSuccess { (workspace, file, gitAvailable) ->
                workspacePath = workspace.workspacePath
                lastSavedText = file.text.orEmpty()
                expectedLastModified = file.lastModifiedEpochMillis
                history.clear()

                mutableState.update {
                    it.copy(
                        workspaceName = workspace.name,
                        file = file,
                        value = TextFieldValue(
                            text = lastSavedText,
                            selection = TextRange(0),
                        ),
                        loading = false,
                        dirty = false,
                        canUndo = false,
                        canRedo = false,
                        gitAvailable = gitAvailable,
                        formatAvailable = formatter.supports(
                            file.name,
                            file.language,
                        ),
                        errorMessage = null,
                    )
                }
                scheduleSyntaxAnalysis(immediate = true)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message
                            ?: "Unable to open file.",
                    )
                }
            }
        }
    }

    private suspend fun saveInternal(
        showSuccess: Boolean = true,
    ): Boolean {
        val current = state.value
        val file = current.file ?: return false

        if (!current.dirty) {
            return true
        }

        mutableState.update {
            it.copy(saving = true, errorMessage = null)
        }

        return runCatching {
            fileStore.save(
                workspaceId = workspaceId,
                relativePath = file.relativePath,
                text = current.value.text,
                expectedText = lastSavedText,
                expectedLastModifiedEpochMillis =
                    expectedLastModified,
            )
        }.fold(
            onSuccess = { saved ->
                lastSavedText = current.value.text
                expectedLastModified =
                    saved.lastModifiedEpochMillis

                mutableState.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        file = file.copy(
                            sizeBytes = saved.sizeBytes,
                            lastModifiedEpochMillis =
                                saved.lastModifiedEpochMillis,
                            text = lastSavedText,
                            lineCount = lineCount(lastSavedText),
                        ),
                        successMessage = if (showSuccess) {
                            "Saved."
                        } else {
                            null
                        },
                    )
                }
                true
            },
            onFailure = { error ->
                mutableState.update {
                    it.copy(saving = false)
                }
                showError(error)
                false
            },
        )
    }

    private fun applyRevision(revision: EditorRevision) {
        val start = revision.selectionStart.coerceIn(
            0,
            revision.text.length,
        )
        val end = revision.selectionEnd.coerceIn(
            0,
            revision.text.length,
        )

        mutableState.update {
            it.copy(
                value = TextFieldValue(
                    text = revision.text,
                    selection = TextRange(start, end),
                ),
                dirty = revision.text != lastSavedText,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
            ).withSearchResults()
        }
        scheduleSyntaxAnalysis()
    }

    private fun scheduleSyntaxAnalysis(
        immediate: Boolean = false,
    ) {
        syntaxJob?.cancel()

        val current = state.value
        val file = current.file ?: return
        if (!syntaxEngine.supports(file.name, file.language)) {
            mutableState.update {
                it.copy(
                    syntaxSnapshot = null,
                    syntaxLoading = false,
                )
            }
            return
        }

        val source = current.value.text
        syntaxJob = viewModelScope.launch {
            if (!immediate) {
                delay(SYNTAX_DEBOUNCE_MILLIS)
            }

            mutableState.update {
                it.copy(syntaxLoading = true)
            }

            runCatching {
                syntaxEngine.analyze(
                    fileName = file.name,
                    language = file.language,
                    source = source,
                )
            }.onSuccess { snapshot ->
                mutableState.update { latest ->
                    if (latest.value.text != source) {
                        latest
                    } else {
                        latest.copy(
                            syntaxSnapshot = snapshot,
                            syntaxLoading = false,
                        )
                    }
                }
            }.onFailure {
                mutableState.update { latest ->
                    if (latest.value.text != source) {
                        latest
                    } else {
                        latest.copy(
                            syntaxSnapshot = null,
                            syntaxLoading = false,
                        )
                    }
                }
            }
        }
    }

    private fun selectActiveMatch() {
        mutableState.update { current ->
            val match = current.searchMatches
                .getOrNull(current.activeMatchIndex)
                ?: return@update current

            current.copy(
                value = current.value.copy(
                    selection = TextRange(
                        match.start,
                        match.endExclusive,
                    ),
                ),
            )
        }
    }

    private fun MobileEditorUiState.withSearchResults():
        MobileEditorUiState {
        val matches = textOperations.findAll(
            text = value.text,
            query = searchQuery,
            matchCase = matchCase,
        )

        val index = when {
            matches.isEmpty() -> -1
            activeMatchIndex < 0 -> 0
            activeMatchIndex > matches.lastIndex -> 0
            else -> activeMatchIndex
        }

        return copy(
            searchMatches = matches,
            activeMatchIndex = index,
        )
    }

    private fun TextFieldValue.toRevision(): EditorRevision =
        EditorRevision(
            text = text,
            selectionStart = selection.start,
            selectionEnd = selection.end,
        )

    private fun lineCount(text: String): Int =
        if (text.isEmpty()) {
            0
        } else {
            text.count { it == '\n' } + 1
        }

    private companion object {
        const val SYNTAX_DEBOUNCE_MILLIS = 160L
    }

    private fun showError(error: Throwable) {
        mutableState.update {
            it.copy(
                errorMessage = error.message
                    ?: "Editor operation failed.",
            )
        }
    }
}
