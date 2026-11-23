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

/** その判定をどの手がかりで決めたか（画面の診断表示に出す）。 */
enum class CallMatchReason {
    NOT_CALL_APP,
    INCOMING_STATUS_WORD,
    INCOMING_BODY_WORD,
    INCOMING_RINGING_FIELD,
    INCOMING_AMBIGUOUS_STATUS,
    INCOMING_AMBIGUOUS_BODY,
    ACTIVE_STATUS_WORD,
    ACTIVE_BODY_WORD,
    ACTIVE_CHRONOMETER,
    ACTIVE_CLOCK,
    ACTIVE_AMBIGUOUS_BODY,
    /** 同じ通知の経過時間の起点が更新された（＝相手が出た）とリスナーが判断した。 */
    ACTIVE_CHRONOMETER_RESTART,
    DIALING_PHRASE,
    DIALING_STATUS_LABEL,
    DIALING_AMBIGUOUS_STATUS,
    DIALING_AMBIGUOUS_BODY,
    UNKNOWN,
}

/** [CallNotificationParser.inspect] の結果。理由と、当たった語（固定のマーカー語だけ）を返す。 */
data class CallParseResult(
    val state: CallNotificationState,
    val reason: CallMatchReason,
    val markers: List<String> = emptyList(),
)

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
 *
 * 判定の順序が重要:
 * - はっきりした着信語（着信中 / Incoming call）を最優先
 * - その次に「はっきりした通話中の語」（通話中 / 通話時間）。OPPO（ColorOS）は相手が
 *   出た後もサブテキスト等に「SIM1 発信」のような発信語を残したまま「通話中」を出すため、
 *   発信語より先に通話中の語を見る（v0.1.2 で発信語を先に見た結果、応答後も止まっていた）
 * - 発信中の判定は「発信中」「呼び出し中」「ダイヤル中」「接続中」など、状態を表す語に限定する。
 *   「発信」「呼び出し」の2文字だけの部分一致は「発信者」「SIM1 発信」などに誤爆するため、
 *   タイトル・サブテキスト（状態の欄）でだけ使う
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

    /** 状態を表す発信中の語（連絡先名にはまず現れない）。 */
    private val EXPLICIT_DIALING_MARKERS = listOf(
        "発信中", "呼び出し中", "ダイヤル中", "接続中", "応答待ち",
        "dialing", "dialling", "outgoing", "connecting",
    )

    /**
     * 状態の欄（タイトル・サブテキスト）でだけ発信中の目印になる短い語。
     * 本文まで見ると `SIM1 発信` のような補足や連絡先名に誤爆するので、状態の欄に限定する。
     */
    private val STATUS_DIALING_LABELS = listOf("発信", "呼び出し")

    /** 連絡先名にも現れうる発信語。状態の欄でだけ、通話中の手がかりより優先する。 */
    private val AMBIGUOUS_DIALING_MARKERS = listOf("ダイヤル", "calling")

    /** 通話中の状態を表すはっきりした語。 */
    private val STRONG_ACTIVE_MARKERS = listOf("通話中", "通話時間")

    /** 通話中の状態にも連絡先名にも見える語。 */
    private val AMBIGUOUS_ACTIVE_MARKERS = listOf(
        "in call", "ongoing call", "on call", "active call",
    )

    private val EXPLICIT_INCOMING_MARKERS = listOf(
        "着信中", "着信", "受話", "incoming", "incoming call",
    )

    /** 連絡先名にも見える着信の語（`Stringing Service` など）。 */
    private val AMBIGUOUS_INCOMING_MARKERS = listOf("ringing")

    /**
     * 欄全体が `Ringing`（末尾の「…」や空白を含む）のときだけ着信とみなす。
     * 着信通知はタイトルが連絡先名でも、本文が `Ringing…` だけになることがある。
     * 部分一致だと `Stringing Service` のような連絡先名まで着信になるため、全体一致にする。
     */
    private val RINGING_FIELD = Regex("""^\s*ringing[\s.…]*$""", RegexOption.IGNORE_CASE)

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

    fun parse(snapshot: CallNotificationSnapshot): CallNotificationState = inspect(snapshot).state

    /** 判定と、その理由・当たった語を返す（診断表示用）。 */
    fun inspect(snapshot: CallNotificationSnapshot): CallParseResult {
        val category = snapshot.category?.lowercase()
        val callLike = isCallApp(snapshot.packageName) || category == "call"
        if (!callLike) return CallParseResult(CallNotificationState.NONE, CallMatchReason.NOT_CALL_APP)

        val combined = list(snapshot.title, snapshot.text, snapshot.bigText, snapshot.subText)
        val all = listOf(snapshot.title, snapshot.text, snapshot.bigText, snapshot.subText)
        // 状態を表す欄（タイトル・サブテキスト）と、連絡先名が入りうる本文を分けて考える。
        val statusFields = listOf(snapshot.title, snapshot.subText)

        // 1. 着信。「着信中」「Incoming call」はタイトル・サブテキストの状態の欄で最優先。
        match(statusFields, EXPLICIT_INCOMING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.INCOMING, CallMatchReason.INCOMING_STATUS_WORD, it)
        }

        // 2. 本文にしか無くても、はっきりした着信語なら着信。
        match(all, EXPLICIT_INCOMING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.INCOMING, CallMatchReason.INCOMING_BODY_WORD, it)
        }

        // 3. 状態の欄が「通話中」なら、発信語が残っていても通話中。
        //    OPPO（ColorOS）は相手が出た後もサブテキストに「SIM1 発信」を残すことがあるため、
        //    発信中の判定より先に見る（v0.1.2 の「応答しても止まる」の直接の原因）。
        match(statusFields, STRONG_ACTIVE_MARKERS)?.let {
            return CallParseResult(CallNotificationState.ACTIVE, CallMatchReason.ACTIVE_STATUS_WORD, it)
        }

        // 4. 本文の「通話中」「通話時間」も、はっきりした発信語より先に通話中とみなす。
        match(all, STRONG_ACTIVE_MARKERS)?.let {
            return CallParseResult(CallNotificationState.ACTIVE, CallMatchReason.ACTIVE_BODY_WORD, it)
        }

        // 5. 欄全体が `Ringing…` なら着信（タイトルが連絡先名でも）。
        //    部分一致にすると `Stringing Service` のような連絡先名まで着信になるため、
        //    全体一致だけを、紛らわしい発信語より先に見る。
        if (hasRingingField(snapshot)) {
            return CallParseResult(CallNotificationState.INCOMING, CallMatchReason.INCOMING_RINGING_FIELD)
        }

        // 6. 「発信中」「呼び出し中」など、はっきりした発信語。OPPO（ColorOS）は発信中のまま
        //    `showChronometer` や 00:00 を出すため、ここではクロノメーターや時刻を見ない。
        match(all, EXPLICIT_DIALING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.DIALING, CallMatchReason.DIALING_PHRASE, it)
        }

        // 7. 状態の欄の短い「発信」「呼び出し」（タイトルが「発信」だけの通知など）。
        //    本文には広げない（`SIM1 発信` のような補足に誤爆するため）。
        //    ただし 00:01 以上の経過表示が出ていれば、呼び出し中はもう終わっている。
        //    OPPO（ColorOS）は発信中に 00:00 を出し、応答してから 00:01… と進むため、
        //    00:00 の間だけ「発信」の語を優先する（サイレントに待ち続けない）。
        val elapsedSeconds = parseElapsedSeconds(combined)
        match(statusFields, STATUS_DIALING_LABELS)?.let {
            if (elapsedSeconds == null || elapsedSeconds == 0) {
                return CallParseResult(CallNotificationState.DIALING, CallMatchReason.DIALING_STATUS_LABEL, it)
            }
        }

        // 8. 文言が無い端末・通話アプリ向け。クロノメーターが動いている、または経過表示がある。
        if (snapshot.chronometerRunning && snapshot.postedWhenMillis > 0L) {
            return CallParseResult(CallNotificationState.ACTIVE, CallMatchReason.ACTIVE_CHRONOMETER)
        }
        if (elapsedSeconds != null) {
            return CallParseResult(CallNotificationState.ACTIVE, CallMatchReason.ACTIVE_CLOCK)
        }

        // 9. 状態の欄が「Ringing」などなら着信。
        match(statusFields, AMBIGUOUS_INCOMING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.INCOMING, CallMatchReason.INCOMING_AMBIGUOUS_STATUS, it)
        }

        // 10. 状態の欄が「Calling…」「ダイヤル中」などなら呼び出し中。
        match(statusFields, AMBIGUOUS_DIALING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.DIALING, CallMatchReason.DIALING_AMBIGUOUS_STATUS, it)
        }

        // 11. 本文にしか無い紛らわしい発信語（連絡先名の可能性がある）。
        match(all, AMBIGUOUS_DIALING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.DIALING, CallMatchReason.DIALING_AMBIGUOUS_BODY, it)
        }

        // 12. 本文にしか無い紛らわしい着信の語（Stringing Service など）。
        match(all, AMBIGUOUS_INCOMING_MARKERS)?.let {
            return CallParseResult(CallNotificationState.INCOMING, CallMatchReason.INCOMING_AMBIGUOUS_BODY, it)
        }

        // 13. 本文にしか無い通話中の語（In call / On Call など）。
        match(all, AMBIGUOUS_ACTIVE_MARKERS)?.let {
            return CallParseResult(CallNotificationState.ACTIVE, CallMatchReason.ACTIVE_AMBIGUOUS_BODY, it)
        }

        return CallParseResult(CallNotificationState.UNKNOWN_CALL, CallMatchReason.UNKNOWN)
    }

    private fun match(fields: List<String>, markers: List<String>): List<String>? {
        val hit = markers.filter { marker -> fields.any { it.contains(marker, ignoreCase = true) } }
        return hit.ifEmpty { null }
    }

    private fun hasRingingField(snapshot: CallNotificationSnapshot): Boolean =
        listOf(snapshot.title, snapshot.text, snapshot.bigText, snapshot.subText)
            .any { RINGING_FIELD.matches(it) }

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
