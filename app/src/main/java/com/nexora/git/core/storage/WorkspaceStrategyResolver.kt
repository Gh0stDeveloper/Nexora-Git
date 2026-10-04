package com.nexora.git.core.storage

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class WorkspaceLocation(
    val strategy: WorkspaceStrategy,
    val path: File?,
)

@Singleton
class WorkspaceStrategyResolver @Inject constructor() {

    fun resolve(uri: Uri): WorkspaceLocation =
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.canonicalFile

                if (path != null && path.isDirectory) {
                    WorkspaceLocation(
                        strategy = WorkspaceStrategy.DIRECT,
                        path = path,
                    )
                } else {
                    WorkspaceLocation(
                        strategy = WorkspaceStrategy.MANAGED,
                        path = null,
                    )
                }
            }

            else -> WorkspaceLocation(
                strategy = WorkspaceStrategy.MANAGED,
                path = null,
            )
        }
}
