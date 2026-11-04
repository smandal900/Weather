package com.skymood.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WmoMapperTest {

    @Test
    fun `maps every documented code range to its condition`() {
        val expected = mapOf(
            WeatherCondition.CLEAR to listOf(0, 1),
            WeatherCondition.CLOUDY to listOf(2, 3),
            WeatherCondition.FOG to listOf(45, 48),
            WeatherCondition.RAIN to listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82),
            WeatherCondition.SNOW to listOf(71, 73, 75, 77, 85, 86),
            WeatherCondition.STORM to listOf(95, 96, 99),
        )
        expected.forEach { (condition, codes) ->
            codes.forEach { code -> assertEquals("code $code", condition, WmoMapper.conditionFor(code)) }
        }
    }

    @Test
    fun `unknown codes fall back to clear`() {
        assertEquals(WeatherCondition.CLEAR, WmoMapper.conditionFor(4))
        assertEquals(WeatherCondition.CLEAR, WmoMapper.conditionFor(-1))
        assertEquals(WeatherCondition.CLEAR, WmoMapper.conditionFor(100))
    }

    @Test
    fun `clear sky uses night scene after dark`() {
        assertEquals(SceneType.CLEAR, WmoMapper.sceneFor(0, isDay = true))
        assertEquals(SceneType.NIGHT, WmoMapper.sceneFor(0, isDay = false))
        assertEquals(SceneType.NIGHT, WmoMapper.sceneFor(1, isDay = false))
    }

    @Test
    fun `other conditions keep their scene at night`() {
        assertEquals(SceneType.CLOUD, WmoMapper.sceneFor(3, isDay = false))
        assertEquals(SceneType.FOG, WmoMapper.sceneFor(45, isDay = false))
        assertEquals(SceneType.RAIN, WmoMapper.sceneFor(63, isDay = false))
        assertEquals(SceneType.SNOW, WmoMapper.sceneFor(75, isDay = true))
        assertEquals(SceneType.STORM, WmoMapper.sceneFor(95, isDay = true))
    }

    @Test
    fun `rain intensity grows with code and precipitation`() {
        val drizzle = WmoMapper.intensityFor(51)
        val heavy = WmoMapper.intensityFor(65)
        assertTrue(drizzle < heavy)
        assertEquals(1f, WmoMapper.intensityFor(61, precipitationMm = 20.0), 0.001f)
        assertTrue(WmoMapper.intensityFor(61, precipitationMm = 4.0) > WmoMapper.intensityFor(61))
    }

    @Test
    fun `cloud intensity follows cloud cover`() {
        assertTrue(WmoMapper.intensityFor(3, cloudCoverPercent = 100) > WmoMapper.intensityFor(2, cloudCoverPercent = 30))
        assertEquals(1f, WmoMapper.intensityFor(3, cloudCoverPercent = 100), 0.001f)
    }

    @Test
    fun `intensity is always within 0 to 1`() {
        for (code in -5..105) {
            val i = WmoMapper.intensityFor(code, precipitationMm = 50.0, cloudCoverPercent = 100)
            assertTrue("code $code -> $i", i in 0f..1f)
        }
    }
}
