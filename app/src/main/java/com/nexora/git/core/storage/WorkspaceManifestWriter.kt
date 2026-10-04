package com.nexora.git.core.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class WorkspaceManifestWriter @Inject constructor(
    private val workspacePaths: WorkspacePaths,
) {
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

        val temp = File(
            target.parentFile,
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
