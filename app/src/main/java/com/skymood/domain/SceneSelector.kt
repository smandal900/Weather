package com.skymood.domain

enum class TimeOfDay(val label: String) {
    DAWN("Morning"),
    DAY("Day"),
    DUSK("Evening"),
    NIGHT("Night");

    companion object {
        /** [isDay] comes from Open-Meteo; the local hour splits daylight into morning, day and evening. */
        fun from(isDay: Boolean, hour: Int): TimeOfDay = when {
            !isDay -> NIGHT
            hour in 5..7 -> DAWN
            hour in 17..20 -> DUSK
            else -> DAY
        }

        fun isDaytimeByClock(hour: Int): Boolean = hour in 6..17
    }
}

/** Everything the wallpaper needs to know to pick and tune a scene. */
data class SceneSpec(
    val type: SceneType,
    val intensity: Float,
    val windKmh: Float,
    val cloudCover: Int,
    val timeOfDay: TimeOfDay,
    val temperatureC: Double?,
    val showTemperature: Boolean,
    val fpsCap: Int,
    val dim: Float = 0f,
    val landscape: LandscapeType = LandscapeType.FOREST,
)

object SceneSelector {

    private const val OVERRIDE_INTENSITY = 0.7f
    private const val FALLBACK_INTENSITY = 0.5f

    /**
     * Picks the weather layer, time of day and landscape from the cached weather and settings.
     * With no cached weather yet, shows a clear sky (stars at night by the local clock).
     */
    fun select(weather: WeatherState?, settings: Settings, hour: Int): SceneSpec {
        val isDay = weather?.isDay ?: TimeOfDay.isDaytimeByClock(hour)
        val override = settings.previewOverride
        val type = override ?: weather?.scene ?: SceneType.CLEAR
        val baseIntensity = when {
            override != null -> OVERRIDE_INTENSITY
            weather != null -> weather.intensity
            else -> FALLBACK_INTENSITY
        }
        val realWind = (weather?.windSpeedKmh ?: 8.0).toFloat()
        return SceneSpec(
            type = type,
            intensity = (baseIntensity * settings.intensity).coerceIn(0.05f, 1.5f),
            // A forced Windy scene needs real wind to look windy.
            windKmh = if (type == SceneType.WINDY) maxOf(realWind, 50f) else realWind,
            cloudCover = weather?.cloudCover ?: 60,
            timeOfDay = settings.timeOverride ?: TimeOfDay.from(isDay, hour),
            temperatureC = weather?.temperatureC,
            showTemperature = settings.showTemperature,
            fpsCap = settings.fpsCap.coerceIn(15, 60),
            dim = settings.dim.coerceIn(0f, 0.7f),
            landscape = settings.landscape,
        )
    }
}
