package com.nexora.git.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspaces ORDER BY lastOpenedAtEpochMillis DESC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE sourceTreeUri = :treeUri LIMIT 1")
    suspend fun findBySourceTreeUri(treeUri: String): WorkspaceEntity?

    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :workspaceId")
    suspend fun deleteById(workspaceId: String)

    @Query(
        """
        UPDATE workspaces
        SET lastOpenedAtEpochMillis = :openedAtEpochMillis
        WHERE id = :workspaceId
        """,
    )
    suspend fun markOpened(
        workspaceId: String,
        openedAtEpochMillis: Long,
    )
}
