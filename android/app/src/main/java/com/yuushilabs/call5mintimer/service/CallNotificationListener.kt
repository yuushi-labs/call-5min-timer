package com.yuushilabs.call5mintimer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yuushilabs.call5mintimer.core.CallDiagnostics
import com.yuushilabs.call5mintimer.core.CallNotificationParser
import com.yuushilabs.call5mintimer.core.CallNotificationSnapshot
import com.yuushilabs.call5mintimer.core.CallNotificationState
import com.yuushilabs.call5mintimer.core.DialingAnswerDetector
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

    /**
     * 通知キーごとの直前の値（最大 [MAX_TRACKED_SNAPSHOTS] 件）。
     * OPPO（ColorOS）のように「発信」の文言が残ったまま経過時間の起点だけが変わる端末で、
     * 応答を検出するために使う。電話番号・連絡先名・本文は持たず、起点とクロノメーターの
     * 有無だけを残す。通話のたびに [callGeneration] を進めるので、前の通話の値は使わない。
     */
    private val answerMarks = object : LinkedHashMap<String, DialingAnswerDetector.Mark>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DialingAnswerDetector.Mark>): Boolean =
            size > MAX_TRACKED_SNAPSHOTS
    }

    /** 通話セッションの世代。着信・終話で進め、古い比較用の値を無効にする。 */
    @Volatile private var callGeneration = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        handle(sbn)
    }

    /**
     * 通話通知1件を判定して、サービスに伝える。
     * 通知の投稿時と、リスナー接続時の既存通知の確認（[onListenerConnected]）の両方から呼ぶ。
     */
    private fun handle(sbn: StatusBarNotification) {
        val snapshot = toSnapshot(sbn)
        val result = CallNotificationParser.inspect(snapshot)
        val generation = callGeneration
        val previous = synchronized(answerMarks) { answerMarks[sbn.key] }
        val promoted = result.state == CallNotificationState.DIALING &&
            previous != null && previous.generation == generation &&
            (sawDialing || CallTimerState.detectedOutgoing.value) &&
            DialingAnswerDetector.chronometerRestarted(previous, snapshot)
        val state = if (promoted) CallNotificationState.ACTIVE else result.state
        // 通話アプリ以外の通知は保存しない（無関係な通知で、通話中の比較用の値が
        // 押し出されないようにする）。
        if (result.state != CallNotificationState.NONE) {
            synchronized(answerMarks) {
                answerMarks[sbn.key] = DialingAnswerDetector.markOf(snapshot, generation)
            }
        }

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
                // 着信は新しい通話。前の通話の比較用の値は使わない。
                sawDialing = false
                incomingKeys.add(sbn.key)
                incomingSessionId = CallTimerState.sessionId.value
                resetAnswerMarks()
            }

            CallNotificationState.NONE, CallNotificationState.UNKNOWN_CALL -> Unit
        }

        // 実機で反応しないときの手がかり。番号・連絡先名は入れず、判定に使った項目だけ残す。
        // 通話アプリ以外の通知で、通話の判定を上書きしない。
        if (state != CallNotificationState.NONE) {
            CallDiagnostics.record(
                snapshot = snapshot,
                result = result,
                state = state,
                sawDialing = sawDialing,
                promotedByChronometerRestart = promoted,
                timerRunning = CallTimerState.running.value,
                timerCounting = CallTimerState.counting.value,
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val snapshot = sbn?.let { toSnapshot(it) } ?: return
        val state = CallNotificationParser.parse(snapshot)
        val key = sbn.key

        // 消えた通知の比較用の値は残さない（着信・非通話の通知も含む）。
        synchronized(answerMarks) { answerMarks.remove(key) }

        if (state == CallNotificationState.INCOMING) {
            // この着信通知が消えた。同じキーが ACTIVE になっても着信応答として扱うため、
            // キーは残す（判定は incomingSessionId で古いセッションを無効化する）。
            resetAnswerMarks()
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
                incomingKeys.clear()
                resetAnswerMarks()
                if (CallTimerState.running.value) {
                    CallTimerForegroundService.stop(this@CallNotificationListener)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "notification listener connected")
        // リスナーが再接続したとき、すでに通話通知が出ていることがある
        // （アプリのプロセスが落ちて再起動した直後、通知アクセスを許可した直後など）。
        // その場合は投稿イベントを取りこぼすので、既存の通知を1度だけ確認する。
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        active.forEach { sbn ->
            runCatching { handle(sbn) }
                .onFailure { Log.w(TAG, "failed to inspect an active notification", it) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 比較用の値を捨て、次の通話を別の世代として扱う。 */
    private fun resetAnswerMarks() {
        callGeneration += 1
        synchronized(answerMarks) { answerMarks.clear() }
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
        const val MAX_TRACKED_SNAPSHOTS = 8

        /** `Notification.EXTRA_SHOW_CHRONOMETER` と同じ値（通話アプリが通話中だけ true にする）。 */
        const val EXTRA_SHOW_CHRONOMETER = "android.showChronometer"
    }
}
