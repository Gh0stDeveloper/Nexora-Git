package com.nexora.git.core.git

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeGitBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            nativeInitialize(
                homeDirectory = context.filesDir.absolutePath,
                certificateDirectory = ANDROID_CA_DIRECTORY,
            )
            initialized = true
        }
    }

    external fun nativeInitialize(
        homeDirectory: String,
        certificateDirectory: String,
    )

    external fun nativeVersion(): String

    external fun nativeInitRepository(
        path: String,
    ): String

    external fun nativeClone(
        url: String,
        destination: String,
        username: String,
        password: String,
    ): String

    external fun nativeRemoteUrl(
        repositoryPath: String,
        remote: String,
    ): String

    external fun nativeStatus(
        repositoryPath: String,
    ): String

    external fun nativeStage(
        repositoryPath: String,
        paths: Array<String>,
    )

    external fun nativeUnstage(
        repositoryPath: String,
        paths: Array<String>,
    )

    external fun nativeCommit(
        repositoryPath: String,
        message: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeBranches(
        repositoryPath: String,
    ): String

    external fun nativeCreateBranch(
        repositoryPath: String,
        name: String,
        startPoint: String,
    )

    external fun nativeCheckout(
        repositoryPath: String,
        ref: String,
    )

    external fun nativeFetch(
        repositoryPath: String,
        remote: String,
        username: String,
        password: String,
    )

    external fun nativePull(
        repositoryPath: String,
        remote: String,
        authorName: String,
        authorEmail: String,
        username: String,
        password: String,
    ): String

    external fun nativePush(
        repositoryPath: String,
        remote: String,
        refspec: String,
        username: String,
        password: String,
    ): String

    external fun nativeDiff(
        repositoryPath: String,
        mode: String,
    ): String

    external fun nativeMerge(
        repositoryPath: String,
        ref: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeConflicts(
        repositoryPath: String,
    ): String

    companion object {
        private const val ANDROID_CA_DIRECTORY =
            "/system/etc/security/cacerts"

        init {
            System.loadLibrary("nexoragit_native")
        }
    }
}
