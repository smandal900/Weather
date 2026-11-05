package com.skymood.data

import com.skymood.domain.SavedLocation
import com.skymood.domain.Settings
import com.skymood.domain.WeatherState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface RefreshResult {
    data class Success(val state: WeatherState) : RefreshResult
    /** The fetch failed; [cached] is still shown if present. */
    data class Failure(val error: Throwable, val cached: WeatherState?) : RefreshResult
    /** There is no manual city, no device location and no saved location. */
    data object NoLocation : RefreshResult
}

class WeatherRepository(
    private val api: WeatherApi,
    private val store: WeatherStore,
    private val locationSource: LocationSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val weather: Flow<WeatherState?> get() = store.weather
    val settings: Flow<Settings> get() = store.settings

    /**
     * Fetches fresh weather and caches it.
     *
     * Location priority: manual city, then a fresh device location (when [useDeviceLocation] and
     * the source returns one, which is saved for later), then the last saved device location.
     */
    suspend fun refresh(useDeviceLocation: Boolean = true): RefreshResult {
        val location = resolveLocation(useDeviceLocation) ?: return RefreshResult.NoLocation
        return try {
            val response = api.forecast(location.latitude, location.longitude)
            val c = response.current
            val state = WeatherState(
                weatherCode = c.weatherCode,
                isDay = c.isDay == 1,
                temperatureC = c.temperature,
                windSpeedKmh = c.windSpeed,
                cloudCover = c.cloudCover,
                precipitationMm = c.precipitation,
                location = location,
                updatedAtMillis = clock(),
                cloudLow = c.cloudLow ?: -1,
                cloudMid = c.cloudMid ?: -1,
                cloudHigh = c.cloudHigh ?: -1,
                humidity = c.humidity ?: 60,
                visibilityM = c.visibility ?: 24_000.0,
                windFromDeg = c.windDirection ?: 270,
                gustKmh = c.windGusts ?: 0.0,
            )
            store.saveWeather(state)
            RefreshResult.Success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RefreshResult.Failure(e, store.weather.first())
        }
    }

    private suspend fun resolveLocation(useDeviceLocation: Boolean): SavedLocation? {
        store.settings.first().manualLocation?.let { return it }
        if (useDeviceLocation) {
            val fresh = try {
                locationSource.currentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (fresh != null) {
                store.saveLastLocation(fresh)
                return fresh
            }
        }
        return store.lastLocation.first()
    }

    /** Saves a device location obtained while the app is in the foreground. */
    suspend fun rememberLocation(location: SavedLocation) = store.saveLastLocation(location)

    suspend fun searchCity(name: String): List<SavedLocation> {
        if (name.isBlank()) return emptyList()
        return api.searchCity(name.trim()).results.orEmpty().map { r ->
            val label = listOfNotNull(r.name, r.admin1, r.country).distinct().joinToString(", ")
            SavedLocation(label, r.latitude, r.longitude)
        }
    }

    suspend fun setManualLocation(location: SavedLocation?) =
        store.updateSettings { it.copy(manualLocation = location) }

    suspend fun updateSettings(transform: (Settings) -> Settings) = store.updateSettings(transform)
}
