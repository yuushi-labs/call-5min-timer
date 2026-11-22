package com.yuushilabs.call5mintimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElapsedTrackerTest {

    private val preset: TimerEngine = TimerEngine(TimerRules.ahamoDefault())

    @Test
    fun `start cue still fires once when counting starts at six seconds`() {
        val tracker = ElapsedTracker(preset, startElapsedSeconds = 6)
        val cue = tracker.takeStartCue()
        assertEquals(1, cue.size)
        assertEquals(0, cue.single().atSecond)
        assertTrue(cue.single().vibrates)
        assertTrue(tracker.takeStartCue().isEmpty())
    }

    @Test
    fun `elapsed seconds begin at the start offset`() {
        val tracker = ElapsedTracker(preset, startElapsedSeconds = 6)
        assertEquals(6, tracker.elapsedSeconds(0))
        assertEquals(7, tracker.elapsedSeconds(1))
        assertEquals(300, tracker.elapsedSeconds(294))
    }

    @Test
    fun `one minute mark arrives 54 seconds after starting at six seconds`() {
        val tracker = ElapsedTracker(preset, startElapsedSeconds = 6)
        tracker.takeStartCue()
        assertTrue(tracker.takeDue(0).isEmpty())
        assertTrue(tracker.takeDue(53).isEmpty())
        val due = tracker.takeDue(54)
        assertEquals(listOf(60), due.map { it.atSecond })
        assertEquals(1, due.single().vibrationRepeats)
    }

    @Test
    fun `five minute mark comes at 294 seconds of real time`() {
        val tracker = ElapsedTracker(preset, startElapsedSeconds = 6)
        tracker.takeStartCue()
        val fired = mutableListOf<Int>()
        for (delta in 0..294) {
            fired += tracker.takeDue(delta).map { it.atSecond }
        }
        assertEquals(listOf(60, 120, 180, 240, 270, 300), fired)
    }

    @Test
    fun `start offset is clamped to the allowed range`() {
        assertEquals(0, ElapsedTracker(preset, startElapsedSeconds = -3).startElapsedSeconds)
        assertEquals(
            ElapsedTracker.MAX_START_ELAPSED_SECONDS,
            ElapsedTracker(preset, startElapsedSeconds = 99).startElapsedSeconds,
        )
    }

    @Test
    fun `marks before the start offset are not replayed`() {
        val rules = TimerRules(
            spotRules = listOf(
                SpotRule(id = "a", atSecond = 3),
                SpotRule(id = "b", atSecond = 6),
                SpotRule(id = "c", atSecond = 8),
            ),
            afterFive = AfterFiveMode.SILENT,
        )
        val tracker = ElapsedTracker(TimerEngine(rules), startElapsedSeconds = 6)
        tracker.takeStartCue()
        // 3秒・6秒（開始位置）はすでに過ぎた合図として鳴らさず、8秒から鳴らす。
        assertEquals(listOf(8), tracker.takeDue(2).map { it.atSecond })
    }

    @Test
    fun `zero offset keeps the original behaviour`() {
        val tracker = ElapsedTracker(preset, startElapsedSeconds = 0)
        tracker.takeStartCue()
        val fired = mutableListOf<Int>()
        for (delta in 0..300) {
            fired += tracker.takeDue(delta).map { it.atSecond }
        }
        assertEquals(listOf(60, 120, 180, 240, 270, 300), fired)
    }

    @Test
    fun `catch up window never returns events twice`() {
        val tracker = ElapsedTracker(preset, startElapsedSeconds = 6)
        tracker.takeStartCue()
        val first = tracker.takeDue(200).map { it.atSecond }
        assertEquals(listOf(60, 120, 180), first)
        trackingRepeat(tracker)
    }

    private fun trackingRepeat(tracker: ElapsedTracker) {
        // 同じ経過秒をもう一度進めても、同じ合図は返らない。
        assertTrue(tracker.takeDue(200).isEmpty())
        assertTrue(tracker.takeDue(199).isEmpty())
        assertEquals(listOf(240), tracker.takeDue(240).map { it.atSecond })
    }
}
