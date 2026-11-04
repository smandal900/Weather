package com.skymood.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstroTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `new and full moon phases match known dates`() {
        // New moon 2024-01-11 11:57 UTC, full moon 2024-01-25 17:54 UTC.
        assertEquals(0f, minOf(Astro.moonPhase(ms("2024-01-11T11:57:00Z")), 1f - Astro.moonPhase(ms("2024-01-11T11:57:00Z"))), 0.03f)
        assertEquals(0.5f, Astro.moonPhase(ms("2024-01-25T17:54:00Z")), 0.03f)
    }

    @Test
    fun `illumination is 0 at new moon and 1 at full moon`() {
        assertEquals(0f, Astro.moonIllumination(0f), 0.001f)
        assertEquals(1f, Astro.moonIllumination(0.5f), 0.001f)
        assertEquals(0.5f, Astro.moonIllumination(0.25f), 0.001f)
    }

    @Test
    fun `sun is nearly overhead at solar noon on the equinox at the equator`() {
        val h = Astro.sunPosition(ms("2026-03-20T12:00:00Z"), 0.0, 0.0)
        assertTrue("elevation ${h.elevationDeg}", h.elevationDeg > 85.0)
    }

    @Test
    fun `Kolkata sun at local noon in December is south and about 45 degrees high`() {
        // Solar noon in Kolkata is about 05:50 UTC in early December.
        val h = Astro.sunPosition(ms("2026-12-05T05:50:00Z"), 22.57, 88.36)
        assertEquals(44.0, h.elevationDeg, 4.0)
        assertEquals(180.0, h.azimuthDeg, 8.0)
    }

    @Test
    fun `sun is below the horizon at local midnight and rises in the east`() {
        val midnight = Astro.sunPosition(ms("2026-12-05T18:30:00Z"), 22.57, 88.36)
        assertTrue(midnight.elevationDeg < -20.0)
        val morning = Astro.sunPosition(ms("2026-12-05T01:30:00Z"), 22.57, 88.36)
        assertTrue("azimuth ${morning.azimuthDeg}", morning.azimuthDeg in 90.0..140.0)
    }

    @Test
    fun `full moon is high around local midnight`() {
        val h = Astro.moonPosition(ms("2024-01-25T18:00:00Z"), 22.57, 88.36 + 0.0)
        // 18:00 UTC is 23:30 local, near the full moon's culmination.
        assertTrue("elevation ${h.elevationDeg}", h.elevationDeg > 25.0)
    }

    @Test
    fun `screen mapping keeps the sun on screen and raises it with elevation`() {
        val low = Astro.toScreen(Astro.Horizontal(180.0, 5.0))
        val high = Astro.toScreen(Astro.Horizontal(180.0, 60.0))
        assertEquals(0.5f, low.first, 0.01f)
        assertTrue(high.second < low.second)
        assertTrue(Astro.toScreen(Astro.Horizontal(20.0, 10.0)).first >= 0.04f)
    }
}
