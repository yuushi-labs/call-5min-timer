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
}
