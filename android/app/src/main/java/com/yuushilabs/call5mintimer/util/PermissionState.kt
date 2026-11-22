package com.yuushilabs.call5mintimer.util

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** 権限・役割の現在の状態。設定ガイドのチェック表示に使う。 */
object PermissionState {

    fun hasReadPhoneState(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 通知へのアクセス（NotificationListenerService）。 */
    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** 電池の最適化の除外（バックグラウンド起動の前提）。 */
    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val power = context.getSystemService(android.os.PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun notificationChannelsBlocked(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled().not()
    }
}
