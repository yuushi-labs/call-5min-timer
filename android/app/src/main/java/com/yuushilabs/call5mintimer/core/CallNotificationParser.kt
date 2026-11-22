package com.yuushilabs.call5mintimer.core

/** 通話アプリの進行中通知から読み取れる状態。 */
enum class CallNotificationState {
    /** 通話アプリの通知ではない。 */
    NONE,

    /** 着信（呼び出し中）。タイマーは起動しない。 */
    INCOMING,

    /** 発信して呼び出し中（相手はまだ出ていない）。 */
    DIALING,

    /** 相手が出て通話中（計測を始めてよい）。 */
    ACTIVE,

    /** 通話アプリの通知だが、状態を決められない。 */
    UNKNOWN_CALL,
}

/**
 * 通知1件から取り出した、判定に使う項目だけを持つ値。
 * Android の型に依存しないので JVM の単体テストで通話アプリの通知パターンを再現できる。
 */
data class CallNotificationSnapshot(
    val packageName: String,
    val title: String = "",
    val text: String = "",
    val bigText: String = "",
    val subText: String = "",
    val category: String? = null,
    /** 通知の `android.showChronometer`（通話中だけ true になる通話アプリが多い）。 */
    val chronometerRunning: Boolean = false,
    /** 通知の `when`（クロノメーターの起点）。0 は未設定。 */
    val postedWhenMillis: Long = 0L,
)

/**
 * 相手が出た瞬間（呼び出し中 → 00:00）の検知。
 *
 * 端末や通話アプリによって通知の文言が違うため、文言・カテゴリ・クロノメーターを
 * 組み合わせて判定する。判定はすべて文字列だけで完結する。
 */
object CallNotificationParser {

    /** 主要な通話アプリのパッケージ。OPPO（ColorOS）系も含む。 */
    private val KNOWN_CALL_PACKAGES = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.incallui",
        "com.oppo.dialer",
        "com.coloros.dialer",
        "com.oplus.dialer",
        "com.oplus.phone",
        "com.samsung.android.dialer",
        "com.android.server.telecom",
    )

    private val DIALING_MARKERS = listOf(
        "発信中", "発信", "呼び出し中", "呼び出し", "ダイヤル中", "ダイヤル",
        "接続中", "応答待ち",
        "calling", "dialing", "dialling", "outgoing", "connecting",
    )

    private val ACTIVE_MARKERS = listOf(
        "通話中", "通話中…", "通話中...", "in call", "ongoing call", "on call", "active call",
    )

    private val INCOMING_MARKERS = listOf(
        "着信中", "着信", "受話", "incoming", "incoming call", "ringing",
    )

    /** `00:00` / `1:23` / `1:02:03` のような経過表示。 */
    private val CLOCK_PATTERN = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)""")

    fun isCallApp(packageName: String): Boolean {
        val name = packageName.lowercase()
        return name in KNOWN_CALL_PACKAGES ||
            name.endsWith(".dialer") ||
            name.contains("incall") ||
            name.contains("telecom") ||
            name.contains("phone")
    }

    fun parse(snapshot: CallNotificationSnapshot): CallNotificationState {
        val category = snapshot.category?.lowercase()
        val callLike = isCallApp(snapshot.packageName) || category == "call"
        if (!callLike) return CallNotificationState.NONE

        val combined = list(snapshot.title, snapshot.text, snapshot.bigText, snapshot.subText)
        val lower = combined.lowercase()

        // 1. クロノメーターが動いていれば、相手が出て計測が始まっている。
        if (snapshot.chronometerRunning && snapshot.postedWhenMillis > 0L) {
            return CallNotificationState.ACTIVE
        }

        // 2. 「通話中」の文言（クロノメーター非対応の端末・通話アプリ向け）。
        if (ACTIVE_MARKERS.any { lower.contains(it) }) return CallNotificationState.ACTIVE

        // 3. 着信。連絡先名や本文に「Calling〜」「12:34」のような語が混ざっていても
        //    着信を優先する（発信・通話中の判定はこの後）。
        if (INCOMING_MARKERS.any { lower.contains(it) }) return CallNotificationState.INCOMING

        // 4. 経過表示（00:00 など）は着信文言が無い場合だけ通話中とみなす。
        val elapsed = parseElapsedSeconds(combined)
        if (elapsed != null) return CallNotificationState.ACTIVE

        // 5. 呼び出し中の文言。
        if (DIALING_MARKERS.any { lower.contains(it) }) return CallNotificationState.DIALING

        return CallNotificationState.UNKNOWN_CALL
    }

    /** `MM:SS` / `H:MM:SS` を秒へ。見つからなければ null。 */
    fun parseElapsedSeconds(text: String): Int? {
        val match = CLOCK_PATTERN.find(text) ?: return null
        val groups = match.groupValues
        return if (groups[3].isNotEmpty()) {
            groups[1].toInt() * 3_600 + groups[2].toInt() * 60 + groups[3].toInt()
        } else {
            groups[1].toInt() * 60 + groups[2].toInt()
        }
    }

    private fun list(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString(" ")
}
