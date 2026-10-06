package com.nexora.git.feature.releases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.releases.GitHubRelease
import com.nexora.git.core.releases.ReleaseAsset
import com.nexora.git.core.releases.ReleaseAssetDownload
import com.nexora.git.core.releases.ReleasesGateway
import com.nexora.git.core.releases.UpdateReleaseRequest
import com.nexora.git.core.repository.RepositoryGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReleaseDetailUiState(
    val owner: String = "",
    val repository: String = "",
    val releaseId: Long = 0L,
    val release: GitHubRelease? = null,
    val canWrite: Boolean = false,
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val deleted: Boolean = false,
    val lastDownload: ReleaseAssetDownload? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class ReleaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val releases: ReleasesGateway,
    private val repositories: RepositoryGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()
    private val releaseId =
        savedStateHandle.get<Long>("releaseId") ?: 0L

    private val mutableState = MutableStateFlow(
        ReleaseDetailUiState(
            owner = owner,
            repository = repository,
            releaseId = releaseId,
        ),
    )
    val state: StateFlow<ReleaseDetailUiState> =
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

            val releaseDeferred = async {
                releases.getRelease(
                    owner,
                    repository,
                    releaseId,
                )
            }
            val repositoryDeferred = async {
                repositories.getRepository(
                    owner,
                    repository,
                )
            }

            val releaseResult =
                releaseDeferred.await()
            val repositoryResult =
                repositoryDeferred.await()

            mutableState.update { current ->
                val details =
                    (repositoryResult as? AppResult.Success)
                        ?.value
                current.copy(
                    release =
                        (releaseResult as? AppResult.Success)
                            ?.value
                            ?: current.release,
                    canWrite =
                        details?.summary?.permissions?.push ==
                            true ||
                            details?.summary?.permissions?.maintain ==
                            true ||
                            details?.summary?.permissions?.admin ==
                            true,
                    loading = false,
                    errorMessage =
                        (releaseResult as? AppResult.Failure)
                            ?.error
                            ?.toReleaseMessage()
                            ?: (repositoryResult as? AppResult.Failure)
                                ?.error
                                ?.toReleaseMessage(),
                )
            }
        }
    }

    fun update(
        request: UpdateReleaseRequest,
    ) {
        runOperation {
            when (
                val result =
                    releases.updateRelease(
                        owner,
                        repository,
                        releaseId,
                        request,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            release = result.value,
                            successMessage =
                                "Release updated.",
                        )
                    }

                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun deleteRelease() {
        runOperation {
            when (
                val result =
                    releases.deleteRelease(
                        owner,
                        repository,
                        releaseId,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            deleted = true,
                            successMessage =
                                "Release deleted.",
                        )
                    }

                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun uploadAsset(
        contentUri: String,
    ) {
        runOperation {
            when (
                val result =
                    releases.uploadAsset(
                        owner,
                        repository,
                        releaseId,
                        contentUri,
                    )
            ) {
                is AppResult.Success -> {
                    mutableState.update { current ->
                        val release =
                            current.release
                        current.copy(
                            release = release?.copy(
                                assets =
                                    release.assets
                                        .filterNot {
                                            it.id ==
                                                result.value.id
                                        } +
                                        result.value,
                            ),
                            successMessage =
                                "Asset " +
                                    result.value.name +
                                    " uploaded.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun updateAsset(
        assetId: Long,
        name: String,
        label: String?,
    ) {
        runOperation {
            when (
                val result =
                    releases.updateAsset(
                        owner,
                        repository,
                        assetId,
                        name,
                        label,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update { current ->
                        val release =
                            current.release
                        current.copy(
                            release = release?.copy(
                                assets =
                                    release.assets.map {
                                        if (
                                            it.id ==
                                                assetId
                                        ) {
                                            result.value
                                        } else {
                                            it
                                        }
                                    },
                            ),
                            successMessage =
                                "Release asset updated.",
                        )
                    }

                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun deleteAsset(
        assetId: Long,
    ) {
        runOperation {
            when (
                val result =
                    releases.deleteAsset(
                        owner,
                        repository,
                        assetId,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update { current ->
                        val release =
                            current.release
                        current.copy(
                            release = release?.copy(
                                assets =
                                    release.assets.filterNot {
                                        it.id ==
                                            assetId
                                    },
                            ),
                            successMessage =
                                "Release asset deleted.",
                        )
                    }

                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun downloadAsset(
        asset: ReleaseAsset,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    lastDownload = null,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            when (
                val result =
                    releases.downloadAsset(
                        owner,
                        repository,
                        asset,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            lastDownload =
                                result.value,
                            successMessage =
                                "Asset downloaded.",
                        )
                    }

                is AppResult.Failure ->
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            errorMessage =
                                result.error
                                    .toReleaseMessage(),
                        )
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

    private fun fail(
        result: AppResult.Failure,
    ) {
        mutableState.update {
            it.copy(
                errorMessage =
                    result.error.toReleaseMessage(),
            )
        }
    }
}
