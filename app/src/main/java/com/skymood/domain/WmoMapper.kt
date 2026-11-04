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

    /** Wind at or above this speed turns a dry sky into the Windy scene. */
    const val WINDY_KMH = 35.0

    /**
     * The weather layer for a code. Light rain and drizzle share the Drizzle scene; moderate and
     * heavy rain use Heavy rain. A dry sky with strong wind becomes Windy. Open-Meteo has no
     * haze code, so Haze is only reachable through the preview override.
     */
    fun sceneFor(code: Int, windKmh: Double = 0.0): SceneType {
        val base = when (code) {
            0, 1 -> SceneType.CLEAR
            2 -> SceneType.PARTLY_CLOUDY
            3 -> SceneType.OVERCAST
            45, 48 -> SceneType.FOG
            in 51..57, 61, 66, 80 -> SceneType.DRIZZLE
            63, 65, 67, 81, 82 -> SceneType.HEAVY_RAIN
            in 71..77, 85, 86 -> SceneType.SNOW
            in 95..99 -> SceneType.STORM
            else -> SceneType.CLEAR
        }
        val dry = base == SceneType.CLEAR || base == SceneType.PARTLY_CLOUDY || base == SceneType.OVERCAST
        return if (dry && windKmh >= WINDY_KMH) SceneType.WINDY else base
    }

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
