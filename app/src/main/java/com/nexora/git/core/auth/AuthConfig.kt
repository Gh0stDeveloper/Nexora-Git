package com.nexora.git.core.auth

import com.nexora.git.BuildConfig
import java.net.URI
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
            isValidAuthEndpointPair(
                brokerBaseUrl = brokerBaseUrl,
                githubCallbackUrl = githubCallbackUrl,
            )
}

internal fun isValidAuthEndpointPair(
    brokerBaseUrl: String,
    githubCallbackUrl: String,
): Boolean {
    val broker = brokerBaseUrl.toStrictHttpsUri() ?: return false
    val callback = githubCallbackUrl.toStrictHttpsUri() ?: return false

    val brokerPath = broker.path.orEmpty()
    if (brokerPath.isNotEmpty() && brokerPath != "/") {
        return false
    }

    return callback.host.equals(broker.host, ignoreCase = true) &&
        effectivePort(callback) == effectivePort(broker) &&
        callback.path == "/oauth/callback"
}

private fun String.toStrictHttpsUri(): URI? =
    runCatching { URI(this) }
        .getOrNull()
        ?.takeIf { uri ->
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        }

private fun effectivePort(uri: URI): Int =
    if (uri.port >= 0) uri.port else 443
