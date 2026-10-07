package com.nexora.git.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nexora.git.core.repository.RepositorySummary
import com.nexora.git.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class DurableRepositoryOperations @Inject constructor(
    @param:ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueClone(
        repository: RepositorySummary,
    ): UUID {
        val unmetered = settingsRepository.wifiOnlyLargeTransfers.first()
        val networkType = if (unmetered) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<RepositoryCloneWorker>()
            .setInputData(
                workDataOf(
                    RepositoryCloneWorker.KEY_OWNER to repository.ownerLogin,
                    RepositoryCloneWorker.KEY_REPOSITORY to repository.name,
                ),
            )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS,
            )
            .addTag(RepositoryCloneWorker.TAG)
            .addTag("repository:" + repository.fullName)
            .build()

        val uniqueName = uniqueCloneName(
            repository.ownerLogin,
            repository.name,
        )
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

    private fun uniqueCloneName(
        owner: String,
        repository: String,
    ): String = "nexora-clone:" +
        owner.lowercase() + "/" + repository.lowercase()
}
