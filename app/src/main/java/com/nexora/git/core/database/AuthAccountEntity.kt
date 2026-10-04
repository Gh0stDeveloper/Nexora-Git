package com.nexora.git.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexora.git.core.auth.AuthAccountSummary

@Entity(tableName = "auth_accounts")
data class AuthAccountEntity(
    @PrimaryKey val accountId: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val tokenExpiresAtEpochMillis: Long?,
    val refreshTokenExpiresAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toSummary(): AuthAccountSummary = AuthAccountSummary(
        accountId = accountId,
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        tokenExpiresAtEpochMillis = tokenExpiresAtEpochMillis,
    )
}
