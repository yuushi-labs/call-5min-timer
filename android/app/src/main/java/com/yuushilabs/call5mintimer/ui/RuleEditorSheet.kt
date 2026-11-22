package com.yuushilabs.call5mintimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yuushilabs.call5mintimer.core.AlertAction
import com.yuushilabs.call5mintimer.core.AlertSpec
import com.yuushilabs.call5mintimer.core.PeriodicRule
import com.yuushilabs.call5mintimer.core.SoundKind
import com.yuushilabs.call5mintimer.core.SpotRule
import com.yuushilabs.call5mintimer.core.VibrationLength
import com.yuushilabs.call5mintimer.core.formatClock

/** 編集中のルール。 */
sealed interface RuleEditorTarget {
    val isNew: Boolean

    data class Periodic(val rule: PeriodicRule, override val isNew: Boolean) : RuleEditorTarget
    data class Spot(val rule: SpotRule, override val isNew: Boolean) : RuleEditorTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorSheet(
    target: RuleEditorTarget,
    onDismiss: () -> Unit,
    onSavePeriodic: (PeriodicRule) -> Unit,
    onSaveSpot: (SpotRule) -> Unit,
    onDelete: (id: String, periodic: Boolean) -> Unit,
    onPreview: (AlertSpec, Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val initialAlert = when (target) {
            is RuleEditorTarget.Periodic -> target.rule.alert
            is RuleEditorTarget.Spot -> target.rule.alert
        }
        var enabled by remember(target) {
            mutableStateOf(
                when (target) {
                    is RuleEditorTarget.Periodic -> target.rule.enabled
                    is RuleEditorTarget.Spot -> target.rule.enabled
                },
            )
        }
        var alert by remember(target) { mutableStateOf(initialAlert) }
        var intervalText by remember(target) {
            mutableStateOf((target as? RuleEditorTarget.Periodic)?.rule?.intervalSeconds?.toString() ?: "60")
        }
        var startText by remember(target) {
            mutableStateOf((target as? RuleEditorTarget.Periodic)?.rule?.startSecond?.let(::formatClock) ?: "1:00")
        }
        var endText by remember(target) {
            mutableStateOf((target as? RuleEditorTarget.Periodic)?.rule?.endSecond?.let(::formatClock) ?: "4:00")
        }
        var scaleRepeats by remember(target) {
            mutableStateOf((target as? RuleEditorTarget.Periodic)?.rule?.scaleRepeats ?: true)
        }
        var atText by remember(target) {
            mutableStateOf((target as? RuleEditorTarget.Spot)?.rule?.atSecond?.let(::formatClock) ?: "4:30")
        }
        var error by remember(target) { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when (target) {
                    is RuleEditorTarget.Periodic -> if (target.isNew) "定期通知を追加" else "定期通知を編集"
                    is RuleEditorTarget.Spot -> if (target.isNew) "スポット通知を追加" else "スポット通知を編集"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("このルールを有効にする", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            when (target) {
                is RuleEditorTarget.Periodic -> {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                        label = { Text("間隔（秒）") },
                        supportingText = { Text("60 で1分ごと。30 なら30秒ごと。") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            label = { Text("開始 (m:ss)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it },
                            label = { Text("終了 (m:ss)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("経過回数に応じて回数を増やす", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "ON: 1回目=1回、2回目=2回… / OFF: 毎回同じ回数",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = scaleRepeats, onCheckedChange = { scaleRepeats = it })
                    }
                }

                is RuleEditorTarget.Spot -> {
                    OutlinedTextField(
                        value = atText,
                        onValueChange = { atText = it },
                        label = { Text("鳴らす時刻 (m:ss)") },
                        supportingText = { Text("例: 4:30 / 4:50 / 5:00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text("鳴らし方", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertAction.entries.forEach { action ->
                    FilterChip(
                        selected = alert.action == action,
                        onClick = { alert = alert.copy(action = action) },
                        label = { Text(action.label()) },
                    )
                }
            }

            if (alert.vibrates) {
                Text("バイブの長さ", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VibrationLength.entries.forEach { length ->
                        FilterChip(
                            selected = alert.vibrationLength == length,
                            onClick = { alert = alert.copy(vibrationLength = length) },
                            label = { Text("${length.label()}（${length.millis}ms）") },
                        )
                    }
                }
                if (!(target is RuleEditorTarget.Periodic && scaleRepeats)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("バイブ回数", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            alert = alert.copy(fixedRepeatCount = (alert.fixedRepeatCount - 1).coerceAtLeast(1))
                        }) { Text("−") }
                        Text("${alert.fixedRepeatCount} 回")
                        TextButton(onClick = {
                            alert = alert.copy(fixedRepeatCount = (alert.fixedRepeatCount + 1).coerceAtMost(10))
                        }) { Text("＋") }
                    }
                } else {
                    Text(
                        "バイブ回数は経過分数に合わせて自動で増えます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = alert.repeatGapMs.toString(),
                    onValueChange = { text ->
                        text.filter { it.isDigit() }.toLongOrNull()?.let { alert = alert.copy(repeatGapMs = it) }
                    },
                    label = { Text("連続バイブの間隔（ms）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (alert.action != AlertAction.SILENT) {
                Text("通知音", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundKind.entries.forEach { kind ->
                        FilterChip(
                            selected = alert.soundKind == kind,
                            onClick = { alert = alert.copy(soundKind = kind) },
                            label = { Text(kind.label()) },
                        )
                    }
                }
                if (alert.playsSound) {
                    Text("音量 ${alert.volumePercent}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = alert.volumePercent.toFloat(),
                        onValueChange = { alert = alert.copy(volumePercent = it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 8,
                    )
                }
            }

            Text(
                "通知そのものはどの設定でも出ます（サイレント通知を含む）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    onPreview(alert, if (target is RuleEditorTarget.Periodic && scaleRepeats) 2 else alert.fixedRepeatCount)
                }) {
                    Text("テスト鳴動")
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        when (target) {
                            is RuleEditorTarget.Periodic -> {
                                val interval = intervalText.toIntOrNull()
                                val start = parseClock(startText)
                                val end = parseClock(endText)
                                if (interval == null || interval < 5) {
                                    error = "間隔は5秒以上で入力してください"
                                    return@Button
                                }
                                if (start == null || end == null) {
                                    error = "時刻は 1:00 の形式で入力してください"
                                    return@Button
                                }
                                if (end < start) {
                                    error = "終了は開始より後にしてください"
                                    return@Button
                                }
                                onSavePeriodic(
                                    target.rule.copy(
                                        enabled = enabled,
                                        intervalSeconds = interval,
                                        startSecond = start,
                                        endSecond = end,
                                        scaleRepeats = scaleRepeats,
                                        alert = alert.normalized(),
                                    ),
                                )
                            }

                            is RuleEditorTarget.Spot -> {
                                val at = parseClock(atText)
                                if (at == null) {
                                    error = "時刻は 4:30 の形式で入力してください"
                                    return@Button
                                }
                                onSaveSpot(
                                    target.rule.copy(
                                        enabled = enabled,
                                        atSecond = at,
                                        alert = alert.normalized(),
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存")
                }
            }

            if (!target.isNew) {
                TextButton(
                    onClick = {
                        when (target) {
                            is RuleEditorTarget.Periodic -> onDelete(target.rule.id, true)
                            is RuleEditorTarget.Spot -> onDelete(target.rule.id, false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("このルールを削除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** `m:ss` / `h:mm:ss` / 秒数のどれでも受け取る。 */
internal fun parseClock(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.contains(':')) return trimmed.toIntOrNull()?.takeIf { it >= 0 }
    val parts = trimmed.split(':').map { it.trim().toIntOrNull() ?: return null }
    val seconds = when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3_600 + parts[1] * 60 + parts[2]
        else -> null
    }
    return seconds?.takeIf { it >= 0 }
}

private fun AlertAction.label(): String = when (this) {
    AlertAction.VIBRATE_ONLY -> "バイブのみ"
    AlertAction.SOUND_ONLY -> "音のみ"
    AlertAction.VIBRATE_AND_SOUND -> "バイブ＋音"
    AlertAction.SILENT -> "サイレント"
}

private fun VibrationLength.label(): String = when (this) {
    VibrationLength.SHORT -> "短"
    VibrationLength.MEDIUM -> "中"
    VibrationLength.LONG -> "長"
}

private fun SoundKind.label(): String = when (this) {
    SoundKind.BEEP -> "ビープ音"
    SoundKind.SYSTEM -> "端末の通知音"
    SoundKind.NONE -> "音なし"
}
