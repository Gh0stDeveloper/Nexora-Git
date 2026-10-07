package com.nexora.git.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Singleton
class SafWorkspaceSyncEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workspacePaths: WorkspacePaths,
    private val manifestWriter: WorkspaceManifestWriter,
) {

    suspend fun sync(
        workspaceId: String,
        sourceTreeUri: Uri,
        scan: ProjectScanResult,
    ): WorkspaceSyncResult = withContext(Dispatchers.IO) {
        val source = DocumentFile.fromTreeUri(context, sourceTreeUri)
            ?: error("Unable to resolve selected project folder")

        require(source.exists() && source.isDirectory) {
            "Selected project folder is no longer available"
        }

        val destination = workspacePaths.workspaceRoot(workspaceId)
        destination.mkdirs()

        require(destination.isDirectory) {
            "Managed workspace directory is unavailable"
        }

        val previousManifest = manifestWriter.read(workspaceId)
        val previousFiles = previousManifest?.files.orEmpty()

        val available = destination.usableSpace
        val requiredSpace = if (previousManifest == null) {
            scan.totalBytes + MIN_FREE_SPACE_BYTES
        } else {
            MIN_FREE_SPACE_BYTES
        }
        if (available > 0L && requiredSpace > available) {
            throw IOException("Not enough device storage for managed workspace")
        }
        val sourcePaths = scan.files
            .mapTo(linkedSetOf()) { it.relativePath }

        val preserveManagedGit =
            File(destination, ".git").exists()

        var copiedFiles = 0
        var skippedFiles = 0
        var deletedFiles = 0
        var copiedBytes = 0L
        val conflicts = linkedSetOf<String>()
        val conflictBaselines =
            linkedMapOf<String, WorkspaceManifestFile>()

        suspend fun copyDirectory(
            directory: DocumentFile,
            basePath: String,
        ) {
            coroutineContext.ensureActive()

            directory.listFiles()
                .sortedBy { it.name.orEmpty().lowercase() }
                .forEach { child ->
                    coroutineContext.ensureActive()

                    val name = child.name
                        ?.takeIf(::isSafeName)
                        ?: return@forEach

                    val relativePath = if (basePath.isBlank()) {
                        name
                    } else {
                        "$basePath/$name"
                    }

                    if (preserveManagedGit &&
                        isGitMetadataPath(relativePath)
                    ) {
                        return@forEach
                    }

                    val target = safeTarget(
                        root = destination,
                        relativePath = relativePath,
                    )

                    if (child.isDirectory) {
                        target.mkdirs()
                        copyDirectory(child, relativePath)
                        return@forEach
                    }

                    if (!child.isFile) return@forEach

                    val sourceSize = child.length().coerceAtLeast(0L)
                    val sourceModified =
                        child.lastModified().coerceAtLeast(0L)
                    val previous = previousFiles[relativePath]

                    if (target.isFile) {
                        if (previous?.conflicted == true) {
                            conflicts += relativePath
                            conflictBaselines[relativePath] = previous
                            skippedFiles += 1
                            return@forEach
                        }

                        val managedChanged = hasManagedFileChanged(
                            target = target,
                            previous = previous,
                        )
                        val sourceChanged = hasSourceFileChanged(
                            sourceSize = sourceSize,
                            sourceModified = sourceModified,
                            previous = previous,
                        )

                        if (managedChanged && sourceChanged) {
                            conflicts += relativePath
                            conflictBaselines[relativePath] =
                                previous ?: WorkspaceManifestFile(
                                    path = relativePath,
                                    sizeBytes = target.length(),
                                    lastModifiedEpochMillis =
                                        target.lastModified(),
                                    conflicted = true,
                                )
                            skippedFiles += 1
                            return@forEach
                        }

                        if (managedChanged && !sourceChanged) {
                            skippedFiles += 1
                            return@forEach
                        }

                        if (!sourceChanged &&
                            matchesSourceMetadata(
                                target = target,
                                sourceSize = sourceSize,
                                sourceModified = sourceModified,
                            )
                        ) {
                            skippedFiles += 1
                            return@forEach
                        }
                    }

                    copyFile(
                        source = child,
                        target = target,
                        relativePath = relativePath,
                    )

                    if (sourceModified > 0L) {
                        target.setLastModified(sourceModified)
                    }

                    copiedFiles += 1
                    copiedBytes += sourceSize
                }
        }

        copyDirectory(source, "")

        previousFiles.forEach { (relativePath, previous) ->
            coroutineContext.ensureActive()

            if (relativePath in sourcePaths) {
                return@forEach
            }

            if (preserveManagedGit &&
                isGitMetadataPath(relativePath)
            ) {
                return@forEach
            }

            val target = safeTarget(
                root = destination,
                relativePath = relativePath,
            )

            if (!target.exists()) return@forEach

            if (previous.conflicted ||
                hasManagedFileChanged(target, previous)
            ) {
                conflicts += relativePath
                conflictBaselines[relativePath] = previous.copy(
                    conflicted = true,
                )
                return@forEach
            }

            if (target.isFile && target.delete()) {
                deletedFiles += 1
            }
        }

        val completedAt = System.currentTimeMillis()
        manifestWriter.write(
            workspaceId = workspaceId,
            scan = scan,
            syncedAtEpochMillis = completedAt,
            conflictBaselines = conflictBaselines,
        )

        WorkspaceSyncResult(
            copiedFiles = copiedFiles,
            skippedFiles = skippedFiles,
            deletedFiles = deletedFiles,
            copiedBytes = copiedBytes,
            conflictedPaths = conflicts.toList(),
            completedAtEpochMillis = completedAt,
        )
    }

    private fun hasManagedFileChanged(
        target: File,
        previous: WorkspaceManifestFile?,
    ): Boolean {
        if (previous == null) return target.exists()
        if (!target.isFile) return true
        if (target.length() != previous.sizeBytes) return true

        return previous.lastModifiedEpochMillis > 0L &&
            target.lastModified() != previous.lastModifiedEpochMillis
    }

    private fun hasSourceFileChanged(
        sourceSize: Long,
        sourceModified: Long,
        previous: WorkspaceManifestFile?,
    ): Boolean {
        if (previous == null) return true
        if (sourceSize != previous.sizeBytes) return true

        if (sourceModified <= 0L ||
            previous.lastModifiedEpochMillis <= 0L
        ) {
            return false
        }

        return sourceModified != previous.lastModifiedEpochMillis
    }

    private fun matchesSourceMetadata(
        target: File,
        sourceSize: Long,
        sourceModified: Long,
    ): Boolean =
        target.isFile &&
            target.length() == sourceSize &&
            (
                sourceModified <= 0L ||
                    target.lastModified() == sourceModified
                )

    private fun copyFile(
        source: DocumentFile,
        target: File,
        relativePath: String,
    ) {
        target.parentFile?.mkdirs()

        val parent = requireNotNull(target.parentFile)
        val temp = File(
            parent,
            "." + target.name + ".nexora-copy.tmp",
        )

        try {
            context.contentResolver
                .openInputStream(source.uri)
                ?.use { input ->
                    temp.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                ?: throw IOException(
                    "Unable to read " + relativePath,
                )

            if (target.exists() && !target.delete()) {
                throw IOException(
                    "Unable to replace " + relativePath,
                )
            }

            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            if (temp.exists()) {
                temp.delete()
            }
        }
    }

    private fun safeTarget(
        root: File,
        relativePath: String,
    ): File {
        val rootPath = root.canonicalPath + File.separator
        val target = File(root, relativePath).canonicalFile

        require(target.canonicalPath.startsWith(rootPath)) {
            "Unsafe project path"
        }

        return target
    }

    private fun isGitMetadataPath(relativePath: String): Boolean =
        relativePath == ".git" ||
            relativePath.startsWith(".git/")

    private fun isSafeName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name

    companion object {
        private const val MIN_FREE_SPACE_BYTES =
            32L * 1024L * 1024L
    }
}
