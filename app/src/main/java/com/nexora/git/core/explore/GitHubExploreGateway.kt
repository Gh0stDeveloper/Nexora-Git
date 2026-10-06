package com.nexora.git.core.explore

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import com.nexora.git.core.repository.RepositorySummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubExploreGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
    private val parser: ExploreJsonParser,
) {

    suspend fun searchRepositories(
        query: String,
    ): AppResult<List<RepositorySummary>> =
        search(query, parser::repositories) { normalized ->
            GitHubRestRequest(
                pathOrUrl = "/search/repositories",
                query = mapOf(
                    "q" to normalized,
                    "per_page" to RESULT_LIMIT.toString(),
                ),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = SEARCH_CACHE_MILLIS,
            )
        }

    suspend fun searchUsers(
        query: String,
    ): AppResult<List<ExploreUser>> =
        search(query, parser::users) { normalized ->
            GitHubRestRequest(
                pathOrUrl = "/search/users",
                query = mapOf(
                    "q" to normalized,
                    "per_page" to RESULT_LIMIT.toString(),
                ),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = SEARCH_CACHE_MILLIS,
            )
        }

    suspend fun searchCode(
        query: String,
    ): AppResult<List<ExploreCodeResult>> =
        search(query, parser::code) { normalized ->
            GitHubRestRequest(
                pathOrUrl = "/search/code",
                query = mapOf(
                    "q" to normalized,
                    "per_page" to RESULT_LIMIT.toString(),
                ),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = SEARCH_CACHE_MILLIS,
            )
        }

    private suspend fun <T> search(
        query: String,
        parse: (String?) -> List<T>,
        request: (String) -> GitHubRestRequest,
    ): AppResult<List<T>> {
        val normalized = query.trim()
        if (
            normalized.isEmpty() ||
            normalized.length > MAX_QUERY_LENGTH
        ) {
            return AppResult.Failure(
                AppError.Validation(
                    "Search query must contain 1–256 characters.",
                ),
            )
        }

        return when (
            val result = platform.rest.execute(
                request(normalized),
            )
        ) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                runCatching {
                    parse(result.value.body)
                }.fold(
                    onSuccess = AppResult<List<T>>::Success,
                    onFailure = { error ->
                        AppResult.Failure(
                            AppError.Parsing(
                                message =
                                    "Invalid GitHub search response",
                                cause = error,
                            ),
                        )
                    },
                )
            }
        }
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 256
        const val RESULT_LIMIT = 30
        const val SEARCH_CACHE_MILLIS = 30_000L
    }
}
