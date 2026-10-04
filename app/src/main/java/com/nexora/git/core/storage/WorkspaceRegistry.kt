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

    suspend fun findById(
        workspaceId: String,
    ): Workspace? =
        workspaceDao.findById(workspaceId)?.toDomain()

    suspend fun findByRemote(
        remoteUrl: String,
    ): Workspace? =
        workspaceDao.findByRepositoryRemote(remoteUrl)?.toDomain()

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

    suspend fun prepareRemoteClone(
        name: String,
        fullName: String,
        remoteUrl: String,
        defaultBranch: String,
        accountId: Long,
    ): Workspace {
        workspaceDao.findByRepositoryRemote(remoteUrl)
            ?.let { existing ->
                val path = existing.managedWorkspacePath
                    ?.let(::File)

                if (existing.strategy ==
                    WorkspaceStrategy.REMOTE_CLONE.name &&
                    path != null &&
                    File(path, ".git").isDirectory
                ) {
                    workspaceDao.markOpened(
                        existing.id,
                        System.currentTimeMillis(),
                    )
                    return requireNotNull(
                        workspaceDao.findById(existing.id),
                    ).toDomain()
                }

                if (existing.strategy ==
                    WorkspaceStrategy.REMOTE_CLONE.name
                ) {
                    path?.deleteRecursively()
                    workspacePaths.metadataRoot(existing.id)
                        .deleteRecursively()
                    workspaceDao.deleteById(existing.id)
                }
            }

        val workspaceId = UUID.randomUUID().toString()
        val destination = workspacePaths.workspaceRoot(workspaceId)
        destination.parentFile?.mkdirs()
        if (destination.exists()) {
            check(destination.deleteRecursively()) {
                "Unable to prepare clone destination"
            }
        }

        val now = System.currentTimeMillis()
        val entity = WorkspaceEntity(
            id = workspaceId,
            name = name,
            sourceTreeUri = null,
            sourceDisplayName = fullName,
            sourceAuthority = null,
            sourceWritable = true,
            strategy = WorkspaceStrategy.REMOTE_CLONE.name,
            managedWorkspacePath = destination.canonicalPath,
            repositoryRemote = remoteUrl,
            currentBranch = defaultBranch,
            accountId = accountId.toString(),
            syncState = WorkspaceSyncState.SYNCING.name,
            lastSyncedAtEpochMillis = null,
            lastScanAtEpochMillis = null,
            fileCount = 0,
            totalBytes = 0,
            secretWarningCount = 0,
            largeFileWarningCount = 0,
            syncConflictCount = 0,
            lastOpenedAtEpochMillis = now,
        )

        workspaceDao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun completeRemoteClone(
        workspaceId: String,
        currentBranch: String?,
    ): WorkspaceImportResult {
        val entity = workspaceDao.findById(workspaceId)
            ?: error("Clone workspace not found")

        require(
            entity.strategy == WorkspaceStrategy.REMOTE_CLONE.name,
        ) {
            "Workspace is not a remote clone"
        }

        val directory = entity.managedWorkspacePath
            ?.let(::File)
            ?: error("Clone workspace path is missing")

        val scan = directScanner.scan(directory)
        val now = System.currentTimeMillis()
        val ready = entity.copy(
            syncState = WorkspaceSyncState.READY.name,
            currentBranch = currentBranch
                ?.takeIf { it.isNotBlank() }
                ?: entity.currentBranch,
            lastSyncedAtEpochMillis = now,
            lastScanAtEpochMillis = now,
            fileCount = scan.fileCount,
            totalBytes = scan.totalBytes,
            secretWarningCount = scan.secretWarningCount,
            largeFileWarningCount = scan.largeFileWarningCount,
            syncConflictCount = 0,
            lastOpenedAtEpochMillis = now,
        )

        workspaceDao.upsert(ready)

        return WorkspaceImportResult(
            workspace = ready.toDomain(),
            scan = scan,
            sync = null,
        )
    }

    suspend fun discardRemoteClone(
        workspaceId: String,
    ) = withContext(Dispatchers.IO) {
        val entity = workspaceDao.findById(workspaceId)
            ?: return@withContext

        if (entity.strategy != WorkspaceStrategy.REMOTE_CLONE.name) {
            return@withContext
        }

        entity.managedWorkspacePath
            ?.let(::File)
            ?.deleteRecursively()
        workspacePaths.metadataRoot(workspaceId)
            .deleteRecursively()
        workspaceDao.deleteById(workspaceId)
    }

    suspend fun bindRepository(
        workspaceId: String,
        remoteUrl: String?,
        currentBranch: String?,
        accountId: Long?,
    ): Workspace {
        val entity = workspaceDao.findById(workspaceId)
            ?: error("Workspace not found")

        val updated = entity.copy(
            repositoryRemote = remoteUrl ?: entity.repositoryRemote,
            currentBranch = currentBranch ?: entity.currentBranch,
            accountId = accountId?.toString() ?: entity.accountId,
            lastOpenedAtEpochMillis = System.currentTimeMillis(),
        )
        workspaceDao.upsert(updated)
        return updated.toDomain()
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

            WorkspaceStrategy.REMOTE_CLONE -> {
                completeRemoteClone(
                    workspaceId = workspaceId,
                    currentBranch = entity.currentBranch,
                )
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

        val strategy = WorkspaceStrategy.valueOf(entity.strategy)
        if (strategy == WorkspaceStrategy.MANAGED ||
            strategy == WorkspaceStrategy.REMOTE_CLONE
        ) {
            entity.managedWorkspacePath
                ?.let(::File)
                ?.deleteRecursively()

            workspacePaths.metadataRoot(workspaceId)
                .deleteRecursively()
        }

        if (strategy == WorkspaceStrategy.MANAGED) {
            entity.sourceTreeUri
                ?.let(Uri::parse)
                ?.let(permissionManager::release)
        }

        workspaceDao.deleteById(workspaceId)
    }
}
