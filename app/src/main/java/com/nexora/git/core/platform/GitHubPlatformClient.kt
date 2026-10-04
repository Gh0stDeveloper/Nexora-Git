package com.nexora.git.core.platform

import com.nexora.git.core.common.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class GitHubPlatformClient @Inject constructor(
    val rest: GitHubRestClient,
    val graphQl: GitHubGraphQlClient,
    val paginator: GitHubRestPaginator,
    private val rateLimitManager: GitHubRateLimitManager,
) {
    val rateLimits: StateFlow<Map<String, GitHubRateLimit>>
        get() = rateLimitManager.limits

    suspend fun fetchRateLimits(): AppResult<GitHubRestResponse> =
        rest.execute(
            GitHubRestRequest(
                pathOrUrl = "/rate_limit",
                cachePolicy = GitHubCachePolicy.NETWORK_ONLY,
            ),
        )
}
