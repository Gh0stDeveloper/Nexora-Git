package com.nexora.git.core.platform

import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class GitHubRestClient @Inject constructor(
    private val client: OkHttpClient,
    private val authSessionRepository: AuthSessionRepository,
    private val urlResolver: GitHubUrlResolver,
    private val linkHeaderParser: GitHubLinkHeaderParser,
    private val permissionResolver: GitHubPermissionResolver,
    private val rateLimitManager: GitHubRateLimitManager,
    private val responseCache: GitHubResponseCache,
    private val cacheCoordinator: GitHubCacheCoordinator,
    private val errorMapper: GitHubApiErrorMapper,
) {

    suspend fun execute(
        request: GitHubRestRequest,
    ): AppResult<GitHubRestResponse> {
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
        request: GitHubRestRequest,
        accountId: Long,
        accessToken: String,
        retryAuthentication: Boolean,
    ): AppResult<GitHubRestResponse> {
        val url = try {
            urlResolver.resolve(
                pathOrUrl = request.pathOrUrl,
                query = request.query,
            )
        } catch (error: Exception) {
            return AppResult.Failure(
                AppError.Validation(error.message),
            )
        }

        val cacheKey = buildCacheKey(
            accountId = accountId,
            request = request,
            url = url.toString(),
        )
        val cached = if (
            request.cachePolicy == GitHubCachePolicy.NO_STORE ||
            request.cachePolicy == GitHubCachePolicy.NETWORK_ONLY
        ) {
            null
        } else {
            responseCache.get(cacheKey)
        }

        if (request.method == GitHubHttpMethod.GET &&
            request.cachePolicy == GitHubCachePolicy.CACHE_FIRST &&
            cached?.isFresh() == true
        ) {
            return AppResult.Success(cached.toResponse(fromCache = true))
        }

        val okhttpRequest = buildRequest(
            request = request,
            url = url.toString(),
            accessToken = accessToken,
            etag = cached?.etag,
        )

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(okhttpRequest).execute().use { response ->
                    val rateLimit = rateLimitManager.updateFromHeaders(
                        headers = response.headers,
                        fallbackResource = "core",
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

                    if (response.code == 304 && cached != null) {
                        val merged = cached.copy(
                            storedAtEpochMillis = System.currentTimeMillis(),
                            rateLimit = rateLimit ?: cached.rateLimit,
                        )
                        responseCache.put(cacheKey, merged)
                        return@withContext AppResult.Success(
                            merged.toResponse(fromCache = true),
                        )
                    }

                    val body = response.body.string()
                        .takeIf { it.isNotBlank() }

                    val acceptedPermissions =
                        permissionResolver.parseAcceptedPermissions(
                            response.header(
                                "X-Accepted-GitHub-Permissions",
                            ),
                        )

                    if (!response.isSuccessful) {
                        return@withContext AppResult.Failure(
                            errorMapper.fromHttp(
                                statusCode = response.code,
                                headers = response.headers,
                                body = body,
                            ),
                        )
                    }

                    val pagination = linkHeaderParser.parse(
                        response.header("Link"),
                    )

                    val result = GitHubRestResponse(
                        statusCode = response.code,
                        body = body,
                        requestId = response.header("X-GitHub-Request-Id"),
                        etag = response.header("ETag"),
                        pagination = pagination,
                        rateLimit = rateLimit,
                        acceptedPermissions = acceptedPermissions,
                        fromCache = false,
                    )

                    if (request.method == GitHubHttpMethod.GET &&
                        request.cachePolicy != GitHubCachePolicy.NO_STORE
                    ) {
                        responseCache.put(
                            cacheKey,
                            GitHubCachedResponse(
                                body = body,
                                etag = result.etag,
                                storedAtEpochMillis =
                                    System.currentTimeMillis(),
                                ttlMillis = request.cacheTtlMillis
                                    .coerceAtLeast(0L),
                                statusCode = result.statusCode,
                                requestId = result.requestId,
                                pagination = pagination,
                                rateLimit = rateLimit,
                                acceptedPermissions =
                                    acceptedPermissions,
                            ),
                        )
                    } else if (request.method != GitHubHttpMethod.GET) {
                        cacheCoordinator.clearForAccount(accountId)
                    }

                    AppResult.Success(result)
                }
            } catch (error: IOException) {
                if (request.cachePolicy == GitHubCachePolicy.NETWORK_FIRST &&
                    cached != null
                ) {
                    AppResult.Success(
                        cached.toResponse(fromCache = true),
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

    private fun buildRequest(
        request: GitHubRestRequest,
        url: String,
        accessToken: String,
        etag: String?,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")

        request.headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        if (request.method == GitHubHttpMethod.GET &&
            request.cachePolicy != GitHubCachePolicy.NETWORK_ONLY &&
            !etag.isNullOrBlank()
        ) {
            builder.header("If-None-Match", etag)
        }

        val requestBody = request.bodyJson
            ?.toRequestBody(JSON_MEDIA_TYPE)

        when (request.method) {
            GitHubHttpMethod.GET -> builder.get()
            GitHubHttpMethod.POST -> builder.post(
                requestBody ?: EMPTY_BODY,
            )
            GitHubHttpMethod.PUT -> builder.put(
                requestBody ?: EMPTY_BODY,
            )
            GitHubHttpMethod.PATCH -> builder.patch(
                requestBody ?: EMPTY_BODY,
            )
            GitHubHttpMethod.DELETE -> {
                if (requestBody == null) {
                    builder.delete()
                } else {
                    builder.delete(requestBody)
                }
            }
        }

        return builder.build()
    }

    private fun buildCacheKey(
        accountId: Long,
        request: GitHubRestRequest,
        url: String,
    ): String = buildString {
        append(accountId)
        append('|')
        append(request.method.name)
        append('|')
        append(url)
        append('|')
        append(request.headers["Accept"].orEmpty())
    }

    private fun GitHubCachedResponse.toResponse(
        fromCache: Boolean,
    ): GitHubRestResponse = GitHubRestResponse(
        statusCode = statusCode,
        body = body,
        requestId = requestId,
        etag = etag,
        pagination = pagination,
        rateLimit = rateLimit,
        acceptedPermissions = acceptedPermissions,
        fromCache = fromCache,
    )

    companion object {
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0)
            .toRequestBody(JSON_MEDIA_TYPE)
    }
}
