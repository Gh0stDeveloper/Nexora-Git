package com.nexora.git.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceTreeUri: String?,
    val managedWorkspacePath: String?,
    val repositoryRemote: String?,
    val currentBranch: String?,
    val accountId: String?,
    val lastOpenedAtEpochMillis: Long,
)
