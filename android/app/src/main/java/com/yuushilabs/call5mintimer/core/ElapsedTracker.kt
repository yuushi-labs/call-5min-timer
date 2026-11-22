package com.yuushilabs.call5mintimer.core

/**
 * 計測の経過秒と「まだ鳴らしていない合図」を管理する。
 *
 * 通話アプリの通知が「発信中」から「通話中（00:00）」に変わるまで数秒かかる端末では、
 * 相手が出てから実際に計測が始まるまでにラグがある。その分を埋めるため、
 * [startElapsedSeconds] 秒ぶん進んだ状態から計測を始められる（例: 0:00 ではなく 0:06）。
 *
 * 開始位置を進めても:
 * - 計測開始の合図（0:00 のバイブ）は、計測を始めた瞬間に 1 回だけ鳴らす
 * - 開始位置より前の秒（すでに過ぎた合図）は鳴らさない（二重発火させない）
 * - 開始位置ちょうどの秒も過ぎたものとして扱う（例: 0:06 開始なら 6 秒の合図は鳴らさない）
 *
 * 通知やバイブには触れないので、JVM の単体テストで検証できる。
 */
class ElapsedTracker(
    private val engine: TimerEngine,
    startElapsedSeconds: Int = 0,
) {
    /** 計測を始めるときの経過秒（0〜[MAX_START_ELAPSED_SECONDS]）。
     * 設定の丸め漏れでサービスが落ちないよう、ここでも丸める。 */
    val startElapsedSeconds: Int =
        startElapsedSeconds.coerceIn(0, MAX_START_ELAPSED_SECONDS)

    private var startCueDelivered = false
    private var lastProcessedSecond = -1

    /** 表示・判定に使う経過秒。[deltaSeconds] は計測を始めてからの実経過秒。 */
    fun elapsedSeconds(deltaSeconds: Int): Int =
        startElapsedSeconds + deltaSeconds.coerceAtLeast(0)

    /** 計測開始の合図（0:00）。最初の1回だけ中身を返し、以降は空。 */
    fun takeStartCue(): List<AlertEvent> {
        if (startCueDelivered) return emptyList()
        startCueDelivered = true
        return engine.eventsAt(0)
    }

    /** [deltaSeconds] までに新しく鳴らす合図。開始位置より前の秒は返さない。 */
    fun takeDue(deltaSeconds: Int): List<AlertEvent> {
        val elapsed = elapsedSeconds(deltaSeconds)
        val fromExclusive = maxOf(lastProcessedSecond, startElapsedSeconds)
        if (elapsed > lastProcessedSecond) {
            lastProcessedSecond = elapsed
        }
        return engine.eventsBetween(fromExclusive, elapsed)
    }

    companion object {
        /** 開始時の経過秒として設定できる上限。 */
        const val MAX_START_ELAPSED_SECONDS = 30
    }
}
