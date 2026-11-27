package com.mioo.dao.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.repository.ThreadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class SubscriptionCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val threadRepository: ThreadRepository,
    private val settingsDataStore: SettingsDataStore,
    private val notifier: SubscriptionNotifier
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FORCE = "force"
    }

    override suspend fun doWork(): Result {
        return try {
            val force = inputData.getBoolean(KEY_FORCE, false)
            val enabled = settingsDataStore.subscriptionNotificationsFlow.first()
            if (!enabled && !force) return Result.success()

            val result = threadRepository.pollSubscriptionUpdatesForNotification(force = force)
            if (result.updates.isNotEmpty()) {
                notifier.notifyNewReplies(result.updates)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Never return Result.retry(): avoid retry storms on X-island server fluctuations.
            Result.success()
        }
    }
}
