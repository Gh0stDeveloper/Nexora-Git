package com.nexora.git.core.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureAuthStorage @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: KeystoreCipher,
) {
    private val preferences = context.getSharedPreferences(
        "nexora_git_secure_auth",
        Context.MODE_PRIVATE,
    )

    fun savePendingAuthorization(pending: PendingAuthorization) {
        val serialized = listOf(
            pending.state,
            pending.codeVerifier,
            pending.createdAtEpochMillis.toString(),
        ).joinToString("
")

        putEncrypted(KEY_PENDING_AUTH, serialized)
    }

    fun readPendingAuthorization(): PendingAuthorization? {
        val value = getDecrypted(KEY_PENDING_AUTH) ?: return null
        val parts = value.split('
')
        if (parts.size != 3) return null

        return PendingAuthorization(
            state = parts[0],
            codeVerifier = parts[1],
            createdAtEpochMillis = parts[2].toLongOrNull() ?: return null,
        )
    }

    fun clearPendingAuthorization() {
        preferences.edit().remove(KEY_PENDING_AUTH).apply()
    }

    fun saveTokenBundle(
        accountId: Long,
        bundle: TokenBundle,
    ) {
        putEncrypted(accessTokenKey(accountId), bundle.accessToken)

        if (bundle.refreshToken.isNullOrBlank()) {
            preferences.edit().remove(refreshTokenKey(accountId)).apply()
        } else {
            putEncrypted(refreshTokenKey(accountId), bundle.refreshToken)
        }
    }

    fun readAccessToken(accountId: Long): String? =
        getDecrypted(accessTokenKey(accountId))

    fun readRefreshToken(accountId: Long): String? =
        getDecrypted(refreshTokenKey(accountId))

    fun deleteTokens(accountId: Long) {
        preferences.edit()
            .remove(accessTokenKey(accountId))
            .remove(refreshTokenKey(accountId))
            .apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun putEncrypted(
        key: String,
        value: String,
    ) {
        preferences.edit()
            .putString(key, cipher.encrypt(value))
            .apply()
    }

    private fun getDecrypted(key: String): String? {
        val encrypted = preferences.getString(key, null) ?: return null

        return runCatching {
            cipher.decrypt(encrypted)
        }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    private fun accessTokenKey(accountId: Long): String =
        "account.$accountId.access"

    private fun refreshTokenKey(accountId: Long): String =
        "account.$accountId.refresh"

    companion object {
        private const val KEY_PENDING_AUTH = "oauth.pending"
    }
}
