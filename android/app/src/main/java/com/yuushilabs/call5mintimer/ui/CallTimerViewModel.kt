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
            val current = settings.first()
            if (current.autoCheckUpdates) checkForUpdates(force = false)
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

    /** そのルールが実際に鳴る1件をプレビューする（回数増加も反映）。 */
    fun previewRule(ruleId: String) {
        val rules = settings.value.rules
        val engine = TimerEngine(rules, maxSeconds = 3_600)
        val event: AlertEvent? = engine.schedule.firstOrNull { it.ruleId == ruleId }
            ?: engine.eventsAt(0).firstOrNull()
        if (event != null) {
            dispatcher.dispatch(event)
        } else {
            val alert = rules.periodicRules.firstOrNull { it.id == ruleId }?.alert
                ?: rules.spotRules.firstOrNull { it.id == ruleId }?.alert
                ?: AlertSpec()
            dispatcher.preview(alert)
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
                UpdateManager.CheckResult.Skipped -> updateState = UpdateUiState.Idle
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
