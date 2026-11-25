package com.mioo.dao.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.repository.ThreadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@HiltWorker
class CloudFeedIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ThreadRepository,
    private val settings: SettingsDataStore
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (settings.dataSaverEnabledFlow.first()) return Result.success()
        var failed = false
        for (uuid in settings.feedFoldersFlow.first().map { it.uuid }.distinct()) {
            try {
                repository.syncCloudFeedIndex(uuid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
            delay(850)
        }
        return if (failed) Result.retry() else Result.success()
    }
}

internal object CloudFeedIndexScheduler {
    private const val NAME = "mioo_cloud_feed_index"

    fun apply(context: Context, enabled: Boolean, foldersChanged: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CloudFeedIndexWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(12, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build())
            .build()
        work.enqueueUniquePeriodicWork(
            NAME,
            if (foldersChanged) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
