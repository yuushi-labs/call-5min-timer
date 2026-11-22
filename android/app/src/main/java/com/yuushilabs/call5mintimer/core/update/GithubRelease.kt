package com.yuushilabs.call5mintimer.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GithubReleaseAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("size") val sizeBytes: Long = 0L,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("assets") val assets: List<GithubReleaseAsset> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(body: String): GithubRelease = json.decodeFromString(serializer(), body)
    }
}

/** 画面に出す更新情報（足りない項目だけを持たない）。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int?,
    val notes: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val releaseUrl: String?,
)

/**
 * 「このリリースは今のアプリより新しいか」の判断。
 *
 * タグ（`v0.2.0`）と本文の `versionCode: 2` を手がかりにする。同じバージョン名でも
 * versionCode が上がっていれば更新とみなす（上書きインストールの可否は versionCode で決まる）。
 */
object UpdateDecision {

    private val VERSION_CODE_PATTERN = Regex("""versionCode\s*[:=]?\s*(\d{1,9})""", RegexOption.IGNORE_CASE)

    fun decide(
        currentVersionName: String,
        currentVersionCode: Int,
        release: GithubRelease,
    ): UpdateInfo? {
        if (release.draft || release.prerelease) return null

        val releaseVersion = release.tagName.removePrefix("v").removePrefix("V").trim()
        if (!isVersionLike(releaseVersion)) return null

        val releaseCode = VERSION_CODE_PATTERN.find(release.body)?.groupValues?.get(1)?.toIntOrNull()
        val newer = if (releaseCode != null && releaseVersion == currentVersionName) {
            releaseCode > currentVersionCode
        } else {
            compareVersions(releaseVersion, currentVersionName) > 0
        }
        if (!newer) return null

        val apk = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                asset.downloadUrl.startsWith("https://")
        } ?: return null

        return UpdateInfo(
            versionName = releaseVersion,
            versionCode = releaseCode,
            notes = release.body.lineSequence().take(12).joinToString("\n").trim(),
            apkUrl = apk.downloadUrl,
            apkFileName = apk.name,
            apkSizeBytes = apk.sizeBytes,
            releaseUrl = release.htmlUrl.takeIf { it.isNotBlank() },
        )
    }

    /** `1.2.3` 形式の比較。数値として比べ、足りない桁は 0 とみなす。 */
    fun compareVersions(a: String, b: String): Int {
        val left = versionParts(a)
        val right = versionParts(b)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> {
        val core = version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return core.split('.').map { part -> part.toIntOrNull() ?: 0 }
    }

    private fun isVersionLike(version: String): Boolean =
        version.isNotEmpty() && version.first().isDigit() && versionParts(version).isNotEmpty()
}
