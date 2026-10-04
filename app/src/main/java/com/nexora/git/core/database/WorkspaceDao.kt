package com.nexora.git.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspaces ORDER BY lastOpenedAtEpochMillis DESC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WorkspaceEntity?

    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Delete
    suspend fun delete(workspace: WorkspaceEntity)
}
