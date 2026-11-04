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
    val sky: SkyState = SkyState.DEFAULT,
    val wildlife: Boolean = true,
)

object SceneSelector {

    private const val OVERRIDE_INTENSITY = 0.7f
    private const val FALLBACK_INTENSITY = 0.5f

    /**
     * Picks the weather layer, time of day and landscape from the cached weather and settings.
     * With no cached weather yet, shows a clear sky (stars at night by the local clock).
     */
    /**
     * [nowMillis] switches on real sun and moon positions for the observer's location; without it
     * (unit tests, previews) the sun and moon sit at a typical spot for the time of day.
     */
    fun select(weather: WeatherState?, settings: Settings, hour: Int, nowMillis: Long? = null): SceneSpec {
        val isDay = weather?.isDay ?: TimeOfDay.isDaytimeByClock(hour)
        val override = settings.previewOverride
        val type = override ?: weather?.scene ?: SceneType.CLEAR
        val baseIntensity = when {
            override != null -> OVERRIDE_INTENSITY
            weather != null -> weather.intensity
            else -> FALLBACK_INTENSITY
        }
        val realWind = (weather?.windSpeedKmh ?: 8.0).toFloat()
        val timeOfDay = settings.timeOverride ?: TimeOfDay.from(isDay, hour)
        return SceneSpec(
            type = type,
            intensity = (baseIntensity * settings.intensity).coerceIn(0.05f, 1.5f),
            // A forced Windy scene needs real wind to look windy.
            windKmh = if (type == SceneType.WINDY) maxOf(realWind, 50f) else realWind,
            cloudCover = weather?.cloudCover ?: 60,
            timeOfDay = timeOfDay,
            temperatureC = weather?.temperatureC,
            showTemperature = settings.showTemperature,
            fpsCap = settings.fpsCap.coerceIn(15, 60),
            dim = settings.dim.coerceIn(0f, 0.7f),
            landscape = settings.landscape,
            sky = skyFor(weather, settings, timeOfDay, nowMillis),
            wildlife = settings.wildlife,
        )
    }

    /** Typical sun and moon spots for each time of day, used for previews and tests. */
    private fun representative(t: TimeOfDay, phase: Float): SkyState = when (t) {
        TimeOfDay.DAWN -> SkyState(0.2f, 0.5f, true, 0.7f, 0.3f, false, phase)
        TimeOfDay.DAY -> SkyState(0.35f, 0.28f, true, 0.7f, 0.3f, false, phase)
        TimeOfDay.DUSK -> SkyState(0.8f, 0.5f, true, 0.3f, 0.3f, false, phase)
        TimeOfDay.NIGHT -> SkyState(0.35f, 0.7f, false, 0.3f, 0.3f, true, phase)
    }

    private fun skyFor(weather: WeatherState?, settings: Settings, timeOfDay: TimeOfDay, nowMillis: Long?): SkyState {
        if (nowMillis == null) return representative(timeOfDay, settings.moonPhaseOverride ?: 0.5f)
        val phase = settings.moonPhaseOverride ?: Astro.moonPhase(nowMillis)
        // Previews of a chosen time of day keep the typical positions, but with today's moon phase.
        if (settings.timeOverride != null) return representative(timeOfDay, phase)
        val loc = weather?.location
        val lat = loc?.latitude ?: 20.0
        val lon = loc?.longitude ?: (java.util.TimeZone.getDefault().getOffset(nowMillis) / 3_600_000.0 * 15.0)
        val sun = Astro.sunPosition(nowMillis, lat, lon)
        val moon = Astro.moonPosition(nowMillis, lat, lon)
        val (sx, sy) = Astro.toScreen(sun)
        val (mx, my) = Astro.toScreen(moon)
        return SkyState(sx, sy, sun.elevationDeg > -1.0, mx, my, moon.elevationDeg > 0.0, phase)
    }
}