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
    fun `codes map to the ten weather scenes`() {
        assertEquals(SceneType.CLEAR, WmoMapper.sceneFor(0))
        assertEquals(SceneType.CLEAR, WmoMapper.sceneFor(1))
        assertEquals(SceneType.PARTLY_CLOUDY, WmoMapper.sceneFor(2))
        assertEquals(SceneType.OVERCAST, WmoMapper.sceneFor(3))
        assertEquals(SceneType.FOG, WmoMapper.sceneFor(45))
        assertEquals(SceneType.DRIZZLE, WmoMapper.sceneFor(51))
        assertEquals(SceneType.DRIZZLE, WmoMapper.sceneFor(61))
        assertEquals(SceneType.HEAVY_RAIN, WmoMapper.sceneFor(63))
        assertEquals(SceneType.HEAVY_RAIN, WmoMapper.sceneFor(82))
        assertEquals(SceneType.SNOW, WmoMapper.sceneFor(75))
        assertEquals(SceneType.STORM, WmoMapper.sceneFor(95))
    }

    @Test
    fun `strong wind makes a dry sky windy but not a wet one`() {
        assertEquals(SceneType.WINDY, WmoMapper.sceneFor(0, windKmh = 40.0))
        assertEquals(SceneType.WINDY, WmoMapper.sceneFor(3, windKmh = 35.0))
        assertEquals(SceneType.PARTLY_CLOUDY, WmoMapper.sceneFor(2, windKmh = 34.0))
        assertEquals(SceneType.HEAVY_RAIN, WmoMapper.sceneFor(65, windKmh = 60.0))
        assertEquals(SceneType.STORM, WmoMapper.sceneFor(95, windKmh = 60.0))
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
