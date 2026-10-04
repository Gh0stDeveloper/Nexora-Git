package com.nexora.git.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceTreeUri: String?,
    val sourceDisplayName: String?,
    val sourceAuthority: String?,
    @ColumnInfo(defaultValue = "0")
    val sourceWritable: Boolean,
    @ColumnInfo(defaultValue = "'MANAGED'")
    val strategy: String,
    val managedWorkspacePath: String?,
    val repositoryRemote: String?,
    val currentBranch: String?,
    val accountId: String?,
    @ColumnInfo(defaultValue = "'READY'")
    val syncState: String,
    val lastSyncedAtEpochMillis: Long?,
    val lastScanAtEpochMillis: Long?,
    @ColumnInfo(defaultValue = "0")
    val fileCount: Long,
    @ColumnInfo(defaultValue = "0")
    val totalBytes: Long,
    @ColumnInfo(defaultValue = "0")
    val secretWarningCount: Int,
    @ColumnInfo(defaultValue = "0")
    val largeFileWarningCount: Int,
    @ColumnInfo(defaultValue = "0")
    val syncConflictCount: Int,
    val lastOpenedAtEpochMillis: Long,
)
