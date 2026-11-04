package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import com.skymood.domain.TimeOfDay

/** Clear weather: the sunny [ClearScene] by day, the starry [NightScene] at night. */
class ClearSkyScene : BaseScene() {

    private val day = ClearScene()
    private val night = NightScene()
    private val active: Scene get() = if (timeOfDay == TimeOfDay.NIGHT) night else day

    override fun onResize() {
        day.resize(width, height)
        night.resize(width, height)
    }

    override fun onIntensityChanged() {
        day.setIntensity(intensity)
        night.setIntensity(intensity)
    }

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        super.setEnvironment(windKmh, cloudCover, timeOfDay)
        day.setEnvironment(windKmh, cloudCover, timeOfDay)
        night.setEnvironment(windKmh, cloudCover, timeOfDay)
    }

    override fun setParallax(offset: Float) {
        super.setParallax(offset)
        day.setParallax(offset)
        night.setParallax(offset)
    }

    override fun update(dtMs: Long) = active.update(dtMs)
    override fun drawBackground(canvas: Canvas) = active.drawBackground(canvas)
    override fun drawForeground(canvas: Canvas) = active.drawForeground(canvas)
}
