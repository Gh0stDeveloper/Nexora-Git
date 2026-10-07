package com.nexora.git.core.git

import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

interface GitCredentialProvider {
    suspend fun credentialsFor(
        remoteUrl: String,
        accountId: Long? = null,
    ): GitTransportCredentials?
}

@Singleton
class GitHubGitCredentialProvider @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
    private val securityPolicy: GitRemoteSecurityPolicy,
) : GitCredentialProvider {

    override suspend fun credentialsFor(
        remoteUrl: String,
        accountId: Long?,
    ): GitTransportCredentials? {
        if (!securityPolicy.allowsGitHubOAuthCredentials(remoteUrl)) {
            return null
        }

        val tokenResult = if (accountId == null) {
            authSessionRepository.getActiveAccessToken()
        } else {
            authSessionRepository.getValidAccessToken(accountId)
        }

        return when (val result = tokenResult) {
            is AuthResult.Success -> GitTransportCredentials(
                username = "x-access-token",
                password = result.value,
            )

            is AuthResult.Failure -> null
        }
    }
}
