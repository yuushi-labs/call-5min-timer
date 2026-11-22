package com.yuushilabs.call5mintimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallNotificationParserTest {

    private fun snapshot(
        packageName: String = "com.android.dialer",
        title: String = "",
        text: String = "",
        bigText: String = "",
        subText: String = "",
        category: String? = "call",
        chronometer: Boolean = false,
        whenMillis: Long = 0L,
    ) = CallNotificationSnapshot(
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        category = category,
        chronometerRunning = chronometer,
        postedWhenMillis = whenMillis,
    )

    @Test
    fun `oppo dialer calling notification is dialing`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "発信中…", text = "090-1234-5678"),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `google dialer calling notification is dialing`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.google.android.dialer", title = "Calling…", text = "03-1234-5678"),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `oppo dialing notification with chronometer and zero clock is still dialing`() {
        // OPPO（ColorOS）は「発信中」のまま showChronometer を立て、00:00 も出す。
        // ここで ACTIVE にすると発信した瞬間からカウントが始まってしまう。
        val state = CallNotificationParser.parse(
            snapshot(
                packageName = "com.oplus.dialer",
                title = "発信中…",
                text = "090-1234-5678",
                bigText = "00:00",
                chronometer = true,
                whenMillis = 1_700_000_000_000L,
            ),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `dialing notification with only a clock and chronometer is still dialing`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "発信", text = "00:00", chronometer = true, whenMillis = 1_700_000_000_000L),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `answered call with a calling contact name in the title is active`() {
        // タイトルが連絡先名でも、クロノメーターが動いていれば応答後とみなす。
        val state = CallNotificationParser.parse(
            snapshot(title = "Calling office", text = "00:00", chronometer = true, whenMillis = 1_700_000_030_000L),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `dialing wording beats an on-call contact name during dialing`() {
        // タイトルが連絡先名（On Call Support）でも、本文が「発信中」なら呼び出し中。
        val state = CallNotificationParser.parse(
            snapshot(title = "On Call Support", text = "発信中…", chronometer = true, whenMillis = 1_700_000_000_000L),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `kelvin callahan answered call is active`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "Kevin Callahan", text = "通話中 00:05", chronometer = true, whenMillis = 1_700_000_030_000L),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `outgoing wording with a running clock is dialing`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.android.phone", title = "Outgoing call", text = "00:00"),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `dialing notification that turns into an active call becomes active`() {
        // 同じ通話の通知が「発信中」から「通話中（00:00）」に入れ替わったケース。
        val dialing = CallNotificationParser.parse(
            snapshot(title = "発信中…", text = "090-1234-5678", chronometer = true, whenMillis = 1_700_000_000_000L),
        )
        val connected = CallNotificationParser.parse(
            snapshot(title = "通話中", text = "090-1234-5678", chronometer = true, whenMillis = 1_700_000_030_000L),
        )
        assertEquals(CallNotificationState.DIALING, dialing)
        assertEquals(CallNotificationState.ACTIVE, connected)
    }

    @Test
    fun `active wording without dialing wording is active even with a clock`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "通話中 00:12", text = "090-1234-5678", chronometer = true, whenMillis = 1_700_000_000_000L),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `answered call with a contact name that contains calling is active`() {
        // 連絡先名（本文）の「Calling office」を発信中の文言と取り違えない。
        val state = CallNotificationParser.parse(
            snapshot(
                title = "通話中",
                text = "Calling office",
                chronometer = true,
                whenMillis = 1_700_000_030_000L,
            ),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `answered call with a dial-like contact name is active`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "通話中", text = "ダイヤルサービス", chronometer = true, whenMillis = 1_700_000_030_000L),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `dialing wording in the body is dialing even with a clock`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "090-1234-5678", text = "発信中…", bigText = "00:00", chronometer = true, whenMillis = 1_700_000_000_000L),
        )
        assertEquals(CallNotificationState.DIALING, state)
    }

    @Test
    fun `active wording in the body beats a calling contact name in the title`() {
        // クロノメーターも経過表示も無いが、本文が「通話中」なら応答後。
        val state = CallNotificationParser.parse(
            snapshot(title = "Calling office", text = "通話中"),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `answered call with a contact name that contains ringing is active`() {
        // 連絡先名（本文）の「Stringing Service」を着信の文言と取り違えない。
        val state = CallNotificationParser.parse(
            snapshot(title = "通話中", text = "Stringing Service", chronometer = true, whenMillis = 1_700_000_030_000L),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `ringing in the body wins over a calling contact name in the title`() {
        // 着信通知はタイトルが連絡先名（Calling office）でも、本文が `Ringing…` なら着信。
        val ringing = CallNotificationParser.parse(
            snapshot(title = "Calling office", text = "Ringing…"),
        )
        assertEquals(CallNotificationState.INCOMING, ringing)
    }

    @Test
    fun `incoming call that is answered is not treated as an outgoing connection`() {
        // 着信（Ringing…）→ 応答（00:00）。着信のキーは incomingKeys に入るので、
        // リスナーは notifyConnected を呼ばない（解析結果が ACTIVE でも着信応答として除外）。
        val ringing = CallNotificationParser.parse(
            snapshot(title = "Calling office", text = "Ringing…"),
        )
        val answered = CallNotificationParser.parse(
            snapshot(title = "Calling office", text = "00:00", chronometer = true, whenMillis = 1_700_000_030_000L),
        )
        assertEquals(CallNotificationState.INCOMING, ringing)
        assertEquals(CallNotificationState.ACTIVE, answered)
    }

    @Test
    fun `ringing wording in the body is still incoming`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.google.android.dialer", title = "090-1234-5678", text = "Ringing…"),
        )
        assertEquals(CallNotificationState.INCOMING, state)
    }

    @Test
    fun `chronometer means the other side answered`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "通話中", text = "03-1234-5678", chronometer = true, whenMillis = 1_700_000_000_000L),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `zero clock text means the other side answered`() {
        val state = CallNotificationParser.parse(snapshot(title = "通話中", text = "00:00"))
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `elapsed clock in big text is active`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "In call", bigText = "01:23 · Mobile"),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `incoming call is never treated as active`() {
        val state = CallNotificationParser.parse(snapshot(title = "着信", text = "090-1234-5678"))
        assertEquals(CallNotificationState.INCOMING, state)
    }

    @Test
    fun `incoming english notification is incoming`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.google.android.dialer", title = "Incoming call", text = "Mom"),
        )
        assertEquals(CallNotificationState.INCOMING, state)
    }

    @Test
    fun `non call app notification is none`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.example.chat", title = "通話中", text = "00:00", category = null),
        )
        assertEquals(CallNotificationState.NONE, state)
    }

    @Test
    fun `call category from an unknown dialer is still a call`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.example.customdialer", title = "通話中", text = "00:00"),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `unknown call notification stays unknown`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "Phone", text = "Something", category = "call"),
        )
        assertEquals(CallNotificationState.UNKNOWN_CALL, state)
    }

    @Test
    fun `elapsed seconds are parsed as mm ss`() {
        assertEquals(0, CallNotificationParser.parseElapsedSeconds("00:00"))
        assertEquals(83, CallNotificationParser.parseElapsedSeconds("通話中 01:23"))
        assertEquals(3_723, CallNotificationParser.parseElapsedSeconds("1:02:03"))
        assertNull(CallNotificationParser.parseElapsedSeconds("発信中"))
        assertNull(CallNotificationParser.parseElapsedSeconds("090-1234-5678"))
    }

    @Test
    fun `incoming call with dialing wording in the body is still incoming`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.google.android.dialer", title = "Incoming call", text = "Calling office"),
        )
        assertEquals(CallNotificationState.INCOMING, state)
    }

    @Test
    fun `incoming call with clock-like text is still incoming`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.google.android.dialer", title = "Incoming call", text = "12:34"),
        )
        assertEquals(CallNotificationState.INCOMING, state)
    }

    @Test
    fun `incoming call with on-call wording in the body is still incoming`() {
        val state = CallNotificationParser.parse(
            snapshot(packageName = "com.google.android.dialer", title = "Incoming call", text = "On Call Support"),
        )
        assertEquals(CallNotificationState.INCOMING, state)
    }

    @Test
    fun `active call without status wording is detected by the clock`() {
        val state = CallNotificationParser.parse(
            snapshot(title = "03-1234-5678", text = "00:00"),
        )
        assertEquals(CallNotificationState.ACTIVE, state)
    }

    @Test
    fun `known call packages are recognised`() {
        assertEquals(true, CallNotificationParser.isCallApp("com.android.dialer"))
        assertEquals(true, CallNotificationParser.isCallApp("com.oplus.dialer"))
        assertEquals(false, CallNotificationParser.isCallApp("com.example.app"))
    }
}
