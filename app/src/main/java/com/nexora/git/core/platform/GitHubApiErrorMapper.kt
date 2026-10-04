package com.nexora.git.core.platform

import com.nexora.git.core.common.AppError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Headers
import org.json.JSONObject

@Singleton
class GitHubApiErrorMapper @Inject constructor(
    private val permissionResolver: GitHubPermissionResolver,
) {

    fun fromHttp(
        statusCode: Int,
        headers: Headers,
        body: String?,
    ): AppError {
        val message = extractMessage(body)
        val remaining = headers["X-RateLimit-Remaining"]?.toLongOrNull()
        val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()
        val retryAfter = headers["Retry-After"]?.toLongOrNull()
        val acceptedPermissions = permissionResolver.parseAcceptedPermissions(
            headers["X-Accepted-GitHub-Permissions"],
        )

        return when (statusCode) {
            401 -> AppError.Authentication(message)

            403 -> {
                if (remaining == 0L || retryAfter != null) {
                    AppError.RateLimited(
                        resetAtEpochSeconds = reset,
                        retryAfterSeconds = retryAfter,
                        secondary = retryAfter != null && remaining != 0L,
                        message = message,
                    )
                } else {
                    AppError.PermissionDenied(
                        acceptedPermissions = acceptedPermissions,
                        message = message,
                    )
                }
            }

            404 -> AppError.NotFound(message)
            409 -> AppError.Conflict(message)

            422 -> AppError.Validation(message)

            429 -> AppError.RateLimited(
                resetAtEpochSeconds = reset,
                retryAfterSeconds = retryAfter,
                secondary = true,
                message = message,
            )

            in 500..599 -> AppError.Server(
                code = statusCode,
                message = message,
            )

            else -> AppError.Server(
                code = statusCode,
                message = message,
            )
        }
    }

    fun fromException(error: Throwable): AppError =
        when (error) {
            is IOException -> AppError.Network(error.message)
            else -> AppError.Unknown(error)
        }

    private fun extractMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null

        return runCatching {
            JSONObject(body).optString("message")
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
