package com.mioo.dao.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
        private const val ALARM_REQUEST_CODE = 9002
    }

    fun apply(enabled: Boolean, intervalMinutes: Int, replaceExisting: Boolean = true) {
        notifier.ensureChannel()
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            wm.cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelAlarm()
            return
        }
        val minutes = intervalMinutes.coerceIn(
            SubscriptionPollPolicy.MIN_INTERVAL_MINUTES,
            SubscriptionPollPolicy.MAX_INTERVAL_MINUTES
        ).toLong()
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
            if (replaceExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        scheduleNextAlarm(minutes.toInt())
    }

    fun scheduleNextAlarm(intervalMinutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val minutes = intervalMinutes.coerceIn(
            SubscriptionPollPolicy.MIN_INTERVAL_MINUTES,
            SubscriptionPollPolicy.MAX_INTERVAL_MINUTES
        ).toLong()
        val triggerAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        val pending = alarmPendingIntent(create = true) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        }
    }

    fun cancelAlarm() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmPendingIntent(create = false) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun alarmPendingIntent(create: Boolean): PendingIntent? {
        val intent = Intent(context, SubscriptionAlarmReceiver::class.java)
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }

    /** One-shot check soon (e.g. after enabling notifications, alarm wakeup, or manual trigger). */
    fun runOnceNow(force: Boolean = true) {
        notifier.ensureChannel()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val builder = OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SubscriptionCheckWorker.KEY_FORCE to force))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
    }
}
