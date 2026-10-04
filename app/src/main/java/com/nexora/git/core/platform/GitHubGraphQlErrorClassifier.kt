package com.nexora.git.core.platform

import com.nexora.git.core.common.AppError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubGraphQlErrorClassifier @Inject constructor() {

    fun classify(
        errors: List<GitHubGraphQlError>,
    ): AppError? {
        if (errors.isEmpty()) return null

        val types = errors.mapNotNull { it.type?.uppercase() }
        val message = errors.joinToString("; ") { it.message }

        return when {
            types.any { it == "FORBIDDEN" } ->
                AppError.PermissionDenied(message = message)

            types.any { it == "NOT_FOUND" } ->
                AppError.NotFound(message)

            types.any {
                it == "UNPROCESSABLE" ||
                    it == "VALIDATION"
            } -> AppError.Validation(message)

            else -> null
        }
    }
}
