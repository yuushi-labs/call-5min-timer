package com.yuushilabs.call5mintimer.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 直近の通話通知の判定結果（画面の「通知判定の診断」用）。
 *
 * 端末ごとに通知の形が違うため、実機で反応しないときに原因を確かめられるよう、
 * 判定に使った項目だけを残す。**電話番号・連絡先名・通知の本文そのものは保持しない**
 * （各欄は「文字数」だけ、当たった語はパーサーが持つ固定のマーカー語だけ）。
 * 端末内のメモリに直近1件だけを置き、ファイルにも送信にも残さない（アプリを終了すると消える）。
 */
data class CallDiagnosticsEntry(
    val packageName: String,
    val state: CallNotificationState,
    val reason: CallMatchReason,
    /** 当たった固定のマーカー語（「通話中」「発信中…」など、パーサー自身の語）。 */
    val markers: List<String>,
    val category: String?,
    val titleLength: Int,
    val textLength: Int,
    val bigTextLength: Int,
    val subTextLength: Int,
    val chronometerRunning: Boolean,
    val hasWhen: Boolean,
    /** 通知に出ていた経過表示（00:01 など）の秒数。無ければ null。 */
    val clockSeconds: Int?,
    /** この通話で一度でも「発信中」を観測したか。 */
    val sawDialing: Boolean,
    /** 経過時間の起点が更新されたことを応答とみなして計測を始めたか。 */
    val promotedByChronometerRestart: Boolean,
    val timerRunning: Boolean,
    val timerCounting: Boolean,
    val receivedAtMillis: Long,
)

object CallDiagnostics {

    private const val MAX_MARKERS = 5

    /** 直近1件。プロセスが終われば消える（永続化しない）。 */
    val latest = MutableStateFlow<CallDiagnosticsEntry?>(null)

    fun record(
        snapshot: CallNotificationSnapshot,
        result: CallParseResult,
        state: CallNotificationState,
        sawDialing: Boolean,
        promotedByChronometerRestart: Boolean = false,
        timerRunning: Boolean = false,
        timerCounting: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val clock = CallNotificationParser.parseElapsedSeconds(
            listOf(snapshot.title, snapshot.text, snapshot.bigText, snapshot.subText)
                .filter { it.isNotBlank() }
                .joinToString(" "),
        )
        latest.value = CallDiagnosticsEntry(
            packageName = snapshot.packageName,
            state = state,
            reason = if (promotedByChronometerRestart) {
                CallMatchReason.ACTIVE_CHRONOMETER_RESTART
            } else {
                result.reason
            },
            markers = result.markers.take(MAX_MARKERS),
            category = snapshot.category,
            titleLength = snapshot.title.length,
            textLength = snapshot.text.length,
            bigTextLength = snapshot.bigText.length,
            subTextLength = snapshot.subText.length,
            chronometerRunning = snapshot.chronometerRunning,
            hasWhen = snapshot.postedWhenMillis > 0L,
            clockSeconds = clock,
            sawDialing = sawDialing,
            promotedByChronometerRestart = promotedByChronometerRestart,
            timerRunning = timerRunning,
            timerCounting = timerCounting,
            receivedAtMillis = nowMillis,
        )
    }

    fun clear() {
        latest.value = null
    }
}
