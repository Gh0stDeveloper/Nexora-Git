package com.nexora.git.core.platform

data class GitHubRestResponse(
    val statusCode: Int,
    val body: String?,
    val requestId: String?,
    val etag: String?,
    val pagination: GitHubRestPagination,
    val rateLimit: GitHubRateLimit?,
    val acceptedPermissions: GitHubPermissionRequirement,
    val fromCache: Boolean,
)

data class GitHubGraphQlError(
    val message: String,
    val type: String?,
    val path: List<String>,
)

data class GitHubGraphQlResponse(
    val dataJson: String?,
    val errors: List<GitHubGraphQlError>,
    val requestId: String?,
    val rateLimit: GitHubRateLimit?,
    val fromCache: Boolean,
) {
    val hasErrors: Boolean
        get() = errors.isNotEmpty()
}
