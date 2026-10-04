package com.nexora.git.core.platform

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubPermissionResolver @Inject constructor() {

    fun parseAcceptedPermissions(
        header: String?,
    ): GitHubPermissionRequirement {
        if (header.isNullOrBlank()) {
            return GitHubPermissionRequirement(emptyList())
        }

        val alternatives = header
            .split(';')
            .mapNotNull { rawAlternative ->
                val permissions = rawAlternative
                    .split(',')
                    .mapNotNull(::parsePermission)

                permissions
                    .takeIf { it.isNotEmpty() }
                    ?.let(::GitHubPermissionSet)
            }

        return GitHubPermissionRequirement(alternatives)
    }

    private fun parsePermission(raw: String): GitHubPermission? {
        val parts = raw.trim().split('=', limit = 2)
        if (parts.size != 2) return null

        val name = parts[0].trim()
        if (name.isBlank()) return null

        val access = when (parts[1].trim().lowercase()) {
            "read" -> GitHubPermissionAccess.READ
            "write" -> GitHubPermissionAccess.WRITE
            "admin" -> GitHubPermissionAccess.ADMIN
            else -> GitHubPermissionAccess.UNKNOWN
        }

        return GitHubPermission(
            name = name,
            access = access,
        )
    }
}
