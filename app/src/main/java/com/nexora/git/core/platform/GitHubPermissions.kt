package com.nexora.git.core.platform

enum class GitHubPermissionAccess {
    READ,
    WRITE,
    ADMIN,
    UNKNOWN,
}

data class GitHubPermission(
    val name: String,
    val access: GitHubPermissionAccess,
)

data class GitHubPermissionSet(
    val permissions: List<GitHubPermission>,
)

data class GitHubPermissionRequirement(
    val alternatives: List<GitHubPermissionSet>,
) {
    val isEmpty: Boolean
        get() = alternatives.isEmpty()
}
