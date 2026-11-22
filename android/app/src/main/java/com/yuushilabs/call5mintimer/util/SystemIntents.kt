package com.yuushilabs.call5mintimer.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** システム設定画面への導線。ガイド画面・権限カードから使う。 */
object SystemIntents {

    /**
     * 電池の最適化の設定画面を開く（ColorOS では「制限なし」に相当）。
     *
     * 除外を直接お願いする [`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`] は
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 権限が必要なので使わない。端末の設定画面を開き、
     * ユーザーが「制限なし（最適化しない）」を選ぶ。状態の確認だけなら権限は要らない。
     */
    fun openBatterySettings(context: Context) {
        val direct = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }.recoverCatching {
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** ColorOS の自動起動設定。端末により画面名が違うため、開けたものを使い、無ければアプリ情報へ。 */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        for (component in candidates) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        openAppDetails(context)
    }

    fun openNotificationAccess(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}
