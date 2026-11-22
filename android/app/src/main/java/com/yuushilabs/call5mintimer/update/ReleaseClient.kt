package com.yuushilabs.call5mintimer.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 最新リリースの応答。[notModified] は ETag が一致して本文が無い（304）の意味。 */
data class ReleaseResponse(
    val body: String?,
    val etag: String?,
    val notModified: Boolean = false,
)

/**
 * `GET /repos/{owner}/{repo}/releases/latest`。
 * 304 は GitHub のレート制限（未認証で1時間60回）に数えられないので、起動ごとの確認でも消費しない。
 */
class ReleaseClient(
    private val endpoint: String = ENDPOINT,
    private val userAgent: String = USER_AGENT,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 10_000,
) {

    suspend fun fetchLatest(etag: String?): Result<ReleaseResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                if (!etag.isNullOrBlank()) setRequestProperty("If-None-Match", etag)
            }
            try {
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED ->
                        ReleaseResponse(body = null, etag = etag, notModified = true)

                    HttpURLConnection.HTTP_OK -> ReleaseResponse(
                        body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                        etag = connection.getHeaderField("ETag"),
                    )

                    else -> {
                        val detail = connection.errorStream
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?.take(200)
                            .orEmpty()
                        throw IOException("HTTP $code $detail".trim())
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * APK を [target] へ落とす。途中で切れたものを完成品と取り違えないよう `.part` 経由で書く。
     */
    suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, target.name + ".part")
            partial.delete()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("APK download failed: HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedBytes
                var received = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read
                            onProgress(received, total)
                        }
                    }
                }
                if (!isPlausibleApk(partial, expectedBytes)) {
                    throw IOException("downloaded file is not a complete APK ($received bytes)")
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                target
            } catch (error: Throwable) {
                partial.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val REPOSITORY = "yuushi-labs/call-5min-timer"
        const val ENDPOINT = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val USER_AGENT = "call-5min-timer-Android"

        /** ZIP の先頭 4 バイト（`PK\u0003\u0004`）。APK は zip、エラーページは違う。 */
        fun isPlausibleApk(file: File, expectedBytes: Long = 0L): Boolean {
            if (!file.isFile || file.length() <= 0L) return false
            if (expectedBytes > 0L && file.length() != expectedBytes) return false
            return runCatching {
                file.inputStream().use { input ->
                    val header = ByteArray(4)
                    input.read(header) == 4 &&
                        header[0] == 'P'.code.toByte() &&
                        header[1] == 'K'.code.toByte() &&
                        header[2] == 0x03.toByte() &&
                        header[3] == 0x04.toByte()
                }
            }.getOrDefault(false)
        }
    }
}
