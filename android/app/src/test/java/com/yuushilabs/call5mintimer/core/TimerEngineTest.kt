package com.yuushilabs.call5mintimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private val preset = TimerEngine(TimerRules.ahamoDefault())

    @Test
    fun `ahamo preset fires at start, every minute, 4-30 and 5-00`() {
        assertEquals(
            listOf(0, 60, 120, 180, 240, 270, 300),
            preset.schedule.map { it.atSecond },
        )
    }

    @Test
    fun `minute marks vibrate the same number of times as elapsed minutes`() {
        val counts = listOf(60, 120, 180, 240).map { second ->
            preset.eventsAt(second).single().vibrationRepeats
        }
        assertEquals(listOf(1, 2, 3, 4), counts)
    }

    @Test
    fun `start cue is a single short vibration without sound`() {
        val event = preset.eventsAt(0).single()
        assertTrue(event.vibrates)
        assertFalse(event.playsSound)
        assertEquals(1, event.vibrationRepeats)
        assertEquals(VibrationLength.SHORT, event.alert.vibrationLength)
    }

    @Test
    fun `4-30 is a long vibration with a beep`() {
        val event = preset.eventsAt(270).single()
        assertTrue(event.vibrates)
        assertTrue(event.playsSound)
        assertEquals(VibrationLength.LONG, event.alert.vibrationLength)
        assertEquals(AlertAction.VIBRATE_AND_SOUND, event.alert.action)
    }

    @Test
    fun `five minute mark is announced once by default`() {
        val event = preset.eventsAt(300).single()
        assertTrue(event.playsSound)
        assertEquals(TimerEngine.RULE_AFTER_FIVE, event.ruleId)
    }

    @Test
    fun `periodic rule stops exactly at its end second`() {
        val rules = TimerRules(
            periodicRules = listOf(
                PeriodicRule(
                    id = "p",
                    intervalSeconds = 30,
                    startSecond = 60,
                    endSecond = 180,
                    scaleRepeats = false,
                    alert = AlertSpec(action = AlertAction.VIBRATE_ONLY, fixedRepeatCount = 2),
                ),
            ),
            afterFive = AfterFiveMode.SILENT,
        )
        val engine = TimerEngine(rules)
        assertEquals(listOf(60, 90, 120, 150, 180), engine.schedule.map { it.atSecond })
        assertTrue(engine.schedule.all { it.vibrationRepeats == 2 })
    }

    @Test
    fun `spot rule replaces periodic rule on the same second`() {
        val rules = TimerRules(
            periodicRules = listOf(
                PeriodicRule(id = "p", intervalSeconds = 60, startSecond = 60, endSecond = 240),
            ),
            spotRules = listOf(
                SpotRule(
                    id = "s",
                    atSecond = 120,
                    alert = AlertSpec(action = AlertAction.VIBRATE_AND_SOUND, vibrationLength = VibrationLength.LONG),
                ),
            ),
            afterFive = AfterFiveMode.SILENT,
        )
        val at120 = TimerEngine(rules).eventsAt(120)
        assertEquals(1, at120.size)
        assertEquals("s", at120.single().ruleId)
        assertEquals(VibrationLength.LONG, at120.single().alert.vibrationLength)
    }

    @Test
    fun `disabled rules are not scheduled`() {
        val enabled = PeriodicRule(id = "on", intervalSeconds = 60, startSecond = 60, endSecond = 120)
        val disabled = PeriodicRule(id = "off", enabled = false, intervalSeconds = 60, startSecond = 30, endSecond = 240)
        val engine = TimerEngine(
            TimerRules(periodicRules = listOf(enabled, disabled), afterFive = AfterFiveMode.SILENT),
        )
        assertEquals(listOf(60, 120), engine.schedule.map { it.atSecond })
    }

    @Test
    fun `fixed repeat count is used when scaling is off`() {
        val rules = TimerRules(
            periodicRules = listOf(
                PeriodicRule(
                    id = "p",
                    intervalSeconds = 60,
                    startSecond = 60,
                    endSecond = 180,
                    scaleRepeats = false,
                    alert = AlertSpec(fixedRepeatCount = 3),
                ),
            ),
            afterFive = AfterFiveMode.SILENT,
        )
        assertTrue(TimerEngine(rules).schedule.all { it.vibrationRepeats == 3 })
    }

    @Test
    fun `after-five per minute keeps ringing every minute with increasing counts`() {
        val engine = TimerEngine(TimerRules(afterFive = AfterFiveMode.PER_MINUTE), maxSeconds = 420)
        assertEquals(listOf(300, 360, 420), engine.schedule.map { it.atSecond })
        assertEquals(listOf(5, 6, 7), engine.schedule.map { it.vibrationRepeats })
    }

    @Test
    fun `after-five silent adds nothing`() {
        val engine = TimerEngine(TimerRules(afterFive = AfterFiveMode.SILENT))
        assertTrue(engine.schedule.isEmpty())
    }

    @Test
    fun `user rule at five minutes wins over the automatic announcement`() {
        val rules = TimerRules(
            spotRules = listOf(SpotRule(id = "user", atSecond = 300, alert = AlertSpec(action = AlertAction.SILENT))),
            afterFive = AfterFiveMode.ONCE_AT_FIVE,
        )
        val event = TimerEngine(rules).eventsAt(300).single()
        assertEquals("user", event.ruleId)
        assertFalse(event.vibrates)
        assertFalse(event.playsSound)
    }

    @Test
    fun `eventsBetween picks up only the requested window`() {
        val events = preset.eventsBetween(59, 121)
        assertEquals(listOf(60, 120), events.map { it.atSecond })
        assertTrue(preset.eventsBetween(120, 120).isEmpty())
    }

    @Test
    fun `next event returns the following mark`() {
        assertEquals(60, preset.nextEvent(0)?.atSecond)
        assertEquals(120, preset.nextEvent(60)?.atSecond)
        assertEquals(300, preset.nextEvent(270)?.atSecond)
        assertNull(preset.nextEvent(300))
    }

    @Test
    fun `long calls stay finite and sorted`() {
        val engine = TimerEngine(TimerRules(afterFive = AfterFiveMode.PER_MINUTE), maxSeconds = 7_200)
        assertTrue(engine.schedule.isNotEmpty())
        assertEquals(engine.schedule.sortedBy { it.atSecond }, engine.schedule)
        assertTrue(engine.schedule.size < 200)
    }

    @Test
    fun `seconds are formatted as clock labels`() {
        assertEquals("0:00", formatClock(0))
        assertEquals("4:30", formatClock(270))
        assertEquals("1:02:03", formatClock(3_723))
        assertEquals("0:00", formatClock(-5))
    }

    @Test
    fun `labels describe the start, remaining time and overrun`() {
        assertEquals("計測開始", preset.eventsAt(0).single().label)
        assertTrue(preset.eventsAt(120).single().label.contains("残り"))
        assertTrue(preset.eventsAt(300).single().label.contains("無料枠"))
    }
}
