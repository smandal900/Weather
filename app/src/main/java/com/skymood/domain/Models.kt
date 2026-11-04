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
    val scene: SceneType get() = WmoMapper.sceneFor(weatherCode, windSpeedKmh)
    val intensity: Float get() = WmoMapper.intensityFor(weatherCode, precipitationMm, cloudCover)
}

@Serializable
data class Settings(
    /** Weather preview override; null means Auto (follow the real weather). */
    val previewOverride: SceneType? = null,
    /** Time-of-day preview override; null means Auto (follow the clock and is_day). */
    val timeOverride: TimeOfDay? = null,
    val landscape: LandscapeType = LandscapeType.FOREST,
    /** Birds, bats, fireflies and animals that fit the weather, time and landscape. */
    val wildlife: Boolean = true,
    /** Forces the moon phase (0 new .. 0.5 full) for testing; null follows the calendar. */
    val moonPhaseOverride: Float? = null,
    /** Multiplier applied to the weather intensity, 0.25..1.5. */
    val intensity: Float = 1f,
    /** 15, 30 or 60. */
    val fpsCap: Int = 30,
    val showTemperature: Boolean = false,
    /** Darkening layer over the wallpaper so icons and text stay readable, 0..0.7. */
    val dim: Float = 0.35f,
    /** When set, weather is fetched for this city instead of the device location. */
    val manualLocation: SavedLocation? = null,
)
