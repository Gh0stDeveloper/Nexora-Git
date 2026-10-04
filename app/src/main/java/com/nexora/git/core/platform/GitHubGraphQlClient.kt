package com.nexora.git.core.platform

import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.network.GitHubApiConfig
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class GitHubGraphQlClient @Inject constructor(
    private val client: OkHttpClient,
    private val authSessionRepository: AuthSessionRepository,
    private val rateLimitManager: GitHubRateLimitManager,
    private val cache: GitHubGraphQlCache,
    private val errorMapper: GitHubApiErrorMapper,
    private val errorClassifier: GitHubGraphQlErrorClassifier,
) {

    suspend fun execute(
        request: GitHubGraphQlRequest,
    ): AppResult<GitHubGraphQlResponse> {
        val accountId = authSessionRepository.getActiveAccountId()
            ?: return AppResult.Failure(
                AppError.Authentication("No active GitHub account"),
            )

        val token = when (
            val tokenResult = authSessionRepository.getValidAccessToken(accountId)
        ) {
            is AuthResult.Success -> tokenResult.value
            is AuthResult.Failure -> {
                return AppResult.Failure(
                    AppError.Authentication(tokenResult.detail),
                )
            }
        }

        return executeAuthorized(
            request = request,
            accountId = accountId,
            accessToken = token,
            retryAuthentication = true,
        )
    }

    private suspend fun executeAuthorized(
        request: GitHubGraphQlRequest,
        accountId: Long,
        accessToken: String,
        retryAuthentication: Boolean,
    ): AppResult<GitHubGraphQlResponse> {
        if (request.query.isBlank()) {
            return AppResult.Failure(
                AppError.Validation("GraphQL query must not be blank"),
            )
        }

        val mutation = request.query.trimStart()
            .startsWith("mutation", ignoreCase = true)

        val cacheKey = cacheKey(
            accountId = accountId,
            request = request,
        )
        val cached = if (mutation) null else cache.get(cacheKey)

        if (!mutation &&
            request.cachePolicy == GitHubCachePolicy.CACHE_FIRST &&
            cached?.isFresh() == true
        ) {
            return AppResult.Success(
                cached.response.copy(fromCache = true),
            )
        }

        val payload = JSONObject()
            .put("query", request.query)
            .put("variables", JSONObject(request.variables))

        val httpRequest = Request.Builder()
            .url(GitHubApiConfig.GRAPHQL_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(httpRequest).execute().use { response ->
                    val rateLimit = rateLimitManager.updateFromHeaders(
                        headers = response.headers,
                        fallbackResource = "graphql",
                    )

                    if (response.code == 401 && retryAuthentication) {
                        return@withContext when (
                            val refreshed =
                                authSessionRepository.forceRefreshAccessToken(
                                    accountId,
                                )
                        ) {
                            is AuthResult.Success -> executeAuthorized(
                                request = request,
                                accountId = accountId,
                                accessToken = refreshed.value,
                                retryAuthentication = false,
                            )

                            is AuthResult.Failure -> AppResult.Failure(
                                AppError.Authentication(refreshed.detail),
                            )
                        }
                    }

                    val rawBody = response.body.string()
                        .takeIf { it.isNotBlank() }

                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(
                            errorMapper.fromHttp(
                                statusCode = response.code,
                                headers = response.headers,
                                body = rawBody,
                            ),
                        )
                    }

                    val parsed = try {
                        parseResponse(
                            body = rawBody,
                            requestId = response.header(
                                "X-GitHub-Request-Id",
                            ),
                            rateLimit = rateLimit,
                        )
                    } catch (error: Exception) {
                        return@withContext AppResult.Failure(
                            AppError.Parsing(
                                message = "Invalid GitHub GraphQL response",
                                cause = error,
                            ),
                        )
                    }

                    if (parsed.dataJson == null && parsed.errors.isNotEmpty()) {
                        val classified =
                            errorClassifier.classify(parsed.errors)
                        if (classified != null) {
                            return@withContext AppResult.Failure(classified)
                        }
                    }

                    if (mutation) {
                        cache.clearForAccount(accountId)
                    } else if (request.cachePolicy != GitHubCachePolicy.NO_STORE) {
                        cache.put(
                            cacheKey,
                            GitHubCachedGraphQlResponse(
                                response = parsed,
                                storedAtEpochMillis =
                                    System.currentTimeMillis(),
                                ttlMillis = request.cacheTtlMillis
                                    .coerceAtLeast(0L),
                            ),
                        )
                    }

                    AppResult.Success(parsed)
                }
            } catch (error: IOException) {
                if (!mutation &&
                    request.cachePolicy == GitHubCachePolicy.NETWORK_FIRST &&
                    cached != null
                ) {
                    AppResult.Success(
                        cached.response.copy(fromCache = true),
                    )
                } else {
                    AppResult.Failure(
                        errorMapper.fromException(error),
                    )
                }
            } catch (error: Exception) {
                AppResult.Failure(
                    errorMapper.fromException(error),
                )
            }
        }
    }

    private fun parseResponse(
        body: String?,
        requestId: String?,
        rateLimit: GitHubRateLimit?,
    ): GitHubGraphQlResponse {
        val root = JSONObject(body ?: "{}")

        val dataJson = root.opt("data")
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()

        val errors = root.optJSONArray("errors")
            ?.toGraphQlErrors()
            .orEmpty()

        return GitHubGraphQlResponse(
            dataJson = dataJson,
            errors = errors,
            requestId = requestId,
            rateLimit = rateLimit,
            fromCache = false,
        )
    }

    private fun JSONArray.toGraphQlErrors(): List<GitHubGraphQlError> =
        buildList {
            for (index in 0 until length()) {
                val error = optJSONObject(index) ?: continue
                val pathArray = error.optJSONArray("path")
                val path = buildList {
                    if (pathArray != null) {
                        for (pathIndex in 0 until pathArray.length()) {
                            add(pathArray.opt(pathIndex)?.toString().orEmpty())
                        }
                    }
                }

                add(
                    GitHubGraphQlError(
                        message = error.optString("message"),
                        type = error.optString("type")
                            .takeIf { it.isNotBlank() },
                        path = path,
                    ),
                )
            }
        }

    private fun cacheKey(
        accountId: Long,
        request: GitHubGraphQlRequest,
    ): String {
        val canonicalVariables = request.variables
            .toSortedMap()
            .entries
            .joinToString("&") { entry ->
                entry.key + "=" + JSONObject.wrap(entry.value).toString()
            }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                (request.query + "|" + canonicalVariables)
                    .toByteArray(Charsets.UTF_8),
            )
            .joinToString("") { byte -> "%02x".format(byte) }

        return accountId.toString() + "|" + digest
    }

    companion object {
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }
}
