package com.nexora.git.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.explore.ExploreCodeResult
import com.nexora.git.core.explore.ExploreSearchSection
import com.nexora.git.core.explore.ExploreUser
import com.nexora.git.core.explore.GitHubExploreGateway
import com.nexora.git.core.repository.RepositorySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val query: String = "",
    val section: ExploreSearchSection =
        ExploreSearchSection.REPOSITORIES,
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val repositories: List<RepositorySummary> = emptyList(),
    val users: List<ExploreUser> = emptyList(),
    val code: List<ExploreCodeResult> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val gateway: GitHubExploreGateway,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        ExploreUiState(),
    )
    val state: StateFlow<ExploreUiState> =
        mutableState.asStateFlow()

    fun setQuery(value: String) {
        mutableState.update {
            it.copy(
                query = value.take(MAX_QUERY_LENGTH),
                errorMessage = null,
            )
        }
    }

    fun setSection(section: ExploreSearchSection) {
        mutableState.update {
            it.copy(
                section = section,
                hasSearched = false,
                repositories = emptyList(),
                users = emptyList(),
                code = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun search() {
        val snapshot = state.value
        if (
            snapshot.loading ||
            snapshot.query.isBlank()
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            when (snapshot.section) {
                ExploreSearchSection.REPOSITORIES ->
                    applyRepositories(
                        gateway.searchRepositories(snapshot.query),
                    )

                ExploreSearchSection.USERS ->
                    applyUsers(
                        gateway.searchUsers(snapshot.query),
                    )

                ExploreSearchSection.CODE ->
                    applyCode(
                        gateway.searchCode(snapshot.query),
                    )
            }
        }
    }

    private fun applyRepositories(
        result: AppResult<List<RepositorySummary>>,
    ) {
        when (result) {
            is AppResult.Success -> mutableState.update {
                it.copy(
                    loading = false,
                    hasSearched = true,
                    repositories = result.value,
                    users = emptyList(),
                    code = emptyList(),
                )
            }
            is AppResult.Failure -> fail(result.error)
        }
    }

    private fun applyUsers(
        result: AppResult<List<ExploreUser>>,
    ) {
        when (result) {
            is AppResult.Success -> mutableState.update {
                it.copy(
                    loading = false,
                    hasSearched = true,
                    repositories = emptyList(),
                    users = result.value,
                    code = emptyList(),
                )
            }
            is AppResult.Failure -> fail(result.error)
        }
    }

    private fun applyCode(
        result: AppResult<List<ExploreCodeResult>>,
    ) {
        when (result) {
            is AppResult.Success -> mutableState.update {
                it.copy(
                    loading = false,
                    hasSearched = true,
                    repositories = emptyList(),
                    users = emptyList(),
                    code = result.value,
                )
            }
            is AppResult.Failure -> fail(result.error)
        }
    }

    private fun fail(error: AppError) {
        mutableState.update {
            it.copy(
                loading = false,
                hasSearched = true,
                errorMessage = error.toExploreMessage(),
            )
        }
    }

    private fun AppError.toExploreMessage(): String =
        when (this) {
            is AppError.Authentication ->
                message ?: "GitHub authentication is required."
            is AppError.Network ->
                message ?: "GitHub search is unavailable offline."
            is AppError.PermissionDenied ->
                message ?: "This GitHub search is not permitted."
            is AppError.RateLimited ->
                message ?: if (secondary) {
                    "GitHub temporarily limited search requests."
                } else {
                    "GitHub search rate limit reached."
                }
            is AppError.NotFound ->
                message ?: "GitHub search endpoint was not found."
            is AppError.Validation ->
                message ?: "Invalid search query."
            is AppError.Server ->
                message ?: "GitHub search failed with HTTP $code."
            is AppError.Parsing ->
                message ?: "GitHub returned an invalid search response."
            is AppError.Conflict ->
                message ?: "GitHub search request conflicted."
            AppError.StoragePermission ->
                "Storage permission is unavailable."
            AppError.DiskFull ->
                "Device storage is full."
            AppError.GitConflict ->
                "A Git conflict is active."
            is AppError.Unknown ->
                "GitHub search failed unexpectedly."
        }

    private companion object {
        const val MAX_QUERY_LENGTH = 256
    }
}
