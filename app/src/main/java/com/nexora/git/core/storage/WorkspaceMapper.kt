package com.nexora.git.core.storage

import com.nexora.git.core.database.WorkspaceEntity

fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = id,
    name = name,
    sourceTreeUri = sourceTreeUri,
    sourceDisplayName = sourceDisplayName,
    sourceAuthority = sourceAuthority,
    sourceWritable = sourceWritable,
    strategy = WorkspaceStrategy.valueOf(strategy),
    workspacePath = managedWorkspacePath.orEmpty(),
    repositoryRemote = repositoryRemote,
    currentBranch = currentBranch,
    accountId = accountId,
    syncState = WorkspaceSyncState.valueOf(syncState),
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
    lastScanAtEpochMillis = lastScanAtEpochMillis,
    fileCount = fileCount,
    totalBytes = totalBytes,
    secretWarningCount = secretWarningCount,
    largeFileWarningCount = largeFileWarningCount,
    lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
)
