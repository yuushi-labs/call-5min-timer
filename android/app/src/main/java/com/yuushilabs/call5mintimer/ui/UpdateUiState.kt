package com.yuushilabs.call5mintimer.ui

import java.io.File

/** 更新まわりの画面状態。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val versionName: String, val notes: String, val sizeBytes: Long) : UpdateUiState
    data class Downloading(val received: Long, val total: Long) : UpdateUiState
    data object Installing : UpdateUiState
    data class NeedsPermission(val apk: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

/** 権限・役割の現在の状態（設定ガイドの表示用）。 */
data class PermissionSnapshot(
    val phoneState: Boolean = false,
    val notifications: Boolean = false,
    val notificationAccess: Boolean = false,
    val batteryExempt: Boolean = false,
) {
    /**
     * 発信を検知してタイマーを動かせる構成。
     *
     * 発信の検知は `READ_PHONE_STATE`（`CallStateReceiver` の OFFHOOK）で行う。
     * 相手の応答は通知アクセスで検知し、通知アクセスが無い端末はオフセット方式で代替する
     * （バックグラウンドからの起動には電池最適化の除外が必要）。
     */
    val canDetectCalls: Boolean
        get() = phoneState && (notificationAccess || batteryExempt)

    val readyCount: Int
        get() = listOf(phoneState, notifications, notificationAccess, batteryExempt).count { it }
}
