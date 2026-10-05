package com.nexora.git.feature.repositories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.repository.ForkRepositoryRequest
import com.nexora.git.core.repository.RepositoryDetails
import com.nexora.git.core.repository.RepositoryGateway
import com.nexora.git.core.repository.RepositorySubscriptionState
import com.nexora.git.core.repository.RepositoryViewerState
import com.nexora.git.core.repository.RepositoryWorkspaceCoordinator
import com.nexora.git.core.repository.UpdateRepositoryRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepositoryDetailUiState(
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val details: RepositoryDetails? = null,
    val viewerState: RepositoryViewerState? = null,
    val viewerCapabilitiesUnavailable: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class RepositoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositoryGateway: RepositoryGateway,
    private val workspaceCoordinator: RepositoryWorkspaceCoordinator,
) : ViewModel() {

    private val owner: String =
        savedStateHandle.get<String>("owner").orEmpty()
    private val name: String =
        savedStateHandle.get<String>("name").orEmpty()

    private val mutableState = MutableStateFlow(
        RepositoryDetailUiState(),
    )
    val state: StateFlow<RepositoryDetailUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            when (
                val result = repositoryGateway.getRepository(
                    owner = owner,
                    name = name,
                )
            ) {
                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            loading = false,
                            errorMessage =
                                result.error.toRepositoryMessage(),
                        )
                    }
                }

                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            loading = false,
                            details = result.value,
                        )
                    }

                    refreshViewerState()
                }
            }
        }
    }

    fun toggleStar() {
        val details = state.value.details ?: return
        val viewer = state.value.viewerState ?: return
        val target = !viewer.starred

        runOperation {
            when (
                val result = repositoryGateway.setStarred(
                    owner = details.summary.ownerLogin,
                    name = details.summary.name,
                    starred = target,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            viewerState = viewer.copy(
                                starred = target,
                            ),
                        )
                    }
                    success(
                        if (target) {
                            "Repository starred."
                        } else {
                            "Repository unstarred."
                        },
                    )
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
            }
        }
    }

    fun toggleWatch() {
        val details = state.value.details ?: return
        val viewer = state.value.viewerState ?: return

        if (!viewer.canSubscribe) {
            failure(
                "GitHub does not allow this token to change repository watching.",
            )
            return
        }

        val target = if (
            viewer.subscription == RepositorySubscriptionState.SUBSCRIBED
        ) {
            RepositorySubscriptionState.UNSUBSCRIBED
        } else {
            RepositorySubscriptionState.SUBSCRIBED
        }

        runOperation {
            when (
                val result = repositoryGateway.setSubscription(
                    repositoryNodeId = details.summary.nodeId,
                    state = target,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            viewerState = viewer.copy(
                                subscription = result.value,
                            ),
                        )
                    }

                    success(
                        if (
                            result.value ==
                                RepositorySubscriptionState.SUBSCRIBED
                        ) {
                            "Repository notifications enabled."
                        } else {
                            "Repository notifications returned to participating only."
                        },
                    )
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
            }
        }
    }

    fun fork() {
        val details = state.value.details ?: return

        runOperation {
            when (
                val result = repositoryGateway.forkRepository(
                    ForkRepositoryRequest(
                        owner = details.summary.ownerLogin,
                        name = details.summary.name,
                    ),
                )
            ) {
                is AppResult.Success -> {
                    success(
                        "Fork requested: " +
                            result.value.summary.fullName +
                            ". GitHub may finish preparing it asynchronously.",
                    )
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
            }
        }
    }

    fun clone() {
        val details = state.value.details ?: return

        runOperation {
            when (
                val result = workspaceCoordinator.clone(
                    details.summary,
                )
            ) {
                is AppResult.Success -> {
                    success(
                        details.summary.fullName +
                            " is ready on this device.",
                    )
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
            }
        }
    }

    fun updateSettings(
        request: UpdateRepositoryRequest,
    ) {
        val details = state.value.details ?: return

        if (!details.summary.permissions.canManageSettings) {
            failure(
                "Repository administration permission is required.",
            )
            return
        }

        if (details.offlineSnapshot) {
            failure(
                "Repository settings require an online GitHub response.",
            )
            return
        }

        runOperation {
            when (
                val result = repositoryGateway.updateRepository(
                    owner = details.summary.ownerLogin,
                    name = details.summary.name,
                    request = request,
                )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(details = result.value)
                    }
                    success("Repository settings updated.")
                }

                is AppResult.Failure -> {
                    failure(result.error.toRepositoryMessage())
                }
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

    private suspend fun refreshViewerState() {
        when (
            val result = repositoryGateway.getViewerState(
                owner = owner,
                name = name,
            )
        ) {
            is AppResult.Success -> {
                mutableState.update {
                    it.copy(
                        viewerState = result.value,
                        viewerCapabilitiesUnavailable = false,
                    )
                }
            }

            is AppResult.Failure -> {
                mutableState.update {
                    it.copy(
                        viewerState = null,
                        viewerCapabilitiesUnavailable = true,
                    )
                }
            }
        }
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            try {
                operation()
            } finally {
                mutableState.update {
                    it.copy(operationInProgress = false)
                }
            }
        }
    }

    private fun success(message: String) {
        mutableState.update {
            it.copy(
                successMessage = message,
                errorMessage = null,
            )
        }
    }

    private fun failure(message: String) {
        mutableState.update {
            it.copy(
                errorMessage = message,
                successMessage = null,
            )
        }
    }
}
