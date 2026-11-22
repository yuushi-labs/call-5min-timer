package com.yuushilabs.call5mintimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuushilabs.call5mintimer.core.ElapsedTracker
import com.yuushilabs.call5mintimer.core.TimerRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 相手応答の検知方法。 */
enum class StartMode {
    /** 進行中通話通知が「00:00」になった瞬間から計測（既定・推奨）。 */
    NOTIFICATION,

    /** 発信（OFFHOOK）から接続猶予オフセット秒後に計測（通知アクセス不要のフォールバック）。 */
    OFFSET,
}

data class AppSettings(
    val startMode: StartMode = StartMode.NOTIFICATION,
    val connectionOffsetSeconds: Int = 5,
    /**
     * 計測を始めるときの経過秒。相手が出てから計測が始まるまでのラグを埋めるための
     * 「開始位置」（例: 6 で 0:06 からスタート）。既定の 0 は 0:00 から。
     */
    val startElapsedSeconds: Int = 0,
    val showElapsedInNotification: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val rules: TimerRules = TimerRules.ahamoDefault(),
) {
    fun normalized(): AppSettings = copy(
        connectionOffsetSeconds = connectionOffsetSeconds.coerceIn(0, 60),
        startElapsedSeconds = startElapsedSeconds.coerceIn(0, ElapsedTracker.MAX_START_ELAPSED_SECONDS),
        rules = rules.normalized(),
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "call5_settings")

private val RULES_JSON = stringPreferencesKey("rules_json")
private val START_MODE = stringPreferencesKey("start_mode")
private val OFFSET_SECONDS = intPreferencesKey("offset_seconds")
private val START_ELAPSED_SECONDS = intPreferencesKey("start_elapsed_seconds")
private val SHOW_ELAPSED = booleanPreferencesKey("show_elapsed")
private val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
private val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
private val UPDATE_ETAG = stringPreferencesKey("update_etag")
private val LAST_RELEASE_JSON = stringPreferencesKey("last_release_json")

/**
 * 通知ルールと設定の保存先。通話の内容・番号・履歴は一切保存しない。
 */
class SettingsRepository(private val context: Context) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            startMode = when (preferences[START_MODE]) {
                StartMode.OFFSET.name -> StartMode.OFFSET
                else -> StartMode.NOTIFICATION
            },
            connectionOffsetSeconds = preferences[OFFSET_SECONDS] ?: 5,
            startElapsedSeconds = preferences[START_ELAPSED_SECONDS] ?: 0,
            showElapsedInNotification = preferences[SHOW_ELAPSED] ?: true,
            autoCheckUpdates = preferences[AUTO_CHECK_UPDATES] ?: true,
            rules = readRules(preferences[RULES_JSON]),
        ).normalized()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val current = AppSettings(
                startMode = when (preferences[START_MODE]) {
                    StartMode.OFFSET.name -> StartMode.OFFSET
                    else -> StartMode.NOTIFICATION
                },
                connectionOffsetSeconds = preferences[OFFSET_SECONDS] ?: 5,
                startElapsedSeconds = preferences[START_ELAPSED_SECONDS] ?: 0,
                showElapsedInNotification = preferences[SHOW_ELAPSED] ?: true,
                autoCheckUpdates = preferences[AUTO_CHECK_UPDATES] ?: true,
                rules = readRules(preferences[RULES_JSON]),
            )
            val next = transform(current).normalized()
            preferences[START_MODE] = next.startMode.name
            preferences[OFFSET_SECONDS] = next.connectionOffsetSeconds
            preferences[START_ELAPSED_SECONDS] = next.startElapsedSeconds
            preferences[SHOW_ELAPSED] = next.showElapsedInNotification
            preferences[AUTO_CHECK_UPDATES] = next.autoCheckUpdates
            preferences[RULES_JSON] = json.encodeToString(TimerRules.serializer(), next.rules)
        }
    }

    suspend fun restorePreset() {
        update { it.copy(rules = TimerRules.ahamoDefault()) }
    }

    suspend fun lastUpdateCheckMillis(): Long =
        context.dataStore.data.first()[LAST_UPDATE_CHECK] ?: 0L

    suspend fun recordUpdateCheck(millis: Long) {
        context.dataStore.edit { it[LAST_UPDATE_CHECK] = millis }
    }

    suspend fun updateEtag(): String? = context.dataStore.data.first()[UPDATE_ETAG]

    /** 前回取得したリリース本文。304 は「更新をインストール済み」ではないので、判定にはこれを使う。 */
    suspend fun lastReleaseBody(): String? = context.dataStore.data.first()[LAST_RELEASE_JSON]

    /** ETag とリリース本文を同じトランザクションで保存する（中断時の不整合を防ぐ）。 */
    suspend fun recordUpdateResult(etag: String?, body: String) {
        context.dataStore.edit { preferences ->
            if (etag.isNullOrBlank()) preferences.remove(UPDATE_ETAG) else preferences[UPDATE_ETAG] = etag
            preferences[LAST_RELEASE_JSON] = body
        }
    }

    private fun readRules(raw: String?): TimerRules {
        if (raw.isNullOrBlank()) return TimerRules.ahamoDefault()
        return try {
            json.decodeFromString(TimerRules.serializer(), raw)
        } catch (_: SerializationException) {
            TimerRules.ahamoDefault()
        } catch (_: IllegalArgumentException) {
            TimerRules.ahamoDefault()
        }
    }
}
