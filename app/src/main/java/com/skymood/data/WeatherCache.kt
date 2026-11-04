package com.skymood.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skymood.domain.SavedLocation
import com.skymood.domain.Settings
import com.skymood.domain.WeatherState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

private val Context.skyDataStore: DataStore<Preferences> by preferencesDataStore(name = "skymood")

/** DataStore-backed [WeatherStore]. Each object is stored as one JSON string. */
class WeatherCache(context: Context) : WeatherStore {

    private val store = context.applicationContext.skyDataStore
    private val json = WeatherApi.json

    override val weather: Flow<WeatherState?> = read(KEY_WEATHER, WeatherState.serializer())
    override val lastLocation: Flow<SavedLocation?> = read(KEY_LOCATION, SavedLocation.serializer())
    override val settings: Flow<Settings> =
        read(KEY_SETTINGS, Settings.serializer()).map { it ?: Settings() }.distinctUntilChanged()

    override suspend fun saveWeather(state: WeatherState) = write(KEY_WEATHER, WeatherState.serializer(), state)

    override suspend fun saveLastLocation(location: SavedLocation) =
        write(KEY_LOCATION, SavedLocation.serializer(), location)

    override suspend fun updateSettings(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val current = prefs[KEY_SETTINGS]?.let { decode(it, Settings.serializer()) } ?: Settings()
            prefs[KEY_SETTINGS] = json.encodeToString(Settings.serializer(), transform(current))
        }
    }

    private fun <T> read(key: Preferences.Key<String>, serializer: KSerializer<T>): Flow<T?> =
        store.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { prefs -> prefs[key]?.let { decode(it, serializer) } }
            .distinctUntilChanged()

    private suspend fun <T> write(key: Preferences.Key<String>, serializer: KSerializer<T>, value: T) {
        store.edit { it[key] = json.encodeToString(serializer, value) }
    }

    private fun <T> decode(raw: String, serializer: KSerializer<T>): T? =
        runCatching { json.decodeFromString(serializer, raw) }.getOrNull()

    private companion object {
        val KEY_WEATHER = stringPreferencesKey("weather")
        val KEY_LOCATION = stringPreferencesKey("last_location")
        val KEY_SETTINGS = stringPreferencesKey("settings")
    }
}
