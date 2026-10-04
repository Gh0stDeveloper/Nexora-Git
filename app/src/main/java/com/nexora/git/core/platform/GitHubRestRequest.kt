package com.nexora.git.core.platform

enum class GitHubHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
}

enum class GitHubCachePolicy {
    NETWORK_ONLY,
    NETWORK_FIRST,
    CACHE_FIRST,
    NO_STORE,
}

data class GitHubRestRequest(
    val pathOrUrl: String,
    val method: GitHubHttpMethod = GitHubHttpMethod.GET,
    val query: Map<String, String?> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val bodyJson: String? = null,
    val cachePolicy: GitHubCachePolicy = GitHubCachePolicy.NETWORK_FIRST,
    val cacheTtlMillis: Long = 60_000L,
)
