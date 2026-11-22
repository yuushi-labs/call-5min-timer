package com.yuushilabs.call5mintimer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yuushilabs.call5mintimer.core.CallNotificationParser
import com.yuushilabs.call5mintimer.core.CallNotificationSnapshot
import com.yuushilabs.call5mintimer.core.CallNotificationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 相手が出た瞬間の検知。
 *
 * 通話アプリの「発信中 → 通話中（00:00）」という通知の変化だけを見る。通知の本文や
 * 電話番号は保存せず、判定結果（呼び出し中 / 通話中）だけを使う。
 */
class CallNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var sawDialing = false
    @Volatile private var incomingCall = false

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        val state = CallNotificationParser.parse(snapshot)
        when (state) {
            CallNotificationState.DIALING -> {
                incomingCall = false
                sawDialing = true
                if (!CallTimerState.running.value) {
                    CallTimerForegroundService.start(this)
                }
            }

            CallNotificationState.ACTIVE -> {
                if (sawDialing && !incomingCall) {
                    CallTimerForegroundService.notifyConnected(this)
                }
            }

            CallNotificationState.INCOMING -> {
                incomingCall = true
                sawDialing = false
            }

            CallNotificationState.NONE, CallNotificationState.UNKNOWN_CALL -> Unit
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        if (!CallNotificationParser.isCallApp(snapshot.packageName)) return
        val state = CallNotificationParser.parse(snapshot)
        if (state != CallNotificationState.ACTIVE && state != CallNotificationState.DIALING) return

        // 通話アプリは通知を消して出し直すことがあるので、少し待ってから本当の終話かを確かめる。
        scope.launch {
            delay(END_GRACE_MS)
            if (!hasActiveCallNotification()) {
                sawDialing = false
                incomingCall = false
                if (CallTimerState.running.value) {
                    CallTimerForegroundService.stop(this@CallNotificationListener)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "notification listener connected")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun hasActiveCallNotification(): Boolean = runCatching {
        activeNotifications.orEmpty().any { sbn ->
            when (CallNotificationParser.parse(toSnapshot(sbn))) {
                CallNotificationState.DIALING, CallNotificationState.ACTIVE -> true
                else -> false
            }
        }
    }.getOrDefault(false)

    private fun toSnapshot(sbn: StatusBarNotification): CallNotificationSnapshot {
        val notification: Notification = sbn.notification
        val extras = notification.extras
        return CallNotificationSnapshot(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            category = notification.category,
            chronometerRunning = extras.getBoolean(EXTRA_SHOW_CHRONOMETER, false),
            postedWhenMillis = notification.`when`,
        )
    }

    private companion object {
        const val TAG = "CallNotification"
        const val END_GRACE_MS = 1_500L

        /** `Notification.EXTRA_SHOW_CHRONOMETER` と同じ値（通話アプリが通話中だけ true にする）。 */
        const val EXTRA_SHOW_CHRONOMETER = "android.showChronometer"
    }
}
