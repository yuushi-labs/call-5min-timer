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
 *
 * 通知の各欄には状態の文言（「発信中」「通話中」など）と連絡先名のどちらも入りうる。
 * 連絡先名に `Calling office` や `On Call Support` のような語が含まれても取り違えないよう、
 * 「はっきりした発信語」「はっきりした通話中の語」「どちらにも見える語」を分けて判定する。
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

    /** 状態を表す発信語（連絡先名にはまず現れない）。 */
    private val EXPLICIT_DIALING_MARKERS = listOf(
        "発信中", "発信", "呼び出し中", "呼び出し", "ダイヤル中",
        "接続中", "応答待ち",
        "dialing", "dialling", "outgoing", "connecting",
    )

    /** 連絡先名にも現れうる発信語。状態の欄でだけ、通話中の手がかりより優先する。 */
    private val AMBIGUOUS_DIALING_MARKERS = listOf("ダイヤル", "calling")

    /** 通話中の状態を表すはっきりした語。 */
    private val STRONG_ACTIVE_MARKERS = listOf("通話中")

    /** 通話中の状態にも連絡先名にも見える語。 */
    private val AMBIGUOUS_ACTIVE_MARKERS = listOf(
        "in call", "ongoing call", "on call", "active call",
    )

    private val EXPLICIT_INCOMING_MARKERS = listOf(
        "着信中", "着信", "受話", "incoming", "incoming call",
    )

    /** 連絡先名にも見える着信の語（`Stringing Service` など）。 */
    private val AMBIGUOUS_INCOMING_MARKERS = listOf("ringing")

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
        // 状態を表す欄（タイトル・サブテキスト）と、連絡先名が入りうる本文を分けて考える。
        val status = list(snapshot.title, snapshot.subText).lowercase()

        // 1. 着信。状態の欄の「着信中」「Incoming call」を最優先する。
        if (EXPLICIT_INCOMING_MARKERS.any { status.contains(it) }) return CallNotificationState.INCOMING

        // 2. 本文にしか無くても、はっきりした着信語なら着信。
        if (EXPLICIT_INCOMING_MARKERS.any { lower.contains(it) }) return CallNotificationState.INCOMING

        // 3. 「発信中」「呼び出し中」など、はっきりした発信語。OPPO（ColorOS）は発信中のまま
        //    `showChronometer` や 00:00 を出すため、ここではクロノメーターや時刻を見ない。
        //    連絡先名の「On Call Support」のような通話中らしい語より優先する。
        if (EXPLICIT_DIALING_MARKERS.any { lower.contains(it) }) return CallNotificationState.DIALING

        // 4. 状態の欄が「通話中」なら、本文に連絡先名（Calling office など）があっても通話中。
        if (STRONG_ACTIVE_MARKERS.any { status.contains(it) }) return CallNotificationState.ACTIVE

        // 5. 本文の「通話中」も、曖昧な発信語（Calling… / ダイヤル）より先に通話中とみなす。
        if (STRONG_ACTIVE_MARKERS.any { lower.contains(it) }) return CallNotificationState.ACTIVE

        // 6. 文言が無い端末・通話アプリ向け。クロノメーターが動いている、または経過表示がある。
        if (snapshot.chronometerRunning && snapshot.postedWhenMillis > 0L) {
            return CallNotificationState.ACTIVE
        }
        if (parseElapsedSeconds(combined) != null) return CallNotificationState.ACTIVE

        // 7. 状態の欄が「Ringing」などなら着信。
        if (AMBIGUOUS_INCOMING_MARKERS.any { status.contains(it) }) return CallNotificationState.INCOMING

        // 8. 状態の欄が「Calling…」「ダイヤル中」などなら呼び出し中。
        if (AMBIGUOUS_DIALING_MARKERS.any { status.contains(it) }) return CallNotificationState.DIALING

        // 9. 本文にしか無い紛らわしい発信語（連絡先名の可能性がある）。
        if (AMBIGUOUS_DIALING_MARKERS.any { lower.contains(it) }) return CallNotificationState.DIALING

        // 10. 本文にしか無い紛らわしい着信の語（Stringing Service など）。
        if (AMBIGUOUS_INCOMING_MARKERS.any { lower.contains(it) }) return CallNotificationState.INCOMING

        // 11. 本文にしか無い通話中の語（In call / On Call など）。
        if (AMBIGUOUS_ACTIVE_MARKERS.any { lower.contains(it) }) return CallNotificationState.ACTIVE

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
