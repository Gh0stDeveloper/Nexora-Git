package com.nexora.git.core.git

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class Libgit2GitEngine @Inject constructor(
    private val bridge: NativeGitBridge,
    private val parser: GitJsonParser,
    private val credentialProvider: GitCredentialProvider,
) : GitEngine {

    override suspend fun version(): String = native {
        bridge.nativeVersion()
    }

    override suspend fun init(
        workspacePath: String,
    ): GitRepository = native {
        parser.repository(
            bridge.nativeInitRepository(workspacePath),
        )
    }

    override suspend fun clone(
        request: GitCloneRequest,
    ): GitRepository = native {
        val credentials = credentialProvider
            .credentialsFor(
                remoteUrl = request.url,
                accountId = request.accountId,
            )
            .orEmpty()

        parser.repository(
            bridge.nativeClone(
                url = request.url,
                destination = request.destinationPath,
                username = credentials.username,
                password = credentials.password,
            ),
        )
    }

    override suspend fun remoteUrl(
        repositoryPath: String,
        remote: String,
    ): String? = native {
        bridge.nativeRemoteUrl(
            repositoryPath = repositoryPath,
            remote = remote,
        ).takeIf { it.isNotBlank() }
    }

    override suspend fun remotes(
        repositoryPath: String,
    ): List<GitRemote> = native {
        parser.remotes(
            bridge.nativeRemotes(repositoryPath),
        )
    }

    override suspend fun addRemote(
        repositoryPath: String,
        name: String,
        url: String,
    ) {
        native {
            bridge.nativeAddRemote(
                repositoryPath = repositoryPath,
                name = name,
                url = url,
            )
        }
    }

    override suspend fun renameRemote(
        repositoryPath: String,
        oldName: String,
        newName: String,
    ) {
        native {
            bridge.nativeRenameRemote(
                repositoryPath = repositoryPath,
                oldName = oldName,
                newName = newName,
            )
        }
    }

    override suspend fun removeRemote(
        repositoryPath: String,
        name: String,
    ) {
        native {
            bridge.nativeRemoveRemote(
                repositoryPath = repositoryPath,
                name = name,
            )
        }
    }

    override suspend fun status(
        repositoryPath: String,
    ): GitStatus = native {
        parser.status(
            bridge.nativeStatus(repositoryPath),
        )
    }

    override suspend fun stage(
        repositoryPath: String,
        paths: List<String>,
    ) {
        native {
            bridge.nativeStage(
                repositoryPath = repositoryPath,
                paths = paths.toTypedArray(),
            )
        }
    }

    override suspend fun unstage(
        repositoryPath: String,
        paths: List<String>,
    ) {
        native {
            bridge.nativeUnstage(
                repositoryPath = repositoryPath,
                paths = paths.toTypedArray(),
            )
        }
    }

    override suspend fun commit(
        repositoryPath: String,
        message: String,
        author: GitAuthor,
    ): GitCommit = native {
        parser.commit(
            bridge.nativeCommit(
                repositoryPath = repositoryPath,
                message = message,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun branches(
        repositoryPath: String,
    ): List<GitBranch> = native {
        parser.branches(
            bridge.nativeBranches(repositoryPath),
        )
    }

    override suspend fun setUpstream(
        repositoryPath: String,
        branch: String,
        upstream: String?,
    ) {
        native {
            bridge.nativeSetUpstream(
                repositoryPath = repositoryPath,
                branch = branch,
                upstream = upstream.orEmpty(),
            )
        }
    }

    override suspend fun divergence(
        repositoryPath: String,
        localRef: String,
        upstreamRef: String,
    ): GitDivergence = native {
        parser.divergence(
            bridge.nativeDivergence(
                repositoryPath = repositoryPath,
                localRef = localRef,
                upstreamRef = upstreamRef,
            ),
        )
    }

    override suspend fun repositoryState(
        repositoryPath: String,
    ): GitRepositoryOperationState = native {
        parser.repositoryState(
            bridge.nativeRepositoryState(repositoryPath),
        )
    }

    override suspend fun createBranch(
        repositoryPath: String,
        name: String,
        startPoint: String?,
    ) {
        native {
            bridge.nativeCreateBranch(
                repositoryPath = repositoryPath,
                name = name,
                startPoint = startPoint.orEmpty(),
            )
        }
    }

    override suspend fun checkout(
        repositoryPath: String,
        ref: String,
    ) {
        native {
            bridge.nativeCheckout(
                repositoryPath = repositoryPath,
                ref = ref,
            )
        }
    }

    override suspend fun fetch(
        repositoryPath: String,
        remote: String,
    ) {
        native {
            val credentials = credentialsForRemote(
                repositoryPath,
                remote,
            )

            bridge.nativeFetch(
                repositoryPath = repositoryPath,
                remote = remote,
                username = credentials.username,
                password = credentials.password,
            )
        }
    }

    override suspend fun pull(
        request: GitPullRequest,
    ): GitMergeResult = native {
        val credentials = credentialsForRemote(
            request.repositoryPath,
            request.remote,
        )

        parser.mergeResult(
            bridge.nativePull(
                repositoryPath = request.repositoryPath,
                remote = request.remote,
                strategy = request.strategy.wireValue,
                authorName = request.author.name,
                authorEmail = request.author.email,
                username = credentials.username,
                password = credentials.password,
            ),
        )
    }

    override suspend fun continueMerge(
        repositoryPath: String,
        author: GitAuthor,
    ): GitMergeResult = native {
        parser.mergeResult(
            bridge.nativeContinueMerge(
                repositoryPath = repositoryPath,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun continueRebase(
        repositoryPath: String,
        author: GitAuthor,
    ): GitMergeResult = native {
        parser.mergeResult(
            bridge.nativeContinueRebase(
                repositoryPath = repositoryPath,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun abortRebase(
        repositoryPath: String,
    ) {
        native {
            bridge.nativeAbortRebase(repositoryPath)
        }
    }

    override suspend fun push(
        request: GitPushRequest,
    ): GitPushResult = native {
        val credentials = credentialsForRemote(
            request.repositoryPath,
            request.remote,
        )

        val json = if (request.forceWithLease) {
            require(request.expectedRemoteOid.isNotBlank()) {
                "Force-with-lease requires the expected remote OID."
            }
            bridge.nativePushForceWithLease(
                repositoryPath = request.repositoryPath,
                remote = request.remote,
                refspec = request.refspec,
                expectedRemoteOid = request.expectedRemoteOid,
                username = credentials.username,
                password = credentials.password,
            )
        } else {
            bridge.nativePush(
                repositoryPath = request.repositoryPath,
                remote = request.remote,
                refspec = request.refspec,
                username = credentials.username,
                password = credentials.password,
            )
        }

        parser.pushResult(json)
    }

    override suspend fun diff(
        repositoryPath: String,
        mode: GitDiffMode,
        relativePath: String?,
    ): GitDiff = native {
        parser.diff(
            bridge.nativeDiff(
                repositoryPath = repositoryPath,
                mode = mode.wireValue,
                relativePath = relativePath.orEmpty(),
            ),
        )
    }

    override suspend fun merge(
        repositoryPath: String,
        ref: String,
        author: GitAuthor,
    ): GitMergeResult = native {
        parser.mergeResult(
            bridge.nativeMerge(
                repositoryPath = repositoryPath,
                ref = ref,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun conflicts(
        repositoryPath: String,
    ): List<GitConflict> = native {
        parser.conflicts(
            bridge.nativeConflicts(repositoryPath),
        )
    }

    override suspend fun resolveConflict(
        repositoryPath: String,
        path: String,
        resolution: GitConflictResolution,
    ) {
        native {
            bridge.nativeResolveConflict(
                repositoryPath = repositoryPath,
                path = path,
                resolution = resolution.wireValue,
            )
        }
    }

    override suspend fun history(
        repositoryPath: String,
        relativePath: String?,
        limit: Int,
    ): List<GitHistoryEntry> = native {
        parser.history(
            bridge.nativeHistory(
                repositoryPath = repositoryPath,
                relativePath = relativePath.orEmpty(),
                limit = limit.coerceIn(1, 200),
            ),
        )
    }


    override suspend fun rebase(
        repositoryPath: String,
        upstreamRef: String,
        author: GitAuthor,
    ): GitMergeResult = native {
        parser.mergeResult(
            bridge.nativeRebase(
                repositoryPath = repositoryPath,
                upstreamRef = upstreamRef,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun cherryPick(
        repositoryPath: String,
        commitRef: String,
        author: GitAuthor,
    ): GitApplyResult = native {
        parser.applyResult(
            bridge.nativeCherryPick(
                repositoryPath = repositoryPath,
                commitRef = commitRef,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun continueCherryPick(
        repositoryPath: String,
        author: GitAuthor,
    ): GitApplyResult = native {
        parser.applyResult(
            bridge.nativeContinueCherryPick(
                repositoryPath = repositoryPath,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun abortCherryPick(
        repositoryPath: String,
    ) {
        native {
            bridge.nativeAbortCherryPick(repositoryPath)
        }
    }

    override suspend fun stashes(
        repositoryPath: String,
    ): List<GitStash> = native {
        parser.stashes(
            bridge.nativeStashes(repositoryPath),
        )
    }

    override suspend fun saveStash(
        repositoryPath: String,
        message: String,
        author: GitAuthor,
        includeUntracked: Boolean,
    ): String = native {
        bridge.nativeSaveStash(
            repositoryPath = repositoryPath,
            message = message,
            authorName = author.name,
            authorEmail = author.email,
            includeUntracked = includeUntracked,
        )
    }

    override suspend fun applyStash(
        repositoryPath: String,
        index: Int,
        pop: Boolean,
    ) {
        native {
            require(index >= 0) {
                "Stash index must be non-negative."
            }
            bridge.nativeApplyStash(
                repositoryPath = repositoryPath,
                index = index,
                pop = pop,
            )
        }
    }

    override suspend fun dropStash(
        repositoryPath: String,
        index: Int,
    ) {
        native {
            require(index >= 0) {
                "Stash index must be non-negative."
            }
            bridge.nativeDropStash(
                repositoryPath = repositoryPath,
                index = index,
            )
        }
    }

    override suspend fun reset(
        repositoryPath: String,
        targetRef: String,
        mode: GitResetMode,
    ) {
        native {
            bridge.nativeReset(
                repositoryPath = repositoryPath,
                targetRef = targetRef,
                mode = mode.wireValue,
            )
        }
    }

    override suspend fun revert(
        repositoryPath: String,
        commitRef: String,
        author: GitAuthor,
    ): GitApplyResult = native {
        parser.applyResult(
            bridge.nativeRevert(
                repositoryPath = repositoryPath,
                commitRef = commitRef,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun continueRevert(
        repositoryPath: String,
        author: GitAuthor,
    ): GitApplyResult = native {
        parser.applyResult(
            bridge.nativeContinueRevert(
                repositoryPath = repositoryPath,
                authorName = author.name,
                authorEmail = author.email,
            ),
        )
    }

    override suspend fun abortRevert(
        repositoryPath: String,
    ) {
        native {
            bridge.nativeAbortRevert(repositoryPath)
        }
    }

    override suspend fun tags(
        repositoryPath: String,
    ): List<GitTag> = native {
        parser.tags(
            bridge.nativeTags(repositoryPath),
        )
    }

    override suspend fun createTag(
        repositoryPath: String,
        name: String,
        targetRef: String,
        message: String,
        author: GitAuthor,
        annotated: Boolean,
    ): String = native {
        bridge.nativeCreateTag(
            repositoryPath = repositoryPath,
            name = name,
            targetRef = targetRef,
            message = message,
            authorName = author.name,
            authorEmail = author.email,
            annotated = annotated,
        )
    }

    override suspend fun deleteTag(
        repositoryPath: String,
        name: String,
    ) {
        native {
            bridge.nativeDeleteTag(
                repositoryPath = repositoryPath,
                name = name,
            )
        }
    }

    override suspend fun submodules(
        repositoryPath: String,
    ): List<GitSubmodule> = native {
        parser.submodules(
            bridge.nativeSubmodules(repositoryPath),
        )
    }

    override suspend fun syncSubmodule(
        repositoryPath: String,
        name: String,
    ) {
        native {
            bridge.nativeSyncSubmodule(
                repositoryPath = repositoryPath,
                name = name,
            )
        }
    }

    override suspend fun updateSubmodule(
        repositoryPath: String,
        name: String,
        initialize: Boolean,
    ) {
        native {
            val submodule = parser.submodules(
                bridge.nativeSubmodules(repositoryPath),
            ).firstOrNull { it.name == name }
                ?: error("Submodule not found.")

            val credentials = credentialProvider
                .credentialsFor(submodule.url)
                .orEmpty()

            bridge.nativeUpdateSubmodule(
                repositoryPath = repositoryPath,
                name = name,
                initialize = initialize,
                username = credentials.username,
                password = credentials.password,
            )
        }
    }

    override suspend fun blame(
        repositoryPath: String,
        relativePath: String,
    ): List<GitBlameHunk> = native {
        parser.blame(
            bridge.nativeBlame(
                repositoryPath = repositoryPath,
                relativePath = relativePath,
            ),
        )
    }

    private suspend fun credentialsForRemote(
        repositoryPath: String,
        remote: String,
    ): GitTransportCredentials {
        val url = bridge.nativeRemoteUrl(
            repositoryPath,
            remote,
        )

        return credentialProvider
            .credentialsFor(url)
            .orEmpty()
    }

    private suspend fun <T> native(
        operation: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        bridge.ensureInitialized()
        operation()
    }

    private fun GitTransportCredentials?.orEmpty():
        GitTransportCredentials = this ?: GitTransportCredentials(
        username = "",
        password = "",
    )
}
