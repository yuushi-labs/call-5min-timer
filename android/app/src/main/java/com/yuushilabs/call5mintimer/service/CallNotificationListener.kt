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

    /** 着信と判定したフラグと、そのときのセッション ID。別セッションに変わっていたら古い着信フラグは無効。 */
    @Volatile private var incomingSessionFlag = false
    @Volatile private var incomingSessionId = 0L

    private fun incomingActive(): Boolean {
        if (!incomingSessionFlag) return false
        // 前の通話（着信）のフラグが次の発信セッションに持ち越されていないか確認する。
        if (incomingSessionId != CallTimerState.sessionId.value) {
            incomingSessionFlag = false
            return false
        }
        return true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        val state = CallNotificationParser.parse(snapshot)
        when (state) {
            CallNotificationState.DIALING -> {
                sawDialing = true
                if (!CallTimerState.running.value) {
                    CallTimerForegroundService.start(this, fromBackground = false)
                }
            }

            CallNotificationState.ACTIVE -> {
                // 発信と分かっている通話（リスナーで呼び出し中を拾った、または PHONE_STATE
                // フォールバックが発信と判定してサービスを起こした）で、まだ数え始めて
                // いないなら、ここが相手の応答（00:00）。現在有効な着信があれば除外する。
                if (!incomingActive() && (sawDialing || CallTimerState.detectedOutgoing.value)) {
                    CallTimerForegroundService.notifyConnected(this)
                }
            }

            CallNotificationState.INCOMING -> {
                sawDialing = false
                incomingSessionFlag = true
                incomingSessionId = CallTimerState.sessionId.value
            }

            CallNotificationState.NONE, CallNotificationState.UNKNOWN_CALL -> Unit
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        val state = CallNotificationParser.parse(snapshot)
        if (state == CallNotificationState.INCOMING) {
            // この着信が消えたので、フラグを持ち越さない。
            incomingSessionFlag = false
            return
        }
        if (state != CallNotificationState.DIALING && state != CallNotificationState.ACTIVE) return

        // 通話アプリは通知を消して別キーで出し直すことがある。1.5 秒待って
        // 「通話アプリの進行中通知が1件も無い」ことと「同じ通話セッションであること」を
        // 確認してから、本当の終話として止める。
        val judgedSessionId = CallTimerState.sessionId.value
        scope.launch {
            delay(END_GRACE_MS)
            val sessionChanged = CallTimerState.sessionId.value != judgedSessionId
            val callNotificationsGone = runCatching {
                activeNotifications.orEmpty().none { sbn ->
                    val parsed = CallNotificationParser.parse(toSnapshot(sbn))
                    parsed == CallNotificationState.DIALING || parsed == CallNotificationState.ACTIVE
                }
            }.getOrDefault(false)
            if (callNotificationsGone && !sessionChanged) {
                sawDialing = false
                incomingSessionFlag = false
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
