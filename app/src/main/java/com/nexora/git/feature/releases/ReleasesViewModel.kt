package com.nexora.git.feature.releases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.releases.CreateReleaseRequest
import com.nexora.git.core.releases.GitHubRelease
import com.nexora.git.core.releases.GitTag
import com.nexora.git.core.releases.ReleasesGateway
import com.nexora.git.core.repository.RepositoryGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReleaseListFilter {
    ALL,
    PUBLISHED,
    DRAFTS,
    PRERELEASES,
}

data class ReleasesUiState(
    val owner: String = "",
    val repository: String = "",
    val defaultBranch: String = "main",
    val canWrite: Boolean = false,
    val releases: List<GitHubRelease> = emptyList(),
    val tags: List<GitTag> = emptyList(),
    val filter: ReleaseListFilter = ReleaseListFilter.ALL,
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val filteredReleases: List<GitHubRelease>
        get() = when (filter) {
            ReleaseListFilter.ALL -> releases
            ReleaseListFilter.PUBLISHED ->
                releases.filter {
                    !it.draft && !it.prerelease
                }
            ReleaseListFilter.DRAFTS ->
                releases.filter { it.draft }
            ReleaseListFilter.PRERELEASES ->
                releases.filter {
                    !it.draft && it.prerelease
                }
        }
}

@HiltViewModel
class ReleasesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val releasesGateway: ReleasesGateway,
    private val repositories: RepositoryGateway,
) : ViewModel() {

    private val owner =
        savedStateHandle.get<String>("owner").orEmpty()
    private val repository =
        savedStateHandle.get<String>("name").orEmpty()

    private val mutableState = MutableStateFlow(
        ReleasesUiState(
            owner = owner,
            repository = repository,
        ),
    )
    val state: StateFlow<ReleasesUiState> =
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

            val releasesDeferred = async {
                releasesGateway.listReleases(
                    owner,
                    repository,
                )
            }
            val tagsDeferred = async {
                releasesGateway.listTags(
                    owner,
                    repository,
                )
            }
            val repositoryDeferred = async {
                repositories.getRepository(
                    owner,
                    repository,
                )
            }

            val releaseResult = releasesDeferred.await()
            val tagResult = tagsDeferred.await()
            val repositoryResult =
                repositoryDeferred.await()

            mutableState.update { current ->
                val details =
                    (repositoryResult as? AppResult.Success)
                        ?.value

                current.copy(
                    releases =
                        (releaseResult as? AppResult.Success)
                            ?.value
                            ?: current.releases,
                    tags =
                        (tagResult as? AppResult.Success)
                            ?.value
                            ?: current.tags,
                    defaultBranch =
                        details?.summary?.defaultBranch
                            ?.takeIf(String::isNotBlank)
                            ?: current.defaultBranch,
                    canWrite =
                        details?.summary?.permissions?.push ==
                            true ||
                            details?.summary?.permissions?.maintain ==
                            true ||
                            details?.summary?.permissions?.admin ==
                            true,
                    loading = false,
                    errorMessage = firstFailure(
                        releaseResult,
                        tagResult,
                        repositoryResult,
                    ),
                )
            }
        }
    }

    fun setFilter(
        filter: ReleaseListFilter,
    ) {
        mutableState.update {
            it.copy(filter = filter)
        }
    }

    fun createRelease(
        request: CreateReleaseRequest,
    ) {
        runOperation {
            when (
                val result =
                    releasesGateway.createRelease(
                        owner,
                        repository,
                        request,
                    )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            releases =
                                listOf(result.value) +
                                    it.releases
                                        .filterNot { existing ->
                                            existing.id ==
                                                result.value.id
                                        },
                            successMessage =
                                "Release " +
                                    result.value.tagName +
                                    " created.",
                        )
                    }
                    refreshTagsOnly()
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun createTag(
        tagName: String,
        targetSha: String,
    ) {
        runOperation {
            when (
                val result =
                    releasesGateway.createLightweightTag(
                        owner,
                        repository,
                        tagName,
                        targetSha,
                    )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            tags =
                                listOf(result.value) +
                                    it.tags.filterNot { tag ->
                                        tag.name ==
                                            result.value.name
                                    },
                            successMessage =
                                "Tag " +
                                    result.value.name +
                                    " created.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
            }
        }
    }

    fun deleteTag(
        tagName: String,
    ) {
        runOperation {
            when (
                val result =
                    releasesGateway.deleteTag(
                        owner,
                        repository,
                        tagName,
                    )
            ) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            tags = it.tags.filterNot { tag ->
                                tag.name == tagName
                            },
                            successMessage =
                                "Tag " + tagName + " deleted.",
                        )
                    }
                }
                is AppResult.Failure -> fail(result)
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

    private suspend fun refreshTagsOnly() {
        when (
            val result =
                releasesGateway.listTags(
                    owner,
                    repository,
                )
        ) {
            is AppResult.Success ->
                mutableState.update {
                    it.copy(tags = result.value)
                }

            is AppResult.Failure -> Unit
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

    private fun firstFailure(
        vararg results: AppResult<*>,
    ): String? =
        results.firstNotNullOfOrNull {
            (it as? AppResult.Failure)
                ?.error
                ?.toReleaseMessage()
        }
}
