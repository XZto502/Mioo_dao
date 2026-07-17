package com.mioo.dao.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mioo.dao.MainActivity
import com.mioo.dao.data.repository.BookmarkNewReply
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System notifications for bookmarked-thread new replies.
 * Same product pattern as 蓝岛 etc.: local poll, not a shared push from other apps.
 */
@Singleton
class SubscriptionNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "subscription_updates"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        private const val SUMMARY_ID = 9001
        private const val GROUP_KEY = "mioo_subscription_group"
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "订阅串更新",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "X岛订阅/收藏串有新回复时提醒（轮询官方 feed 与本地收藏）"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyNewReplies(updates: List<BookmarkNewReply>) {
        if (updates.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()

        val nm = NotificationManagerCompat.from(context)
        updates.take(8).forEach { update ->
            val title = update.title ?: "No.${update.threadId}"
            val text = "有 ${update.newReplyDelta} 条新回复"
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_THREAD_ID, update.threadId)
                // Unique data so PendingIntents don't collide
                data = android.net.Uri.parse("mioodao://thread/${update.threadId}")
            }
            val pending = PendingIntent.getActivity(
                context,
                update.threadId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$text"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setContentIntent(pending)
                .build()
            try {
                nm.notify(update.threadId.hashCode() and 0x7FFFFFFF, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted
                return
            }
        }

        if (updates.size > 1) {
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("喵岛")
                .setContentText("${updates.size} 个收藏串有新回复")
                .setStyle(
                    NotificationCompat.InboxStyle()
                        .setSummaryText("${updates.size} 条订阅更新")
                        .also { style ->
                            updates.take(5).forEach { u ->
                                style.addLine("${u.title}: +${u.newReplyDelta}")
                            }
                        }
                )
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            try {
                nm.notify(SUMMARY_ID, summary)
            } catch (_: SecurityException) {
            }
        }
    }
}
