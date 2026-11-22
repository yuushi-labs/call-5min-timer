package com.yuushilabs.call5mintimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuushilabs.call5mintimer.util.SystemIntents

/**
 * 権限・役割と OPPO（ColorOS）固有設定のガイド。
 * 各項目の現在の状態を出し、押すと該当の設定画面へ移動する。
 */
@Composable
fun SetupGuideScreen(
    modifier: Modifier = Modifier,
    permissions: PermissionSnapshot,
    onRequestPhoneState: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestCallRedirection: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "発信を検知してタイマーを動かすための設定です。上から順に確認してください。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            GuideStep(
                number = 1,
                title = "電話の状態",
                granted = permissions.phoneState,
                body = "発信・終話を読むための権限です。許可しないと終話で止まれません。",
                actionLabel = "許可する",
                onAction = onRequestPhoneState,
            )
        }
        item {
            GuideStep(
                number = 2,
                title = "通知の表示",
                granted = permissions.notifications,
                body = "通話中の経過とお知らせを出します（Android 13 以降）。",
                actionLabel = "許可する",
                onAction = onRequestNotifications,
            )
        }
        item {
            GuideStep(
                number = 3,
                title = "通知へのアクセス",
                granted = permissions.notificationAccess,
                body = "通話アプリの通知から「相手が出た瞬間（00:00）」を検知します。数字や通話内容は保存しません。",
                actionLabel = "設定を開く",
                onAction = onOpenNotificationAccess,
            )
        }
        item {
            GuideStep(
                number = 4,
                title = "発信リダイレクト",
                granted = permissions.callRedirection,
                body = "発信の瞬間にアプリを起動します。迷惑電話対策（CallScreeningService）とは別の枠なので、電話帳ナビの設定は変更不要です。",
                actionLabel = "設定を開く",
                onAction = onRequestCallRedirection,
            )
        }
        item {
            GuideStep(
                number = 5,
                title = "電池の最適化を除外",
                granted = permissions.batteryExempt,
                body = "画面消灯中・アプリがバックグラウンドでも発信時に起動できるようにします（OPPO では「制限なし」に相当）。「オフセット」方式で計測するには必須です。",
                actionLabel = "設定を開く",
                onAction = { SystemIntents.openBatterySettings(context) },
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OPPO（ColorOS）での安定動作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "ColorOS はバックグラウンドのアプリを強く止めます。次の2つを設定すると、画面ロック中やタスクキル後でも発信時に起動しやすくなります。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    GuideLink("電池の最適化から外す") { SystemIntents.openBatterySettings(context) }
                    GuideLink("自動起動を許可する") { SystemIntents.openAutoStartSettings(context) }
                    GuideLink("アプリ情報を開く") { SystemIntents.openAppDetails(context) }
                    Text(
                        "設定 > アプリ > call-5min-timer > 「電池」→「制限なし」／「自動起動」を ON。項目名は ColorOS の版によって少し異なります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("電話帳ナビとの関係", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "本アプリは迷惑電話対策の枠（CallScreeningService）を取りません。電話帳ナビの判定・表示はそのまま動きます。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("うまく動かないとき", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "・発信しても起動しない → 「発信リダイレクト」か「電池の最適化を除外」を設定\n" +
                            "・相手が出てもカウントが始まらない → 通知アクセスを許可するか、計測開始を「オフセット」に変更\n" +
                            "・オフセット方式で計測が始まらない → 電池の最適化を除外する（バックグラウンド起動の制限のため）\n" +
                            "・終話しても通知が消えない → 電話の状態を許可する\n" +
                            "・着信でも動いてしまう → 発信のみ対象です。誤動作する場合は通知アクセス方式に切り替えてください",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    granted: Boolean,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF1B7F4C) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$number. $title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(body, style = MaterialTheme.typography.bodySmall)
                if (!granted) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                } else {
                    Text("設定済み", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1B7F4C))
                }
            }
        }
    }
}

@Composable
private fun GuideLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
