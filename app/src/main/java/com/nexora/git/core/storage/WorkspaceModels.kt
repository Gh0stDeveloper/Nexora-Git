package com.nexora.git.core.storage

enum class WorkspaceStrategy {
    DIRECT,
    MANAGED,
}

enum class WorkspaceSyncState {
    READY,
    SYNCING,
    CONFLICTS,
    ERROR,
}

data class Workspace(
    val id: String,
    val name: String,
    val sourceTreeUri: String?,
    val sourceDisplayName: String?,
    val sourceAuthority: String?,
    val sourceWritable: Boolean,
    val strategy: WorkspaceStrategy,
    val workspacePath: String,
    val repositoryRemote: String?,
    val currentBranch: String?,
    val accountId: String?,
    val syncState: WorkspaceSyncState,
    val lastSyncedAtEpochMillis: Long?,
    val lastScanAtEpochMillis: Long?,
    val fileCount: Long,
    val totalBytes: Long,
    val secretWarningCount: Int,
    val largeFileWarningCount: Int,
    val syncConflictCount: Int,
    val lastOpenedAtEpochMillis: Long,
)

data class WorkspaceImportResult(
    val workspace: Workspace,
    val scan: ProjectScanResult,
    val sync: WorkspaceSyncResult?,
)

data class WorkspaceSyncResult(
    val copiedFiles: Int,
    val skippedFiles: Int,
    val deletedFiles: Int,
    val copiedBytes: Long,
    val conflictedPaths: List<String>,
    val completedAtEpochMillis: Long,
)

enum class ProjectRiskSeverity {
    INFO,
    WARNING,
    BLOCKING,
}

enum class ProjectRiskType {
    SECRET,
    LARGE_FILE,
}

data class ProjectRisk(
    val path: String,
    val type: ProjectRiskType,
    val severity: ProjectRiskSeverity,
    val message: String,
    val ignoredByGit: Boolean,
)

data class ProjectScanFile(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val ignoredByGit: Boolean,
)

data class ProjectScanResult(
    val displayName: String,
    val files: List<ProjectScanFile>,
    val risks: List<ProjectRisk>,
    val fileCount: Long,
    val totalBytes: Long,
    val gitIgnoreFiles: Int,
    val detectedProjectTypes: Set<String>,
) {
    val secretWarningCount: Int
        get() = risks.asSequence()
            .filter {
                it.type == ProjectRiskType.SECRET &&
                    it.severity != ProjectRiskSeverity.INFO
            }
            .distinctBy { it.path }
            .count()

    val largeFileWarningCount: Int
        get() = risks.asSequence()
            .filter {
                it.type == ProjectRiskType.LARGE_FILE &&
                    it.severity != ProjectRiskSeverity.INFO
            }
            .distinctBy { it.path }
            .count()

    val hasBlockingRisks: Boolean
        get() = risks.any { it.severity == ProjectRiskSeverity.BLOCKING }
}
