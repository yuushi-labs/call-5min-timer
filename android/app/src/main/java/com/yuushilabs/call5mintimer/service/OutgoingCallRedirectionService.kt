package com.yuushilabs.call5mintimer.service

import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

/**
 * 発信の瞬間に OS から呼ばれるトリガー。
 *
 * 電話帳ナビ等が使う CallScreeningService とは別の役割なので競合しない。発信先番号の
 * 改変・ブロックは一切せず、そのまま通す（placeCallUnmodified）。ここで通話中だけ動く
 * タイマーサービスを起こし、相手の応答検知へ引き継ぐ。
 */
class OutgoingCallRedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        initialGateway: PhoneAccountHandle,
        speakerphoneOn: Boolean,
    ) {
        // タイマーの起動に失敗しても通話は必ずそのまま通す。
        runCatching { CallTimerForegroundService.start(this) }
            .onFailure { Log.w(TAG, "timer start failed; letting the call proceed", it) }
        placeCallUnmodified()
    }

    private companion object {
        const val TAG = "CallRedirection"
    }
}
