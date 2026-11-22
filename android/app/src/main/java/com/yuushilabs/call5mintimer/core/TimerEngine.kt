package com.yuushilabs.call5mintimer.core

/**
 * 1回の発火。
 *
 * [atSecond] は相手が応答してからの経過秒。0 は計測開始の合図。
 * [vibrationRepeats] は「回数増加」を反映した実際のバイブ回数。
 */
data class AlertEvent(
    val atSecond: Int,
    val vibrationRepeats: Int,
    val alert: AlertSpec,
    val ruleId: String,
    val label: String,
) {
    val vibrates: Boolean get() = alert.vibrates && vibrationRepeats > 0
    val playsSound: Boolean get() = alert.playsSound
}

/**
 * 経過時間から「いつ・何を鳴らすか」を組み立てる純粋なエンジン。
 *
 * 通知そのもの（Notification）やバイブ（Vibrator）には触れないので、JVM の単体テストで
 * スケジュール全体を検証できる。
 */
class TimerEngine(
    rules: TimerRules,
    private val maxSeconds: Int = DEFAULT_MAX_SECONDS,
) {
    /** 時刻ごとに1件へ畳んだスケジュール（昇順）。同じ秒はスポット優先。 */
    val schedule: List<AlertEvent> = buildSchedule(rules.normalized(), maxSeconds)

    fun eventsAt(second: Int): List<AlertEvent> = schedule.filter { it.atSecond == second }

    /** `(fromExclusive, toInclusive]` に入る発火。取りこぼした秒をまとめて拾うときに使う。 */
    fun eventsBetween(fromExclusive: Int, toInclusive: Int): List<AlertEvent> {
        if (toInclusive <= fromExclusive) return emptyList()
        return schedule.filter { it.atSecond > fromExclusive && it.atSecond <= toInclusive }
    }

    fun nextEvent(afterSecond: Int): AlertEvent? =
        schedule.firstOrNull { it.atSecond > afterSecond }

    private fun buildSchedule(rules: TimerRules, maxSeconds: Int): List<AlertEvent> {
        val bySecond = LinkedHashMap<Int, AlertEvent>()

        for (rule in rules.periodicRules) {
            if (!rule.enabled) continue
            if (rule.intervalSeconds <= 0) continue
            var second = rule.startSecond
            var index = 1
            var guard = 0
            while (second <= rule.endSecond && second <= maxSeconds && guard < MAX_EVENTS_PER_RULE) {
                val repeats = if (rule.scaleRepeats) {
                    index
                } else {
                    rule.alert.fixedRepeatCount
                }.coerceIn(1, MAX_VIBRATION_REPEATS)
                bySecond[second] = AlertEvent(
                    atSecond = second,
                    vibrationRepeats = repeats,
                    alert = rule.alert,
                    ruleId = rule.id,
                    label = describe(second),
                )
                second += rule.intervalSeconds
                index += 1
                guard += 1
            }
        }

        // スポットは同じ秒の定期ルールを置き換える（より具体的な指定を優先）。
        for (spot in rules.spotRules) {
            if (!spot.enabled) continue
            if (spot.atSecond < 0 || spot.atSecond > maxSeconds) continue
            bySecond[spot.atSecond] = AlertEvent(
                atSecond = spot.atSecond,
                vibrationRepeats = spot.alert.fixedRepeatCount.coerceIn(1, MAX_VIBRATION_REPEATS),
                alert = spot.alert,
                ruleId = spot.id,
                label = describe(spot.atSecond),
            )
        }

        // 5分以降の扱い。ユーザーのルールが既にある秒は上書きしない。
        when (rules.afterFive) {
            AfterFiveMode.SILENT -> Unit
            AfterFiveMode.ONCE_AT_FIVE -> {
                if (FIVE_MINUTES <= maxSeconds && bySecond[FIVE_MINUTES] == null) {
                    bySecond[FIVE_MINUTES] = AlertEvent(
                        atSecond = FIVE_MINUTES,
                        vibrationRepeats = 1,
                        alert = AlertSpec(
                            action = AlertAction.VIBRATE_AND_SOUND,
                            vibrationLength = VibrationLength.MEDIUM,
                            soundKind = SoundKind.BEEP,
                            volumePercent = 80,
                        ),
                        ruleId = RULE_AFTER_FIVE,
                        label = describe(FIVE_MINUTES),
                    )
                }
            }
            AfterFiveMode.PER_MINUTE -> {
                var second = FIVE_MINUTES
                while (second <= maxSeconds) {
                    if (bySecond[second] == null) {
                        bySecond[second] = AlertEvent(
                            atSecond = second,
                            vibrationRepeats = (second / 60).coerceIn(1, MAX_VIBRATION_REPEATS),
                            alert = AlertSpec(
                                action = AlertAction.VIBRATE_ONLY,
                                vibrationLength = VibrationLength.SHORT,
                                soundKind = SoundKind.NONE,
                            ),
                            ruleId = RULE_AFTER_FIVE,
                            label = describe(second),
                        )
                    }
                    second += 60
                }
            }
        }

        return bySecond.values.sortedBy { it.atSecond }
    }

    private fun describe(second: Int): String = when {
        second == 0 -> "計測開始"
        second < FIVE_MINUTES -> "${formatClock(second)}経過（残り${formatClock(FIVE_MINUTES - second)}）"
        second == FIVE_MINUTES -> "5:00 経過（無料枠ここまで）"
        else -> "${formatClock(second)}経過（超過）"
    }

    companion object {
        const val FIVE_MINUTES = 300
        const val RULE_AFTER_FIVE = "after-five"
        const val DEFAULT_MAX_SECONDS = 3_600
        private const val MAX_EVENTS_PER_RULE = 2_000
        private const val MAX_VIBRATION_REPEATS = 10
    }
}
