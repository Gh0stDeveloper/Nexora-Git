package com.nexora.git.core.auth

import android.net.Uri
import com.nexora.git.core.database.AuthAccountDao
import com.nexora.git.core.database.AuthAccountEntity
import com.nexora.git.core.settings.SettingsRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AuthSessionRepository @Inject constructor(
    private val authConfig: AuthConfig,
    private val pkceGenerator: PkceGenerator,
    private val authorizationUrlFactory: GitHubAuthorizationUrlFactory,
    private val callbackParser: OAuthCallbackParser,
    private val secureAuthStorage: SecureAuthStorage,
    private val authBrokerClient: AuthBrokerClient,
    private val githubIdentityClient: GitHubIdentityClient,
    private val authAccountDao: AuthAccountDao,
    private val settingsRepository: SettingsRepository,
) {
    private val refreshMutex = Mutex()

    val accounts: Flow<List<AuthAccountSummary>> =
        authAccountDao.observeAll().map { accounts ->
            accounts.map(AuthAccountEntity::toSummary)
        }

    val activeAccountId: Flow<Long?> = settingsRepository.activeAccountId

    fun isConfigured(): Boolean = authConfig.isConfigured

    fun beginAuthorization(): AuthResult<Uri> {
        if (!authConfig.isConfigured) {
            return AuthResult.Failure(AuthFailure.NOT_CONFIGURED)
        }

        return runCatching {
            val pkce = pkceGenerator.generate()
            val state = pkceGenerator.generateState()

            secureAuthStorage.savePendingAuthorization(
                PendingAuthorization(
                    state = state,
                    codeVerifier = pkce.verifier,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )

            authorizationUrlFactory.create(
                state = state,
                codeChallenge = pkce.challenge,
            )
        }.fold(
            onSuccess = AuthResult<Uri>::Success,
            onFailure = {
                AuthResult.Failure(
                    reason = AuthFailure.SECURE_STORAGE_FAILED,
                    detail = it.message,
                )
            },
        )
    }

    suspend fun completeCallback(uri: Uri): AuthResult<AuthAccountSummary> {
        return when (val callback = callbackParser.parse(uri)) {
            AuthCallbackResult.Invalid -> {
                secureAuthStorage.clearPendingAuthorization()
                AuthResult.Failure(AuthFailure.INVALID_CALLBACK)
            }

            is AuthCallbackResult.Error -> {
                secureAuthStorage.clearPendingAuthorization()
                AuthResult.Failure(
                    reason = AuthFailure.GITHUB_DENIED,
                    detail = callback.description ?: callback.code,
                )
            }

            is AuthCallbackResult.AuthorizationCode -> {
                completeAuthorizationCode(callback)
            }
        }
    }

    suspend fun getValidAccessToken(
        accountId: Long,
    ): AuthResult<String> = refreshMutex.withLock {
        val account = authAccountDao.findById(accountId)
            ?: return@withLock AuthResult.Failure(
                AuthFailure.ACCOUNT_NOT_FOUND,
            )

        val accessToken = secureAuthStorage.readAccessToken(accountId)
            ?: return@withLock AuthResult.Failure(
                AuthFailure.SECURE_STORAGE_FAILED,
            )

        val expiry = account.tokenExpiresAtEpochMillis
        if (expiry == null || expiry > System.currentTimeMillis() + REFRESH_SKEW_MS) {
            return@withLock AuthResult.Success(accessToken)
        }

        refreshAccountLocked(account)
    }

    suspend fun getActiveAccessToken(): AuthResult<String> {
        val accountId = settingsRepository.activeAccountId.first()
            ?: return AuthResult.Failure(AuthFailure.ACCOUNT_NOT_FOUND)

        return getValidAccessToken(accountId)
    }

    suspend fun switchAccount(accountId: Long): AuthResult<AuthAccountSummary> {
        val account = authAccountDao.findById(accountId)
            ?: return AuthResult.Failure(AuthFailure.ACCOUNT_NOT_FOUND)

        val token = getValidAccessToken(accountId)
        if (token is AuthResult.Failure) {
            return token
        }

        settingsRepository.setActiveAccountId(accountId)
        return AuthResult.Success(account.toSummary())
    }

    suspend fun signOut(
        accountId: Long,
        revokeRemote: Boolean = true,
    ) {
        val accessToken = secureAuthStorage.readAccessToken(accountId)

        if (revokeRemote && !accessToken.isNullOrBlank() && authConfig.isConfigured) {
            runCatching {
                authBrokerClient.revoke(accessToken)
            }
        }

        secureAuthStorage.deleteTokens(accountId)
        authAccountDao.deleteById(accountId)

        if (settingsRepository.activeAccountId.first() == accountId) {
            val fallback = authAccountDao.getAll().firstOrNull()?.accountId
            settingsRepository.setActiveAccountId(fallback)
        }
    }

    suspend fun clearAllLocalSessions() {
        secureAuthStorage.clearAll()
        authAccountDao.getAll().forEach { account ->
            authAccountDao.deleteById(account.accountId)
        }
        settingsRepository.setActiveAccountId(null)
    }

    private suspend fun completeAuthorizationCode(
        callback: AuthCallbackResult.AuthorizationCode,
    ): AuthResult<AuthAccountSummary> {
        val pending = secureAuthStorage.readPendingAuthorization()
            ?: return AuthResult.Failure(AuthFailure.INVALID_CALLBACK)

        if (System.currentTimeMillis() - pending.createdAtEpochMillis > AUTH_WINDOW_MS) {
            secureAuthStorage.clearPendingAuthorization()
            return AuthResult.Failure(AuthFailure.AUTHORIZATION_EXPIRED)
        }

        if (!constantTimeEquals(callback.state, pending.state)) {
            secureAuthStorage.clearPendingAuthorization()
            return AuthResult.Failure(AuthFailure.STATE_MISMATCH)
        }

        secureAuthStorage.clearPendingAuthorization()

        val tokenBundle = try {
            authBrokerClient.exchange(
                code = callback.code,
                codeVerifier = pending.codeVerifier,
            )
        } catch (error: Exception) {
            return AuthResult.Failure(
                reason = AuthFailure.TOKEN_EXCHANGE_FAILED,
                detail = error.message,
            )
        }

        val identity = try {
            githubIdentityClient.getIdentity(tokenBundle.accessToken)
        } catch (error: Exception) {
            runCatching {
                authBrokerClient.revoke(tokenBundle.accessToken)
            }

            return AuthResult.Failure(
                reason = AuthFailure.IDENTITY_FAILED,
                detail = error.message,
            )
        }

        val now = System.currentTimeMillis()
        val existing = authAccountDao.findById(identity.id)

        val entity = AuthAccountEntity(
            accountId = identity.id,
            login = identity.login,
            name = identity.name,
            avatarUrl = identity.avatarUrl,
            tokenExpiresAtEpochMillis =
                tokenBundle.accessTokenExpiresAtEpochMillis,
            refreshTokenExpiresAtEpochMillis =
                tokenBundle.refreshTokenExpiresAtEpochMillis,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )

        return try {
            secureAuthStorage.saveTokenBundle(identity.id, tokenBundle)
            authAccountDao.upsert(entity)
            settingsRepository.setActiveAccountId(identity.id)
            AuthResult.Success(entity.toSummary())
        } catch (error: Exception) {
            secureAuthStorage.deleteTokens(identity.id)
            AuthResult.Failure(
                reason = AuthFailure.SECURE_STORAGE_FAILED,
                detail = error.message,
            )
        }
    }

    private suspend fun refreshAccountLocked(
        account: AuthAccountEntity,
    ): AuthResult<String> {
        val now = System.currentTimeMillis()
        val refreshExpiry = account.refreshTokenExpiresAtEpochMillis

        if (refreshExpiry != null && refreshExpiry <= now) {
            return AuthResult.Failure(AuthFailure.REFRESH_EXPIRED)
        }

        val refreshToken = secureAuthStorage.readRefreshToken(account.accountId)
            ?: return AuthResult.Failure(AuthFailure.REFRESH_EXPIRED)

        val bundle = try {
            authBrokerClient.refresh(refreshToken)
        } catch (error: Exception) {
            return AuthResult.Failure(
                reason = AuthFailure.REFRESH_FAILED,
                detail = error.message,
            )
        }

        return try {
            secureAuthStorage.saveTokenBundle(account.accountId, bundle)
            authAccountDao.upsert(
                account.copy(
                    tokenExpiresAtEpochMillis =
                        bundle.accessTokenExpiresAtEpochMillis,
                    refreshTokenExpiresAtEpochMillis =
                        bundle.refreshTokenExpiresAtEpochMillis,
                    updatedAtEpochMillis = now,
                ),
            )
            AuthResult.Success(bundle.accessToken)
        } catch (error: Exception) {
            AuthResult.Failure(
                reason = AuthFailure.SECURE_STORAGE_FAILED,
                detail = error.message,
            )
        }
    }

    private fun constantTimeEquals(
        left: String,
        right: String,
    ): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8),
        right.toByteArray(Charsets.UTF_8),
    )

    companion object {
        private const val AUTH_WINDOW_MS = 10 * 60 * 1_000L
        private const val REFRESH_SKEW_MS = 5 * 60 * 1_000L
    }
}
