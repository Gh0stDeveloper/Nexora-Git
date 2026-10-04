package com.nexora.git.core.auth

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthCallbackParser @Inject constructor(
    private val authConfig: AuthConfig,
) {
    fun parse(uri: Uri): AuthCallbackResult {
        val expected = Uri.parse(authConfig.appCallbackUri)

        if (uri.scheme != expected.scheme ||
            uri.host != expected.host ||
            uri.path != expected.path
        ) {
            return AuthCallbackResult.Invalid
        }

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return AuthCallbackResult.Error(
                code = error,
                description = uri.getQueryParameter("error_description"),
            )
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")

        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            return AuthCallbackResult.Invalid
        }

        return AuthCallbackResult.AuthorizationCode(
            code = code,
            state = state,
        )
    }
}
