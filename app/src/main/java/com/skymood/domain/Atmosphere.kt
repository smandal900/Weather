package com.skymood.domain

import kotlin.math.abs
import kotlin.math.sin

/**
 * What the sky is actually like, taken from the weather report and turned into the numbers the
 * wallpaper needs. Nothing here is random: the amount of cloud at each height, how bright the
 * sun and moon look through it, how dark the clouds are, how clear the air is and which way
 * everything drifts all come from the report.
 */
data class Atmosphere(
    /** Total cloud cover 0..1. */
    val cover: Float,
    /** Low, mid and high cloud cover 0..1 (low clouds fill the near sky, high clouds are thin and far). */
    val low: Float,
    val mid: Float,
    val high: Float,
    /** How clear the air is, 0 thick fog .. 1 crystal clear, from visibility and humidity. */
    val clarity: Float,
    /** How dark and heavy the clouds look, 0..1, from cloud cover, rain and storms. */
    val darkness: Float,
    /** Which way the wind carries clouds and rain on screen: -1 left .. +1 right (never close to 0). */
    val drift: Float,
    val precipMm: Float,
) {
    /** 0 (clear) .. 1 (no sun or moon visible) when looking through the clouds. */
    val obscuration: Float get() = (cover * 0.92f).coerceIn(0f, 1f)

    /** Clarity in four steps, so a slow change does not repaint the landscape over and over. */
    val clarityBucket: Int get() = Math.round(clarity * 4f).coerceIn(0, 4)

    companion object {
        val DEFAULT = Atmosphere(0.3f, 0.2f, 0.2f, 0.2f, 1f, 0.1f, 1f, 0f)

        /** Looking south, east is on the left, so an east wind (from the east) pushes clouds right. */
        private fun drift(windFromDeg: Int): Float {
            val towards = Math.toRadians(((windFromDeg + 180) % 360).toDouble())
            val vx = (-sin(towards)).toFloat()
            val sign = if (vx >= 0f) 1f else -1f
            return sign * maxOf(0.25f, abs(vx))
        }

        fun of(w: WeatherState): Atmosphere {
            val cover = (w.cloudCover / 100f).coerceIn(0f, 1f)
            fun layer(v: Int) = if (v < 0) cover else (v / 100f).coerceIn(0f, 1f)
            val visKm = (w.visibilityM / 1000.0).toFloat()
            // Visibility of 15 km or more is clear; fog is under 1 km. Very humid air is a little hazier.
            // The square root spreads the foggy end out (100 m looks very different from 1 km).
            val vis = Math.sqrt((visKm / 15f).coerceIn(0f, 1f).toDouble()).toFloat().coerceIn(0.05f, 1f)
            val humid = 1f - 0.2f * ((w.humidity - 70) / 30f).coerceIn(0f, 1f)
            val precip = (w.precipitationMm / 6.0).toFloat().coerceIn(0f, 1f)
            val storm = if (w.weatherCode in 95..99) 0.35f else 0f
            val darkness = (0.2f * cover + 0.65f * precip + storm + (if (cover > 0.85f) 0.1f else 0f)).coerceIn(0f, 1f)
            return Atmosphere(
                cover = cover, low = layer(w.cloudLow), mid = layer(w.cloudMid), high = layer(w.cloudHigh),
                clarity = (vis * humid).coerceIn(0.05f, 1f), darkness = darkness,
                drift = drift(w.windFromDeg), precipMm = w.precipitationMm.toFloat(),
            )
        }

        /** Believable numbers for a previewed weather type, so the preview matches a real report. */
        fun forPreview(type: SceneType): Atmosphere = when (type) {
            SceneType.CLEAR -> Atmosphere(0.08f, 0.04f, 0.05f, 0.12f, 1f, 0.05f, 1f, 0f)
            SceneType.PARTLY_CLOUDY -> Atmosphere(0.5f, 0.45f, 0.3f, 0.25f, 0.95f, 0.15f, 1f, 0f)
            SceneType.OVERCAST -> Atmosphere(0.95f, 0.9f, 0.8f, 0.6f, 0.8f, 0.45f, 1f, 0f)
            SceneType.DRIZZLE -> Atmosphere(0.95f, 0.95f, 0.7f, 0.4f, 0.55f, 0.55f, 1f, 0.5f)
            SceneType.HEAVY_RAIN -> Atmosphere(1f, 1f, 0.9f, 0.6f, 0.45f, 0.8f, 1f, 4f)
            SceneType.STORM -> Atmosphere(1f, 1f, 1f, 0.8f, 0.4f, 1f, 1f, 8f)
            SceneType.FOG -> Atmosphere(1f, 1f, 0.5f, 0.1f, 0.12f, 0.3f, 1f, 0f)
            SceneType.SNOW -> Atmosphere(0.95f, 0.9f, 0.8f, 0.5f, 0.5f, 0.35f, 1f, 1f)
            SceneType.WINDY -> Atmosphere(0.4f, 0.2f, 0.35f, 0.4f, 1f, 0.12f, 1f, 0f)
            SceneType.HAZE -> Atmosphere(0.3f, 0.1f, 0.25f, 0.35f, 0.25f, 0.2f, 1f, 0f)
        }
    }
}
