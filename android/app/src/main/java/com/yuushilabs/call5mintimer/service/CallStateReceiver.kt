package com.yuushilabs.call5mintimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * CallRedirectionService を使えない場合の補助。
 *
 * PHONE_STATE から発信相当（RINGING を経ない OFFHOOK）を拾い、終話（IDLE）で止める。
 * 着信は RINGING → OFFHOOK の順になるので、直前が RINGING なら発信とみなさない。
 * （プロセスが落ちても判定できるよう、直前の状態は SharedPreferences に残す）
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                preferences.edit().putString(KEY_LAST_STATE, state).apply()
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val previous = preferences.getString(KEY_LAST_STATE, null)
                preferences.edit().putString(KEY_LAST_STATE, state).apply()
                val looksOutgoing = previous != TelephonyManager.EXTRA_STATE_RINGING
                if (looksOutgoing) {
                    Log.i(TAG, "OFFHOOK without RINGING: treating as outgoing call")
                    // 前の応答待ちセッションが残っていれば start() がセッションを更新する。
                    // バックグラウンドからは電池最適化の除外があるときだけ起動する。
                    // 除外が無い場合は起動を断念し、UI の診断に警告を出す。
                    CallTimerForegroundService.start(context, fromBackground = true)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                preferences.edit().putString(KEY_LAST_STATE, state).apply()
                if (CallTimerState.running.value) {
                    CallTimerForegroundService.stop(context)
                }
            }
        }
    }

    private companion object {
        const val TAG = "CallStateReceiver"
        const val PREFERENCES = "call_state"
        const val KEY_LAST_STATE = "last_state"
    }
}
