package com.mioo.dao.notification

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.repository.ThreadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import java.util.concurrent.TimeUnit

/** Uses the same complete-page and image download path as manual offline updates. */
@HiltWorker
class OfflineUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ThreadRepository,
    private val settings: SettingsDataStore
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            if (!allowed()) return Result.success()
            var failed = false
            val threads = repository.getOfflineThreadSummaries().first().sortedBy { it.downloadedAt }
            for (thread in threads) {
                if (!allowed()) break
                // A deletion made since taking the snapshot must not recreate that offline entry.
                if (!repository.isThreadDownloaded(thread.threadId).first()) continue
                if (repository.downloadFullThread(thread.threadId, conservative = true).last() is XdResponse.Error) failed = true
                delay(10000)
            }
            if (failed) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun allowed(): Boolean {
        val (enabled, wifiOnly) = settings.offlineUpdatePolicy.first()
        if (!enabled) return false
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!wifiOnly || (network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)))
    }
}

object OfflineUpdateScheduler {
    private const val NAME = "mioo_offline_update"
    fun apply(context: Context, enabled: Boolean, wifiOnly: Boolean, policyChanged: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<OfflineUpdateWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        // Replace on policy changes to stop a currently running task under the old constraints.
        manager.enqueueUniquePeriodicWork(NAME,
            if (policyChanged) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request)
    }
}
