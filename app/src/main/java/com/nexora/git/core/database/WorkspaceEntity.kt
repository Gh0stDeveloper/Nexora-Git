package com.nexora.git.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceTreeUri: String?,
    val sourceDisplayName: String?,
    val sourceAuthority: String?,
    val sourceWritable: Boolean,
    val strategy: String,
    val managedWorkspacePath: String?,
    val repositoryRemote: String?,
    val currentBranch: String?,
    val accountId: String?,
    val syncState: String,
    val lastSyncedAtEpochMillis: Long?,
    val lastScanAtEpochMillis: Long?,
    val fileCount: Long,
    val totalBytes: Long,
    val secretWarningCount: Int,
    val largeFileWarningCount: Int,
    val syncConflictCount: Int,
    val lastOpenedAtEpochMillis: Long,
)
