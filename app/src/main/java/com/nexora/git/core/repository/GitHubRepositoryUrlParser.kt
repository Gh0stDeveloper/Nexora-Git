package com.nexora.git.core.repository

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubRepositoryCoordinates(
    val owner: String,
    val name: String,
)

@Singleton
class GitHubRepositoryUrlParser @Inject constructor() {

    fun parse(url: String): GitHubRepositoryCoordinates? {
        val uri = runCatching {
            URI(url.trim())
        }.getOrNull() ?: return null

        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("github.com", ignoreCase = true) ||
            uri.userInfo != null ||
            uri.port != -1 ||
            !uri.query.isNullOrBlank() ||
            !uri.fragment.isNullOrBlank()
        ) {
            return null
        }

        val segments = uri.path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }

        if (segments.size != 2) return null

        val owner = segments[0]
        val name = segments[1].removeSuffix(".git")

        if (!OWNER.matches(owner) || !REPOSITORY.matches(name)) {
            return null
        }

        return GitHubRepositoryCoordinates(
            owner = owner,
            name = name,
        )
    }

    companion object {
        private val OWNER =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
