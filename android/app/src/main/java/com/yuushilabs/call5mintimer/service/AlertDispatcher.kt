package com.yuushilabs.call5mintimer.service

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.yuushilabs.call5mintimer.core.AlertEvent
import com.yuushilabs.call5mintimer.core.AlertSpec
import com.yuushilabs.call5mintimer.core.SoundKind
import com.yuushilabs.call5mintimer.core.VibrationLength

/**
 * ルール1件分のバイブ・通知音・通知をまとめて出す。
 *
 * バイブはパターン1本で鳴らす（途中で止められるようにするため）。音はアプリ内蔵の
 * ToneGenerator か端末の通知音で、ルールごとの音量をそのまま使う。
 */
class AlertDispatcher(private val context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun dispatch(event: AlertEvent) {
        if (event.vibrates) {
            vibrate(event.alert.vibrationLength, event.vibrationRepeats, event.alert.repeatGapMs)
        }
        if (event.playsSound) {
            playSound(event.alert)
        }
        postNotification(event)
    }

    /** 設定画面のテスト鳴動。 */
    fun preview(alert: AlertSpec, repeats: Int = alert.fixedRepeatCount) {
        if (alert.vibrates) vibrate(alert.vibrationLength, repeats, alert.repeatGapMs)
        if (alert.playsSound) playSound(alert)
    }

    /** 呼び出し中の短い確認バイブ（相手応答待ちの合図）。 */
    fun waitingPulse() {
        vibrate(VibrationLength.SHORT, 1, 0L)
    }

    /** 実行中の振動を止める（終話時に呼ぶ）。 */
    fun cancel() {
        runCatching { vibrator?.cancel() }
    }

    private fun vibrate(length: VibrationLength, repeats: Int, gapMs: Long) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val times = repeats.coerceIn(1, 10)
        val pattern = buildList {
            add(0L)
            repeat(times) { index ->
                add(length.millis)
                if (index < times - 1) add(gapMs.coerceAtLeast(30L))
            }
        }.toLongArray()
        runCatching {
            val effect = if (pattern.size <= 2) {
                VibrationEffect.createOneShot(length.millis, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(pattern, -1)
            }
            device.vibrate(effect)
        }.onFailure { Log.w(TAG, "vibrate failed", it) }
    }

    private fun playSound(alert: AlertSpec) {
        when (alert.soundKind) {
            SoundKind.BEEP -> playBeep(alert.volumePercent)
            SoundKind.SYSTEM -> playSystemTone(alert.volumePercent)
            SoundKind.NONE -> Unit
        }
    }

    private fun playBeep(volumePercent: Int) {
        // 音量0は「鳴らさない」の意味。1へ切り上げて鳴らさない。
        val volume = volumePercent.coerceIn(0, 100)
        if (volume <= 0) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_DURATION_MS)
            } finally {
                // 鳴り終わる前に解放すると音が切れるので、少しだけ待ってから捨てる。
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    runCatching { tone.release() }
                }, BEEP_DURATION_MS + 150L)
            }
        }.onFailure { Log.w(TAG, "beep failed", it) }
    }

    private fun playSystemTone(volumePercent: Int) {
        if (volumePercent.coerceIn(0, 100) <= 0) return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.volume = volumePercent.coerceIn(0, 100) / 100f
            ringtone.play()
        }.onFailure { Log.w(TAG, "system tone failed", it) }
    }

    private val postedAlertIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private fun postNotification(event: AlertEvent) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val text = event.label
        runCatching {
            val id = Notifications.alertNotificationId(event.atSecond.toLong())
            manager.notify(id, Notifications.alertNotification(context, text))
            postedAlertIds.add(id)
        }.onFailure { Log.w(TAG, "notify failed", it) }
    }

    /** 終話時に、このセッションで出した経過のお知らせだけを消す。 */
    fun cancelPostedAlerts() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val ids = synchronized(postedAlertIds) {
            val copy = postedAlertIds.toList()
            postedAlertIds.clear()
            copy
        }
        for (id in ids) {
            runCatching { manager.cancel(id) }
        }
    }

    private companion object {
        const val TAG = "AlertDispatcher"
        const val BEEP_DURATION_MS = 260
    }
}
