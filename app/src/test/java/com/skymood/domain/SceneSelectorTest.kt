package com.skymood.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneSelectorTest {

    private val loc = SavedLocation("Here", 0.0, 0.0)
    private fun weather(code: Int, isDay: Boolean = true) =
        WeatherState(code, isDay, 10.0, 20.0, 50, 0.0, loc, 0L)

    @Test
    fun `no cache falls back to clear by day and night by night`() {
        assertEquals(SceneType.CLEAR, SceneSelector.select(null, Settings(), hour = 12).type)
        assertEquals(SceneType.NIGHT, SceneSelector.select(null, Settings(), hour = 23).type)
        assertNull(SceneSelector.select(null, Settings(), hour = 12).temperatureC)
    }

    @Test
    fun `cached weather picks the scene`() {
        assertEquals(SceneType.RAIN, SceneSelector.select(weather(63), Settings(), 12).type)
        assertEquals(SceneType.NIGHT, SceneSelector.select(weather(0, isDay = false), Settings(), 12).type)
    }

    @Test
    fun `preview override wins`() {
        val spec = SceneSelector.select(weather(0), Settings(previewOverride = SceneType.STORM), 12)
        assertEquals(SceneType.STORM, spec.type)
    }

    @Test
    fun `intensity multiplier is applied and clamped`() {
        val base = SceneSelector.select(weather(65), Settings(), 12).intensity
        val boosted = SceneSelector.select(weather(65), Settings(intensity = 1.5f), 12).intensity
        assertEquals(minOf(base * 1.5f, 1.5f), boosted, 0.001f)
    }

    @Test
    fun `time of day buckets`() {
        assertEquals(TimeOfDay.DAWN, TimeOfDay.from(true, 6))
        assertEquals(TimeOfDay.DAY, TimeOfDay.from(true, 12))
        assertEquals(TimeOfDay.DUSK, TimeOfDay.from(true, 18))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.from(false, 12))
    }
}
