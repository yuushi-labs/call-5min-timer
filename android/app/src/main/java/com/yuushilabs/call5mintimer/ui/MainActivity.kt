package com.yuushilabs.call5mintimer.ui

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuushilabs.call5mintimer.ui.theme.CallTimerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallTimerTheme {
                CallTimerApp()
            }
        }
    }
}

private enum class Screen { Home, Guide }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallTimerApp(viewModel: CallTimerViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions = viewModel.permissions
    val updateState = viewModel.updateState
    val running by viewModel.sessionRunning.collectAsStateWithLifecycle()
    val counting by viewModel.sessionCounting.collectAsStateWithLifecycle()
    val elapsed by viewModel.sessionElapsed.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(Screen.Home) }
    var editor by remember { mutableStateOf<RuleEditorTarget?>(null) }

    // ガイド画面・編集シートではシステムの戻るでも Home / シートを閉じる。
    androidx.activity.compose.BackHandler(enabled = editor != null) { editor = null }
    androidx.activity.compose.BackHandler(enabled = editor == null && screen == Screen.Guide) {
        screen = Screen.Home
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshPermissions() }

    val requestCallRedirection: () -> Unit = {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (screen == Screen.Home) "call-5min-timer" else "動作チェック＆設定ガイド")
                },
                navigationIcon = {
                    if (screen == Screen.Guide) {
                        IconButton(onClick = { screen = Screen.Home }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (screen) {
            Screen.Home -> HomeScreen(
                modifier = Modifier.padding(padding),
                settings = settings,
                permissions = permissions,
                running = running,
                counting = counting,
                elapsedSeconds = elapsed,
                updateState = updateState,
                timeline = viewModel.presetTimeline(),
                onOpenGuide = { screen = Screen.Guide },
                onRequestPhoneState = {
                    phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenNotificationAccess = {
                    runCatching {
                        com.yuushilabs.call5mintimer.util.SystemIntents.openNotificationAccess(context)
                    }
                },
                onRequestCallRedirection = requestCallRedirection,
                onOpenBatterySettings = {
                    com.yuushilabs.call5mintimer.util.SystemIntents.openBatterySettings(context)
                },
                onSetStartMode = viewModel::setStartMode,
                onSetOffsetSeconds = viewModel::setOffsetSeconds,
                onSetShowElapsed = viewModel::setShowElapsedInNotification,
                onSetAfterFive = viewModel::setAfterFive,
                onRestorePreset = viewModel::restorePreset,
                onAddPeriodic = { editor = RuleEditorTarget.Periodic(viewModel.newPeriodicRule(), isNew = true) },
                onAddSpot = { editor = RuleEditorTarget.Spot(viewModel.newSpotRule(), isNew = true) },
                onEditPeriodic = { editor = RuleEditorTarget.Periodic(it, isNew = false) },
                onEditSpot = { editor = RuleEditorTarget.Spot(it, isNew = false) },
                onTogglePeriodic = viewModel::setPeriodicEnabled,
                onToggleSpot = viewModel::setSpotEnabled,
                onDeletePeriodic = viewModel::deletePeriodic,
                onDeleteSpot = viewModel::deleteSpot,
                onPreviewRule = viewModel::previewRule,
                onCheckUpdates = { viewModel.checkForUpdates(force = true) },
                onDownloadUpdate = viewModel::downloadAndInstall,
                onRetryInstall = viewModel::retryInstall,
                onOpenInstallSettings = viewModel::openInstallPermissionSettings,
                onSetAutoCheck = viewModel::setAutoCheckUpdates,
            )

            Screen.Guide -> SetupGuideScreen(
                modifier = Modifier.padding(padding),
                permissions = permissions,
                onRequestPhoneState = {
                    phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenNotificationAccess = {
                    runCatching {
                        com.yuushilabs.call5mintimer.util.SystemIntents.openNotificationAccess(context)
                    }
                },
                onRequestCallRedirection = requestCallRedirection,
            )
        }
    }

    editor?.let { target ->
        RuleEditorSheet(
            target = target,
            onDismiss = { editor = null },
            onSavePeriodic = { rule ->
                viewModel.savePeriodic(rule)
                editor = null
            },
            onSaveSpot = { rule ->
                viewModel.saveSpot(rule)
                editor = null
            },
            onDelete = { id, periodic ->
                if (periodic) viewModel.deletePeriodic(id) else viewModel.deleteSpot(id)
                editor = null
            },
            onPreview = viewModel::previewAlert,
        )
    }
}
