package com.nexora.git.core.auth

internal data class SecureRuntimeAuthConfig(
    val githubClientId: String,
    val authBrokerBaseUrl: String,
    val githubCallbackUrl: String,
    val appCallbackUri: String,
)

internal object SecureRuntimeConfigNative {
    private val nativeLoaded: Boolean = runCatching {
        System.loadLibrary("nexoraconfig")
        true
    }.getOrDefault(false)

    private external fun readConfig(): Array<String>

    fun read(): SecureRuntimeAuthConfig {
        if (!nativeLoaded) {
            return EMPTY
        }

        val values = runCatching { readConfig() }.getOrNull()
        if (values == null || values.size != FIELD_COUNT) {
            return EMPTY
        }

        return SecureRuntimeAuthConfig(
            githubClientId = values[0],
            authBrokerBaseUrl = values[1],
            githubCallbackUrl = values[2],
            appCallbackUri = values[3],
        )
    }

    private const val FIELD_COUNT = 4

    private val EMPTY = SecureRuntimeAuthConfig(
        githubClientId = "",
        authBrokerBaseUrl = "",
        githubCallbackUrl = "",
        appCallbackUri = "",
    )
}
