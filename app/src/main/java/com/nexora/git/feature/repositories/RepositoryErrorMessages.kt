package com.nexora.git.feature.repositories

import com.nexora.git.core.common.AppError

internal fun AppError.toRepositoryMessage(): String =
    when (this) {
        is AppError.Network ->
            message ?: "Network unavailable. Cached repositories may still be shown."

        is AppError.Authentication ->
            message ?: "GitHub authentication is required."

        is AppError.PermissionDenied -> {
            val permissionText = acceptedPermissions.alternatives
                .flatMap { it.permissions }
                .distinctBy { it.name to it.access }
                .joinToString { permission ->
                    permission.name + " " +
                        permission.access.name.lowercase()
                }

            if (permissionText.isBlank()) {
                message ?: "GitHub denied this operation."
            } else {
                "GitHub denied this operation. Required: " +
                    permissionText + "."
            }
        }

        is AppError.RateLimited ->
            if (secondary) {
                "GitHub temporarily throttled this operation. Try again later."
            } else {
                "GitHub API rate limit reached. Try again after the reset."
            }

        is AppError.NotFound ->
            message ?: "Repository not found."

        is AppError.Conflict ->
            message ?: "GitHub reported a repository conflict."

        is AppError.Validation ->
            message ?: "Repository request is invalid."

        AppError.StoragePermission ->
            "Storage permission is required."

        AppError.DiskFull ->
            "There is not enough device storage."

        AppError.GitConflict ->
            "The local repository contains Git conflicts."

        is AppError.Server ->
            message ?: "GitHub returned a server error."

        is AppError.Parsing ->
            message ?: "GitHub returned an unexpected response."

        is AppError.Unknown ->
            cause?.message ?: "Repository operation failed."
    }
