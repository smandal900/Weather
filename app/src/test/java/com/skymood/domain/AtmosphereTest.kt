package com.skymood.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereTest {

    private val place = SavedLocation("Test", 51.5, -0.1)

    private fun weather(
        code: Int = 0, cover: Int = 0, low: Int = -1, mid: Int = -1, high: Int = -1,
        precip: Double = 0.0, humidity: Int = 50, visM: Double = 24_000.0, windFrom: Int = 270,
    ) = WeatherState(
        weatherCode = code, isDay = true, temperatureC = 15.0, windSpeedKmh = 10.0, cloudCover = cover,
        precipitationMm = precip, location = place, updatedAtMillis = 0L,
        cloudLow = low, cloudMid = mid, cloudHigh = high, humidity = humidity, visibilityM = visM, windFromDeg = windFrom,
    )

    @Test fun `cloud layers follow the reported heights`() {
        val a = Atmosphere.of(weather(cover = 80, low = 10, mid = 60, high = 90))
        assertEquals(0.8f, a.cover, 0.001f)
        assertEquals(0.1f, a.low, 0.001f)
        assertEquals(0.6f, a.mid, 0.001f)
        assertEquals(0.9f, a.high, 0.001f)
    }

    @Test fun `missing heights fall back to the total cover`() {
        val a = Atmosphere.of(weather(cover = 40))
        assertEquals(0.4f, a.low, 0.001f)
        assertEquals(0.4f, a.high, 0.001f)
    }

    @Test fun `low visibility and humid air lower clarity`() {
        val clear = Atmosphere.of(weather(visM = 24_000.0, humidity = 40))
        val fog = Atmosphere.of(weather(code = 45, visM = 300.0, humidity = 100))
        assertTrue(clear.clarity > 0.95f)
        assertTrue(fog.clarity < 0.15f)
        assertEquals(4, clear.clarityBucket)
        assertEquals(0, fog.clarityBucket)
    }

    @Test fun `rain and storms make clouds darker`() {
        val fair = Atmosphere.of(weather(cover = 30))
        val rain = Atmosphere.of(weather(code = 63, cover = 100, precip = 2.5))
        val storm = Atmosphere.of(weather(code = 95, cover = 100, precip = 6.0))
        assertTrue(fair.darkness < rain.darkness)
        assertTrue(rain.darkness < storm.darkness)
    }

    @Test fun `drift follows the wind direction on screen`() {
        // Looking south, east is on the left: a wind from the west carries clouds east (left)...
        assertTrue(Atmosphere.of(weather(windFrom = 270)).drift < 0f)
        // ...and a wind from the east carries them right.
        assertTrue(Atmosphere.of(weather(windFrom = 90)).drift > 0f)
        // A wind straight from the north or south still moves them, never exactly zero.
        assertTrue(kotlin.math.abs(Atmosphere.of(weather(windFrom = 0)).drift) >= 0.25f)
    }

    @Test fun `preview weather gets matching numbers but keeps the real wind direction`() {
        val settings = Settings(previewOverride = SceneType.STORM)
        val spec = SceneSelector.select(weather(windFrom = 90), settings, hour = 12)
        assertEquals(Atmosphere.forPreview(SceneType.STORM).cover, spec.atmosphere.cover, 0.001f)
        assertTrue(spec.atmosphere.drift > 0f)
    }

    @Test fun `real weather without a preview uses the report`() {
        val spec = SceneSelector.select(weather(cover = 70, low = 70, mid = 20, high = 5), Settings(), hour = 12)
        assertEquals(0.7f, spec.atmosphere.cover, 0.001f)
        assertEquals(0.05f, spec.atmosphere.high, 0.001f)
    }

    @Test fun `fog gets thicker as visibility drops`() {
        val thin = SceneSelector.select(weather(code = 45, visM = 900.0), Settings(), hour = 12)
        val thick = SceneSelector.select(weather(code = 45, visM = 120.0), Settings(), hour = 12)
        assertTrue(thick.intensity > thin.intensity)
    }
}
