package com.nexora.git.core.work

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nexora.git.core.storage.WorkspaceRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class DurableWorkspaceOperations @Inject constructor(
    @param:ApplicationContext context: Context,
    private val workspaceRegistry: WorkspaceRegistry,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueImport(treeUri: Uri): UUID {
        val permission = workspaceRegistry.persistSafTreeAccess(treeUri)
        require(permission.readable) {
            "Selected project folder is not readable."
        }

        val request = OneTimeWorkRequestBuilder<DurableWorkspaceWorker>()
            .setInputData(
                workDataOf(
                    DurableWorkspaceWorker.KEY_OPERATION to
                        DurableWorkspaceWorker.Operation.IMPORT_SAF.name,
                    DurableWorkspaceWorker.KEY_TREE_URI to treeUri.toString(),
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10,
                TimeUnit.SECONDS,
            )
            .addTag(DurableWorkspaceWorker.TAG)
            .build()

        workManager.enqueueUniqueWork(
            "nexora-import:" + treeUri.toString().hashCode(),
            ExistingWorkPolicy.KEEP,
            request,
        )
        return request.id
    }

    suspend fun enqueueSync(workspaceId: String): UUID {
        val request = OneTimeWorkRequestBuilder<DurableWorkspaceWorker>()
            .setInputData(
                workDataOf(
                    DurableWorkspaceWorker.KEY_OPERATION to
                        DurableWorkspaceWorker.Operation.SYNC.name,
                    DurableWorkspaceWorker.KEY_WORKSPACE_ID to workspaceId,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10,
                TimeUnit.SECONDS,
            )
            .addTag(DurableWorkspaceWorker.TAG)
            .addTag("workspace:" + workspaceId)
            .build()

        val uniqueName = "nexora-workspace-sync:" + workspaceId
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

    fun observe(id: UUID): Flow<WorkInfo> =
        workManager.getWorkInfoByIdFlow(id)

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }
}
