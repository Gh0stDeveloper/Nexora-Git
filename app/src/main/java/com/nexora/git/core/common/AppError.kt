package com.nexora.git.core.common

sealed interface AppError {
    data object Network : AppError
    data object Authentication : AppError
    data object PermissionDenied : AppError
    data object RateLimited : AppError
    data object NotFound : AppError
    data object Conflict : AppError
    data object StoragePermission : AppError
    data object DiskFull : AppError
    data object GitConflict : AppError
    data class Server(val code: Int) : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}
