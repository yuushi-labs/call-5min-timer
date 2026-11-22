package com.yuushilabs.call5mintimer.core

import kotlinx.serialization.Serializable

/** ルールが鳴らすもの。通知そのものは、どの種類でも併せて出す。 */
@Serializable
enum class AlertAction {
    /** バイブのみ（通知音なし）。 */
    VIBRATE_ONLY,

    /** 通知音のみ。 */
    SOUND_ONLY,

    /** バイブと通知音。 */
    VIBRATE_AND_SOUND,

    /** サイレント（通知を出すだけで鳴らさない）。 */
    SILENT,
}

@Serializable
enum class VibrationLength(val millis: Long) {
    SHORT(120),
    MEDIUM(250),
    LONG(600),
}

@Serializable
enum class SoundKind {
    /** アプリ内蔵の短いビープ音（通話中に聞き取りやすい）。 */
    BEEP,

    /** 端末の既定の通知音。 */
    SYSTEM,

    NONE,
}

/** 4分30秒アラートのあと、5分以降をどう扱うか。 */
@Serializable
enum class AfterFiveMode {
    /** 5分以降も1分ごとに鳴らす。 */
    PER_MINUTE,

    /** 5分ちょうどで1回だけ鳴らす。 */
    ONCE_AT_FIVE,

    /** 5分以降は鳴らさない。 */
    SILENT,
}

@Serializable
data class AlertSpec(
    val action: AlertAction = AlertAction.VIBRATE_ONLY,
    val vibrationLength: VibrationLength = VibrationLength.SHORT,
    val repeatGapMs: Long = 180L,
    /** 「経過回数に応じて回数を増やす」が OFF のときの回数。 */
    val fixedRepeatCount: Int = 1,
    val soundKind: SoundKind = SoundKind.BEEP,
    val volumePercent: Int = 80,
) {
    val vibrates: Boolean
        get() = action == AlertAction.VIBRATE_ONLY || action == AlertAction.VIBRATE_AND_SOUND

    val playsSound: Boolean
        get() = soundKind != SoundKind.NONE &&
            (action == AlertAction.SOUND_ONLY || action == AlertAction.VIBRATE_AND_SOUND)

    fun normalized(): AlertSpec = copy(
        repeatGapMs = repeatGapMs.coerceIn(0L, 2_000L),
        fixedRepeatCount = fixedRepeatCount.coerceIn(1, 10),
        volumePercent = volumePercent.coerceIn(0, 100),
    )

    /** テスト鳴動・プレビュー用の説明。 */
    fun summary(): String {
        val parts = mutableListOf<String>()
        when (action) {
            AlertAction.VIBRATE_ONLY -> parts += "バイブのみ"
            AlertAction.SOUND_ONLY -> parts += "音のみ"
            AlertAction.VIBRATE_AND_SOUND -> parts += "バイブ＋音"
            AlertAction.SILENT -> parts += "サイレント"
        }
        if (vibrates) {
            parts += "バイブ${vibrationLength.millis}ms"
        }
        if (playsSound) {
            parts += when (soundKind) {
                SoundKind.BEEP -> "ビープ${volumePercent}%"
                SoundKind.SYSTEM -> "端末音${volumePercent}%"
                SoundKind.NONE -> ""
            }
        }
        return parts.filter { it.isNotBlank() }.joinToString(" / ")
    }
}

/** 一定間隔で鳴らすルール。 */
@Serializable
data class PeriodicRule(
    val id: String,
    val enabled: Boolean = true,
    /** 間隔（秒）。例: 60 で1分ごと。 */
    val intervalSeconds: Int = 60,
    /** 最初に鳴らす経過秒。 */
    val startSecond: Int = 60,
    /** 最後に鳴らす経過秒。 */
    val endSecond: Int = 240,
    /** ON で 1回目=1回、2回目=2回…とバイブ回数を増やす。 */
    val scaleRepeats: Boolean = true,
    val alert: AlertSpec = AlertSpec(),
) {
    fun normalized(): PeriodicRule = copy(
        intervalSeconds = intervalSeconds.coerceIn(5, 3_600),
        startSecond = startSecond.coerceIn(0, 24 * 3_600),
        endSecond = endSecond.coerceIn(0, 24 * 3_600),
        alert = alert.normalized(),
    )

    fun summary(): String {
        val range = "${formatClock(startSecond)}〜${formatClock(endSecond)}"
        val repeat = if (scaleRepeats) "回数増加" else "${alert.fixedRepeatCount}回固定"
        return "每隔 ${intervalSeconds}秒 / $range / $repeat"
    }
}

/** 指定の経過時刻に1回だけ鳴らすルール。 */
@Serializable
data class SpotRule(
    val id: String,
    val enabled: Boolean = true,
    val atSecond: Int = 270,
    val alert: AlertSpec = AlertSpec(),
) {
    fun normalized(): SpotRule = copy(
        atSecond = atSecond.coerceIn(0, 24 * 3_600),
        alert = alert.normalized(),
    )
}

@Serializable
data class TimerRules(
    val periodicRules: List<PeriodicRule> = emptyList(),
    val spotRules: List<SpotRule> = emptyList(),
    val afterFive: AfterFiveMode = AfterFiveMode.ONCE_AT_FIVE,
) {
    fun normalized(): TimerRules = copy(
        periodicRules = periodicRules.map { it.normalized() },
        spotRules = spotRules.map { it.normalized() },
    )

    companion object {
        const val PRESET_PERIODIC_ID = "preset-periodic"
        const val PRESET_START_ID = "preset-start"
        const val PRESET_430_ID = "preset-430"

        /** 「ahamo 5分標準」プリセット。 */
        fun ahamoDefault(): TimerRules = TimerRules(
            periodicRules = listOf(
                PeriodicRule(
                    id = PRESET_PERIODIC_ID,
                    intervalSeconds = 60,
                    startSecond = 60,
                    endSecond = 240,
                    scaleRepeats = true,
                    alert = AlertSpec(
                        action = AlertAction.VIBRATE_ONLY,
                        vibrationLength = VibrationLength.SHORT,
                        repeatGapMs = 180L,
                        soundKind = SoundKind.NONE,
                    ),
                ),
            ),
            spotRules = listOf(
                SpotRule(
                    id = PRESET_START_ID,
                    atSecond = 0,
                    alert = AlertSpec(
                        action = AlertAction.VIBRATE_ONLY,
                        vibrationLength = VibrationLength.SHORT,
                        soundKind = SoundKind.NONE,
                    ),
                ),
                SpotRule(
                    id = PRESET_430_ID,
                    atSecond = 270,
                    alert = AlertSpec(
                        action = AlertAction.VIBRATE_AND_SOUND,
                        vibrationLength = VibrationLength.LONG,
                        soundKind = SoundKind.BEEP,
                        volumePercent = 90,
                    ),
                ),
            ),
            afterFive = AfterFiveMode.ONCE_AT_FIVE,
        )
    }
}

/** 秒数を `m:ss` で表す（1時間以上は `h:mm:ss`）。 */
fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
