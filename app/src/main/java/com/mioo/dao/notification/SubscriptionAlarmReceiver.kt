package com.mioo.dao.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mioo.dao.data.local.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var scheduler: SubscriptionScheduler

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val enabled = settingsDataStore.subscriptionNotificationsFlow.first()
                if (!enabled) {
                    scheduler.cancelAlarm()
                    return@launch
                }
                val intervalMinutes = settingsDataStore.notificationIntervalMinutesFlow.first()
                scheduler.scheduleNextAlarm(intervalMinutes)
                scheduler.runOnceNow(force = false)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
