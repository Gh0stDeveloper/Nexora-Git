package com.nexora.git.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspacePaths @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun workspaceRoot(workspaceId: String): File =
        File(context.filesDir, "workspaces/$workspaceId/repo")

    fun metadataRoot(workspaceId: String): File =
        File(context.filesDir, "workspace-meta/$workspaceId")

    fun syncManifest(workspaceId: String): File =
        File(metadataRoot(workspaceId), "sync-manifest.json")
}
