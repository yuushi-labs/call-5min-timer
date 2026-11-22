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
