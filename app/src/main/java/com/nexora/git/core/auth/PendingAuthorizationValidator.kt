package com.nexora.git.core.auth

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingAuthorizationValidator @Inject constructor() {

    fun validate(
        pending: PendingAuthorization,
        returnedState: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): AuthFailure? {
        if (nowEpochMillis < pending.createdAtEpochMillis ||
            nowEpochMillis - pending.createdAtEpochMillis > AUTH_WINDOW_MS
        ) {
            return AuthFailure.AUTHORIZATION_EXPIRED
        }

        val matches = MessageDigest.isEqual(
            returnedState.toByteArray(Charsets.UTF_8),
            pending.state.toByteArray(Charsets.UTF_8),
        )

        return if (matches) {
            null
        } else {
            AuthFailure.STATE_MISMATCH
        }
    }

    companion object {
        const val AUTH_WINDOW_MS = 10 * 60 * 1_000L
    }
}
