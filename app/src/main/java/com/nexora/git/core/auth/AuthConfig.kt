package com.nexora.git.core.auth

import com.nexora.git.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthConfig @Inject constructor() {
    val githubClientId: String = BuildConfig.GITHUB_CLIENT_ID.trim()
    val brokerBaseUrl: String = BuildConfig.AUTH_BROKER_BASE_URL.trim().trimEnd('/')
    val githubCallbackUrl: String = BuildConfig.GITHUB_CALLBACK_URL.trim()
    val appCallbackUri: String = BuildConfig.APP_CALLBACK_URI

    val isConfigured: Boolean
        get() = githubClientId.isNotBlank() &&
            brokerBaseUrl.startsWith("https://") &&
            githubCallbackUrl.startsWith("https://")
}
