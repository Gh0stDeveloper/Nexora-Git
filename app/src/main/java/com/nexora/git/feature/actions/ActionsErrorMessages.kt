package com.nexora.git.feature.actions

import com.nexora.git.core.common.AppError

internal fun AppError.toActionsMessage(): String =
    when (this) {
        is AppError.Network ->
            message ?: "Network unavailable."
        is AppError.Authentication ->
            message ?: "GitHub authentication is required."
        is AppError.PermissionDenied ->
            message ?: "GitHub denied this Actions operation."
        is AppError.RateLimited ->
            if (secondary) {
                "GitHub temporarily throttled this Actions operation."
            } else {
                "GitHub API rate limit reached."
            }
        is AppError.NotFound ->
            message ?: "Workflow, run, job, or artifact not found."
        is AppError.Conflict ->
            message ?: "GitHub reported an Actions conflict."
        is AppError.Validation ->
            message ?: "GitHub Actions request is invalid."
        is AppError.Server ->
            message ?: "GitHub returned an Actions server error."
        is AppError.Parsing ->
            message ?: "GitHub returned an unexpected Actions response."
        is AppError.Unknown ->
            cause?.message ?: "GitHub Actions operation failed."
        AppError.StoragePermission ->
            "Storage permission is unavailable."
        AppError.DiskFull ->
            "There is not enough free storage for this artifact."
        AppError.GitConflict ->
            "This operation is unavailable while Git has conflicts."
    }
