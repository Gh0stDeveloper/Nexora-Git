package com.nexora.git.core.platform

data class GitHubRestPagination(
    val nextUrl: String? = null,
    val previousUrl: String? = null,
    val firstUrl: String? = null,
    val lastUrl: String? = null,
) {
    val hasNext: Boolean
        get() = nextUrl != null
}

data class GitHubGraphQlPageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)
