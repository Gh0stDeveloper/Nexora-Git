package com.nexora.git.feature.releases

import com.nexora.git.core.common.AppError

internal fun AppError.toReleaseMessage(): String =
    when (this) {
        is AppError.Network ->
            message ?: "Network unavailable."
        is AppError.Authentication ->
            message ?: "GitHub authentication is required."
        is AppError.PermissionDenied ->
            message ?: "GitHub denied this release operation."
        is AppError.RateLimited ->
            if (secondary) {
                "GitHub temporarily throttled this release operation."
            } else {
                "GitHub API rate limit reached."
            }
        is AppError.NotFound ->
            message ?: "Release, tag, or asset not found."
        is AppError.Conflict ->
            message ?: "GitHub reported a release conflict."
        is AppError.Validation ->
            message ?: "Release request is invalid."
        is AppError.Server ->
            message ?: "GitHub returned a release server error."
        is AppError.Parsing ->
            message ?: "GitHub returned an unexpected release response."
        is AppError.Unknown ->
            cause?.message ?: "Release operation failed."
        AppError.StoragePermission ->
            "Storage access is unavailable."
        AppError.DiskFull ->
            "There is not enough free storage for this release asset."
        AppError.GitConflict ->
            "This operation is unavailable while Git has conflicts."
    }
