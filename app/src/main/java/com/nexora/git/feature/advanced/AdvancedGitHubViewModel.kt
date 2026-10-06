package com.nexora.git.feature.advanced

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.advanced.AdvancedDiscussionHub
import com.nexora.git.core.advanced.AdvancedGitHubGateway
import com.nexora.git.core.advanced.AdvancedProjectHub
import com.nexora.git.core.advanced.GitHubCodespaceSummary
import com.nexora.git.core.advanced.GitHubGistSummary
import com.nexora.git.core.advanced.GitHubPagesSite
import com.nexora.git.core.advanced.RepositorySecurityOverview
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdvancedSection<T>(
    val loading: Boolean = true,
    val value: T? = null,
    val errorMessage: String? = null,
)

data class AdvancedGitHubUiState(
    val discussions: AdvancedSection<AdvancedDiscussionHub> =
        AdvancedSection(),
    val projects: AdvancedSection<AdvancedProjectHub> =
        AdvancedSection(),
    val pages: AdvancedSection<GitHubPagesSite> =
        AdvancedSection(),
    val security: AdvancedSection<RepositorySecurityOverview> =
        AdvancedSection(),
    val gists: AdvancedSection<List<GitHubGistSummary>> =
        AdvancedSection(),
    val codespaces: AdvancedSection<List<GitHubCodespaceSummary>> =
        AdvancedSection(),
    val operationInProgress: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdvancedGitHubViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gateway: AdvancedGitHubGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()

    private val mutableState =
        MutableStateFlow(AdvancedGitHubUiState())
    val state: StateFlow<AdvancedGitHubUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshDiscussions()
            refreshProjects()
            refreshPages()
            refreshSecurity()
            refreshGists()
            refreshCodespaces()
        }
    }

    fun createDiscussion(
        categoryId: String,
        title: String,
        body: String,
    ) {
        runOperation {
            when (
                val result = gateway.createDiscussion(
                    owner = owner,
                    repository = repository,
                    categoryId = categoryId,
                    title = title,
                    body = body,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    mutableState.update { current ->
                        val hub = current.discussions.value
                        current.copy(
                            discussions =
                                if (hub == null) {
                                    current.discussions
                                } else {
                                    current.discussions.copy(
                                        value = hub.copy(
                                            discussions =
                                                listOf(result.value) +
                                                    hub.discussions
                                                        .filterNot {
                                                            it.id ==
                                                                result.value.id
                                                        },
                                        ),
                                    )
                                },
                        )
                    }
                    succeed("Discussion created.")
                }
            }
        }
    }

    fun createProject(title: String) {
        runOperation {
            when (
                val result = gateway.createProject(
                    owner = owner,
                    repository = repository,
                    title = title,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    mutableState.update { current ->
                        val hub = current.projects.value
                        current.copy(
                            projects =
                                if (hub == null) {
                                    current.projects
                                } else {
                                    current.projects.copy(
                                        value = hub.copy(
                                            projects =
                                                listOf(result.value) +
                                                    hub.projects
                                                        .filterNot {
                                                            it.id ==
                                                                result.value.id
                                                        },
                                        ),
                                    )
                                },
                        )
                    }
                    succeed("Project created.")
                }
            }
        }
    }

    fun enablePages() {
        runOperation {
            when (
                val result = gateway.enablePages(
                    owner,
                    repository,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            pages = AdvancedSection(
                                loading = false,
                                value = result.value,
                            ),
                        )
                    }
                    succeed("GitHub Pages enabled.")
                }
            }
        }
    }

    fun requestPagesBuild() {
        runOperation {
            when (
                val result = gateway.requestPagesBuild(
                    owner,
                    repository,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    succeed("GitHub Pages build requested.")
                    refreshPages()
                }
            }
        }
    }

    fun createGist(
        fileName: String,
        content: String,
        description: String,
        publicGist: Boolean,
    ) {
        runOperation {
            when (
                val result = gateway.createGist(
                    fileName = fileName,
                    content = content,
                    description = description,
                    publicGist = publicGist,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    mutableState.update { current ->
                        val existing =
                            current.gists.value.orEmpty()
                        current.copy(
                            gists = AdvancedSection(
                                loading = false,
                                value =
                                    listOf(result.value) +
                                        existing.filterNot {
                                            it.id == result.value.id
                                        },
                            ),
                        )
                    }
                    succeed("Gist created.")
                }
            }
        }
    }

    fun deleteGist(id: String) {
        runOperation {
            when (val result = gateway.deleteGist(id)) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            gists = current.gists.copy(
                                value =
                                    current.gists.value
                                        .orEmpty()
                                        .filterNot {
                                            it.id == id
                                        },
                            ),
                        )
                    }
                    succeed("Gist deleted.")
                }
            }
        }
    }

    fun createCodespace() {
        runOperation {
            when (
                val result = gateway.createCodespace(
                    owner,
                    repository,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    upsertCodespace(result.value)
                    succeed("Codespace creation requested.")
                }
            }
        }
    }

    fun setCodespaceRunning(
        name: String,
        running: Boolean,
    ) {
        runOperation {
            when (
                val result = gateway.setCodespaceRunning(
                    name,
                    running,
                )
            ) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    upsertCodespace(result.value)
                    succeed(
                        if (running) {
                            "Codespace start requested."
                        } else {
                            "Codespace stop requested."
                        },
                    )
                }
            }
        }
    }

    fun deleteCodespace(name: String) {
        runOperation {
            when (val result = gateway.deleteCodespace(name)) {
                is AppResult.Failure ->
                    fail(result.error.toAdvancedMessage())

                is AppResult.Success -> {
                    mutableState.update { current ->
                        current.copy(
                            codespaces =
                                current.codespaces.copy(
                                    value =
                                        current.codespaces.value
                                            .orEmpty()
                                            .filterNot {
                                                it.name == name
                                            },
                                ),
                        )
                    }
                    succeed("Codespace deleted.")
                }
            }
        }
    }

    fun dismissMessages() {
        mutableState.update {
            it.copy(
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private suspend fun refreshDiscussions() {
        mutableState.update {
            it.copy(
                discussions =
                    it.discussions.copy(
                        loading = true,
                        errorMessage = null,
                    ),
            )
        }
        when (
            val result =
                gateway.listDiscussions(owner, repository)
        ) {
            is AppResult.Failure ->
                mutableState.update {
                    it.copy(
                        discussions = AdvancedSection(
                            loading = false,
                            value = it.discussions.value,
                            errorMessage =
                                result.error.toAdvancedMessage(),
                        ),
                    )
                }

            is AppResult.Success ->
                mutableState.update {
                    it.copy(
                        discussions = AdvancedSection(
                            loading = false,
                            value = result.value,
                        ),
                    )
                }
        }
    }

    private suspend fun refreshProjects() {
        mutableState.update {
            it.copy(
                projects =
                    it.projects.copy(
                        loading = true,
                        errorMessage = null,
                    ),
            )
        }
        when (
            val result =
                gateway.listProjects(owner, repository)
        ) {
            is AppResult.Failure ->
                mutableState.update {
                    it.copy(
                        projects = AdvancedSection(
                            loading = false,
                            value = it.projects.value,
                            errorMessage =
                                result.error.toAdvancedMessage(),
                        ),
                    )
                }

            is AppResult.Success ->
                mutableState.update {
                    it.copy(
                        projects = AdvancedSection(
                            loading = false,
                            value = result.value,
                        ),
                    )
                }
        }
    }

    private suspend fun refreshPages() {
        mutableState.update {
            it.copy(
                pages =
                    it.pages.copy(
                        loading = true,
                        errorMessage = null,
                    ),
            )
        }
        when (
            val result =
                gateway.getPages(owner, repository)
        ) {
            is AppResult.Failure ->
                mutableState.update {
                    it.copy(
                        pages = AdvancedSection(
                            loading = false,
                            value = it.pages.value,
                            errorMessage =
                                result.error.toAdvancedMessage(),
                        ),
                    )
                }

            is AppResult.Success ->
                mutableState.update {
                    it.copy(
                        pages = AdvancedSection(
                            loading = false,
                            value = result.value,
                        ),
                    )
                }
        }
    }

    private suspend fun refreshSecurity() {
        mutableState.update {
            it.copy(
                security =
                    it.security.copy(
                        loading = true,
                        errorMessage = null,
                    ),
            )
        }
        when (
            val result =
                gateway.getSecurityOverview(owner, repository)
        ) {
            is AppResult.Failure ->
                mutableState.update {
                    it.copy(
                        security = AdvancedSection(
                            loading = false,
                            value = it.security.value,
                            errorMessage =
                                result.error.toAdvancedMessage(),
                        ),
                    )
                }

            is AppResult.Success ->
                mutableState.update {
                    it.copy(
                        security = AdvancedSection(
                            loading = false,
                            value = result.value,
                        ),
                    )
                }
        }
    }

    private suspend fun refreshGists() {
        mutableState.update {
            it.copy(
                gists =
                    it.gists.copy(
                        loading = true,
                        errorMessage = null,
                    ),
            )
        }
        when (val result = gateway.listGists()) {
            is AppResult.Failure ->
                mutableState.update {
                    it.copy(
                        gists = AdvancedSection(
                            loading = false,
                            value = it.gists.value,
                            errorMessage =
                                result.error.toAdvancedMessage(),
                        ),
                    )
                }

            is AppResult.Success ->
                mutableState.update {
                    it.copy(
                        gists = AdvancedSection(
                            loading = false,
                            value = result.value,
                        ),
                    )
                }
        }
    }

    private suspend fun refreshCodespaces() {
        mutableState.update {
            it.copy(
                codespaces =
                    it.codespaces.copy(
                        loading = true,
                        errorMessage = null,
                    ),
            )
        }
        when (val result = gateway.listCodespaces()) {
            is AppResult.Failure ->
                mutableState.update {
                    it.copy(
                        codespaces = AdvancedSection(
                            loading = false,
                            value = it.codespaces.value,
                            errorMessage =
                                result.error.toAdvancedMessage(),
                        ),
                    )
                }

            is AppResult.Success ->
                mutableState.update {
                    it.copy(
                        codespaces = AdvancedSection(
                            loading = false,
                            value = result.value,
                        ),
                    )
                }
        }
    }

    private fun upsertCodespace(
        codespace: GitHubCodespaceSummary,
    ) {
        mutableState.update { current ->
            current.copy(
                codespaces = AdvancedSection(
                    loading = false,
                    value =
                        listOf(codespace) +
                            current.codespaces.value
                                .orEmpty()
                                .filterNot {
                                    it.name == codespace.name
                                },
                ),
            )
        }
    }

    private fun runOperation(
        block: suspend () -> Unit,
    ) {
        if (state.value.operationInProgress) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    successMessage = null,
                    errorMessage = null,
                )
            }
            try {
                block()
            } finally {
                mutableState.update {
                    it.copy(operationInProgress = false)
                }
            }
        }
    }

    private fun succeed(message: String) {
        mutableState.update {
            it.copy(
                successMessage = message,
                errorMessage = null,
            )
        }
    }

    private fun fail(message: String) {
        mutableState.update {
            it.copy(
                errorMessage = message,
                successMessage = null,
            )
        }
    }

    private fun AppError.toAdvancedMessage(): String =
        when (this) {
            is AppError.Authentication ->
                message ?: "GitHub authentication is required."
            is AppError.PermissionDenied ->
                message
                    ?: "The GitHub App lacks the required permission."
            is AppError.RateLimited ->
                message ?: "GitHub rate limit reached."
            is AppError.NotFound ->
                message ?: "This GitHub capability is unavailable."
            is AppError.Network ->
                message ?: "Unable to reach GitHub."
            is AppError.Server ->
                message ?: "GitHub returned a server error."
            is AppError.Conflict ->
                message ?: "GitHub rejected the operation because of a conflict."
            is AppError.Validation ->
                message ?: "GitHub rejected the request."
            is AppError.Parsing ->
                message ?: "GitHub returned an unreadable response."
            is AppError.Unknown ->
                "Unexpected GitHub error."
            AppError.StoragePermission ->
                "Local storage permission is required."
            AppError.DiskFull ->
                "The device does not have enough free storage."
            AppError.GitConflict ->
                "A local Git conflict requires attention."
        }
}
