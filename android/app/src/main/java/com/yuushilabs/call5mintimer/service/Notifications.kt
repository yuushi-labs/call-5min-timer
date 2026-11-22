package com.yuushilabs.call5mintimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yuushilabs.call5mintimer.R
import com.yuushilabs.call5mintimer.ui.MainActivity

/**
 * 通知チャネルと通知の組み立て。
 *
 * 鳴り物（バイブ・音）は AlertDispatcher が直接出すので、チャネル自体はサイレントにして
 * 二重に鳴らないようにする。
 */
object Notifications {

    const val CHANNEL_STATUS = "status"
    const val CHANNEL_ALERTS = "alerts"
    const val STATUS_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_BASE = 2000
    private const val ALERT_NOTIFICATION_RANGE = 1000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannels(listOf(status, alerts))
    }

    fun statusNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("通話計測中")
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun alertNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("5分通話タイマー")
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
    }

    fun alertNotificationId(alertId: Long): Int = ALERT_NOTIFICATION_BASE + (alertId % ALERT_NOTIFICATION_RANGE).toInt()

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
