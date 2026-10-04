package com.nexora.git.core.platform

enum class GitHubGraphQlOperation {
    QUERY,
    MUTATION,
}

data class GitHubGraphQlRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
    val operation: GitHubGraphQlOperation = GitHubGraphQlOperation.QUERY,
    val cachePolicy: GitHubCachePolicy = GitHubCachePolicy.NETWORK_FIRST,
    val cacheTtlMillis: Long = 30_000L,
)
