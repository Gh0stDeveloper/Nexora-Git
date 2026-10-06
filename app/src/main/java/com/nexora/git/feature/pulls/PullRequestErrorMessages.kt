package com.nexora.git.feature.pulls

import com.nexora.git.core.common.AppError

internal fun AppError.toPullRequestMessage(): String =
    when (this) {
        is AppError.Network ->
            message ?: "Network unavailable."
        is AppError.Authentication ->
            message ?: "GitHub authentication is required."
        is AppError.PermissionDenied ->
            message ?: "GitHub denied this pull request operation."
        is AppError.RateLimited ->
            if (secondary) {
                "GitHub temporarily throttled this review operation."
            } else {
                "GitHub API rate limit reached."
            }
        is AppError.NotFound ->
            message ?: "Pull request or repository not found."
        is AppError.Conflict ->
            message ?: "GitHub reported a pull request conflict."
        is AppError.Validation ->
            message ?: "Pull request request is invalid."
        is AppError.Server ->
            message ?: "GitHub returned a server error."
        is AppError.Parsing ->
            message ?: "GitHub returned an unexpected pull request response."
        is AppError.Unknown ->
            cause?.message ?: "Pull request operation failed."
        AppError.StoragePermission,
        AppError.DiskFull,
        AppError.GitConflict ->
            "This operation is unavailable."
    }
