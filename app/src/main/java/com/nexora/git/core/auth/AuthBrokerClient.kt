package com.nexora.git.core.auth

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class AuthBrokerClient @Inject constructor(
    private val authConfig: AuthConfig,
) {
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    suspend fun exchange(
        code: String,
        codeVerifier: String,
    ): TokenBundle = postForToken(
        path = "/v1/oauth/exchange",
        payload = JSONObject()
            .put("code", code)
            .put("code_verifier", codeVerifier),
    )

    suspend fun refresh(
        refreshToken: String,
    ): TokenBundle = postForToken(
        path = "/v1/oauth/refresh",
        payload = JSONObject()
            .put("refresh_token", refreshToken),
    )

    suspend fun revoke(accessToken: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("/v1/oauth/revoke"))
            .post(
                JSONObject()
                    .put("access_token", accessToken)
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AuthBrokerException(
                    statusCode = response.code,
                    message = "Token revocation failed",
                )
            }
        }
    }

    private suspend fun postForToken(
        path: String,
        payload: JSONObject,
    ): TokenBundle = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint(path))
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()

            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optString("error_description")
                        .ifBlank { JSONObject(body).optString("error") }
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Authentication broker request failed"

                throw AuthBrokerException(
                    statusCode = response.code,
                    message = message,
                )
            }

            val json = try {
                JSONObject(body)
            } catch (error: Exception) {
                throw IOException("Invalid auth broker response", error)
            }

            val accessToken = json.optString("access_token")
            if (accessToken.isBlank()) {
                throw IOException("Auth broker response omitted access token")
            }

            val now = System.currentTimeMillis()
            val expiresIn = json.optLong("expires_in", 0L)
                .takeIf { it > 0L }
            val refreshExpiresIn = json.optLong(
                "refresh_token_expires_in",
                0L,
            ).takeIf { it > 0L }

            TokenBundle(
                accessToken = accessToken,
                refreshToken = json.optString("refresh_token")
                    .takeIf { it.isNotBlank() },
                accessTokenExpiresAtEpochMillis =
                    expiresIn?.let { now + it * 1_000L },
                refreshTokenExpiresAtEpochMillis =
                    refreshExpiresIn?.let { now + it * 1_000L },
                tokenType = json.optString("token_type", "bearer"),
            )
        }
    }

    private fun endpoint(path: String): String {
        check(authConfig.isConfigured) {
            "GitHub authentication is not configured for this build"
        }
        return authConfig.brokerBaseUrl + path
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class AuthBrokerException(
    val statusCode: Int,
    override val message: String,
) : IOException(message)
