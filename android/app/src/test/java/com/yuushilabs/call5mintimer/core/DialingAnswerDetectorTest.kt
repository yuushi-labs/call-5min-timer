package com.yuushilabs.call5mintimer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialingAnswerDetectorTest {

    private fun snapshot(
        title: String = "",
        text: String = "",
        chronometer: Boolean = true,
        whenMillis: Long = 0L,
    ) = CallNotificationSnapshot(
        packageName = "com.coloros.dialer",
        title = title,
        text = text,
        category = "call",
        chronometerRunning = chronometer,
        postedWhenMillis = whenMillis,
    )

    @Test
    fun `a chronometer that starts from zero means the other side answered`() {
        // OPPO は発信中に起点（when）を入れず 00:00 を出し、応答時に起点を入れることがある。
        val dialing = snapshot(title = "発信中…", whenMillis = 0L)
        val answered = snapshot(title = "発信中…", text = "00:01", whenMillis = 1_700_000_030_000L)
        assertTrue(DialingAnswerDetector.chronometerRestarted(dialing, answered))
    }

    @Test
    fun `a fixed chronometer base during dialing is not an answer`() {
        val first = snapshot(title = "発信中…", text = "00:00", whenMillis = 1_700_000_000_000L)
        val later = snapshot(title = "発信中…", text = "00:00", whenMillis = 1_700_000_000_000L)
        assertFalse(DialingAnswerDetector.chronometerRestarted(first, later))
    }

    @Test
    fun `a base that moved forward by a second is an answer`() {
        val first = snapshot(whenMillis = 1_700_000_000_000L)
        val answered = snapshot(whenMillis = 1_700_000_001_000L)
        assertTrue(DialingAnswerDetector.chronometerRestarted(first, answered))
    }

    @Test
    fun `a tiny base difference is not an answer`() {
        val first = snapshot(whenMillis = 1_700_000_000_000L)
        val jitter = snapshot(whenMillis = 1_700_000_000_400L)
        assertFalse(DialingAnswerDetector.chronometerRestarted(first, jitter))
    }

    @Test
    fun `a notification without a chronometer is not an answer`() {
        val first = snapshot(chronometer = false, whenMillis = 0L)
        val later = snapshot(chronometer = false, whenMillis = 1_700_000_030_000L)
        assertFalse(DialingAnswerDetector.chronometerRestarted(first, later))
    }

    @Test
    fun `a notification without a base time is not an answer`() {
        val first = snapshot(whenMillis = 1_700_000_000_000L)
        val later = snapshot(whenMillis = 0L)
        assertFalse(DialingAnswerDetector.chronometerRestarted(first, later))
    }

    @Test
    fun `the first snapshot of a key is never an answer`() {
        assertFalse(DialingAnswerDetector.chronometerRestarted(null, snapshot(whenMillis = 1_700_000_030_000L)))
    }
}
