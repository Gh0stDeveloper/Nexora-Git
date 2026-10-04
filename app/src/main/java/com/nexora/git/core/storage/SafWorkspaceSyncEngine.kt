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
    @ApplicationContext private val context: Context,
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

        val available = destination.usableSpace
        if (available > 0L &&
            scan.totalBytes + MIN_FREE_SPACE_BYTES > available
        ) {
            throw IOException("Not enough device storage for managed workspace")
        }

        val preserveManagedGit =
            File(destination, ".git").exists()

        val knownPaths = linkedSetOf<String>()
        var copiedFiles = 0
        var skippedFiles = 0
        var copiedBytes = 0L

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
                        (relativePath == ".git" ||
                            relativePath.startsWith(".git/"))
                    ) {
                        return@forEach
                    }

                    val target = safeTarget(
                        root = destination,
                        relativePath = relativePath,
                    )

                    if (child.isDirectory) {
                        knownPaths += relativePath
                        target.mkdirs()
                        copyDirectory(child, relativePath)
                        return@forEach
                    }

                    if (!child.isFile) return@forEach

                    knownPaths += relativePath

                    val sourceSize = child.length().coerceAtLeast(0L)
                    val sourceModified =
                        child.lastModified().coerceAtLeast(0L)

                    val unchanged = target.isFile &&
                        target.length() == sourceSize &&
                        sourceModified > 0L &&
                        target.lastModified() == sourceModified

                    if (unchanged) {
                        skippedFiles += 1
                        return@forEach
                    }

                    target.parentFile?.mkdirs()
                    val temp = File(
                        target.parentFile,
                        "." + target.name + ".nexora-copy.tmp",
                    )

                    context.contentResolver
                        .openInputStream(child.uri)
                        ?.use { input ->
                            temp.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                        ?: throw IOException(
                            "Unable to read " + relativePath,
                        )

                    if (target.exists() && !target.delete()) {
                        temp.delete()
                        throw IOException(
                            "Unable to replace " + relativePath,
                        )
                    }

                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }

                    if (sourceModified > 0L) {
                        target.setLastModified(sourceModified)
                    }

                    copiedFiles += 1
                    copiedBytes += sourceSize
                }
        }

        copyDirectory(source, "")

        var deletedFiles = 0
        destination.walkBottomUp().forEach { target ->
            coroutineContext.ensureActive()
            if (target == destination) return@forEach

            val relative = target.relativeTo(destination)
                .invariantSeparatorsPath

            if (preserveManagedGit &&
                (relative == ".git" || relative.startsWith(".git/"))
            ) {
                return@forEach
            }

            if (relative.endsWith(".nexora-copy.tmp")) {
                target.delete()
                return@forEach
            }

            if (relative !in knownPaths) {
                if (target.isFile) {
                    if (target.delete()) {
                        deletedFiles += 1
                    }
                } else if (target.isDirectory) {
                    target.delete()
                }
            }
        }

        val completedAt = System.currentTimeMillis()
        manifestWriter.write(
            workspaceId = workspaceId,
            scan = scan,
            syncedAtEpochMillis = completedAt,
        )

        WorkspaceSyncResult(
            copiedFiles = copiedFiles,
            skippedFiles = skippedFiles,
            deletedFiles = deletedFiles,
            copiedBytes = copiedBytes,
            completedAtEpochMillis = completedAt,
        )
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
