package com.nexora.git.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nexora.git.core.git.GitPullStrategy
import com.nexora.git.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Singleton
class DurableGitOperations @Inject constructor(
    @param:ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueFetch(
        workspaceId: String,
        remote: String,
    ): UUID = enqueue(
        workspaceId = workspaceId,
        operation = DurableGitWorker.Operation.FETCH,
        values = mapOf(
            DurableGitWorker.KEY_REMOTE to remote,
        ),
    )

    suspend fun enqueuePull(
        workspaceId: String,
        remote: String,
        strategy: GitPullStrategy,
        authorName: String,
        authorEmail: String,
    ): UUID = enqueue(
        workspaceId = workspaceId,
        operation = DurableGitWorker.Operation.PULL,
        values = mapOf(
            DurableGitWorker.KEY_REMOTE to remote,
            DurableGitWorker.KEY_PULL_STRATEGY to strategy.name,
            DurableGitWorker.KEY_AUTHOR_NAME to authorName,
            DurableGitWorker.KEY_AUTHOR_EMAIL to authorEmail,
        ),
    )

    suspend fun enqueuePush(
        workspaceId: String,
        remote: String,
        targetBranch: String,
        forceWithLease: Boolean,
    ): UUID = enqueue(
        workspaceId = workspaceId,
        operation = DurableGitWorker.Operation.PUSH,
        values = mapOf(
            DurableGitWorker.KEY_REMOTE to remote,
            DurableGitWorker.KEY_TARGET_BRANCH to targetBranch,
            DurableGitWorker.KEY_FORCE_WITH_LEASE to forceWithLease,
        ),
    )

    fun observe(id: UUID): Flow<WorkInfo> =
        workManager.getWorkInfoByIdFlow(id).filterNotNull()

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    private suspend fun enqueue(
        workspaceId: String,
        operation: DurableGitWorker.Operation,
        values: Map<String, Any>,
    ): UUID {
        val unmetered = settingsRepository.wifiOnlyLargeTransfers.first()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .setRequiresStorageNotLow(true)
            .build()

        val input = androidx.work.Data.Builder()
            .putString(DurableGitWorker.KEY_WORKSPACE_ID, workspaceId)
            .putString(DurableGitWorker.KEY_OPERATION, operation.name)
            .apply {
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> error("Unsupported durable Git input type.")
                    }
                }
            }
            .build()

        val request = OneTimeWorkRequestBuilder<DurableGitWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS,
            )
            .addTag(DurableGitWorker.TAG)
            .addTag("workspace:" + workspaceId)
            .build()

        val uniqueName = "nexora-git-network:" + workspaceId
        workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request,
        )

        return workManager
            .getWorkInfosForUniqueWorkFlow(uniqueName)
            .first { it.isNotEmpty() }
            .firstOrNull { !it.state.isFinished }
            ?.id
            ?: request.id
    }
}
