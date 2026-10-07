package com.nexora.git.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nexora.git.core.common.AppResult
import dagger.hilt.android.EntryPointAccessors

class RepositoryCloneWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_OWNER)?.trim().orEmpty()
        val repository = inputData.getString(KEY_REPOSITORY)?.trim().orEmpty()

        if (owner.isBlank() || repository.isBlank()) {
            return Result.failure(
                workDataOf(KEY_ERROR to "Invalid repository coordinates."),
            )
        }

        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            DurableWorkEntryPoint::class.java,
        )

        setProgress(workDataOf(KEY_PHASE to PHASE_RESOLVE))

        val details = when (
            val result = dependencies.repositoryGateway()
                .getRepository(owner, repository)
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                return retryOrFailure(result.error.toString())
            }
        }

        setProgress(workDataOf(KEY_PHASE to PHASE_CLONE))

        return when (
            val result = dependencies.repositoryWorkspaceCoordinator()
                .clone(details.summary)
        ) {
            is AppResult.Success -> {
                setProgress(workDataOf(KEY_PHASE to PHASE_COMPLETE))
                Result.success(
                    workDataOf(
                        KEY_WORKSPACE_ID to result.value.id,
                        KEY_REPOSITORY_FULL_NAME to details.summary.fullName,
                    ),
                )
            }

            is AppResult.Failure -> {
                retryOrFailure(result.error.toString())
            }
        }
    }

    private fun retryOrFailure(message: String): Result {
        return if (runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.failure(
                workDataOf(KEY_ERROR to message.take(MAX_ERROR_LENGTH)),
            )
        }
    }

    companion object {
        const val TAG = "nexora-repository-clone"
        const val KEY_OWNER = "owner"
        const val KEY_REPOSITORY = "repository"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val KEY_WORKSPACE_ID = "workspace_id"
        const val KEY_REPOSITORY_FULL_NAME = "repository_full_name"

        const val PHASE_RESOLVE = "resolve"
        const val PHASE_CLONE = "clone"
        const val PHASE_COMPLETE = "complete"

        private const val MAX_RETRIES = 2
        private const val MAX_ERROR_LENGTH = 1_024
    }
}
