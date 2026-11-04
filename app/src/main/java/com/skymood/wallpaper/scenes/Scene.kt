package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import com.skymood.domain.TimeOfDay

/**
 * One animated weather scene. Scenes draw onto an Android [Canvas]; an OpenGL ES backend
 * would provide its own implementations behind the same lifecycle.
 *
 * Implementations must not allocate inside [update] or [draw].
 */
interface Scene {
    fun update(dtMs: Long)
    fun draw(canvas: Canvas)
    /** 0..1 is the normal range; up to 1.5 when the user boosts the intensity slider. */
    fun setIntensity(intensity: Float)
    fun resize(width: Int, height: Int)

    fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {}
    /** -1 (first home screen) .. 1 (last home screen). */
    fun setParallax(offset: Float) {}
}

abstract class BaseScene : Scene {
    protected var width = 1
    protected var height = 1
    @JvmField protected var intensity = 0.5f
    protected var windKmh = 0f
    protected var cloudCover = 50
    protected var timeOfDay = TimeOfDay.DAY
    @JvmField protected var parallax = 0f

    protected val sky = SkyGradient()

    /** Horizontal shift in pixels for far-away elements. */
    protected val parallaxPx: Float get() = -parallax * width * 0.04f

    /** Wind tilt in radians: 0 when calm, about 35 degrees at 60 km/h or more. */
    protected val windAngle: Float get() = (windKmh / 60f).coerceIn(0f, 1f) * 0.6f

    final override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val changed = width != this.width || height != this.height
        this.width = width
        this.height = height
        if (changed) onResize()
    }

    final override fun setIntensity(intensity: Float) {
        val v = intensity.coerceIn(0f, 1.5f)
        if (v != this.intensity) {
            this.intensity = v
            onIntensityChanged()
        }
    }

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        this.windKmh = windKmh
        this.cloudCover = cloudCover
        if (timeOfDay != this.timeOfDay) {
            this.timeOfDay = timeOfDay
            onTimeOfDayChanged()
        }
    }

    override fun setParallax(offset: Float) {
        parallax = offset.coerceIn(-1f, 1f)
    }

    protected open fun onResize() {}
    protected open fun onIntensityChanged() {}
    protected open fun onTimeOfDayChanged() {}
}
