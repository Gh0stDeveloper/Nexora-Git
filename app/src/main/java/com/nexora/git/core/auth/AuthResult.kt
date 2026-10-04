package com.nexora.git.core.auth

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    data class Failure(
        val reason: AuthFailure,
        val detail: String? = null,
    ) : AuthResult<Nothing>
}

enum class AuthFailure {
    NOT_CONFIGURED,
    INVALID_CALLBACK,
    STATE_MISMATCH,
    AUTHORIZATION_EXPIRED,
    GITHUB_DENIED,
    TOKEN_EXCHANGE_FAILED,
    REFRESH_EXPIRED,
    REFRESH_FAILED,
    IDENTITY_FAILED,
    ACCOUNT_NOT_FOUND,
    SECURE_STORAGE_FAILED,
    UNKNOWN,
}
