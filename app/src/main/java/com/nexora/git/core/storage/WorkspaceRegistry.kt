package com.nexora.git.core.storage

import android.net.Uri
import com.nexora.git.core.database.WorkspaceDao
import com.nexora.git.core.database.WorkspaceEntity
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class WorkspaceRegistry @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val permissionManager: SafPermissionManager,
    private val strategyResolver: WorkspaceStrategyResolver,
    private val workspacePaths: WorkspacePaths,
    private val safScanner: SafProjectScanner,
    private val directScanner: DirectProjectScanner,
    private val syncEngine: SafWorkspaceSyncEngine,
) {
    val workspaces: Flow<List<Workspace>> =
        workspaceDao.observeAll().map { entities ->
            entities.map(WorkspaceEntity::toDomain)
        }

    suspend fun importSafTree(
        treeUri: Uri,
    ): WorkspaceImportResult {
        val permission = permissionManager.persist(treeUri)
        require(permission.readable) {
            "Selected project folder is not readable"
        }

        val scan = safScanner.scan(treeUri)
        val existing = workspaceDao.findBySourceTreeUri(
            treeUri.toString(),
        )
        val workspaceId = existing?.id ?: UUID.randomUUID().toString()
        val location = strategyResolver.resolve(treeUri)

        require(location.strategy == WorkspaceStrategy.MANAGED) {
            "SAF content trees must use a managed workspace"
        }

        val workspacePath = workspacePaths
            .workspaceRoot(workspaceId)
            .canonicalPath

        val now = System.currentTimeMillis()
        val syncing = WorkspaceEntity(
            id = workspaceId,
            name = scan.displayName,
            sourceTreeUri = treeUri.toString(),
            sourceDisplayName = scan.displayName,
            sourceAuthority = treeUri.authority,
            sourceWritable = permission.writable,
            strategy = WorkspaceStrategy.MANAGED.name,
            managedWorkspacePath = workspacePath,
            repositoryRemote = existing?.repositoryRemote,
            currentBranch = existing?.currentBranch,
            accountId = existing?.accountId,
            syncState = WorkspaceSyncState.SYNCING.name,
            lastSyncedAtEpochMillis =
                existing?.lastSyncedAtEpochMillis,
            lastScanAtEpochMillis = now,
            fileCount = scan.fileCount,
            totalBytes = scan.totalBytes,
            secretWarningCount = scan.secretWarningCount,
            largeFileWarningCount = scan.largeFileWarningCount,
            syncConflictCount = existing?.syncConflictCount ?: 0,
            lastOpenedAtEpochMillis = now,
        )

        workspaceDao.upsert(syncing)

        return try {
            val sync = syncEngine.sync(
                workspaceId = workspaceId,
                sourceTreeUri = treeUri,
                scan = scan,
            )

            val ready = syncing.copy(
                syncState = if (sync.conflictedPaths.isEmpty()) {
                    WorkspaceSyncState.READY.name
                } else {
                    WorkspaceSyncState.CONFLICTS.name
                },
                lastSyncedAtEpochMillis =
                    sync.completedAtEpochMillis,
                syncConflictCount = sync.conflictedPaths.size,
            )
            workspaceDao.upsert(ready)

            WorkspaceImportResult(
                workspace = ready.toDomain(),
                scan = scan,
                sync = sync,
            )
        } catch (error: Exception) {
            workspaceDao.upsert(
                syncing.copy(
                    syncState = WorkspaceSyncState.ERROR.name,
                ),
            )
            throw error
        }
    }

    suspend fun registerDirectDirectory(
        directory: File,
    ): WorkspaceImportResult {
        val canonical = directory.canonicalFile
        require(canonical.isDirectory && canonical.canRead()) {
            "Direct project directory is not readable"
        }

        val sourceUri = Uri.fromFile(canonical)
        val scan = directScanner.scan(canonical)
        val existing = workspaceDao.findBySourceTreeUri(
            sourceUri.toString(),
        )
        val now = System.currentTimeMillis()

        val entity = WorkspaceEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = scan.displayName,
            sourceTreeUri = sourceUri.toString(),
            sourceDisplayName = scan.displayName,
            sourceAuthority = null,
            sourceWritable = canonical.canWrite(),
            strategy = WorkspaceStrategy.DIRECT.name,
            managedWorkspacePath = canonical.path,
            repositoryRemote = existing?.repositoryRemote,
            currentBranch = existing?.currentBranch,
            accountId = existing?.accountId,
            syncState = WorkspaceSyncState.READY.name,
            lastSyncedAtEpochMillis = now,
            lastScanAtEpochMillis = now,
            fileCount = scan.fileCount,
            totalBytes = scan.totalBytes,
            secretWarningCount = scan.secretWarningCount,
            largeFileWarningCount = scan.largeFileWarningCount,
            syncConflictCount = 0,
            lastOpenedAtEpochMillis = now,
        )

        workspaceDao.upsert(entity)

        return WorkspaceImportResult(
            workspace = entity.toDomain(),
            scan = scan,
            sync = null,
        )
    }

    suspend fun sync(
        workspaceId: String,
    ): WorkspaceImportResult {
        val entity = workspaceDao.findById(workspaceId)
            ?: error("Workspace not found")

        return when (WorkspaceStrategy.valueOf(entity.strategy)) {
            WorkspaceStrategy.DIRECT -> {
                val directory = entity.managedWorkspacePath
                    ?.let(::File)
                    ?: error("Direct workspace path is missing")
                registerDirectDirectory(directory)
            }

            WorkspaceStrategy.MANAGED -> {
                val treeUri = entity.sourceTreeUri
                    ?.let(Uri::parse)
                    ?: error("Managed workspace source is missing")
                importSafTree(treeUri)
            }
        }
    }

    suspend fun markOpened(workspaceId: String) {
        workspaceDao.markOpened(
            workspaceId = workspaceId,
            openedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(
        workspaceId: String,
    ) = withContext(Dispatchers.IO) {
        val entity = workspaceDao.findById(workspaceId)
            ?: return@withContext

        if (entity.strategy == WorkspaceStrategy.MANAGED.name) {
            entity.managedWorkspacePath
                ?.let(::File)
                ?.deleteRecursively()

            workspacePaths.metadataRoot(workspaceId)
                .deleteRecursively()

            entity.sourceTreeUri
                ?.let(Uri::parse)
                ?.let(permissionManager::release)
        }

        workspaceDao.deleteById(workspaceId)
    }
}
