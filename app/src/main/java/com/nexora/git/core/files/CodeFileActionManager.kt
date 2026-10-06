package com.nexora.git.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CodeFileActionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileSystem: CodeBrowserFileSystem,
) {

    suspend fun createShareIntent(
        workspaceId: String,
        relativePath: String,
        mimeType: String,
    ): Intent = withContext(Dispatchers.IO) {
        val source = fileSystem.resolveShareableFile(
            workspaceId,
            relativePath,
        )

        val shareRoot = File(
            context.cacheDir,
            "code-share",
        ).apply {
            mkdirs()
        }

        shareRoot.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .forEach(File::delete)

        val target = File(
            shareRoot,
            safeShareName(source.name),
        )
        source.copyTo(target, overwrite = true)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".files",
            target,
        )

        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun exportTo(
        workspaceId: String,
        relativePath: String,
        destination: Uri,
    ) = withContext(Dispatchers.IO) {
        val source = fileSystem.resolveShareableFile(
            workspaceId,
            relativePath,
        )

        val output = context.contentResolver
            .openOutputStream(destination, "wt")
            ?: error("Unable to open export destination")

        output.buffered().use { stream ->
            source.inputStream().buffered().use { input ->
                input.copyTo(stream)
            }
        }
    }

    private fun safeShareName(name: String): String =
        name.replace(
            Regex("[^A-Za-z0-9._-]"),
            "_",
        ).take(128).ifBlank {
            "file"
        }
}
