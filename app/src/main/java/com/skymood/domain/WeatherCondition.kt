package com.skymood.domain

/** Broad weather condition derived from a WMO weather code. */
enum class WeatherCondition { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

/** The animated scene the wallpaper shows. */
enum class SceneType(val label: String) {
    CLEAR("Clear"),
    NIGHT("Night"),
    CLOUD("Clouds"),
    FOG("Fog"),
    RAIN("Rain"),
    SNOW("Snow"),
    STORM("Storm"),
}
