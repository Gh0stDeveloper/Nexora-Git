package com.nexora.git.core.platform

data class GitHubGraphQlRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
    val cachePolicy: GitHubCachePolicy = GitHubCachePolicy.NETWORK_FIRST,
    val cacheTtlMillis: Long = 30_000L,
)
