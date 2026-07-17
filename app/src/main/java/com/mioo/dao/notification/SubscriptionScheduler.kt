package com.mioo.dao.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: SubscriptionNotifier
) {
    companion object {
        const val WORK_NAME = "mioo_subscription_check"
    }

    fun apply(enabled: Boolean, intervalMinutes: Int) {
        notifier.ensureChannel()
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        // WorkManager minimum periodic interval is 15 minutes
        val minutes = intervalMinutes.coerceIn(15, 180).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
            minutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** One-shot check soon (e.g. after enabling notifications). */
    fun runOnceNow() {
        notifier.ensureChannel()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
