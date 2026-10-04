package com.nexora.git.core.auth

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubAuthorizationUrlFactory @Inject constructor(
    private val authConfig: AuthConfig,
) {
    fun create(
        state: String,
        codeChallenge: String,
    ): Uri = Uri.parse("https://github.com/login/oauth/authorize")
        .buildUpon()
        .appendQueryParameter("client_id", authConfig.githubClientId)
        .appendQueryParameter("redirect_uri", authConfig.githubCallbackUrl)
        .appendQueryParameter("state", state)
        .appendQueryParameter("code_challenge", codeChallenge)
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("prompt", "select_account")
        .build()
}
