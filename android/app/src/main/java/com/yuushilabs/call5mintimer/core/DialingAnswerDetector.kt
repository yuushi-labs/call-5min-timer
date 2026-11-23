package com.yuushilabs.call5mintimer.core

/**
 * 同じ通知の前回・今回の値を比べて「相手が出た」ことを検出する。
 *
 * OPPO（ColorOS）などでは、相手が出た後も通知の文言が `発信…` のまま残り、
 * 経過時間（クロノメーター）の起点だけが応答時刻に変わる場合がある。文言では
 * 区別できないので、起点（`when`）が 1 秒以上先へ動いたことを応答の合図として使う。
 *
 * 呼び出し中の間は起点が動かないため、発信した瞬間から数え始める v0.1.0 の誤りには戻らない。
 * 比較に持つのは起点とクロノメーターの有無だけで、電話番号や連絡先名は保持しない。
 */
object DialingAnswerDetector {

    /** 起点がこれ以上先へ動いたら「更新された」とみなす（ミリ秒）。 */
    const val RESTART_THRESHOLD_MS = 1_000L

    /**
     * 応答判定のために残す直前の値。文字列は持たない。
     * [generation] は通話セッションの世代で、別の通話の値と取り違えないために使う。
     */
    data class Mark(
        val generation: Long,
        val chronometerRunning: Boolean,
        val postedWhenMillis: Long,
    )

    fun markOf(snapshot: CallNotificationSnapshot, generation: Long): Mark = Mark(
        generation = generation,
        chronometerRunning = snapshot.chronometerRunning,
        postedWhenMillis = snapshot.postedWhenMillis,
    )

    fun chronometerRestarted(previous: Mark?, current: CallNotificationSnapshot): Boolean {
        if (previous == null) return false
        if (!current.chronometerRunning || current.postedWhenMillis <= 0L) return false
        // 起点が未設定（0）から入った場合も、経過時間が動き出した合図として扱う。
        return current.postedWhenMillis >= previous.postedWhenMillis + RESTART_THRESHOLD_MS
    }
}
