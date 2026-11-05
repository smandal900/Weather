package com.skymood.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneSelectorTest {

    private val loc = SavedLocation("Here", 0.0, 0.0)
    private fun weather(code: Int, isDay: Boolean = true, wind: Double = 20.0) =
        WeatherState(code, isDay, 10.0, wind, 50, 0.0, loc, 0L)

    @Test
    fun `no cache falls back to a clear sky by the clock`() {
        val day = SceneSelector.select(null, Settings(), hour = 12)
        val night = SceneSelector.select(null, Settings(), hour = 23)
        assertEquals(SceneType.CLEAR, day.type)
        assertEquals(TimeOfDay.DAY, day.timeOfDay)
        assertEquals(SceneType.CLEAR, night.type)
        assertEquals(TimeOfDay.NIGHT, night.timeOfDay)
        assertNull(day.temperatureC)
    }

    @Test
    fun `cached weather picks the scene and is_day picks night`() {
        assertEquals(SceneType.HEAVY_RAIN, SceneSelector.select(weather(63), Settings(), 12).type)
        assertEquals(TimeOfDay.NIGHT, SceneSelector.select(weather(0, isDay = false), Settings(), 12).timeOfDay)
    }

    @Test
    fun `weather, time and landscape overrides win`() {
        val spec = SceneSelector.select(
            weather(0),
            Settings(
                previewOverride = SceneType.STORM,
                timeOverride = TimeOfDay.DUSK,
                landscape = LandscapeType.WATERFALL,
            ),
            12,
        )
        assertEquals(SceneType.STORM, spec.type)
        assertEquals(TimeOfDay.DUSK, spec.timeOfDay)
        assertEquals(LandscapeType.WATERFALL, spec.landscape)
    }

    @Test
    fun `forced windy scene gets strong wind`() {
        val spec = SceneSelector.select(weather(0, wind = 5.0), Settings(previewOverride = SceneType.WINDY), 12)
        assertTrue(spec.windKmh >= 50f)
    }

    @Test
    fun `every combination of time, landscape and weather is reachable`() {
        val seen = mutableSetOf<Triple<TimeOfDay, LandscapeType, SceneType>>()
        for (t in TimeOfDay.entries) for (l in LandscapeType.entries) for (s in SceneType.entries) {
            val spec = SceneSelector.select(null, Settings(previewOverride = s, timeOverride = t, landscape = l), 12)
            seen += Triple(spec.timeOfDay, spec.landscape, spec.type)
        }
        assertEquals(TimeOfDay.entries.size * LandscapeType.entries.size * SceneType.entries.size, seen.size)
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
