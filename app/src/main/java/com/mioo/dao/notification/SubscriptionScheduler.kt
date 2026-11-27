package com.mioo.dao.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
        const val IMMEDIATE_WORK_NAME = "mioo_subscription_check_now"
    }

    fun apply(enabled: Boolean, intervalMinutes: Int) {
        notifier.ensureChannel()
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val minutes = intervalMinutes.coerceIn(
            SubscriptionPollPolicy.MIN_INTERVAL_MINUTES,
            SubscriptionPollPolicy.MAX_INTERVAL_MINUTES
        ).toLong()
        val flexMinutes = (minutes / 4).coerceAtLeast(15L).coerceAtMost(minutes)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
            minutes,
            TimeUnit.MINUTES,
            flexMinutes,
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

    /** One-shot check soon (e.g. after enabling notifications or manual trigger). */
    fun runOnceNow(force: Boolean = true) {
        notifier.ensureChannel()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SubscriptionCheckWorker.KEY_FORCE to force))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
