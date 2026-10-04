package com.nexora.git.core.git

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitRemoteSecurityPolicy @Inject constructor() {

    fun allowsGitHubOAuthCredentials(
        remoteUrl: String,
    ): Boolean {
        val uri = runCatching {
            URI(remoteUrl)
        }.getOrNull() ?: return false

        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.port == -1
    }
}
