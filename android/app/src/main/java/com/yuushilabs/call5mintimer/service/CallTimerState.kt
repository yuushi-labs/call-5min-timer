package com.yuushilabs.call5mintimer.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 通話セッションの現在の状態（画面表示用）。
 * サービスと画面は同じプロセスなので、Flow をそのまま共有する。
 */
object CallTimerState {
    val running = MutableStateFlow(false)
    val counting = MutableStateFlow(false)
    /** 計測開始からの経過秒。待機中は -1。 */
    val elapsedSeconds = MutableStateFlow(-1)

    /** 現在のセッションの識別子（開始時刻）。終話・再開の取り違え防止に使う。 */
    val sessionId = MutableStateFlow(0L)

    /**
     * PHONE_STATE で「発信」と判定済み（リスナーの sawDialing と同じ役割）。
     * サービスを起動できなかった場合も発信の事実は残す（通知リスナーが最初から
     * ACTIVE の通知を見たときに応答で計測を始められるように）。
     */
    val detectedOutgoing = MutableStateFlow(false)

    /** 電池最適化の除外が無く、バックグラウンドからの起動を断念した。 */
    val batteryBlocked = MutableStateFlow(false)
}
