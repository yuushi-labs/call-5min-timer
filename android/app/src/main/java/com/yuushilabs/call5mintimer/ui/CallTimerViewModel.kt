package com.yuushilabs.call5mintimer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuushilabs.call5mintimer.core.AlertSpec
import com.yuushilabs.call5mintimer.core.AlertEvent
import com.yuushilabs.call5mintimer.core.PeriodicRule
import com.yuushilabs.call5mintimer.core.SpotRule
import com.yuushilabs.call5mintimer.core.TimerEngine
import com.yuushilabs.call5mintimer.core.TimerRules
import com.yuushilabs.call5mintimer.data.AppSettings
import com.yuushilabs.call5mintimer.data.SettingsRepository
import com.yuushilabs.call5mintimer.data.StartMode
import com.yuushilabs.call5mintimer.service.AlertDispatcher
import com.yuushilabs.call5mintimer.service.CallTimerState
import com.yuushilabs.call5mintimer.update.UpdateManager
import com.yuushilabs.call5mintimer.util.PermissionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class CallTimerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val updateManager = UpdateManager(application, repository)
    private val dispatcher = AlertDispatcher(application)

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    val sessionRunning = CallTimerState.running
    val sessionCounting = CallTimerState.counting
    val sessionElapsed = CallTimerState.elapsedSeconds
    val batteryBlocked = CallTimerState.batteryBlocked

    var permissions by mutableStateOf(PermissionSnapshot())
        private set

    var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    private var pendingUpdate: UpdateManager.CheckResult.Available? = null
    private var downloadedApk: File? = null

    init {
        refreshPermissions()
        updateManager.cleanupOldApks(keep = null)
        viewModelScope.launch {
            // DataStore の実データを待ってから判定する（初期値の autoCheckUpdates=true を
            // 信じない。OFF で保存済みなら起動時に確認しない）。
            val current = repository.current()
            if (current.autoCheckUpdates) {
                // デバウンス中の再起動でもバナーが復活するよう、保存済み本文から復元してから
                // ネットワーク確認を行う（確認しても Skipped なら復元したバナーを維持）。
                when (val restored = updateManager.restoreLastKnown()) {
                    is UpdateManager.CheckResult.Available -> {
                        if (updateState is UpdateUiState.Idle) {
                            pendingUpdate = restored
                            updateState = UpdateUiState.Available(
                                versionName = restored.info.versionName,
                                notes = restored.info.notes,
                                sizeBytes = restored.info.apkSizeBytes,
                            )
                        }
                    }

                    else -> Unit
                }
                checkForUpdates(force = false)
            }
        }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        permissions = PermissionSnapshot(
            phoneState = PermissionState.hasReadPhoneState(context),
            notifications = PermissionState.hasPostNotifications(context),
            notificationAccess = PermissionState.hasNotificationAccess(context),
            callRedirection = PermissionState.hasCallRedirectionRole(context),
            installPackages = PermissionState.hasInstallPackages(context),
            batteryExempt = PermissionState.isBatteryOptimizationExempt(context),
        )
    }

    // --- 設定 ---

    fun setStartMode(mode: StartMode) = viewModelScope.launch {
        repository.update { it.copy(startMode = mode) }
    }

    fun setOffsetSeconds(seconds: Int) = viewModelScope.launch {
        repository.update { it.copy(connectionOffsetSeconds = seconds.coerceIn(0, 60)) }
    }

    fun setShowElapsedInNotification(enabled: Boolean) = viewModelScope.launch {
        repository.update { it.copy(showElapsedInNotification = enabled) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) = viewModelScope.launch {
        repository.update { it.copy(autoCheckUpdates = enabled) }
    }

    fun setAfterFive(mode: com.yuushilabs.call5mintimer.core.AfterFiveMode) = viewModelScope.launch {
        repository.update { current ->
            current.copy(rules = current.rules.copy(afterFive = mode))
        }
    }

    fun restorePreset() = viewModelScope.launch { repository.restorePreset() }

    // --- ルール ---

    fun savePeriodic(rule: PeriodicRule) = viewModelScope.launch {
        repository.update { current ->
            val rules = current.rules
            val exists = rules.periodicRules.any { it.id == rule.id }
            val updated = if (exists) {
                rules.periodicRules.map { if (it.id == rule.id) rule else it }
            } else {
                rules.periodicRules + rule
            }
            current.copy(rules = rules.copy(periodicRules = updated))
        }
    }

    fun saveSpot(rule: SpotRule) = viewModelScope.launch {
        repository.update { current ->
            val rules = current.rules
            val exists = rules.spotRules.any { it.id == rule.id }
            val updated = if (exists) {
                rules.spotRules.map { if (it.id == rule.id) rule else it }
            } else {
                rules.spotRules + rule
            }
            current.copy(rules = rules.copy(spotRules = updated))
        }
    }

    fun deletePeriodic(id: String) = viewModelScope.launch {
        repository.update { current ->
            current.copy(
                rules = current.rules.copy(periodicRules = current.rules.periodicRules.filterNot { it.id == id }),
            )
        }
    }

    fun deleteSpot(id: String) = viewModelScope.launch {
        repository.update { current ->
            current.copy(
                rules = current.rules.copy(spotRules = current.rules.spotRules.filterNot { it.id == id }),
            )
        }
    }

    fun setPeriodicEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        repository.update { current ->
            current.copy(
                rules = current.rules.copy(
                    periodicRules = current.rules.periodicRules.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    },
                ),
            )
        }
    }

    fun setSpotEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        repository.update { current ->
            current.copy(
                rules = current.rules.copy(
                    spotRules = current.rules.spotRules.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    },
                ),
            )
        }
    }

    fun newPeriodicRule(): PeriodicRule = PeriodicRule(
        id = "periodic-" + UUID.randomUUID().toString().take(8),
        intervalSeconds = 60,
        startSecond = 60,
        endSecond = 240,
        scaleRepeats = true,
        alert = AlertSpec(),
    )

    fun newSpotRule(): SpotRule = SpotRule(
        id = "spot-" + UUID.randomUUID().toString().take(8),
        atSecond = 270,
        alert = AlertSpec(),
    )

    /** そのルール自身の設定を鳴らす（スケジュール上の別ルールを拾わない）。 */
    fun previewRule(ruleId: String) {
        val rules = settings.value.rules
        val periodic = rules.periodicRules.firstOrNull { it.id == ruleId }
        val spot = rules.spotRules.firstOrNull { it.id == ruleId }
        when {
            periodic != null -> {
                // 回数増加が ON なら「2回目の発火」を想定して 2 回で試す。
                val repeats = if (periodic.scaleRepeats) 2 else periodic.alert.fixedRepeatCount
                dispatcher.preview(periodic.alert, repeats)
            }

            spot != null -> dispatcher.preview(spot.alert, spot.alert.fixedRepeatCount)
        }
    }

    fun previewAlert(alert: AlertSpec, repeats: Int) {
        dispatcher.preview(alert, repeats)
    }

    /** 「ahamo 5分標準」が実際に鳴る時刻の一覧（画面の説明用）。 */
    fun presetTimeline(): List<AlertEvent> = TimerEngine(TimerRules.ahamoDefault()).schedule

    // --- アプリ内アップデート ---

    fun checkForUpdates(force: Boolean = true) {
        if (updateState is UpdateUiState.Checking || updateState is UpdateUiState.Downloading) return
        // デバウンスで Skipped になったときに、復元済みのバナーを壊さないように取っておく。
        val previousBanner = updateState as? UpdateUiState.Available
        updateState = UpdateUiState.Checking
        viewModelScope.launch {
            when (val result = updateManager.check(force)) {
                is UpdateManager.CheckResult.Available -> {
                    pendingUpdate = result
                    updateState = UpdateUiState.Available(
                        versionName = result.info.versionName,
                        notes = result.info.notes,
                        sizeBytes = result.info.apkSizeBytes,
                    )
                }

                UpdateManager.CheckResult.UpToDate -> updateState = UpdateUiState.UpToDate
                UpdateManager.CheckResult.Skipped -> updateState = previousBanner ?: UpdateUiState.Idle
                is UpdateManager.CheckResult.Failed -> updateState = UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadAndInstall() {
        val info = pendingUpdate?.info ?: return
        if (updateState is UpdateUiState.Downloading) return
        updateState = UpdateUiState.Downloading(0L, info.apkSizeBytes)
        viewModelScope.launch {
            val result = updateManager.download(info) { received, total ->
                updateState = UpdateUiState.Downloading(received, total)
            }
            result.onSuccess { file ->
                downloadedApk = file
                install(file)
            }.onFailure { error ->
                updateState = UpdateUiState.Error(error.message ?: "ダウンロードに失敗しました")
            }
        }
    }

    fun retryInstall() {
        val file = downloadedApk ?: return
        install(file)
    }

    fun openInstallPermissionSettings() {
        updateManager.openInstallPermissionSettings()
    }

    private fun install(file: File) {
        updateManager.install(file)
            .onSuccess { updateState = UpdateUiState.Installing }
            .onFailure { error ->
                updateState = if (error is UpdateManager.InstallPermissionRequired) {
                    UpdateUiState.NeedsPermission(file)
                } else {
                    UpdateUiState.Error(error.message ?: "インストーラを開けませんでした")
                }
            }
    }
}
