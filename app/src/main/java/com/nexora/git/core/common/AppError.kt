package com.nexora.git.core.common

import com.nexora.git.core.platform.GitHubPermissionRequirement

sealed interface AppError {
    data class Network(
        val message: String? = null,
    ) : AppError

    data class Authentication(
        val message: String? = null,
    ) : AppError

    data class PermissionDenied(
        val acceptedPermissions: GitHubPermissionRequirement =
            GitHubPermissionRequirement(emptyList()),
        val message: String? = null,
    ) : AppError

    data class RateLimited(
        val resetAtEpochSeconds: Long? = null,
        val retryAfterSeconds: Long? = null,
        val secondary: Boolean = false,
        val message: String? = null,
    ) : AppError

    data class NotFound(
        val message: String? = null,
    ) : AppError

    data class Conflict(
        val message: String? = null,
    ) : AppError

    data class Validation(
        val message: String? = null,
    ) : AppError

    data object StoragePermission : AppError
    data object DiskFull : AppError
    data object GitConflict : AppError

    data class Server(
        val code: Int,
        val message: String? = null,
    ) : AppError

    data class Parsing(
        val message: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    data class Unknown(
        val cause: Throwable? = null,
    ) : AppError
}
