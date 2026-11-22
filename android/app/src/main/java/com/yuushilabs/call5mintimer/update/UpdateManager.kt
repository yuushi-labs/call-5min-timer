package com.yuushilabs.call5mintimer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.yuushilabs.call5mintimer.BuildConfig
import com.yuushilabs.call5mintimer.core.update.GithubRelease
import com.yuushilabs.call5mintimer.core.update.UpdateDecision
import com.yuushilabs.call5mintimer.core.update.UpdateInfo
import com.yuushilabs.call5mintimer.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GitHub Releases を見て、アプリ内で自分自身を更新する。
 *
 * - ETag（304）と 10 分のデバウンスで無駄な通信を抑える
 * - ダウンロードは cache/updates/ に置き、進捗を返す
 * - FileProvider 経由でパッケージインストーラを開き、古い APK は自動で消す
 */
class UpdateManager(
    private val context: Context,
    private val repository: SettingsRepository,
    private val client: ReleaseClient = ReleaseClient(),
) {

    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object UpToDate : CheckResult
        data object Skipped : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    class InstallPermissionRequired : IllegalStateException(
        "提供元不明のアプリのインストール許可が必要です",
    )

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun check(force: Boolean, now: Long = System.currentTimeMillis()): CheckResult =
        withContext(Dispatchers.IO) {
            val lastCheck = repository.lastUpdateCheckMillis()
            if (!force && now - lastCheck in 0 until MIN_CHECK_INTERVAL_MS) {
                return@withContext CheckResult.Skipped
            }
            val etag = repository.updateEtag()
            var response = client.fetchLatest(etag).getOrElse { error ->
                return@withContext CheckResult.Failed(error.message ?: "確認に失敗しました")
            }
            if (response.notModified) {
                // 304 は「リリース情報が前回と変わっていない」の意味で、「更新済み」ではない。
                // 保存済みの本文で判定する。無ければ ETag を付けずに取り直す。
                val stored = repository.lastReleaseBody()
                response = if (stored.isNullOrBlank()) {
                    client.fetchLatest(null).getOrElse { error ->
                        return@withContext CheckResult.Failed(error.message ?: "確認に失敗しました")
                    }
                } else {
                    ReleaseResponse(body = stored, etag = etag)
                }
            }
            repository.recordUpdateCheck(now)

            val body = response.body ?: return@withContext CheckResult.Failed("空の応答")
            val release = runCatching { GithubRelease.parse(body) }.getOrElse {
                return@withContext CheckResult.Failed("リリース情報を読めませんでした")
            }
            // 本文を先に保存してから ETag を保存する。逆順だと中断時に「ETag は新しいのに
            // 本文は古い」になり、次の 304 が古い本文で判定されてしまう。
            repository.recordLastReleaseBody(body)
            repository.recordUpdateEtag(response.etag)
            val info = UpdateDecision.decide(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                release = release,
            ) ?: return@withContext CheckResult.UpToDate
            CheckResult.Available(info)
        }

    /**
     * 保存済みのリリース本文から更新状態を復元する（通信なし）。
     * デバウンス中に再起動しても、未インストールの更新バナーが復活するようにする。
     */
    suspend fun restoreLastKnown(): CheckResult = withContext(Dispatchers.IO) {
        val body = repository.lastReleaseBody() ?: return@withContext CheckResult.Skipped
        val release = runCatching { GithubRelease.parse(body) }.getOrElse {
            return@withContext CheckResult.Skipped
        }
        val info = UpdateDecision.decide(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            release = release,
        ) ?: return@withContext CheckResult.UpToDate
        CheckResult.Available(info)
    }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        // 前のバージョンの APK を残さない（ストレージを消費しない）。
        cleanupOldApks(keep = null)
        val target = File(directory, info.apkFileName.ifBlank { "call-5min-timer.apk" })
        client.download(info.apkUrl, target, info.apkSizeBytes, onProgress)
            .onSuccess { cleanupOldApks(keep = it) }
    }

    /** システムのインストーラを開く。許可が無ければ [InstallPermissionRequired]。 */
    fun install(apk: File): Result<Unit> = runCatching {
        if (!canInstallPackages()) throw InstallPermissionRequired()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 「提供元不明のアプリ」の許可画面を開く。 */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
    }

    fun cleanupOldApks(keep: File?) {
        val directory = File(context.cacheDir, "updates")
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (keep != null && file.absolutePath == keep.absolutePath) continue
            if (file.extension.equals("apk", ignoreCase = true) || file.name.endsWith(".part")) {
                file.delete()
            }
        }
    }

    companion object {
        /** 自動確認の最短間隔（デバウンス）。 */
        const val MIN_CHECK_INTERVAL_MS = 10 * 60 * 1_000L
    }
}
