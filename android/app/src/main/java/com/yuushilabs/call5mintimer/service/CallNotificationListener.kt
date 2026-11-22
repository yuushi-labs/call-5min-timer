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
import java.util.Collections

/**
 * 相手が出た瞬間の検知。
 *
 * 通話アプリの「発信中 → 通話中（00:00）」という通知の変化だけを見る。通知の本文や
 * 電話番号は保存せず、判定結果（呼び出し中 / 通話中）だけを使う。
 */
class CallNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var sawDialing = false

    /** この発信セッションで見た通話通知のキー。終話判定はこのキーだけで行う。 */
    private val sessionKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** 着信と判定した通知のキー。同じキーが ACTIVE になったら着信が応答されたものとして除外する。 */
    private val incomingKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** 着信と判定したときのセッション ID。別セッションに変わっていたら古い着信情報は無効。 */
    @Volatile private var incomingSessionId = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        val state = CallNotificationParser.parse(snapshot)
        when (state) {
            CallNotificationState.DIALING -> {
                sawDialing = true
                sessionKeys.add(sbn.key)
                incomingKeys.remove(sbn.key)
                if (!CallTimerState.running.value) {
                    CallTimerForegroundService.start(this, fromBackground = false)
                }
            }

            CallNotificationState.ACTIVE -> {
                val isIncomingAnswered = sbn.key in incomingKeys &&
                    incomingSessionId == CallTimerState.sessionId.value
                // 発信と分かっている通話（リスナーで呼び出し中を拾った、または PHONE_STATE
                // フォールバックが発信と判定してサービスを起こした）で、まだ数え始めて
                // いないなら、ここが相手の応答（00:00）。追跡中の着信が応答されたものは除外。
                if (!isIncomingAnswered && (sawDialing || CallTimerState.detectedOutgoing.value)) {
                    CallTimerForegroundService.notifyConnected(this)
                }
            }

            CallNotificationState.INCOMING -> {
                sawDialing = false
                incomingKeys.add(sbn.key)
                incomingSessionId = CallTimerState.sessionId.value
            }

            CallNotificationState.NONE, CallNotificationState.UNKNOWN_CALL -> Unit
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        val state = CallNotificationParser.parse(snapshot)
        val key = sbn.key

        if (state == CallNotificationState.INCOMING) {
            // この着信通知が消えた。同じキーが ACTIVE になっても着信応答として扱うため、
            // キーは残す（判定は incomingSessionId で古いセッションを無効化する）。
            return
        }
        if (state != CallNotificationState.DIALING && state != CallNotificationState.ACTIVE) return

        // 現在のセッションで追跡している通話通知だけが終話判定の対象。
        // 留守番電話の表示など無関係な通知はここで除外される。
        if (key !in sessionKeys) return

        // 通話アプリは通知を消して別キーで出し直すことがある。1.5 秒待って
        // 「このセッションで追跡している通話通知が1件も無い」ことと「同じ通話セッション
        // であること」を確認してから、本当の終話として止める。
        val judgedSessionId = CallTimerState.sessionId.value
        scope.launch {
            delay(END_GRACE_MS)
            val sessionChanged = CallTimerState.sessionId.value != judgedSessionId
            val sessionNotificationsGone = runCatching {
                val activeKeys = activeNotifications.orEmpty().map { it.key }.toSet()
                sessionKeys.none { it in activeKeys }
            }.getOrDefault(false)
            if (sessionNotificationsGone && !sessionChanged) {
                sawDialing = false
                sessionKeys.clear()
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
