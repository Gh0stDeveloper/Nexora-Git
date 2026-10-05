package com.nexora.git.core.editor

import android.util.AtomicFile
import com.nexora.git.core.files.BrowserFileKind
import com.nexora.git.core.files.CodeBrowserFileSystem
import com.nexora.git.core.files.WorkspacePathPolicy
import com.nexora.git.core.storage.WorkspaceRegistry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedEditorFile(
    val lastModifiedEpochMillis: Long,
    val sizeBytes: Long,
)

@Singleton
class EditorFileStore @Inject constructor(
    private val workspaceRegistry: WorkspaceRegistry,
    private val fileSystem: CodeBrowserFileSystem,
    private val pathPolicy: WorkspacePathPolicy,
) {

    suspend fun load(
        workspaceId: String,
        relativePath: String,
    ) = withContext(Dispatchers.IO) {
        val file = fileSystem.read(
            workspaceId = workspaceId,
            relativePath = relativePath,
        )

        require(
            file.kind == BrowserFileKind.TEXT ||
                file.kind == BrowserFileKind.MARKDOWN,
        ) {
            "Only text files can be edited."
        }

        require(!file.truncated) {
            "This file is larger than the mobile editor safety limit."
        }

        require(file.sizeBytes <= MAX_EDITOR_BYTES) {
            "This file is larger than the mobile editor safety limit."
        }

        file
    }

    suspend fun save(
        workspaceId: String,
        relativePath: String,
        text: String,
        expectedLastModifiedEpochMillis: Long,
    ): SavedEditorFile = withContext(Dispatchers.IO) {
        val workspace = workspaceRegistry.findById(workspaceId)
            ?: error("Workspace not found")

        val root = File(workspace.workspacePath).canonicalFile
        val file = pathPolicy.resolve(
            root = root,
            relativePath = relativePath,
        )

        require(file.isFile) {
            "File is unavailable."
        }
        require(file.canWrite()) {
            "File is read-only."
        }

        val currentModified = file.lastModified()
            .coerceAtLeast(0L)

        require(
            currentModified == expectedLastModifiedEpochMillis,
        ) {
            "The file changed outside Nexora Git. Reload it before saving."
        }

        val encoded = text.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_EDITOR_BYTES) {
            "The edited file exceeds the mobile editor safety limit."
        }

        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()

        try {
            stream.write(encoded)
            stream.flush()
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }

        SavedEditorFile(
            lastModifiedEpochMillis =
                file.lastModified().coerceAtLeast(0L),
            sizeBytes = file.length().coerceAtLeast(0L),
        )
    }

    companion object {
        const val MAX_EDITOR_BYTES = 512L * 1024L
    }
}
