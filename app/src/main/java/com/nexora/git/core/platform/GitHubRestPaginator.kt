package com.nexora.git.core.platform

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRestPaginator @Inject constructor(
    private val restClient: GitHubRestClient,
) {

    suspend fun nextPage(
        current: GitHubRestResponse,
        cachePolicy: GitHubCachePolicy = GitHubCachePolicy.NETWORK_FIRST,
    ): AppResult<GitHubRestResponse> {
        val nextUrl = current.pagination.nextUrl
            ?: return AppResult.Failure(
                AppError.NotFound("No next REST page is available"),
            )

        return restClient.execute(
            GitHubRestRequest(
                pathOrUrl = nextUrl,
                method = GitHubHttpMethod.GET,
                cachePolicy = cachePolicy,
            ),
        )
    }
}
