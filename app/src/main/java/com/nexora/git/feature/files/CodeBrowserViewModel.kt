package com.nexora.git.feature.files

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.files.BrowserDirectory
import com.nexora.git.core.files.BrowserEntry
import com.nexora.git.core.files.BrowserFile
import com.nexora.git.core.files.CodeBrowserFileSystem
import com.nexora.git.core.files.CodeFileActionManager
import com.nexora.git.core.git.GitBlameHunk
import com.nexora.git.core.git.GitEngine
import com.nexora.git.core.git.GitHistoryEntry
import com.nexora.git.core.storage.WorkspaceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CodeBrowserTab {
    CODE,
    PREVIEW,
    HISTORY,
    BLAME,
}

data class CodeBrowserUiState(
    val workspaceName: String = "",
    val currentDirectory: String = "",
    val entries: List<BrowserEntry> = emptyList(),
    val selectedFile: BrowserFile? = null,
    val selectedTab: CodeBrowserTab = CodeBrowserTab.CODE,
    val gitAvailable: Boolean = false,
    val history: List<GitHistoryEntry> = emptyList(),
    val blame: List<GitBlameHunk> = emptyList(),
    val loading: Boolean = true,
    val historyLoading: Boolean = false,
    val blameLoading: Boolean = false,
    val historyError: String? = null,
    val blameError: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspaceRegistry: WorkspaceRegistry,
    private val fileSystem: CodeBrowserFileSystem,
    private val fileActionManager: CodeFileActionManager,
    private val gitEngine: GitEngine,
) : ViewModel() {

    private val workspaceId: String =
        savedStateHandle.get<String>("workspaceId").orEmpty()

    private var workspacePath: String = ""

    private val mutableState = MutableStateFlow(
        CodeBrowserUiState(),
    )
    val state: StateFlow<CodeBrowserUiState> =
        mutableState.asStateFlow()

    private val mutableShareEvents = MutableSharedFlow<Intent>(
        extraBufferCapacity = 1,
    )
    val shareEvents: SharedFlow<Intent> =
        mutableShareEvents.asSharedFlow()

    init {
        loadWorkspace()
    }

    fun open(entry: BrowserEntry) {
        if (entry.directory) {
            loadDirectory(entry.relativePath)
        } else {
            loadFile(entry.relativePath)
        }
    }

    fun navigateUp(): Boolean {
        val current = state.value

        if (current.selectedFile != null) {
            mutableState.update {
                it.copy(
                    selectedFile = null,
                    selectedTab = CodeBrowserTab.CODE,
                    history = emptyList(),
                    blame = emptyList(),
                    historyError = null,
                    blameError = null,
                )
            }
            return true
        }

        if (current.currentDirectory.isBlank()) {
            return false
        }

        val parent = current.currentDirectory
            .substringBeforeLast(
                delimiter = "/",
                missingDelimiterValue = "",
            )

        loadDirectory(parent)
        return true
    }

    fun openDirectory(relativePath: String) {
        loadDirectory(relativePath)
    }

    fun selectTab(tab: CodeBrowserTab) {
        mutableState.update {
            it.copy(selectedTab = tab)
        }

        when (tab) {
            CodeBrowserTab.HISTORY -> loadHistory()
            CodeBrowserTab.BLAME -> loadBlame()
            CodeBrowserTab.CODE,
            CodeBrowserTab.PREVIEW -> Unit
        }
    }

    fun refresh() {
        val selected = state.value.selectedFile
        if (selected != null) {
            loadFile(selected.relativePath)
        } else {
            loadDirectory(state.value.currentDirectory)
        }
    }

    fun shareSelected() {
        val file = state.value.selectedFile ?: return

        viewModelScope.launch {
            runCatching {
                fileActionManager.createShareIntent(
                    workspaceId = workspaceId,
                    relativePath = file.relativePath,
                    mimeType = file.mimeType,
                )
            }.onSuccess { intent ->
                mutableShareEvents.emit(intent)
            }.onFailure { error ->
                showError(
                    error.message ?: "Unable to share file.",
                )
            }
        }
    }

    fun exportSelected(destination: Uri) {
        val file = state.value.selectedFile ?: return

        viewModelScope.launch {
            runCatching {
                fileActionManager.exportTo(
                    workspaceId = workspaceId,
                    relativePath = file.relativePath,
                    destination = destination,
                )
            }.onSuccess {
                mutableState.update {
                    it.copy(
                        successMessage = "File saved.",
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                showError(
                    error.message ?: "Unable to save file.",
                )
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

    private fun loadWorkspace() {
        viewModelScope.launch {
            if (workspaceId.isBlank()) {
                showError("Workspace identifier is missing.")
                mutableState.update {
                    it.copy(loading = false)
                }
                return@launch
            }

            val workspace = workspaceRegistry.findById(workspaceId)
            if (workspace == null) {
                showError("Workspace not found.")
                mutableState.update {
                    it.copy(loading = false)
                }
                return@launch
            }

            workspacePath = workspace.workspacePath

            val gitAvailable = File(
                workspace.workspacePath,
                ".git",
            ).isDirectory

            mutableState.update {
                it.copy(
                    workspaceName = workspace.name,
                    gitAvailable = gitAvailable,
                )
            }

            loadDirectory("")
        }
    }

    private fun loadDirectory(relativePath: String) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                    selectedFile = null,
                    history = emptyList(),
                    blame = emptyList(),
                    historyError = null,
                    blameError = null,
                )
            }

            runCatching {
                fileSystem.list(
                    workspaceId = workspaceId,
                    relativePath = relativePath,
                )
            }.onSuccess(::applyDirectory)
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            errorMessage = error.message
                                ?: "Unable to read directory.",
                        )
                    }
                }
        }
    }

    private fun applyDirectory(directory: BrowserDirectory) {
        mutableState.update {
            it.copy(
                currentDirectory = directory.relativePath,
                entries = directory.entries,
                loading = false,
            )
        }
    }

    private fun loadFile(relativePath: String) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                    history = emptyList(),
                    blame = emptyList(),
                    historyError = null,
                    blameError = null,
                )
            }

            runCatching {
                fileSystem.read(
                    workspaceId = workspaceId,
                    relativePath = relativePath,
                )
            }.onSuccess { file ->
                mutableState.update {
                    it.copy(
                        selectedFile = file,
                        selectedTab = defaultTab(file),
                        loading = false,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message
                            ?: "Unable to read file.",
                    )
                }
            }
        }
    }

    private fun loadHistory() {
        val current = state.value
        val file = current.selectedFile ?: return

        if (!current.gitAvailable ||
            current.historyLoading ||
            current.history.isNotEmpty()
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    historyLoading = true,
                    historyError = null,
                )
            }

            runCatching {
                gitEngine.history(
                    repositoryPath = workspacePath,
                    relativePath = file.relativePath,
                    limit = 100,
                )
            }.onSuccess { history ->
                mutableState.update {
                    it.copy(
                        history = history,
                        historyLoading = false,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        historyLoading = false,
                        historyError = error.message
                            ?: "File history is unavailable.",
                    )
                }
            }
        }
    }

    private fun loadBlame() {
        val current = state.value
        val file = current.selectedFile ?: return

        if (!current.gitAvailable ||
            current.blameLoading ||
            current.blame.isNotEmpty()
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    blameLoading = true,
                    blameError = null,
                )
            }

            runCatching {
                gitEngine.blame(
                    repositoryPath = workspacePath,
                    relativePath = file.relativePath,
                )
            }.onSuccess { blame ->
                mutableState.update {
                    it.copy(
                        blame = blame,
                        blameLoading = false,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        blameLoading = false,
                        blameError = error.message
                            ?: "Blame is unavailable for this file.",
                    )
                }
            }
        }
    }

    private fun defaultTab(file: BrowserFile): CodeBrowserTab =
        when (file.kind) {
            com.nexora.git.core.files.BrowserFileKind.MARKDOWN,
            com.nexora.git.core.files.BrowserFileKind.IMAGE ->
                CodeBrowserTab.PREVIEW

            com.nexora.git.core.files.BrowserFileKind.TEXT,
            com.nexora.git.core.files.BrowserFileKind.BINARY ->
                CodeBrowserTab.CODE
        }

    private fun showError(message: String) {
        mutableState.update {
            it.copy(
                errorMessage = message,
                successMessage = null,
            )
        }
    }
}
