package com.mioo.dao.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.repository.ThreadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SubscriptionCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val threadRepository: ThreadRepository,
    private val settingsDataStore: SettingsDataStore,
    private val notifier: SubscriptionNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val enabled = settingsDataStore.subscriptionNotificationsFlow.first()
            if (!enabled) return Result.success()

            // Poll official X-island feed UUID + local favorites for new replies
            val updates = threadRepository.pollSubscriptionUpdatesForNotification(limit = 30)
            if (updates.isNotEmpty()) {
                notifier.notifyNewReplies(updates)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
