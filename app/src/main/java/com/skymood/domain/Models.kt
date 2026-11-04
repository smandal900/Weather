package com.skymood.domain

import kotlinx.serialization.Serializable

@Serializable
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/** The latest weather the wallpaper should show. Persisted as JSON in DataStore. */
@Serializable
data class WeatherState(
    val weatherCode: Int,
    val isDay: Boolean,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val cloudCover: Int,
    val precipitationMm: Double,
    val location: SavedLocation,
    val updatedAtMillis: Long,
) {
    val condition: WeatherCondition get() = WmoMapper.conditionFor(weatherCode)
    val scene: SceneType get() = WmoMapper.sceneFor(condition, isDay)
    val intensity: Float get() = WmoMapper.intensityFor(weatherCode, precipitationMm, cloudCover)
}

@Serializable
data class Settings(
    /** null means Auto (follow the real weather). */
    val previewOverride: SceneType? = null,
    /** Multiplier applied to the weather intensity, 0.25..1.5. */
    val intensity: Float = 1f,
    /** 15, 30 or 60. */
    val fpsCap: Int = 30,
    val showTemperature: Boolean = false,
    /** When set, weather is fetched for this city instead of the device location. */
    val manualLocation: SavedLocation? = null,
)
