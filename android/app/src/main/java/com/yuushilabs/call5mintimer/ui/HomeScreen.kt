package com.yuushilabs.call5mintimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuushilabs.call5mintimer.BuildConfig
import com.yuushilabs.call5mintimer.core.AfterFiveMode
import com.yuushilabs.call5mintimer.core.AlertEvent
import com.yuushilabs.call5mintimer.core.ElapsedTracker
import com.yuushilabs.call5mintimer.core.PeriodicRule
import com.yuushilabs.call5mintimer.core.SpotRule
import com.yuushilabs.call5mintimer.core.TimerEngine
import com.yuushilabs.call5mintimer.core.TimerRules
import com.yuushilabs.call5mintimer.core.formatClock
import com.yuushilabs.call5mintimer.data.AppSettings
import com.yuushilabs.call5mintimer.data.StartMode

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    settings: AppSettings,
    permissions: PermissionSnapshot,
    running: Boolean,
    counting: Boolean,
    elapsedSeconds: Int,
    batteryBlocked: Boolean,
    updateState: UpdateUiState,
    timeline: List<AlertEvent>,
    onOpenGuide: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSetStartMode: (StartMode) -> Unit,
    onSetOffsetSeconds: (Int) -> Unit,
    onSetStartElapsed: (Int) -> Unit,
    onSetShowElapsed: (Boolean) -> Unit,
    onSetAfterFive: (AfterFiveMode) -> Unit,
    onRestorePreset: () -> Unit,
    onAddPeriodic: () -> Unit,
    onAddSpot: () -> Unit,
    onEditPeriodic: (PeriodicRule) -> Unit,
    onEditSpot: (SpotRule) -> Unit,
    onTogglePeriodic: (String, Boolean) -> Unit,
    onToggleSpot: (String, Boolean) -> Unit,
    onDeletePeriodic: (String) -> Unit,
    onDeleteSpot: (String) -> Unit,
    onPreviewRule: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRetryInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onSetAutoCheck: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(running = running, counting = counting, elapsedSeconds = elapsedSeconds, batteryBlocked = batteryBlocked)
        }
        item {
            PermissionsCard(
                permissions = permissions,
                onOpenGuide = onOpenGuide,
                onRequestPhoneState = onRequestPhoneState,
                onRequestNotifications = onRequestNotifications,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenBatterySettings = onOpenBatterySettings,
            )
        }
        item {
            PresetTimelineCard(timeline)
        }
        item {
            StartModeCard(
                settings = settings,
                onSetStartMode = onSetStartMode,
                onSetOffsetSeconds = onSetOffsetSeconds,
                onSetStartElapsed = onSetStartElapsed,
                onSetShowElapsed = onSetShowElapsed,
            )
        }
        item {
            RulesCard(
                rules = settings.rules,
                onSetAfterFive = onSetAfterFive,
                onRestorePreset = onRestorePreset,
                onAddPeriodic = onAddPeriodic,
                onAddSpot = onAddSpot,
                onEditPeriodic = onEditPeriodic,
                onEditSpot = onEditSpot,
                onTogglePeriodic = onTogglePeriodic,
                onToggleSpot = onToggleSpot,
                onDeletePeriodic = onDeletePeriodic,
                onDeleteSpot = onDeleteSpot,
                onPreviewRule = onPreviewRule,
            )
        }
        item {
            UpdateCard(
                updateState = updateState,
                autoCheck = settings.autoCheckUpdates,
                onCheckUpdates = onCheckUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onRetryInstall = onRetryInstall,
                onOpenInstallSettings = onOpenInstallSettings,
                onSetAutoCheck = onSetAutoCheck,
            )
        }
        item {
            Text(
                "通話の内容・電話番号・通話履歴は保存も送信もしません。通信はアプリ内アップデートの確認だけに使います。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, counting: Boolean, elapsedSeconds: Int, batteryBlocked: Boolean) {
    val title = when {
        counting -> "通話計測中"
        running -> "相手の応答を待っています"
        else -> "待機中"
    }
    val detail = when {
        counting -> "経過 ${formatClock(elapsedSeconds)} / 5分まで ${formatClock((300 - elapsedSeconds).coerceAtLeast(0))}"
        running -> "呼び出し中はカウントしません。相手が出た瞬間に 1 回バイブします。"
        else -> "発信すると自動で起動し、終話で止まります（常駐しません）。"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (counting) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (batteryBlocked && !counting) {
                Text(
                    "電池の最適化の除外が無いため、バックグラウンドからの起動を見送りました。" +
                        "「動作に必要な設定」の「電池の最適化を除外」を設定してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionSnapshot,
    onOpenGuide: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    SectionCard(
        title = "動作に必要な設定（${permissions.readyCount}/4）",
        subtitle = "発信を通話アプリの通知からも拾うため、通知へのアクセスを推奨します。",
    ) {
        PermissionRow("電話の状態", "発信・終話の検知", permissions.phoneState, "許可", onRequestPhoneState)
        PermissionRow("通知の表示", "通話中の経過とお知らせ", permissions.notifications, "許可", onRequestNotifications)
        PermissionRow(
            "通知へのアクセス",
            "相手が出た瞬間（00:00）の検知",
            permissions.notificationAccess,
            "設定",
            onOpenNotificationAccess,
        )
        PermissionRow(
            "電池の最適化を除外",
            "画面消灯・バックグラウンドでも起動させる",
            permissions.batteryExempt,
            "設定",
            onOpenBatterySettings,
        )
        OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
            Text("OPPO（ColorOS）向けの設定ガイドを開く")
        }
        if (!permissions.canDetectCalls) {
            Text(
                "電話の状態に加えて、通知へのアクセスか電池最適化の除外が必要です" +
                    "（電池最適化だけなら「オフセット」方式で動きます）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) Color(0xFF1B7F4C) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text("OK", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1B7F4C))
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PresetTimelineCard(timeline: List<AlertEvent>) {
    val preview = timeline.filter { it.atSecond <= 300 }
    SectionCard(
        title = "ahamo 5分標準の鳴り方",
        subtitle = "経過分数と同じ回数のバイブで、画面を見なくても時間が分かります。",
    ) {
        preview.forEach { event ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatClock(event.atSecond),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    buildString {
                        append("バイブ${event.vibrationRepeats}回")
                        if (event.alert.vibrationLength.millis >= 600) append("（長め）")
                        if (event.playsSound) append(" ＋ 通知音")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (event.atSecond == 0) "開始" else if (event.atSecond == 300) "5分" else "経過",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StartModeCard(
    settings: AppSettings,
    onSetStartMode: (StartMode) -> Unit,
    onSetOffsetSeconds: (Int) -> Unit,
    onSetStartElapsed: (Int) -> Unit,
    onSetShowElapsed: (Boolean) -> Unit,
) {
    SectionCard(
        title = "計測開始の検知",
        subtitle = "相手が出た瞬間から数えるのが基本です。通知を読めない端末ではオフセットで代替します。",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.startMode == StartMode.NOTIFICATION,
                onClick = { onSetStartMode(StartMode.NOTIFICATION) },
                label = { Text("通知で検知（推奨）") },
            )
            FilterChip(
                selected = settings.startMode == StartMode.OFFSET,
                onClick = { onSetStartMode(StartMode.OFFSET) },
                label = { Text("オフセット") },
            )
        }
        if (settings.startMode == StartMode.OFFSET) {
            Text(
                "発信から ${settings.connectionOffsetSeconds} 秒後に計測を始めます。",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = settings.connectionOffsetSeconds.toFloat(),
                onValueChange = { onSetOffsetSeconds(it.toInt()) },
                valueRange = 0f..30f,
                steps = 29,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "通知に現在の経過時間を表示する",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.showElapsedInNotification,
                onCheckedChange = onSetShowElapsed,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("開始時の経過秒", style = MaterialTheme.typography.titleSmall)
        Text(
            "相手が出てから通知が「通話中」に変わるまでの遅れを埋めます。" +
                "今は ${formatClock(settings.startElapsedSeconds)} から計測を始めます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onSetStartElapsed((settings.startElapsedSeconds - 1).coerceAtLeast(0)) },
            ) {
                Text("−1秒")
            }
            Text(
                formatClock(settings.startElapsedSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onSetStartElapsed(
                        (settings.startElapsedSeconds + 1)
                            .coerceAtMost(ElapsedTracker.MAX_START_ELAPSED_SECONDS),
                    )
                },
            ) {
                Text("+1秒")
            }
        }
        Slider(
            value = settings.startElapsedSeconds.toFloat(),
            onValueChange = { onSetStartElapsed(it.toInt()) },
            valueRange = 0f..ElapsedTracker.MAX_START_ELAPSED_SECONDS.toFloat(),
            steps = ElapsedTracker.MAX_START_ELAPSED_SECONDS - 1,
        )
        Text(
            if (settings.startElapsedSeconds > 0) {
                "0:00 の合図は開始の瞬間に鳴り、5分の合図は " +
                    "${formatClock(TimerEngine.FIVE_MINUTES - settings.startElapsedSeconds)} に来ます。"
            } else {
                "0:00 からスタート（既定）。計測が遅れる端末では 5〜8 秒ほどにすると実際の通話時間に近づきます。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RulesCard(
    rules: TimerRules,
    onSetAfterFive: (AfterFiveMode) -> Unit,
    onRestorePreset: () -> Unit,
    onAddPeriodic: () -> Unit,
    onAddSpot: () -> Unit,
    onEditPeriodic: (PeriodicRule) -> Unit,
    onEditSpot: (SpotRule) -> Unit,
    onTogglePeriodic: (String, Boolean) -> Unit,
    onToggleSpot: (String, Boolean) -> Unit,
    onDeletePeriodic: (String) -> Unit,
    onDeleteSpot: (String) -> Unit,
    onPreviewRule: (String) -> Unit,
) {
    SectionCard(
        title = "通知ルール",
        subtitle = "どのルールでも通知は出ます。バイブ・音の組み合わせを個別に設定できます。",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("定期通知", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAddPeriodic) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("追加")
            }
        }
        if (rules.periodicRules.isEmpty()) {
            Text("定期通知はありません", style = MaterialTheme.typography.bodySmall)
        }
        rules.periodicRules.forEach { rule ->
            RuleRow(
                title = rule.summary(),
                subtitle = rule.alert.summary(),
                enabled = rule.enabled,
                onToggle = { onTogglePeriodic(rule.id, it) },
                onPreview = { onPreviewRule(rule.id) },
                onEdit = { onEditPeriodic(rule) },
                onDelete = { onDeletePeriodic(rule.id) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("スポット通知", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAddSpot) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("追加")
            }
        }
        if (rules.spotRules.isEmpty()) {
            Text("スポット通知はありません", style = MaterialTheme.typography.bodySmall)
        }
        rules.spotRules.sortedBy { it.atSecond }.forEach { rule ->
            RuleRow(
                title = "${formatClock(rule.atSecond)} に1回",
                subtitle = rule.alert.summary(),
                enabled = rule.enabled,
                onToggle = { onToggleSpot(rule.id, it) },
                onPreview = { onPreviewRule(rule.id) },
                onEdit = { onEditSpot(rule) },
                onDelete = { onDeleteSpot(rule.id) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("5分以降の挙動", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rules.afterFive == AfterFiveMode.PER_MINUTE,
                onClick = { onSetAfterFive(AfterFiveMode.PER_MINUTE) },
                label = { Text("1分ごと") },
            )
            FilterChip(
                selected = rules.afterFive == AfterFiveMode.ONCE_AT_FIVE,
                onClick = { onSetAfterFive(AfterFiveMode.ONCE_AT_FIVE) },
                label = { Text("5分で1回") },
            )
            FilterChip(
                selected = rules.afterFive == AfterFiveMode.SILENT,
                onClick = { onSetAfterFive(AfterFiveMode.SILENT) },
                label = { Text("鳴らさない") },
            )
        }
        OutlinedButton(onClick = onRestorePreset, modifier = Modifier.fillMaxWidth()) {
            Text("「ahamo 5分標準」プリセットに戻す")
        }
    }
}

@Composable
private fun RuleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "テスト鳴動")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "編集")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "削除")
            }
        }
    }
}

@Composable
private fun UpdateCard(
    updateState: UpdateUiState,
    autoCheck: Boolean,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRetryInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onSetAutoCheck: (Boolean) -> Unit,
) {
    SectionCard(
        title = "アプリ内アップデート",
        subtitle = "GitHub Releases の最新版を確認します（現在 v${BuildConfig.VERSION_NAME}）。",
    ) {
        when (updateState) {
            UpdateUiState.Idle -> Unit
            UpdateUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("確認中…", style = MaterialTheme.typography.bodySmall)
            }

            UpdateUiState.UpToDate -> Text(
                "最新版です。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1B7F4C),
            )

            is UpdateUiState.Available -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "v${updateState.versionName} があります",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (updateState.sizeBytes > 0) {
                    Text(
                        "サイズ ${"%.1f".format(updateState.sizeBytes / 1024.0 / 1024.0)} MB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (updateState.notes.isNotBlank()) {
                    Text(
                        updateState.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDownloadUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text("ダウンロードして更新")
                }
            }

            is UpdateUiState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fraction = if (updateState.total > 0) {
                    (updateState.received.toFloat() / updateState.total).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "ダウンロード中 ${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            UpdateUiState.Installing -> Text(
                "インストーラを開きました。画面の案内に従って更新してください。",
                style = MaterialTheme.typography.bodySmall,
            )

            is UpdateUiState.NeedsPermission -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "「提供元不明のアプリ」の許可が必要です。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenInstallSettings) { Text("許可する") }
                    OutlinedButton(onClick = onRetryInstall) { Text("もう一度") }
                }
            }

            is UpdateUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(updateState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("起動時に自動で確認", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = autoCheck, onCheckedChange = onSetAutoCheck)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCheckUpdates) { Text("更新を確認") }
            if (updateState is UpdateUiState.Error) {
                OutlinedButton(onClick = onCheckUpdates) { Text("再試行") }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "自動更新は GitHub の公開リポジトリ（${com.yuushilabs.call5mintimer.update.ReleaseClient.REPOSITORY}）だけを参照します。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
