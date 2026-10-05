package com.nexora.git.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubRepositoryDao {

    @Query(
        """
        SELECT * FROM github_repositories
        WHERE accountId = :accountId
        ORDER BY
            CASE WHEN updatedAt IS NULL THEN 1 ELSE 0 END,
            updatedAt DESC,
            fullName COLLATE NOCASE ASC
        """,
    )
    fun observeForAccount(
        accountId: Long,
    ): Flow<List<GitHubRepositoryEntity>>

    @Query(
        """
        SELECT * FROM github_repositories
        WHERE accountId = :accountId
        ORDER BY
            CASE WHEN updatedAt IS NULL THEN 1 ELSE 0 END,
            updatedAt DESC,
            fullName COLLATE NOCASE ASC
        """,
    )
    suspend fun getForAccount(
        accountId: Long,
    ): List<GitHubRepositoryEntity>

    @Query(
        """
        SELECT * FROM github_repositories
        WHERE accountId = :accountId
          AND fullName = :fullName
        LIMIT 1
        """,
    )
    suspend fun findByFullName(
        accountId: Long,
        fullName: String,
    ): GitHubRepositoryEntity?

    @Upsert
    suspend fun upsertAll(
        repositories: List<GitHubRepositoryEntity>,
    )

    @Query(
        "DELETE FROM github_repositories WHERE accountId = :accountId",
    )
    suspend fun deleteForAccount(accountId: Long)

    @Transaction
    suspend fun replaceForAccount(
        accountId: Long,
        repositories: List<GitHubRepositoryEntity>,
    ) {
        deleteForAccount(accountId)
        upsertAll(repositories)
    }
}
