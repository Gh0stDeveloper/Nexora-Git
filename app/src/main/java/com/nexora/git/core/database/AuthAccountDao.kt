package com.nexora.git.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthAccountDao {

    @Query("SELECT * FROM auth_accounts ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<AuthAccountEntity>>

    @Query("SELECT * FROM auth_accounts ORDER BY updatedAtEpochMillis DESC")
    suspend fun getAll(): List<AuthAccountEntity>

    @Query("SELECT * FROM auth_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun findById(accountId: Long): AuthAccountEntity?

    @Upsert
    suspend fun upsert(account: AuthAccountEntity)

    @Query("DELETE FROM auth_accounts WHERE accountId = :accountId")
    suspend fun deleteById(accountId: Long)
}
