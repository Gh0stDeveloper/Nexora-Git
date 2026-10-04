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
    ) {
        val target = workspacePaths.syncManifest(workspaceId)
        target.parentFile?.mkdirs()

        val files = JSONArray()
        scan.files.forEach { file ->
            files.put(
                JSONObject()
                    .put("path", file.relativePath)
                    .put("size", file.sizeBytes)
                    .put(
                        "lastModified",
                        file.lastModifiedEpochMillis,
                    )
                    .put("ignoredByGit", file.ignoredByGit),
            )
        }

        val json = JSONObject()
            .put("version", 1)
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
