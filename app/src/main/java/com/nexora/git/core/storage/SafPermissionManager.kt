package com.nexora.git.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PersistedTreePermission(
    val uri: Uri,
    val readable: Boolean,
    val writable: Boolean,
)

@Singleton
class SafPermissionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver
        get() = context.contentResolver

    fun persist(uri: Uri): PersistedTreePermission {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "SAF tree URI must use content://"
        }

        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val writable = runCatching {
            resolver.takePersistableUriPermission(uri, read or write)
            true
        }.getOrElse {
            resolver.takePersistableUriPermission(uri, read)
            false
        }

        return permissionFor(uri)?.copy(writable = writable)
            ?: PersistedTreePermission(
                uri = uri,
                readable = true,
                writable = writable,
            )
    }

    fun permissionFor(uri: Uri): PersistedTreePermission? {
        val permission = resolver.persistedUriPermissions.firstOrNull {
            it.uri == uri
        } ?: return null

        return PersistedTreePermission(
            uri = uri,
            readable = permission.isReadPermission,
            writable = permission.isWritePermission,
        )
    }

    fun release(uri: Uri) {
        val permission = permissionFor(uri) ?: return
        var flags = 0
        if (permission.readable) {
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        if (permission.writable) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        if (flags != 0) {
            runCatching {
                resolver.releasePersistableUriPermission(uri, flags)
            }
        }
    }
}
