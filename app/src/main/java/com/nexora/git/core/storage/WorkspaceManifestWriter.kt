package com.nexora.git.core.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class WorkspaceManifestFile(
    val path: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val conflicted: Boolean = false,
)

data class WorkspaceSyncManifest(
    val syncedAtEpochMillis: Long,
    val files: Map<String, WorkspaceManifestFile>,
)

@Singleton
class WorkspaceManifestWriter @Inject constructor(
    private val workspacePaths: WorkspacePaths,
) {
    fun read(
        workspaceId: String,
    ): WorkspaceSyncManifest? {
        val target = workspacePaths.syncManifest(workspaceId)
        if (!target.isFile) return null

        return runCatching {
            val root = JSONObject(target.readText())
            val array = root.optJSONArray("files") ?: JSONArray()
            val files = buildMap {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val path = item.getString("path")
                    put(
                        path,
                        WorkspaceManifestFile(
                            path = path,
                            sizeBytes = item.optLong("size"),
                            lastModifiedEpochMillis =
                                item.optLong("lastModified"),
                            conflicted =
                                item.optBoolean("conflicted", false),
                        ),
                    )
                }
            }

            WorkspaceSyncManifest(
                syncedAtEpochMillis = root.optLong("syncedAt"),
                files = files,
            )
        }.getOrNull()
    }

    fun write(
        workspaceId: String,
        scan: ProjectScanResult,
        syncedAtEpochMillis: Long,
        conflictBaselines: Map<String, WorkspaceManifestFile> =
            emptyMap(),
    ) {
        val target = workspacePaths.syncManifest(workspaceId)
        target.parentFile?.mkdirs()

        val sourceFiles = scan.files.associateBy {
            it.relativePath
        }
        val allPaths = linkedSetOf<String>().apply {
            addAll(sourceFiles.keys)
            addAll(conflictBaselines.keys)
        }

        val files = JSONArray()
        allPaths.forEach { path ->
            val conflict = conflictBaselines[path]
            val source = sourceFiles[path]

            val entry = when {
                conflict != null -> conflict.copy(
                    path = path,
                    conflicted = true,
                )

                source != null -> WorkspaceManifestFile(
                    path = path,
                    sizeBytes = source.sizeBytes,
                    lastModifiedEpochMillis =
                        source.lastModifiedEpochMillis,
                    conflicted = false,
                )

                else -> return@forEach
            }

            files.put(
                JSONObject()
                    .put("path", entry.path)
                    .put("size", entry.sizeBytes)
                    .put(
                        "lastModified",
                        entry.lastModifiedEpochMillis,
                    )
                    .put("conflicted", entry.conflicted),
            )
        }

        val json = JSONObject()
            .put("version", 2)
            .put("syncedAt", syncedAtEpochMillis)
            .put("fileCount", scan.fileCount)
            .put("totalBytes", scan.totalBytes)
            .put("files", files)
            .toString()

        val parent = requireNotNull(target.parentFile)
        val temp = File(
            parent,
            target.name + ".tmp",
        )
        temp.writeText(json)

        check(temp.renameTo(target) || run {
            temp.copyTo(target, overwrite = true)
            temp.delete()
            true
        }) {
            "Unable to store workspace sync manifest"
        }
    }
}
