package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Paint
import com.skymood.domain.Atmosphere
import com.skymood.domain.TimeOfDay

/**
 * The sky of clear, partly cloudy and windy weather: the sunny [ClearScene] by day, the starry
 * [NightScene] at night, with the clouds from the weather report drifting across it. How much
 * cloud there is, how grey the sky turns and how much of the sun, moon and stars still shows
 * through all come from the report, so a clear sky has no clouds and a mostly cloudy one hides
 * the sun and stars.
 */
class ClearSkyScene : BaseScene() {

    private val day = ClearScene()
    private val night = NightScene()
    private val clouds = CloudScene(CloudScene.Style.FAIR, drawSky = false)
    private val veilPaint = Paint()
    private val active: Scene get() = if (timeOfDay == TimeOfDay.NIGHT) night else day

    override fun onResize() {
        day.resize(width, height)
        night.resize(width, height)
        clouds.resize(width, height)
    }

    override fun onIntensityChanged() {
        day.setIntensity(intensity)
        night.setIntensity(intensity)
        clouds.setIntensity(intensity)
    }

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        super.setEnvironment(windKmh, cloudCover, timeOfDay)
        day.setEnvironment(windKmh, cloudCover, timeOfDay)
        night.setEnvironment(windKmh, cloudCover, timeOfDay)
        clouds.setEnvironment(windKmh, cloudCover, timeOfDay)
        veilPaint.color = if (timeOfDay == TimeOfDay.NIGHT) 0xFF161A22.toInt() else 0xFF9AA5B3.toInt()
    }

    override fun setSky(state: com.skymood.domain.SkyState) {
        super.setSky(state)
        day.setSky(state)
        night.setSky(state)
        clouds.setSky(state)
    }

    override fun setAtmosphere(atmosphere: Atmosphere) {
        super.setAtmosphere(atmosphere)
        day.setAtmosphere(atmosphere)
        night.setAtmosphere(atmosphere)
        clouds.setAtmosphere(atmosphere)
    }

    override fun setParallax(offset: Float) {
        super.setParallax(offset)
        day.setParallax(offset)
        night.setParallax(offset)
        clouds.setParallax(offset)
    }

    override fun update(dtMs: Long) {
        active.update(dtMs)
        clouds.update(dtMs)
    }

    override fun drawBackground(canvas: Canvas) {
        active.drawBackground(canvas)
        // The more of the sky is covered, the greyer and flatter it looks.
        val a = (atmo.cover * 0.4f * 255f).toInt().coerceIn(0, 110)
        if (a > 0) {
            veilPaint.alpha = a
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), veilPaint)
        }
        clouds.drawBackground(canvas)
    }

    override fun drawForeground(canvas: Canvas) = active.drawForeground(canvas)
}
