package com.nexora.git.feature.issues

import com.nexora.git.core.common.AppError

internal fun AppError.toIssueMessage(): String =
    when (this) {
        is AppError.Network ->
            message ?: "Network unavailable."

        is AppError.Authentication ->
            message ?: "GitHub authentication is required."

        is AppError.PermissionDenied ->
            message ?: "GitHub denied this Issues operation."

        is AppError.RateLimited ->
            if (secondary) {
                "GitHub temporarily throttled this Issues operation."
            } else {
                "GitHub API rate limit reached."
            }

        is AppError.NotFound ->
            message ?: "Issue or repository not found."

        is AppError.Conflict ->
            message ?: "GitHub reported an Issues conflict."

        is AppError.Validation ->
            message ?: "Issue request is invalid."

        is AppError.Server ->
            message ?: "GitHub returned a server error."

        is AppError.Parsing ->
            message ?: "GitHub returned an unexpected Issues response."

        is AppError.Unknown ->
            cause?.message ?: "Issues operation failed."

        AppError.StoragePermission,
        AppError.DiskFull,
        AppError.GitConflict ->
            "This operation is unavailable."
    }
