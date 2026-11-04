package com.skymood.data

import com.skymood.domain.SavedLocation
import com.skymood.domain.SceneType
import com.skymood.domain.Settings
import com.skymood.domain.WeatherState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WeatherRepositoryTest {

    private val paris = SavedLocation("Paris", 48.85, 2.35)
    private val tokyo = SavedLocation("Tokyo", 35.68, 139.69)

    private class FakeApi : WeatherApi {
        var current = CurrentWeatherDto(63, 1, 12.5, 18.0, 90, 2.4)
        var fail: Exception? = null
        val requests = mutableListOf<Pair<Double, Double>>()
        var cities = listOf(GeocodingResult("Berlin", 52.52, 13.41, "Germany", "Land Berlin"))

        override suspend fun forecast(latitude: Double, longitude: Double, current: String, timezone: String): ForecastResponse {
            requests += latitude to longitude
            fail?.let { throw it }
            return ForecastResponse(latitude, longitude, this.current)
        }

        override suspend fun searchCity(name: String, count: Int, language: String, format: String) =
            GeocodingResponse(cities.filter { it.name.contains(name, ignoreCase = true) })
    }

    private class FakeStore : WeatherStore {
        val weatherFlow = MutableStateFlow<WeatherState?>(null)
        val locationFlow = MutableStateFlow<SavedLocation?>(null)
        val settingsFlow = MutableStateFlow(Settings())
        override val weather: Flow<WeatherState?> = weatherFlow
        override val lastLocation: Flow<SavedLocation?> = locationFlow
        override val settings: Flow<Settings> = settingsFlow
        override suspend fun saveWeather(state: WeatherState) { weatherFlow.value = state }
        override suspend fun saveLastLocation(location: SavedLocation) { locationFlow.value = location }
        override suspend fun updateSettings(transform: (Settings) -> Settings) {
            settingsFlow.value = transform(settingsFlow.value)
        }
    }

    private val api = FakeApi()
    private val store = FakeStore()
    private var deviceLocation: SavedLocation? = paris
    private val repo = WeatherRepository(api, store, { deviceLocation }, clock = { 1_000L })

    @Test
    fun `refresh maps the response and caches it`() = runTest {
        val result = repo.refresh()

        assertTrue(result is RefreshResult.Success)
        val cached = store.weatherFlow.value!!
        assertEquals(63, cached.weatherCode)
        assertTrue(cached.isDay)
        assertEquals(12.5, cached.temperatureC, 0.0)
        assertEquals(18.0, cached.windSpeedKmh, 0.0)
        assertEquals(paris, cached.location)
        assertEquals(1_000L, cached.updatedAtMillis)
        assertEquals(SceneType.RAIN, cached.scene)
        assertEquals(paris, store.locationFlow.value)
    }

    @Test
    fun `manual city wins over the device location`() = runTest {
        repo.setManualLocation(tokyo)

        repo.refresh()

        assertEquals(listOf(tokyo.latitude to tokyo.longitude), api.requests)
        assertEquals(tokyo, store.weatherFlow.value!!.location)
    }

    @Test
    fun `falls back to the last saved location when no fresh one is available`() = runTest {
        store.locationFlow.value = tokyo
        deviceLocation = null

        repo.refresh()

        assertEquals(tokyo, store.weatherFlow.value!!.location)
    }

    @Test
    fun `does not ask for device location when not allowed`() = runTest {
        store.locationFlow.value = tokyo

        repo.refresh(useDeviceLocation = false)

        assertEquals(tokyo, store.weatherFlow.value!!.location)
    }

    @Test
    fun `no location at all reports NoLocation and makes no request`() = runTest {
        deviceLocation = null

        assertEquals(RefreshResult.NoLocation, repo.refresh())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `network failure keeps the cached weather`() = runTest {
        repo.refresh()
        val before = store.weatherFlow.value
        api.fail = IOException("offline")
        api.current = CurrentWeatherDto(0, 1, 30.0, 0.0, 0, 0.0)

        val result = repo.refresh()

        assertTrue(result is RefreshResult.Failure)
        assertEquals(before, (result as RefreshResult.Failure).cached)
        assertEquals(before, store.weatherFlow.first())
    }

    @Test
    fun `failure without cache reports null cached state`() = runTest {
        api.fail = IOException("offline")

        val result = repo.refresh() as RefreshResult.Failure

        assertNull(result.cached)
    }

    @Test
    fun `searchCity builds readable labels`() = runTest {
        val results = repo.searchCity("ber")

        assertEquals(1, results.size)
        assertEquals("Berlin, Land Berlin, Germany", results[0].name)
        assertEquals(52.52, results[0].latitude, 0.0)
        assertTrue(repo.searchCity("  ").isEmpty())
    }
}
