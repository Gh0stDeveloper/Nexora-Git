package com.nexora.git.core.auth

data class PendingAuthorization(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMillis: Long,
)

data class TokenBundle(
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAtEpochMillis: Long?,
    val refreshTokenExpiresAtEpochMillis: Long?,
    val tokenType: String,
)

data class GitHubIdentity(
    val id: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
)

data class AuthAccountSummary(
    val accountId: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val tokenExpiresAtEpochMillis: Long?,
)

sealed interface AuthCallbackResult {
    data class AuthorizationCode(
        val code: String,
        val state: String,
    ) : AuthCallbackResult

    data class Error(
        val code: String,
        val description: String?,
    ) : AuthCallbackResult

    data object Invalid : AuthCallbackResult
}
