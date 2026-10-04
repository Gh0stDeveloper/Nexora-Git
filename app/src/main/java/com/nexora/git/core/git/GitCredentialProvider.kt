package com.nexora.git.core.git

import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

interface GitCredentialProvider {
    suspend fun credentialsFor(
        remoteUrl: String,
    ): GitTransportCredentials?
}

@Singleton
class GitHubGitCredentialProvider @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
) : GitCredentialProvider {

    override suspend fun credentialsFor(
        remoteUrl: String,
    ): GitTransportCredentials? {
        if (!isAuthorizedGitHubHttpsUrl(remoteUrl)) {
            return null
        }

        return when (
            val result = authSessionRepository.getActiveAccessToken()
        ) {
            is AuthResult.Success -> GitTransportCredentials(
                username = "x-access-token",
                password = result.value,
            )

            is AuthResult.Failure -> null
        }
    }

    internal fun isAuthorizedGitHubHttpsUrl(
        remoteUrl: String,
    ): Boolean {
        val uri = runCatching {
            URI(remoteUrl)
        }.getOrNull() ?: return false

        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null
    }
}
