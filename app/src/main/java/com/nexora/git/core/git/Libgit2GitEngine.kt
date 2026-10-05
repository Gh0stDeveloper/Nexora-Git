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
            .credentialsFor(request.url)
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
