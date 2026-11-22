package com.yuushilabs.call5mintimer.data

import com.yuushilabs.call5mintimer.core.ElapsedTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `start elapsed seconds defaults to zero`() {
        assertEquals(0, AppSettings().startElapsedSeconds)
    }

    @Test
    fun `start elapsed seconds is clamped to the allowed range`() {
        assertEquals(0, AppSettings(startElapsedSeconds = -5).normalized().startElapsedSeconds)
        assertEquals(6, AppSettings(startElapsedSeconds = 6).normalized().startElapsedSeconds)
        assertEquals(
            ElapsedTracker.MAX_START_ELAPSED_SECONDS,
            AppSettings(startElapsedSeconds = 99).normalized().startElapsedSeconds,
        )
    }

    @Test
    fun `start offset does not change the default rules`() {
        val settings = AppSettings(startElapsedSeconds = 6).normalized()
        assertEquals(60, settings.rules.periodicRules.first().intervalSeconds)
        assertEquals(listOf(0, 270), settings.rules.spotRules.map { it.atSecond })
    }
}
