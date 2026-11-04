package com.skymood.data

import com.skymood.domain.SavedLocation
import com.skymood.domain.Settings
import com.skymood.domain.WeatherState
import kotlinx.coroutines.flow.Flow

/** Persistent storage for the cached weather, last known location and settings. */
interface WeatherStore {
    val weather: Flow<WeatherState?>
    val lastLocation: Flow<SavedLocation?>
    val settings: Flow<Settings>

    suspend fun saveWeather(state: WeatherState)
    suspend fun saveLastLocation(location: SavedLocation)
    suspend fun updateSettings(transform: (Settings) -> Settings)
}

/** Source of the device location. Returns null when it is unavailable or not permitted. */
fun interface LocationSource {
    suspend fun currentLocation(): SavedLocation?
}
