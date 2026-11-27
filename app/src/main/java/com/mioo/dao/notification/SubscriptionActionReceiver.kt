package com.mioo.dao.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mioo.dao.data.local.SubscriptionNotifyMode
import com.mioo.dao.data.repository.ThreadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var threadRepository: ThreadRepository

    @Inject
    lateinit var notifier: SubscriptionNotifier

    companion object {
        const val ACTION_MARK_READ = "com.mioo.dao.action.SUBSCRIPTION_MARK_READ"
        const val ACTION_MUTE_THREAD = "com.mioo.dao.action.SUBSCRIPTION_MUTE_THREAD"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val threadId = intent?.getStringExtra(EXTRA_THREAD_ID)?.takeIf { it.isNotBlank() } ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()
        actionScope.launch {
            try {
                when (action) {
                    ACTION_MARK_READ -> threadRepository.markSubscriptionNotifiedRead(threadId)
                    ACTION_MUTE_THREAD -> {
                        threadRepository.setThreadNotifyMode(threadId, SubscriptionNotifyMode.MUTE)
                        notifier.cancelThreadNotification(threadId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
