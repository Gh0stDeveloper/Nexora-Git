package com.nexora.git.feature.social

import com.nexora.git.core.common.AppError

internal fun AppError.toSocialMessage(): String =
    when (this) {
        is AppError.Network ->
            message ?: "Network unavailable."
        is AppError.Authentication ->
            message ?: "GitHub authentication is required."
        is AppError.PermissionDenied ->
            message ?: "GitHub denied this account/social operation."
        is AppError.RateLimited ->
            if (secondary) {
                "GitHub temporarily throttled this account/social operation."
            } else {
                "GitHub API rate limit reached."
            }
        is AppError.NotFound ->
            message ?: "GitHub social resource not found."
        is AppError.Conflict ->
            message ?: "GitHub reported a social operation conflict."
        is AppError.Validation ->
            message ?: "Account/social request is invalid."
        is AppError.Server ->
            message ?: "GitHub returned a social server error."
        is AppError.Parsing ->
            message ?: "GitHub returned an unexpected social response."
        is AppError.Unknown ->
            cause?.message ?: "Account/social operation failed."
        AppError.StoragePermission ->
            "Storage access is unavailable."
        AppError.DiskFull ->
            "There is not enough free storage."
        AppError.GitConflict ->
            "This operation is unavailable while Git has conflicts."
    }
