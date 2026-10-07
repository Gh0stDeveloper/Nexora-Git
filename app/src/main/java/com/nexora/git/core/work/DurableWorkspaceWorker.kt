package com.nexora.git.core.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.android.EntryPointAccessors

class DurableWorkspaceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val operation = inputData.getString(KEY_OPERATION)
            ?.let { runCatching { Operation.valueOf(it) }.getOrNull() }
            ?: return failure("Invalid workspace operation.")

        val registry = EntryPointAccessors.fromApplication(
            applicationContext,
            DurableWorkEntryPoint::class.java,
        ).workspaceRegistry()

        return runCatching {
            when (operation) {
                Operation.IMPORT_SAF -> {
                    val uri = inputData.getString(KEY_TREE_URI)
                        ?.let(Uri::parse)
                        ?: error("Project folder URI is missing.")
                    setProgress(workDataOf(KEY_PHASE to "import"))
                    val result = registry.importSafTree(uri)
                    Result.success(
                        workDataOf(
                            KEY_WORKSPACE_ID to result.workspace.id,
                            KEY_WORKSPACE_NAME to result.workspace.name,
                            KEY_RISK_COUNT to result.scan.risks.size,
                        ),
                    )
                }

                Operation.SYNC -> {
                    val workspaceId = inputData
                        .getString(KEY_WORKSPACE_ID)
                        .orEmpty()
                    require(workspaceId.isNotBlank()) {
                        "Workspace id is missing."
                    }
                    setProgress(workDataOf(KEY_PHASE to "sync"))
                    val result = registry.sync(workspaceId)
                    Result.success(
                        workDataOf(
                            KEY_WORKSPACE_ID to result.workspace.id,
                            KEY_WORKSPACE_NAME to result.workspace.name,
                            KEY_RISK_COUNT to result.scan.risks.size,
                        ),
                    )
                }
            }
        }.getOrElse { error ->
            if (runAttemptCount < MAX_RETRIES &&
                isRetryable(error.message.orEmpty())
            ) {
                Result.retry()
            } else {
                failure(error.message ?: "Workspace operation failed.")
            }
        }
    }

    private fun failure(message: String): Result =
        Result.failure(
            workDataOf(
                KEY_ERROR to message.take(MAX_ERROR_LENGTH),
            ),
        )

    private fun isRetryable(message: String): Boolean {
        val value = message.lowercase()
        return "temporar" in value ||
            "busy" in value ||
            "unavailable" in value ||
            "i/o" in value ||
            "ioexception" in value
    }

    enum class Operation {
        IMPORT_SAF,
        SYNC,
    }

    companion object {
        const val TAG = "nexora-durable-workspace"
        const val KEY_OPERATION = "operation"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_WORKSPACE_ID = "workspace_id"
        const val KEY_WORKSPACE_NAME = "workspace_name"
        const val KEY_RISK_COUNT = "risk_count"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 1
        private const val MAX_ERROR_LENGTH = 1_024
    }
}
