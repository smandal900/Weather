package com.skymood.domain

/**
 * Maps WMO weather codes (as returned by Open-Meteo) to a [WeatherCondition], a [SceneType]
 * and an animation intensity in 0..1.
 */
object WmoMapper {

    fun conditionFor(code: Int): WeatherCondition = when (code) {
        0, 1 -> WeatherCondition.CLEAR
        2, 3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..67, in 80..82 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        in 95..99 -> WeatherCondition.STORM
        // Codes outside the documented table: show a calm sky rather than guessing.
        else -> WeatherCondition.CLEAR
    }

    fun sceneFor(condition: WeatherCondition, isDay: Boolean): SceneType = when (condition) {
        WeatherCondition.CLEAR -> if (isDay) SceneType.CLEAR else SceneType.NIGHT
        WeatherCondition.CLOUDY -> SceneType.CLOUD
        WeatherCondition.FOG -> SceneType.FOG
        WeatherCondition.RAIN -> SceneType.RAIN
        WeatherCondition.SNOW -> SceneType.SNOW
        WeatherCondition.STORM -> SceneType.STORM
    }

    fun sceneFor(code: Int, isDay: Boolean): SceneType = sceneFor(conditionFor(code), isDay)

    /**
     * Intensity 0..1. Rain and snow use the code's nominal strength, raised by the measured
     * precipitation (mm in the last hour, about 8 mm counts as the heaviest). Clouds use cloud cover.
     */
    fun intensityFor(code: Int, precipitationMm: Double = 0.0, cloudCoverPercent: Int = 0): Float {
        val fromPrecip = (precipitationMm / 8.0).toFloat().coerceIn(0f, 1f)
        val base = when (code) {
            0 -> 0.2f
            1 -> 0.35f
            2 -> 0.5f
            3 -> 0.85f
            45 -> 0.6f
            48 -> 0.8f
            51 -> 0.2f
            53 -> 0.3f
            55 -> 0.45f
            56 -> 0.3f
            57 -> 0.5f
            61 -> 0.35f
            63 -> 0.6f
            65 -> 0.9f
            66 -> 0.4f
            67 -> 0.8f
            80 -> 0.4f
            81 -> 0.65f
            82 -> 1.0f
            71 -> 0.3f
            73 -> 0.6f
            75 -> 0.9f
            77 -> 0.3f
            85 -> 0.5f
            86 -> 0.9f
            95 -> 0.7f
            96 -> 0.85f
            99 -> 1.0f
            else -> 0.5f
        }
        return when (conditionFor(code)) {
            WeatherCondition.RAIN, WeatherCondition.SNOW, WeatherCondition.STORM -> maxOf(base, fromPrecip)
            WeatherCondition.CLOUDY -> maxOf(base * 0.5f, cloudCoverPercent / 100f).coerceIn(0.2f, 1f)
            else -> base
        }
    }
}
