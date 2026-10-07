package com.nexora.git.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nexora.git.core.git.GitAuthor
import com.nexora.git.core.git.GitPullRequest
import com.nexora.git.core.git.GitPullStrategy
import com.nexora.git.core.git.GitPushRequest
import dagger.hilt.android.EntryPointAccessors

class DurableGitWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val workspaceId = inputData.getString(KEY_WORKSPACE_ID).orEmpty()
        val operation = inputData.getString(KEY_OPERATION)
            ?.let { runCatching { Operation.valueOf(it) }.getOrNull() }
            ?: return failure("Invalid durable Git operation.")
        val remote = inputData.getString(KEY_REMOTE)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "origin"

        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            DurableWorkEntryPoint::class.java,
        )
        val workspace = dependencies.workspaceRegistry()
            .findById(workspaceId)
            ?: return failure("Workspace no longer exists.")
        val path = workspace.workspacePath
        val git = dependencies.gitEngine()

        return runCatching {
            setProgress(workDataOf(KEY_PHASE to operation.name.lowercase()))
            when (operation) {
                Operation.FETCH -> {
                    git.fetch(path, remote)
                    "Fetched " + remote + "."
                }

                Operation.PULL -> {
                    val author = requiredAuthor()
                    val strategy = inputData.getString(KEY_PULL_STRATEGY)
                        ?.let { GitPullStrategy.valueOf(it) }
                        ?: GitPullStrategy.MERGE
                    val result = git.pull(
                        GitPullRequest(
                            repositoryPath = path,
                            remote = remote,
                            author = author,
                            strategy = strategy,
                        ),
                    )
                    if (result.conflicts.isEmpty()) {
                        "Pull completed: " +
                            result.state.name.lowercase().replace('_', ' ') + "."
                    } else {
                        "Pull completed with " +
                            result.conflicts.size +
                            " conflict(s)."
                    }
                }

                Operation.PUSH -> {
                    val status = git.status(path)
                    val current = status.branch.takeIf(String::isNotBlank)
                        ?: error("Push requires a checked-out local branch.")
                    val target = inputData.getString(KEY_TARGET_BRANCH)
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?: current
                    requireSafeBranch(current)
                    requireSafeBranch(target)
                    val force = inputData.getBoolean(KEY_FORCE_WITH_LEASE, false)
                    val expectedOid = if (force) {
                        git.divergence(
                            repositoryPath = path,
                            localRef = current,
                            upstreamRef = remote + "/" + target,
                        ).upstreamOid.also {
                            require(it.isNotBlank()) {
                                "Force-with-lease requires a verified remote-tracking branch. Fetch first."
                            }
                        }
                    } else {
                        ""
                    }

                    val remoteUrl = git.remoteUrl(path, remote)
                    if (remoteUrl != null &&
                        dependencies.gitLfsTransport().supportsRemote(remoteUrl)
                    ) {
                        setProgress(workDataOf(KEY_PHASE to "lfs-upload"))
                        dependencies.gitLfsTransport().uploadPending(
                            repositoryPath = path,
                            remoteUrl = remoteUrl,
                        )
                    }

                    setProgress(workDataOf(KEY_PHASE to "push"))
                    git.push(
                        GitPushRequest(
                            repositoryPath = path,
                            remote = remote,
                            refspec = "refs/heads/" + current +
                                ":refs/heads/" + target,
                            forceWithLease = force,
                            expectedRemoteOid = expectedOid,
                        ),
                    )

                    if (!force) {
                        runCatching {
                            git.fetch(path, remote)
                            git.setUpstream(
                                repositoryPath = path,
                                branch = current,
                                upstream = remote + "/" + target,
                            )
                        }
                    }
                    "Pushed " + current + " to " +
                        remote + "/" + target + "."
                }
            }
        }.fold(
            onSuccess = { message ->
                Result.success(workDataOf(KEY_MESSAGE to message))
            },
            onFailure = { error ->
                if (runAttemptCount < MAX_RETRIES &&
                    isRetryable(error.message.orEmpty())
                ) {
                    Result.retry()
                } else {
                    failure(error.message ?: "Durable Git operation failed.")
                }
            },
        )
    }

    private fun requiredAuthor(): GitAuthor {
        val name = inputData.getString(KEY_AUTHOR_NAME).orEmpty().trim()
        val email = inputData.getString(KEY_AUTHOR_EMAIL).orEmpty().trim()
        require(name.isNotBlank() && email.isNotBlank()) {
            "Git author name and email are required."
        }
        return GitAuthor(name, email)
    }

    private fun requireSafeBranch(value: String) {
        require(
            value.isNotBlank() &&
                value.length <= 255 &&
                !value.startsWith("-") &&
                !value.endsWith("/") &&
                !value.endsWith(".lock") &&
                ".." !in value &&
                "@{" !in value &&
                value.none {
                    it.isWhitespace() ||
                        it in setOf('~', '^', ':', '?', '*', '[', '\\') ||
                        it.code < 32
                },
        ) {
            "Invalid Git branch name."
        }
    }

    private fun isRetryable(message: String): Boolean {
        val value = message.lowercase()
        return "network" in value ||
            "timeout" in value ||
            "temporar" in value ||
            "connection" in value ||
            "rate limit" in value ||
            "503" in value ||
            "502" in value
    }

    private fun failure(message: String): Result =
        Result.failure(
            workDataOf(
                KEY_ERROR to message.take(MAX_ERROR_LENGTH),
            ),
        )

    enum class Operation {
        FETCH,
        PULL,
        PUSH,
    }

    companion object {
        const val TAG = "nexora-durable-git"
        const val KEY_WORKSPACE_ID = "workspace_id"
        const val KEY_OPERATION = "operation"
        const val KEY_REMOTE = "remote"
        const val KEY_PULL_STRATEGY = "pull_strategy"
        const val KEY_AUTHOR_NAME = "author_name"
        const val KEY_AUTHOR_EMAIL = "author_email"
        const val KEY_TARGET_BRANCH = "target_branch"
        const val KEY_FORCE_WITH_LEASE = "force_with_lease"
        const val KEY_PHASE = "phase"
        const val KEY_MESSAGE = "message"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 2
        private const val MAX_ERROR_LENGTH = 1_024
    }
}
