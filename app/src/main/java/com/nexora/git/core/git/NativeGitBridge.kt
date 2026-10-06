package com.nexora.git.core.git

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeGitBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
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

    external fun nativeRemotes(
        repositoryPath: String,
    ): String

    external fun nativeAddRemote(
        repositoryPath: String,
        name: String,
        url: String,
    )

    external fun nativeRenameRemote(
        repositoryPath: String,
        oldName: String,
        newName: String,
    )

    external fun nativeRemoveRemote(
        repositoryPath: String,
        name: String,
    )

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

    external fun nativeSetUpstream(
        repositoryPath: String,
        branch: String,
        upstream: String,
    )

    external fun nativeDivergence(
        repositoryPath: String,
        localRef: String,
        upstreamRef: String,
    ): String

    external fun nativeRepositoryState(
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
        strategy: String,
        authorName: String,
        authorEmail: String,
        username: String,
        password: String,
    ): String

    external fun nativeContinueMerge(
        repositoryPath: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeContinueRebase(
        repositoryPath: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeAbortRebase(
        repositoryPath: String,
    )

    external fun nativePush(
        repositoryPath: String,
        remote: String,
        refspec: String,
        username: String,
        password: String,
    ): String

    external fun nativePushForceWithLease(
        repositoryPath: String,
        remote: String,
        refspec: String,
        expectedRemoteOid: String,
        username: String,
        password: String,
    ): String

    external fun nativeDiff(
        repositoryPath: String,
        mode: String,
        relativePath: String,
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

    external fun nativeResolveConflict(
        repositoryPath: String,
        path: String,
        resolution: String,
    )

    external fun nativeHistory(
        repositoryPath: String,
        relativePath: String,
        limit: Int,
    ): String


    external fun nativeRebase(
        repositoryPath: String,
        upstreamRef: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeCherryPick(
        repositoryPath: String,
        commitRef: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeContinueCherryPick(
        repositoryPath: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeAbortCherryPick(
        repositoryPath: String,
    )

    external fun nativeStashes(
        repositoryPath: String,
    ): String

    external fun nativeSaveStash(
        repositoryPath: String,
        message: String,
        authorName: String,
        authorEmail: String,
        includeUntracked: Boolean,
    ): String

    external fun nativeApplyStash(
        repositoryPath: String,
        index: Int,
        pop: Boolean,
    )

    external fun nativeDropStash(
        repositoryPath: String,
        index: Int,
    )

    external fun nativeReset(
        repositoryPath: String,
        targetRef: String,
        mode: String,
    )

    external fun nativeRevert(
        repositoryPath: String,
        commitRef: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeContinueRevert(
        repositoryPath: String,
        authorName: String,
        authorEmail: String,
    ): String

    external fun nativeAbortRevert(
        repositoryPath: String,
    )

    external fun nativeTags(
        repositoryPath: String,
    ): String

    external fun nativeCreateTag(
        repositoryPath: String,
        name: String,
        targetRef: String,
        message: String,
        authorName: String,
        authorEmail: String,
        annotated: Boolean,
    ): String

    external fun nativeDeleteTag(
        repositoryPath: String,
        name: String,
    )

    external fun nativeSubmodules(
        repositoryPath: String,
    ): String

    external fun nativeSyncSubmodule(
        repositoryPath: String,
        name: String,
    )

    external fun nativeUpdateSubmodule(
        repositoryPath: String,
        name: String,
        initialize: Boolean,
        username: String,
        password: String,
    )

    external fun nativeBlame(
        repositoryPath: String,
        relativePath: String,
    ): String

    companion object {
        private const val ANDROID_CA_DIRECTORY =
            "/system/etc/security/cacerts"

        init {
            System.loadLibrary("nexoragit_native")
        }
    }
}
