package com.skymood.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Where the sun and moon are, and what the moon looks like, mapped to wallpaper coordinates. */
data class SkyState(
    /** 0..1 across the screen, 0 = left. */
    val sunX: Float,
    /** 0..1 down the screen; the horizon is at about 0.6. */
    val sunY: Float,
    val sunVisible: Boolean,
    val moonX: Float,
    val moonY: Float,
    val moonVisible: Boolean,
    /** 0 = new moon, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    val moonPhase: Float,
) {
    companion object {
        val DEFAULT = SkyState(0.35f, 0.28f, true, 0.3f, 0.3f, true, 0.5f)
    }
}

/**
 * Low-precision astronomy (good to about a degree for the sun and a few degrees for the moon),
 * plenty for placing a sun or moon on a wallpaper.
 */
object Astro {
    private const val SYNODIC_DAYS = 29.530588853
    private const val NEW_MOON_JD = 2451550.26 // 2000-01-06 18:14 UTC

    fun julianDay(epochMillis: Long): Double = epochMillis / 86_400_000.0 + 2_440_587.5

    /** Moon phase as a fraction of the cycle: 0 new, 0.5 full. */
    fun moonPhase(epochMillis: Long): Float {
        val cycles = (julianDay(epochMillis) - NEW_MOON_JD) / SYNODIC_DAYS
        return (cycles - floor(cycles)).toFloat()
    }

    /** Fraction of the moon's disc that is lit, 0..1. */
    fun moonIllumination(phase: Float): Float = ((1.0 - cos(2.0 * PI * phase)) / 2.0).toFloat()

    class Horizontal(val azimuthDeg: Double, val elevationDeg: Double)

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun wrap(d: Double) = d - 360.0 * floor(d / 360.0)

    /** The sun's position for an observer at [latDeg], [lonDeg] (east positive). */
    fun sunPosition(epochMillis: Long, latDeg: Double, lonDeg: Double): Horizontal {
        val n = julianDay(epochMillis) - 2451545.0
        return horizontal(n, sunLongitude(n), latDeg, lonDeg)
    }

    /** The moon's position, assuming it lies on the ecliptic (error up to about 5 degrees). */
    fun moonPosition(epochMillis: Long, latDeg: Double, lonDeg: Double): Horizontal {
        val n = julianDay(epochMillis) - 2451545.0
        val lambda = wrap(sunLongitude(n) + moonPhase(epochMillis) * 360.0)
        return horizontal(n, lambda, latDeg, lonDeg)
    }

    private fun sunLongitude(n: Double): Double {
        val l = wrap(280.460 + 0.9856474 * n)
        val g = rad(wrap(357.528 + 0.9856003 * n))
        return wrap(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
    }

    private fun horizontal(n: Double, lambdaDeg: Double, latDeg: Double, lonDeg: Double): Horizontal {
        val lambda = rad(lambdaDeg)
        val eps = rad(23.439 - 0.0000004 * n)
        val ra = atan2(cos(eps) * sin(lambda), cos(lambda))
        val dec = asin(sin(eps) * sin(lambda))
        val gmst = wrap(280.46061837 + 360.98564736629 * n)
        val ha = rad(wrap(gmst + lonDeg) - deg(ra))
        val lat = rad(latDeg)
        val sinEl = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha)
        val el = asin(sinEl.coerceIn(-1.0, 1.0))
        val az = atan2(-cos(dec) * sin(ha), sin(dec) * cos(lat) - cos(dec) * sin(lat) * cos(ha))
        return Horizontal(wrap(deg(az)), deg(el))
    }

    /** Maps a horizontal position to wallpaper coordinates, looking towards the south. */
    fun toScreen(h: Horizontal): Pair<Float, Float> {
        val x = (0.5 + (h.azimuthDeg - 180.0) / 200.0).coerceIn(0.04, 0.96)
        val y = (0.6 - h.elevationDeg.coerceAtLeast(-5.0) / 75.0 * 0.5).coerceIn(0.08, 0.62)
        return x.toFloat() to y.toFloat()
    }
}
