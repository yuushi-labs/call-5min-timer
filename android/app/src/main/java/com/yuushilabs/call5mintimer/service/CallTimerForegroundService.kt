package com.yuushilabs.call5mintimer.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yuushilabs.call5mintimer.core.TimerEngine
import com.yuushilabs.call5mintimer.core.formatClock
import com.yuushilabs.call5mintimer.data.AppSettings
import com.yuushilabs.call5mintimer.data.SettingsRepository
import com.yuushilabs.call5mintimer.data.StartMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 通話中だけ動くタイマー本体。
 *
 * 発信の検知（PHONE_STATE の OFFHOOK）で起動し、相手の応答が分かった時点
 * （or オフセット秒後）から 0 秒を数え始める。終話（CALL_STATE_IDLE）または通話通知の
 * 消滅で自分から止まり、常駐しない。
 *
 * 起動経路について:
 * - 発信（`CallStateReceiver`）はバックグラウンドから動くため、Android 12+ の前面サービス
 *   起動制限を回避できる「電池最適化の除外」があるときだけ起動する（無ければ UI に警告）
 * - 通知リスナー（`CallNotificationListener`）は通常の前面。呼び出し中に待機状態で起動し、
 *   相手の応答（ACTIVE）で計測を始める（DIALING の間は数えない）
 */
class CallTimerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SettingsRepository
    private lateinit var dispatcher: AlertDispatcher

    private var settings: AppSettings = AppSettings()
    private var engine: TimerEngine? = null
    private var tickJob: Job? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: Any? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var foregroundStarted = false
    private var sessionInitialized = false
    private var counting = false
    private var pendingConnected = false
    private var pendingConnectedAtElapsed = 0L
    private var sessionStartElapsed = 0L
    private var countStartElapsed = 0L
    private var lastDispatchedSecond = -1
    private var sawNonIdle = false
    private var lastNotificationText = ""

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        dispatcher = AlertDispatcher(applicationContext)
        CallTimerState.running.value = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }

            ACTION_CONNECTED -> {
                pendingConnected = true
                // 応答の時刻を計測起点として保持する（初期化が遅れても経過時間がずれない）。
                if (pendingConnectedAtElapsed == 0L) {
                    pendingConnectedAtElapsed = SystemClock.elapsedRealtime()
                }
                if (!sessionInitialized) {
                    // 通知リスナーから直接届いた場合（発信トリガーより先）も、ここで初期化する。
                    startSession()
                } else {
                    ensureForeground("相手の応答を検知しました")
                    startCounting(0L, baseElapsed = pendingConnectedAtElapsed)
                }
            }

            ACTION_START, null -> {
                startSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        if (sessionInitialized) return
        sessionInitialized = true
        sessionStartElapsed = SystemClock.elapsedRealtime()
        CallTimerState.sessionId.value = sessionStartElapsed
        CallTimerState.detectedOutgoing.value = true
        if (!ensureForeground("相手の応答を待っています")) {
            // 前面サービスへ昇格できない（バックグラウンド起動制限など）場合は
            // 黙って続けず、ここで諦める。UI には電池最適化の警告を出す。
            Log.w(TAG, "could not promote to foreground service; giving up this session")
            CallTimerState.batteryBlocked.value = true
            shutdown()
            return
        }
        CallTimerState.batteryBlocked.value = false
        registerCallStateCallback()
        scope.launch {
            val loaded = runCatching { repository.current() }.getOrElse { AppSettings() }
            settings = loaded
            engine = TimerEngine(loaded.rules, maxSeconds = MAX_CALL_SECONDS + 60)
            val listenerEnabled = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(applicationContext)
                .contains(packageName)

            if (settings.startMode == StartMode.OFFSET) {
                startCounting(settings.connectionOffsetSeconds * 1_000L)
            } else if (!listenerEnabled) {
                // 通知アクセスが無い端末では、仕様どおりオフセット方式へ自動で切り替える。
                startCounting(settings.connectionOffsetSeconds * 1_000L)
            } else if (pendingConnected) {
                // 応答通知（ACTION_CONNECTED）が初期化より先に届いていた場合は、
                // 応答を受けた時刻を起点にして保留しておいた応答を処理する。
                startCounting(0L, baseElapsed = pendingConnectedAtElapsed)
            } else {
                // 通知方式では相手の応答（ACTION_CONNECTED）を待つ。応答が検知できない
                // 場合はユーザーがオフセット方式へ切り替える（勝手に数え始めない）。
            }
        }
    }

    private fun startCounting(delayMillis: Long, baseElapsed: Long = SystemClock.elapsedRealtime()) {
        if (counting) return
        counting = true
        CallTimerState.counting.value = true
        acquireWakeLock()
        countStartElapsed = baseElapsed + delayMillis
        lastDispatchedSecond = -1
        tickJob?.cancel()
        tickJob = scope.launch { tickLoop() }
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            val elapsedMs = SystemClock.elapsedRealtime() - countStartElapsed
            if (elapsedMs >= 0L) {
                val elapsed = (elapsedMs / 1_000L).toInt()
                val schedule = engine
                if (schedule != null) {
                    val due = schedule.eventsBetween(lastDispatchedSecond, elapsed)
                    // 端末のスリープ等で大きく飛んだときは、古い合図をまとめて鳴らさない。
                    if (due.size > MAX_CATCH_UP_EVENTS) {
                        due.lastOrNull()?.let { dispatcher.dispatch(it) }
                    } else {
                        due.forEach { dispatcher.dispatch(it) }
                    }
                    lastDispatchedSecond = elapsed
                }
                CallTimerState.elapsedSeconds.value = elapsed
                updateStatus(elapsed)
                if (elapsed >= MAX_CALL_SECONDS) {
                    shutdown()
                    return
                }
            }
            delay(TICK_INTERVAL_MS)
        }
    }

    private fun updateStatus(elapsed: Int) {
        val text = if (settings.showElapsedInNotification) {
            val remaining = (TimerEngine.FIVE_MINUTES - elapsed).coerceAtLeast(0)
            "$elapsed 秒（${formatClock(elapsed)}）・5分まで ${formatClock(remaining)}"
        } else {
            "計測中"
        }
        if (text != lastNotificationText) {
            lastNotificationText = text
            notifyStatus(text)
        }
    }

    /** 前面サービスへ昇格する。失敗したら false を返し、呼び出し側に止まる判断を委ねる。 */
    private fun ensureForeground(text: String): Boolean {
        if (foregroundStarted) {
            notifyStatus(text)
            return true
        }
        val notification = Notifications.statusNotification(this, text)
        return runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.STATUS_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
            foregroundStarted = true
            true
        }.getOrElse {
            Log.w(TAG, "startForeground failed (background start restriction?)", it)
            false
        }
    }

    private fun notifyStatus(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(
                Notifications.STATUS_NOTIFICATION_ID,
                Notifications.statusNotification(this, text),
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val power = getSystemService(PowerManager::class.java) ?: return
            // オフセット待ちの時間と終了処理の余裕も含めて保持する。
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(MAX_CALL_SECONDS * 1_000L + WAKE_LOCK_MARGIN_MS)
            }
        }.onFailure { Log.w(TAG, "wake lock acquire failed", it) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun registerCallStateCallback() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val telephony = getSystemService(TelephonyManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            runCatching {
                telephony.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            }.onFailure { Log.w(TAG, "registerTelephonyCallback failed", it) }
        } else {
            // Android 10〜11 には TelephonyCallback が無いので PhoneStateListener を使う。
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            runCatching {
                @Suppress("DEPRECATION")
                telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            }.onFailure { Log.w(TAG, "PhoneStateListener failed", it) }
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> sawNonIdle = true
            TelephonyManager.CALL_STATE_RINGING -> sawNonIdle = true
            TelephonyManager.CALL_STATE_IDLE -> if (sawNonIdle) shutdown()
        }
    }

    private fun unregisterCallStateCallback() {
        val telephony = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { callback ->
                runCatching { telephony?.unregisterTelephonyCallback(callback) }
            }
        }
        telephonyCallback = null
        @Suppress("DEPRECATION")
        (phoneStateListener as? PhoneStateListener)?.let { listener ->
            runCatching { telephony?.listen(listener, PhoneStateListener.LISTEN_NONE) }
        }
        phoneStateListener = null
    }

    /** タイマー・通知・サービスをまとめて畳む。終話でも呼ばれる。 */
    fun shutdown() {
        counting = false
        tickJob?.cancel()
        tickJob = null
        unregisterCallStateCallback()
        releaseWakeLock()
        dispatcher.cancel()
        CallTimerState.counting.value = false
        CallTimerState.elapsedSeconds.value = -1
        CallTimerState.running.value = false
        CallTimerState.sessionId.value = 0L
        CallTimerState.detectedOutgoing.value = false
        if (foregroundStarted) {
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            foregroundStarted = false
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(Notifications.STATUS_NOTIFICATION_ID)
        dispatcher.cancelPostedAlerts()
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        unregisterCallStateCallback()
        releaseWakeLock()
        CallTimerState.running.value = false
        CallTimerState.counting.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallTimerService"
        private const val WAKE_LOCK_TAG = "call5min:timer"
        const val ACTION_START = "com.yuushilabs.call5mintimer.action.START"
        const val ACTION_CONNECTED = "com.yuushilabs.call5mintimer.action.CONNECTED"
        const val ACTION_STOP = "com.yuushilabs.call5mintimer.action.STOP"

        private const val TICK_INTERVAL_MS = 200L
        private const val MAX_CALL_SECONDS = 2 * 60 * 60
        private const val MAX_CATCH_UP_EVENTS = 3
        private const val WAKE_LOCK_MARGIN_MS = 10 * 60 * 1_000L

        /**
         * 通話中タイマーを起動する。
         *
         * [fromBackground] が true（PHONE_STATE レシーバー経由）のときは、Android 12+ の
         * バックグラウンド起動制限を回避できる「電池最適化の除外」があるときだけ起動する。
         * 除外が無い場合は UI に警告を出して諦める（黙って動かない）。
         */
        fun start(context: Context, fromBackground: Boolean) {
            if (CallTimerState.running.value) {
                if (!CallTimerState.counting.value) {
                    // 前のセッションが「応答待ち」のまま残っている場合（連続発信など）は
                    // 新しいセッションとして扱う（遅延終了判定の取り違え防止）。
                    CallTimerState.sessionId.value = SystemClock.elapsedRealtime()
                    CallTimerState.detectedOutgoing.value = true
                }
                return
            }
            if (fromBackground) {
                val exempt = context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                if (!exempt) {
                    Log.w(TAG, "battery optimization not exempted; not starting from background")
                    CallTimerState.batteryBlocked.value = true
                    return
                }
            }
            val intent = Intent(context, CallTimerForegroundService::class.java)
                .setAction(ACTION_START)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
                CallTimerState.running.value = true
            }.onFailure {
                Log.w(TAG, "start failed", it)
                CallTimerState.batteryBlocked.value = true
            }
        }

        /** 相手の応答（00:00）を検知したときに呼ぶ。 */
        fun notifyConnected(context: Context) {
            if (!CallTimerState.running.value) {
                start(context, fromBackground = false)
            }
            val intent = Intent(context, CallTimerForegroundService::class.java)
                .setAction(ACTION_CONNECTED)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "connected failed", it) }
        }

        /** 終話・通話通知の消滅時に呼ぶ。 */
        fun stop(context: Context) {
            val intent = Intent(context, CallTimerForegroundService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "stop failed", it) }
        }
    }
}
